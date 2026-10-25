"""集中配置：全部来自环境变量（12-factor），不落任何密钥进代码。"""
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # Java <-> Python 内部契约 Token
    internal_token: str = "dev-internal-token"

    # 大模型 Provider：mock（默认，离线可跑）| openai（OpenAI 兼容 API）
    llm_provider: str = "mock"
    llm_base_url: str = "https://api.openai.com/v1"
    llm_api_key: str = ""
    llm_model: str = "gpt-4o-mini"
    llm_timeout_seconds: float = 60.0
    llm_max_retries: int = 2

    # 向量 Provider：hashing（本地降级实现，零依赖）| openai
    embedding_provider: str = "hashing"
    embedding_base_url: str = "https://api.openai.com/v1"
    embedding_api_key: str = ""
    embedding_model: str = "text-embedding-3-small"
    embedding_dim: int = 256
    embedding_timeout_seconds: float = 30.0

    # SQLite 检索库目录（可随时重建的派生数据）
    data_dir: str = "./data/index-store"

    # 输入与资源上限（与 docs/AI_WORKFLOW.md 对应）
    max_question_chars: int = 2000
    max_stack_chars: int = 8000
    max_chunks_per_request: int = 500
    max_chunk_chars: int = 8000
    max_evidence_blocks: int = 12
    max_evidence_total_chars: int = 24000
    retrieval_top_k: int = 8


_settings: Settings | None = None


def get_settings() -> Settings:
    global _settings
    if _settings is None:
        _settings = Settings()
    return _settings
