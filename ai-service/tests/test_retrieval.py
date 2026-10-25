"""混合检索：RRF 融合、降级行为。"""
from app.embeddings import EmbeddingUnavailable, HashingEmbedding
from app.retrieval import search

from conftest import SAMPLE_CHUNKS


def test_hybrid_returns_both_channels(store, embedding):
    store.upsert_chunks(1, SAMPLE_CHUNKS, embedding)
    evidence, vector_enabled = search(store, 1, ["login username"], 5, "hybrid", embedding)
    assert evidence
    assert vector_enabled is True


def test_keyword_mode_only(store, embedding):
    store.upsert_chunks(1, SAMPLE_CHUNKS, embedding)
    evidence, _ = search(store, 1, ["UserDao"], 5, "keyword", embedding)
    assert all("keyword" in ev.channels for ev in evidence)


def test_vector_unavailable_falls_back(store):
    class BrokenProvider(HashingEmbedding):
        def embed_batch(self, texts):
            raise EmbeddingUnavailable("offline")

    store.upsert_chunks(1, SAMPLE_CHUNKS, HashingEmbedding(64))
    # 检索时 provider 坏了：应降级关键词而非抛错
    evidence, vector_enabled = search(store, 1, ["login"], 5, "semantic", BrokenProvider(64))
    assert vector_enabled is False
    assert evidence  # 关键词兜底仍然有结果


def test_empty_store_returns_no_evidence(store, embedding):
    evidence, _ = search(store, 99, ["anything"], 5, "hybrid", embedding)
    assert evidence == []
