package com.devpilot.module.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 存储在 ai_analysis.structured_result 中的负载（读取时反序列化）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StoredPayload(List<AgentEvidenceDto> evidence, List<AgentStepDto> steps,
                            List<String> warnings, AgentReportDto report) {
}
