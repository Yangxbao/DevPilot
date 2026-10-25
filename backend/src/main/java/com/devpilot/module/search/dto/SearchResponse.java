package com.devpilot.module.search.dto;

import java.util.List;

public record SearchResponse(List<SearchHitVO> results, long total, int page, int size, boolean vectorEnabled) {
}
