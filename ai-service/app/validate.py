"""输出校验：模型原始文本 -> 严格 JSON -> Schema 校验 -> 证据引用合法性核查。"""
from __future__ import annotations

import json
import re
from pydantic import BaseModel, Field, ValidationError


class OutputFormatError(Exception):
    pass


class QaOutput(BaseModel):
    answer: str = Field(min_length=1)
    evidence_ids: list[str] = Field(default_factory=list)
    confidence: float = Field(default=0.5, ge=0.0, le=1.0)


class BugOutput(BaseModel):
    summary: str = Field(min_length=1)
    possible_causes: list[str] = Field(default_factory=list)
    impact: str = ""
    verification_steps: list[str] = Field(default_factory=list)
    fix_directions: str = ""
    confidence: float = Field(default=0.5, ge=0.0, le=1.0)
    evidence_ids: list[str] = Field(default_factory=list)


def extract_json(text: str) -> dict:
    """解析模型输出为 JSON；容忍代码围栏与前后缀文本；失败时尝试提取首个平衡的花括号块。"""
    cleaned = text.strip()
    if cleaned.startswith("```"):
        cleaned = re.sub(r"^```[a-zA-Z]*\n?", "", cleaned)
        cleaned = re.sub(r"\n?```$", "", cleaned).strip()
    try:
        parsed = json.loads(cleaned)
        if isinstance(parsed, dict):
            return parsed
    except json.JSONDecodeError:
        pass
    # 平衡花括号提取（忽略字符串内的括号简化处理：取最长 { ... } 片段）
    start = cleaned.find("{")
    while start != -1:
        depth = 0
        for i in range(start, len(cleaned)):
            if cleaned[i] == "{":
                depth += 1
            elif cleaned[i] == "}":
                depth -= 1
                if depth == 0:
                    candidate = cleaned[start : i + 1]
                    try:
                        parsed = json.loads(candidate)
                        if isinstance(parsed, dict):
                            return parsed
                    except json.JSONDecodeError:
                        break
                    break
        start = cleaned.find("{", start + 1)
    raise OutputFormatError("模型输出不是合法 JSON")


def validate_citations(output: BaseModel, valid_ids: set[str]) -> tuple[list[str], list[str]]:
    """核查 evidence_ids ⊆ 实际提供的证据集合。返回 (合法引用, 被剔除的伪造引用)。"""
    cited = getattr(output, "evidence_ids", []) or []
    valid = [c for c in cited if c in valid_ids]
    invalid = [c for c in cited if c not in valid_ids]
    return valid, invalid
