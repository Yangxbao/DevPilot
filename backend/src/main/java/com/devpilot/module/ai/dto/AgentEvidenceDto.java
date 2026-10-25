package com.devpilot.module.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentEvidenceDto(String chunkId, Long fileId, String path, String language,
                               int startLine, int endLine, String snippet, double score) {
}
