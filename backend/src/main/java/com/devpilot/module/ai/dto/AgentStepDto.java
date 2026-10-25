package com.devpilot.module.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentStepDto(String name, String status, String detail, Long durationMs) {
}
