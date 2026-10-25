"""API 请求/响应模型（pydantic v2），与 docs/API_CONTRACT.md 第 2 节对应。

Java 侧使用 camelCase JSON，本服务统一通过 alias_generator 做驼峰映射：
- 请求解析接受 camelCase（populate_by_name 也兼容 snake_case，便于测试）
- 响应序列化默认按 alias 输出 camelCase（FastAPI response_model_by_alias=True）
"""
from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class ApiModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class ChunkIn(ApiModel):
    id: str
    file_id: int = 0
    path: str = ""
    language: str = ""
    start_line: int = 0
    end_line: int = 0
    content: str


class ReindexIn(ApiModel):
    repository_id: int
    chunks: list[ChunkIn] = Field(max_length=500)


class ReindexOut(ApiModel):
    repository_id: int
    indexed: int
    vector_enabled: bool


class SearchIn(ApiModel):
    repository_id: int
    query: str
    top_k: int = 10
    mode: str = "hybrid"


class SearchHitOut(ApiModel):
    chunk_id: str
    file_id: int
    path: str
    language: str
    start_line: int
    end_line: int
    snippet: str
    score: float
    channel: str


class SearchOut(ApiModel):
    vector_enabled: bool
    results: list[SearchHitOut]


class QaIn(ApiModel):
    repository_id: int
    question: str
    top_k: int = 8


class BugIn(ApiModel):
    repository_id: int
    title: str
    error_message: str
    stack_trace: str = ""
    repro_steps: str = ""
    top_k: int = 8


class EvidenceOut(ApiModel):
    chunk_id: str
    file_id: int
    path: str
    language: str
    start_line: int
    end_line: int
    snippet: str
    score: float


class StepOut(ApiModel):
    name: str
    status: str
    detail: str = ""
    duration_ms: int = 0


class ReportOut(ApiModel):
    summary: str
    possible_causes: list[str]
    impact: str
    verification_steps: list[str]
    fix_directions: str
    confidence: float


class AgentOut(ApiModel):
    mode: str
    provider: str
    model: str
    answer: str
    confidence: float
    evidence: list[EvidenceOut]
    steps: list[StepOut]
    warnings: list[str]
    duration_ms: int
    report: ReportOut | None = None


class HealthOut(ApiModel):
    status: str
    llm_provider: str
    embedding_provider: str
    vector_ready: bool
