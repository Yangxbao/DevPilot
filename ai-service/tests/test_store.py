"""IndexStore：写入、关键词检索、清理、向量通道开关。"""
from app.embeddings import HashingEmbedding, EmbeddingUnavailable

from conftest import SAMPLE_CHUNKS


def test_upsert_and_count(store, embedding):
    indexed, vector_enabled = store.upsert_chunks(1, SAMPLE_CHUNKS, embedding)
    assert indexed == 3
    assert vector_enabled is True
    assert store.count(1) == 3


def test_keyword_search_hits_code(store, embedding):
    store.upsert_chunks(1, SAMPLE_CHUNKS, embedding)
    hits = store.keyword_search(1, "login username", 10)
    assert hits, "关键词 login/username 应命中 UserService"
    assert any("UserService" in h["path"] for h in hits)


def test_keyword_search_chinese(store, embedding):
    store.upsert_chunks(1, SAMPLE_CHUNKS, embedding)
    hits = store.keyword_search(1, "登录接口", 10)
    assert any("api.md" in h["path"] for h in hits)


def test_clear(store, embedding):
    store.upsert_chunks(1, SAMPLE_CHUNKS, embedding)
    store.clear(1)
    assert store.count(1) == 0


def test_embedding_failure_degrades_to_keyword(store):
    class BrokenProvider(HashingEmbedding):
        def embed_batch(self, texts):
            raise EmbeddingUnavailable("boom")

    indexed, vector_enabled = store.upsert_chunks(2, SAMPLE_CHUNKS, BrokenProvider(64))
    assert indexed == 3
    assert vector_enabled is False
    # 关键词仍可用
    assert store.keyword_search(2, "UserDao", 10)
    # 向量通道为空
    assert store.has_vectors(2) is False
