"""提示注入防护：检测、脱敏、体积上限。检索内容一律视为不可信数据。"""
from __future__ import annotations

import re

# 中英文常见注入特征（命名捕获便于在轨迹中说明）
_PATTERNS: list[tuple[str, re.Pattern]] = [
    ("ignore_instructions", re.compile(r"ignore\s+(all\s+)?(previous|prior|above|earlier)\s+(instructions|prompts?)", re.I)),
    ("ignore_instructions_cn", re.compile(r"忽略(之前|以上|上述|前面)(的)?(所有)?(指令|提示|设定)", re.I)),
    ("role_override", re.compile(r"(you\s+are\s+now|从现在开始你是| pretend to be )", re.I)),
    ("system_prompt_probe", re.compile(r"(system\s+prompt|系统提示词|泄露.{0,6}(密钥|key|token))", re.I)),
    ("exfiltration", re.compile(r"(curl\s+http|wget\s+http|fetch\()", re.I)),
]

# 日志脱敏：常见密钥形态
_SECRET_RE = re.compile(r"(sk-[A-Za-z0-9_\-]{8,}|ghp_[A-Za-z0-9]{8,}|AKIA[0-9A-Z]{12,})")


def scan_injection(text: str) -> list[str]:
    """返回命中的注入特征名列表。"""
    if not text:
        return []
    return [name for name, pattern in _PATTERNS if pattern.search(text)]


def sanitize_for_log(text: str, max_chars: int = 500) -> str:
    """脱敏 + 截断，用于执行轨迹与日志。"""
    if text is None:
        return ""
    cleaned = _SECRET_RE.sub("***", text)
    cleaned = cleaned.replace("\n", " ")
    return cleaned[:max_chars]


def truncate(text: str | None, max_chars: int) -> tuple[str, bool]:
    """返回 (文本, 是否被截断)。"""
    if text is None:
        return "", False
    if len(text) <= max_chars:
        return text, False
    return text[:max_chars], True
