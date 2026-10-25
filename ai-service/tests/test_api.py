"""API 集成：内部 Token 鉴权 + reindex -> search -> agent 全链路。"""
import pytest
from fastapi.testclient import TestClient

from app.main import app
from app import main as app_main


@pytest.fixture()
def client(tmp_path, monkeypatch):
    from app.config import Settings

    monkeypatch.setattr(app_main, "_store", None)
    monkeypatch.setenv("DATA_DIR", str(tmp_path / "store"))
    monkeypatch.setenv("INTERNAL_TOKEN", "test-token")
    # 重新加载配置单例
    import app.config as config
    monkeypatch.setattr(config, "_settings", None)
    with TestClient(app) as c:
        yield c


def test_health(client):
    resp = client.get("/health")
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "UP"
    assert "llmProvider" in body


def test_internal_requires_token(client):
    resp = client.post("/internal/reindex", json={"repository_id": 1, "chunks": []})
    assert resp.status_code == 401


def test_reindex_search_qa_roundtrip(client):
    headers = {"X-Internal-Token": "test-token"}
    chunks = [
        {"id": "1-1", "file_id": 1, "path": "src/Calc.java", "language": "java",
         "start_line": 1, "end_line": 20,
         "content": "public class Calc { public int add(int a, int b) { return a + b; } }"},
        {"id": "1-2", "file_id": 2, "path": "README.md", "language": "markdown",
         "start_line": 1, "end_line": 5, "content": "计算器示例项目，包含加法功能。"},
    ]
    resp = client.post("/internal/reindex", json={"repositoryId": 7, "chunks": chunks}, headers=headers)
    assert resp.status_code == 200
    body = resp.json()
    assert body["indexed"] == 2 and body["vectorEnabled"] is True

    resp = client.post("/internal/search",
                       json={"repositoryId": 7, "query": "add", "top_k": 5, "mode": "hybrid"},
                       headers=headers)
    assert resp.status_code == 200
    assert resp.json()["results"], "搜索 add 应有结果"

    resp = client.post("/agent/qa", json={"repository_id": 7, "question": "加法功能在哪实现？", "top_k": 5},
                       headers=headers)
    assert resp.status_code == 200
    qa = resp.json()
    assert qa["mode"] == "mock"
    assert qa["evidence"] and qa["steps"]
    assert qa["warnings"] == []

    resp = client.post("/agent/bug-analysis",
                       json={"repository_id": 7, "title": "加法报错",
                             "error_message": "java.lang.NullPointerException at Calc.add",
                             "stack_trace": "at Calc.add(Calc.java:2)"},
                       headers=headers)
    assert resp.status_code == 200
    assert resp.json()["report"] is not None

    resp = client.delete("/internal/index/7", headers=headers)
    assert resp.status_code == 200
