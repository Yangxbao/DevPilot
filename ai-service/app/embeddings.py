"""可插拔 Embedding Provider。

- HashingEmbedding：本地确定性实现（特征哈希 + L2 归一化），零外部依赖，
  质量低于真实语义向量，用于离线演示与自动化测试，UI 会明确标注。
- OpenAIEmbedding：OpenAI 兼容 /embeddings 接口。任何失败抛 EmbeddingUnavailable，
  由上层决定降级（只保留关键词通道，绝不伪装向量检索成功）。
"""
from __future__ import annotations

import hashlib
import math
import re
import struct
from dataclasses import dataclass

import httpx

_TOKEN_RE = re.compile(r"[a-zA-Z_][a-zA-Z0-9_]*|\d+|[\u4e00-\u9fff]")


class EmbeddingUnavailable(Exception):
    pass


@dataclass
class EmbeddingResult:
    vectors: list[list[float] | None]
    provider: str
    model: str


class EmbeddingProvider:
    name = "base"
    model = "base"

    def embed_batch(self, texts: list[str]) -> list[list[float] | None]:  # pragma: no cover
        raise NotImplementedError


class HashingEmbedding(EmbeddingProvider):
    def __init__(self, dim: int = 256) -> None:
        self.dim = dim
        self.name = "hashing"
        self.model = f"hashing-{dim}"

    def _tokenize(self, text: str) -> list[str]:
        return _TOKEN_RE.findall(text.lower())[:2000]

    def embed_batch(self, texts: list[str]) -> list[list[float] | None]:
        vectors: list[list[float] | None] = []
        for text in texts:
            vec = [0.0] * self.dim
            tokens = self._tokenize(text)
            if not tokens:
                vectors.append(vec)
                continue
            for token in tokens:
                digest = hashlib.blake2b(token.encode("utf-8"), digest_size=8).digest()
                idx = int.from_bytes(digest, "little") % self.dim
                vec[idx] += 1.0
            norm = math.sqrt(sum(v * v for v in vec)) or 1.0
            vectors.append([v / norm for v in vec])
        return vectors


class OpenAIEmbedding(EmbeddingProvider):
    def __init__(self, base_url: str, api_key: str, model: str, timeout: float) -> None:
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.model = model
        self.timeout = timeout
        self.name = "openai"

    def embed_batch(self, texts: list[str]) -> list[list[float] | None]:
        if not texts:
            return []
        try:
            response = httpx.post(
                f"{self.base_url}/embeddings",
                headers={"Authorization": f"Bearer {self.api_key}"},
                json={"model": self.model, "input": texts},
                timeout=self.timeout,
            )
            response.raise_for_status()
            data = response.json()["data"]
            return [item["embedding"] for item in sorted(data, key=lambda x: x["index"])]
        except Exception as exc:  # 网络/鉴权/限流一律视为不可用
            raise EmbeddingUnavailable(f"embedding provider failed: {exc}") from exc


def build_embedding_provider(settings) -> EmbeddingProvider:
    if settings.embedding_provider == "openai":
        return OpenAIEmbedding(
            settings.embedding_base_url,
            settings.embedding_api_key,
            settings.embedding_model,
            settings.embedding_timeout_seconds,
        )
    return HashingEmbedding(settings.embedding_dim)


def pack_vector(vector: list[float]) -> bytes:
    return struct.pack(f"{len(vector)}f", *vector)


def unpack_vector(blob: bytes) -> list[float]:
    count = len(blob) // 4
    return list(struct.unpack(f"{count}f", blob))
