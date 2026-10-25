package com.devpilot.module.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Agent 统一响应：问答与 Bug 分析共用。
 * Bug 分析时 report 非空；answer 始终是人类可读正文。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentResultDto(
        String mode,
        String provider,
        String model,
        String answer,
        Double confidence,
        List<AgentEvidenceDto> evidence,
        List<AgentStepDto> steps,
        List<String> warnings,
        Long durationMs,
        AgentReportDto report) {
}
