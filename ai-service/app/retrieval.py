"""混合检索：关键词(FTS5) + 向量(余弦) 双通道，RRF（Reciprocal Rank Fusion）融合。

score = Σ 1 / (60 + rank_i)，命中两个通道的证据天然靠前。
Embedding 不可用时自动只跑关键词通道，vector_enabled 如实返回 False。
"""
from __future__ import annotations

from dataclasses import dataclass

from .embeddings import EmbeddingProvider, EmbeddingUnavailable
from .store import IndexStore

RRF_K = 60


@dataclass
class Evidence:
    chunk_id: str
    file_id: int
    path: str
    language: str
    start_line: int
    end_line: int
    content: str
    snippet: str
    score: float
    channels: str


def make_snippet(content: str, window: int = 240) -> str:
    text = content.strip()
    if len(text) <= window:
        return text
    return text[:window] + "…"


def _to_evidence(row: dict, score: float, channels: str) -> Evidence:
    return Evidence(
        chunk_id=row["chunk_id"],
        file_id=row.get("file_id") or 0,
        path=row["path"],
        language=row.get("language", ""),
        start_line=row.get("start_line", 0),
        end_line=row.get("end_line", 0),
        content=row["content"],
        snippet=make_snippet(row["content"]),
        score=round(score, 4),
        channels=channels,
    )


def search(
    store: IndexStore,
    repository_id: int,
    queries: list[str],
    top_k: int,
    mode: str,
    embedding_provider: EmbeddingProvider,
) -> tuple[list[Evidence], bool]:
    """多查询混合检索。mode: keyword | semantic | hybrid。返回 (去重后的证据, vector_enabled)。"""
    vector_enabled = store.has_vectors(repository_id)
    rrf: dict[str, float] = {}
    rows: dict[str, dict] = {}
    channels_of: dict[str, set[str]] = {}

    def add(channel: str, hits: list[dict]) -> None:
        for rank, row in enumerate(hits):
            cid = row["chunk_id"]
            rows[cid] = row
            channels_of.setdefault(cid, set()).add(channel)
            rrf[cid] = rrf.get(cid, 0.0) + 1.0 / (RRF_K + rank + 1)

    for query in queries:
        if not query.strip():
            continue
        if mode in ("keyword", "hybrid"):
            add("keyword", store.keyword_search(repository_id, query, top_k))
        if mode in ("semantic", "hybrid") and vector_enabled:
            try:
                qvec = embedding_provider.embed_batch([query])[0]
            except EmbeddingUnavailable:
                vector_enabled = False
                qvec = None
            if qvec is not None:
                add("vector", store.vector_search(repository_id, qvec, top_k))

    if mode == "semantic" and not vector_enabled:
        # 语义不可用时兜底关键词，保证仍有可用证据（调用方可知 vector_enabled=False）
        for query in queries:
            add("keyword", store.keyword_search(repository_id, query, top_k))

    ordered = sorted(rrf.items(), key=lambda item: item[1], reverse=True)[: max(top_k * 2, 10)]
    evidence = []
    for cid, score in ordered:
        channels = "+".join(sorted(channels_of[cid]))
        evidence.append(_to_evidence(rows[cid], score, channels))
    return evidence, vector_enabled
