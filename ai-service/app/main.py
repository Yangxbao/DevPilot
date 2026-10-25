"""DevPilot AI 服务入口。

路由分两组：
- /internal/*：Java 后端调用（内部 Token 鉴权）：索引推送 / 检索 / 索引清理
- /agent/*：Agent 工作流（问答与 Bug 分析）
- /health：健康检查（公开，供 Docker/编排探测）
"""
from __future__ import annotations

import logging

import fastapi
from fastapi import Depends, HTTPException

from . import retrieval, workflow
from .config import Settings, get_settings
from .embeddings import EmbeddingUnavailable, build_embedding_provider
from .llm import LLMUnavailable, build_llm
from .schemas import (AgentOut, BugIn, HealthOut, QaIn, ReindexIn, ReindexOut, SearchIn, SearchOut)
from .security import require_internal_token
from .store import IndexStore

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
logger = logging.getLogger("devpilot-ai")

app = fastapi.FastAPI(title="DevPilot AI Service", version="0.1.0")

_store: IndexStore | None = None


def get_store() -> IndexStore:
    global _store
    if _store is None:
        _store = IndexStore(get_settings().data_dir)
    return _store


@app.get("/health", response_model=HealthOut)
def health() -> HealthOut:
    settings: Settings = get_settings()
    store = get_store()
    return HealthOut(
        status="UP",
        llm_provider=settings.llm_provider,
        embedding_provider=settings.embedding_provider,
        vector_ready=True,  # 具体仓库的向量可用性在检索响应中逐次返回
    )


@app.post("/internal/reindex", response_model=ReindexOut,
          dependencies=[Depends(require_internal_token)])
def reindex(payload: ReindexIn) -> ReindexOut:
    settings = get_settings()
    store = get_store()
    embedding = build_embedding_provider(settings)
    chunks = [c.model_dump() for c in payload.chunks]
    indexed, vector_enabled = store.upsert_chunks(payload.repository_id, chunks, embedding)
    if not vector_enabled:
        logger.warning("repo=%s embedding 不可用，仅建立关键词索引", payload.repository_id)
    return ReindexOut(repository_id=payload.repository_id, indexed=indexed, vector_enabled=vector_enabled)


@app.delete("/internal/index/{repository_id}", dependencies=[Depends(require_internal_token)])
def delete_index(repository_id: int) -> dict:
    get_store().clear(repository_id)
    return {"deleted": repository_id}


@app.post("/internal/search", response_model=SearchOut,
          dependencies=[Depends(require_internal_token)])
def search(payload: SearchIn) -> SearchOut:
    settings = get_settings()
    store = get_store()
    embedding = build_embedding_provider(settings)
    mode = payload.mode if payload.mode in ("keyword", "semantic", "hybrid") else "hybrid"
    evidence, vector_enabled = retrieval.search(
        store, payload.repository_id, [payload.query], payload.top_k, mode, embedding)
    results = [
        {
            "chunk_id": ev.chunk_id,
            "file_id": ev.file_id,
            "path": ev.path,
            "language": ev.language,
            "start_line": ev.start_line,
            "end_line": ev.end_line,
            "snippet": ev.snippet,
            "score": ev.score,
            "channel": ev.channels,
        }
        for ev in evidence
    ]
    return SearchOut(vector_enabled=vector_enabled, results=results)


def _to_agent_out(result: workflow.WorkflowResult) -> AgentOut:
    return AgentOut(
        mode=result.mode,
        provider=result.provider,
        model=result.model,
        answer=result.answer,
        confidence=result.confidence,
        evidence=result.evidence,
        steps=result.steps,
        warnings=result.warnings,
        durationMs=result.duration_ms,
        report=result.report,
    )


@app.post("/agent/qa", response_model=AgentOut, dependencies=[Depends(require_internal_token)])
def agent_qa(payload: QaIn) -> AgentOut:
    settings = get_settings()
    try:
        result = workflow.run_qa(settings, get_store(), build_llm(settings),
                                 build_embedding_provider(settings), payload.repository_id,
                                 payload.question, payload.top_k)
    except workflow.AgentInputError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except LLMUnavailable as exc:
        logger.warning("qa LLM 不可用: %s", exc)
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    return _to_agent_out(result)


@app.post("/agent/bug-analysis", response_model=AgentOut, dependencies=[Depends(require_internal_token)])
def agent_bug(payload: BugIn) -> AgentOut:
    settings = get_settings()
    try:
        result = workflow.run_bug_analysis(settings, get_store(), build_llm(settings),
                                           build_embedding_provider(settings), payload.repository_id,
                                           payload.title, payload.error_message,
                                           payload.stack_trace, payload.repro_steps, payload.top_k)
    except workflow.AgentInputError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except LLMUnavailable as exc:
        logger.warning("bug-analysis LLM 不可用: %s", exc)
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    return _to_agent_out(result)
