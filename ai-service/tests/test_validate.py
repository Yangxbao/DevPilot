"""输出校验：JSON 提取容错与引用核查。"""
import pytest
from pydantic import ValidationError

from app.validate import BugOutput, QaOutput, extract_json, validate_citations


def test_extract_json_plain():
    assert extract_json('{"answer": "hi"}') == {"answer": "hi"}


def test_extract_json_with_fence_and_prefix():
    text = '好的，结果如下：\n```json\n{"answer": "a", "evidence_ids": ["1-1"], "confidence": 0.4}\n```'
    assert extract_json(text)["answer"] == "a"


def test_extract_json_embedded():
    text = '前言 {"answer": "x", "confidence": 0.9} 后记'
    assert extract_json(text)["answer"] == "x"


def test_extract_json_failure():
    with pytest.raises(Exception):
        extract_json("完全不是 JSON")


def test_qa_output_defaults():
    out = QaOutput(answer="ok")
    assert out.confidence == 0.5 and out.evidence_ids == []


def test_confidence_bounds():
    with pytest.raises(ValidationError):
        QaOutput(answer="ok", confidence=1.5)


def test_validate_citations():
    out = QaOutput(answer="a", evidence_ids=["1-1", "9-9"])
    valid, invalid = validate_citations(out, {"1-1", "1-2"})
    assert valid == ["1-1"]
    assert invalid == ["9-9"]


def test_bug_output_shape():
    out = BugOutput(summary="s", possible_causes=["c"], confidence=0.3, evidence_ids=["1-2"])
    assert out.impact == "" and out.verification_steps == []
