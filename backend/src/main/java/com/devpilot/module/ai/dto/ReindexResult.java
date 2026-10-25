package com.devpilot.module.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReindexResult(Long repositoryId, int indexed, boolean vectorEnabled) {
}
