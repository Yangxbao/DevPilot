"""可插拔 LLM Provider。

- MockLLM：只根据真实检索到的证据做模板化总结（不联网），明确标识 mock 模式。
- OpenAIChatLLM：OpenAI 兼容 /chat/completions，带超时与有限重试（5xx/超时，退避 1s）。
  失败抛 LLMUnavailable，由工作流决定报错语义（不静默编造）。
"""
from __future__ import annotations

import json
import time
from dataclasses import dataclass, field

import httpx


class LLMUnavailable(Exception):
    pass


@dataclass
class GenerationTask:
    """一次生成任务：system/user 提示词 + 结构化的证据（Mock 使用）。"""

    kind: str  # qa | bug
    system: str
    user: str
    question: str = ""
    evidence: list = field(default_factory=list)  # list[retrieval.Evidence]


class LLMProvider:
    name = "base"
    model = "base"
    mode = "real"

    def generate(self, task: GenerationTask) -> str:  # pragma: no cover
        raise NotImplementedError


class MockLLM(LLMProvider):
    name = "mock"
    model = "mock-v1"
    mode = "mock"

    def generate(self, task: GenerationTask) -> str:
        evidence = task.evidence
        if task.kind == "qa":
            lines = [
                "【演示模式说明】当前未配置真实大模型（LLM_PROVIDER=mock），"
                "以下回答由服务端基于真实检索结果自动汇总，不包含任何模型推断。",
                "",
                f"针对问题「{task.question[:120]}」，在代码库中检索到 {len(evidence)} 条相关证据：",
            ]
            citations = []
            for i, ev in enumerate(evidence, start=1):
                lines.append(f"- [E{i}] {ev.path}:{ev.start_line}-{ev.end_line}（{ev.language or '未知类型'}，相关度 {ev.score:.3f}）")
                citations.append(ev.chunk_id)
            lines.append("")
            lines.append("以上路径与行号均来自服务端检索结果，可在前端点击核对；")
            lines.append("如需语义级回答，请配置 LLM_PROVIDER=openai 并提供 API Key。")
            payload = {
                "answer": "\n".join(lines),
                "evidence_ids": citations[:12],
                "confidence": round(min(0.5, 0.15 + 0.08 * len(evidence)), 2),
            }
        else:
            causes: list[str] = []
            for ev in evidence[:3]:
                causes.append(f"证据 [E{evidence.index(ev) + 1}]（{ev.path}:{ev.start_line}）附近存在与错误输入相关的代码，需要人工确认其调用链")
            if not causes:
                causes = ["未检索到直接相关代码，无法给出有证据的原因（不做无证据猜测）"]
            payload = {
                "summary": f"【演示模式】基于检索到的 {len(evidence)} 条证据生成的初步定位，供人工核对，非模型推断。",
                "possible_causes": causes,
                "impact": "需要结合证据位置人工评估影响范围（演示模式不做无证据的推断）",
                "verification_steps": [
                    f"打开 {ev.path} 第 {ev.start_line}-{ev.end_line} 行核对证据" for ev in evidence[:3]
                ]
                or ["先补充堆栈与复现步骤后重试"],
                "fix_directions": "演示模式不给出具体修复方案；请配置真实模型后重新分析",
                "confidence": round(min(0.4, 0.1 + 0.07 * len(evidence)), 2),
                "evidence_ids": [ev.chunk_id for ev in evidence[:12]],
            }
        return json.dumps(payload, ensure_ascii=False)


class OpenAIChatLLM(LLMProvider):
    name = "openai"
    mode = "real"

    def __init__(self, base_url: str, api_key: str, model: str, timeout: float, max_retries: int) -> None:
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.model = model
        self.timeout = timeout
        self.max_retries = max_retries

    def generate(self, task: GenerationTask) -> str:
        messages = [
            {"role": "system", "content": task.system},
            {"role": "user", "content": task.user},
        ]
        last_error: Exception | None = None
        for attempt in range(self.max_retries + 1):
            try:
                response = httpx.post(
                    f"{self.base_url}/chat/completions",
                    headers={"Authorization": f"Bearer {self.api_key}"},
                    json={
                        "model": self.model,
                        "messages": messages,
                        "temperature": 0.2,
                        "response_format": {"type": "json_object"},
                    },
                    timeout=self.timeout,
                )
                if response.status_code >= 500 or response.status_code == 429:
                    last_error = LLMUnavailable(f"upstream {response.status_code}")
                    time.sleep(1 + attempt)
                    continue
                response.raise_for_status()
                return response.json()["choices"][0]["message"]["content"]
            except LLMUnavailable:
                raise
            except Exception as exc:
                last_error = exc
                time.sleep(1 + attempt)
        raise LLMUnavailable(f"LLM 调用失败（已重试 {self.max_retries} 次）: {last_error}")


def build_llm(settings) -> LLMProvider:
    if settings.llm_provider == "openai":
        if not settings.llm_api_key:
            raise LLMUnavailable("LLM_PROVIDER=openai 但未配置 LLM_API_KEY")
        return OpenAIChatLLM(
            settings.llm_base_url,
            settings.llm_api_key,
            settings.llm_model,
            settings.llm_timeout_seconds,
            settings.llm_max_retries,
        )
    return MockLLM()
