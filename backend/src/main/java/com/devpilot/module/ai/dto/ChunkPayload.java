package com.devpilot.module.ai.dto;

/**
 * 推送给 Python 服务的分块负载。
 */
public record ChunkPayload(String id, Long fileId, String path, String language,
                           int startLine, int endLine, String content) {
}
