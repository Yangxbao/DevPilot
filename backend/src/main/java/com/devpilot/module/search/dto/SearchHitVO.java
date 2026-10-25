package com.devpilot.module.search.dto;

public record SearchHitVO(Long chunkId, Long fileId, Long repositoryId, String path, String language,
                          int startLine, int endLine, String snippet, double score, String channel) {
}
