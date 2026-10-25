"""共享 fixture：临时索引库、本地 Embedding、Mock LLM。"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import pytest

from app.config import Settings
from app.embeddings import HashingEmbedding
from app.llm import MockLLM
from app.store import IndexStore


@pytest.fixture()
def settings(tmp_path) -> Settings:
    return Settings(data_dir=str(tmp_path / "index-store"), internal_token="test-token")


@pytest.fixture()
def store(settings) -> IndexStore:
    return IndexStore(settings.data_dir)


@pytest.fixture()
def embedding() -> HashingEmbedding:
    return HashingEmbedding(64)


@pytest.fixture()
def mock_llm() -> MockLLM:
    return MockLLM()


SAMPLE_CHUNKS = [
    {
        "id": "1-1",
        "file_id": 1,
        "path": "src/main/java/com/demo/UserService.java",
        "language": "java",
        "start_line": 1,
        "end_line": 30,
        "content": "public class UserService {\n    public User login(String username, String password) {\n        User user = userDao.findByName(username);\n        return user.checkPassword(password);\n    }\n}",
    },
    {
        "id": "1-2",
        "file_id": 2,
        "path": "src/main/java/com/demo/UserDao.java",
        "language": "java",
        "start_line": 1,
        "end_line": 20,
        "content": "public interface UserDao {\n    User findByName(String username);\n}",
    },
    {
        "id": "1-3",
        "file_id": 3,
        "path": "docs/api.md",
        "language": "markdown",
        "start_line": 1,
        "end_line": 10,
        "content": "# 登录接口文档\nPOST /api/login 用户名密码登录，失败返回 401。",
    },
]
