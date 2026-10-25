package com.devpilot.module.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchResponseDto(boolean vectorEnabled, List<SearchHitDto> results) {
}
