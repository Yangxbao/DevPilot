"""SQLite 检索库：每个仓库一个库文件，可随时删除重建（派生数据）。

- 关键词通道：FTS5 全文索引（bm25 排序）；中文按二元组(bigram)预处理索引与查询，
  弥补 unicode61 分词器不支持 CJK 词组匹配的问题。
- 向量通道：chunks.embedding BLOB（float32 数组），余弦相似度纯 Python 计算。
- Embedding 不可用时：只建 FTS，返回 vector_enabled=False（诚实降级）。

连接管理：Windows 下必须显式 close，否则文件句柄占用会导致 clear 失败。
"""
from __future__ import annotations

import os
import re
import sqlite3
from contextlib import closing

from .embeddings import EmbeddingProvider, EmbeddingUnavailable, pack_vector, unpack_vector

_SCHEMA = """
CREATE TABLE IF NOT EXISTS chunks (
    chunk_id   TEXT PRIMARY KEY,
    file_id    INTEGER NOT NULL,
    path       TEXT NOT NULL,
    language   TEXT NOT NULL DEFAULT '',
    start_line INTEGER NOT NULL,
    end_line   INTEGER NOT NULL,
    content    TEXT NOT NULL,
    embedding  BLOB
);
CREATE VIRTUAL TABLE IF NOT EXISTS chunks_fts USING fts5(
    chunk_id UNINDEXED, content, path UNINDEXED, language UNINDEXED
);
"""

_CJK_RUN = re.compile(r"([\u4e00-\u9fff]{2,})")


def cjk_bigrams(text: str) -> str:
    """把连续汉字串展开为二元组序列：登录接口 -> 登录 登接 接口。ASCII 不变。"""

    def expand(match: re.Match) -> str:
        run = match.group(1)
        if len(run) == 2:
            return run
        return " ".join(run[i : i + 2] for i in range(len(run) - 1))

    return _CJK_RUN.sub(expand, text)


class IndexStore:
    def __init__(self, data_dir: str) -> None:
        self.data_dir = data_dir
        os.makedirs(data_dir, exist_ok=True)

    def _connect(self, repository_id: int) -> sqlite3.Connection:
        path = os.path.join(self.data_dir, f"repo-{repository_id}.sqlite")
        conn = sqlite3.connect(path)
        conn.executescript(_SCHEMA)
        return conn

    def clear(self, repository_id: int) -> None:
        path = os.path.join(self.data_dir, f"repo-{repository_id}.sqlite")
        if os.path.exists(path):
            os.remove(path)

    def count(self, repository_id: int) -> int:
        with closing(self._connect(repository_id)) as conn:
            row = conn.execute("SELECT COUNT(*) FROM chunks").fetchone()
            return int(row[0]) if row else 0

    def upsert_chunks(
        self,
        repository_id: int,
        chunks: list[dict],
        embedding_provider: EmbeddingProvider,
    ) -> tuple[int, bool]:
        """写入一批分块。返回 (写入条数, vector_enabled)。"""
        if not chunks:
            return 0, True
        chunks = chunks[:500]
        texts = [c["content"][:8000] for c in chunks]
        vectors: list[list[float] | None]
        vector_enabled = True
        try:
            vectors = embedding_provider.embed_batch(texts)
        except EmbeddingUnavailable:
            vectors = [None] * len(chunks)
            vector_enabled = False

        inserted = 0
        with closing(self._connect(repository_id)) as conn:
            for chunk, vector in zip(chunks, vectors):
                emb_blob = pack_vector(vector) if vector is not None else None
                conn.execute(
                    "INSERT OR REPLACE INTO chunks VALUES (?,?,?,?,?,?,?,?)",
                    (
                        chunk["id"],
                        int(chunk.get("file_id") or 0),
                        chunk.get("path", ""),
                        chunk.get("language", ""),
                        int(chunk.get("start_line") or 0),
                        int(chunk.get("end_line") or 0),
                        chunk["content"][:8000],
                        emb_blob,
                    ),
                )
                conn.execute(
                    "INSERT INTO chunks_fts (chunk_id, content, path, language) VALUES (?,?,?,?)",
                    (chunk["id"], cjk_bigrams(chunk["content"][:8000]),
                     chunk.get("path", ""), chunk.get("language", "")),
                )
                inserted += 1
            conn.commit()
        return inserted, vector_enabled

    def keyword_search(self, repository_id: int, query: str, top_k: int) -> list[dict]:
        tokens = re.findall(r"\w+", cjk_bigrams(query))[:24]
        if not tokens:
            return []
        match_expr = " OR ".join(f'"{t}"' for t in tokens)
        with closing(self._connect(repository_id)) as conn:
            rows = conn.execute(
                """
                SELECT c.chunk_id, c.file_id, c.path, c.language, c.start_line, c.end_line, c.content
                FROM chunks_fts f JOIN chunks c ON c.chunk_id = f.chunk_id
                WHERE chunks_fts MATCH ?
                ORDER BY bm25(chunks_fts)
                LIMIT ?
                """,
                (match_expr, top_k),
            ).fetchall()
        return [self._row_to_dict(r) for r in rows]

    def vector_search(self, repository_id: int, query_vector: list[float], top_k: int) -> list[dict]:
        with closing(self._connect(repository_id)) as conn:
            rows = conn.execute(
                "SELECT chunk_id, file_id, path, language, start_line, end_line, content, embedding "
                "FROM chunks WHERE embedding IS NOT NULL"
            ).fetchall()
        scored = []
        for row in rows:
            vec = unpack_vector(row[7])
            score = _cosine(query_vector, vec)
            if score > 0:
                scored.append((score, self._row_to_dict(row)))
        scored.sort(key=lambda item: item[0], reverse=True)
        return [item[1] for item in scored[:top_k]]

    def has_vectors(self, repository_id: int) -> bool:
        with closing(self._connect(repository_id)) as conn:
            row = conn.execute("SELECT COUNT(*) FROM chunks WHERE embedding IS NOT NULL").fetchone()
            return bool(row and int(row[0]) > 0)

    @staticmethod
    def _row_to_dict(row) -> dict:
        return {
            "chunk_id": row[0],
            "file_id": row[1],
            "path": row[2],
            "language": row[3],
            "start_line": row[4],
            "end_line": row[5],
            "content": row[6],
        }


def _cosine(a: list[float], b: list[float]) -> float:
    if not a or not b or len(a) != len(b):
        return 0.0
    dot = sum(x * y for x, y in zip(a, b))
    na = sum(x * x for x in a) ** 0.5
    nb = sum(x * x for x in b) ** 0.5
    if na == 0 or nb == 0:
        return 0.0
    return dot / (na * nb)
