package com.devpilot.module.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Bug 分析结构化报告（由 Python 侧做 Schema 校验后返回）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentReportDto(String summary, List<String> possibleCauses, String impact,
                             List<String> verificationSteps, String fixDirections, double confidence) {
}
