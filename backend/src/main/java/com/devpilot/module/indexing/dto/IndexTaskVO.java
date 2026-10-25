package com.devpilot.module.indexing.dto;

import com.devpilot.module.indexing.IndexTask;

import java.time.LocalDateTime;

public record IndexTaskVO(Long id, Long repositoryId, String taskNo, String triggerType, String status,
                          Integer totalFiles, Integer processedFiles, Integer totalChunks,
                          String errorMessage, LocalDateTime startedAt, LocalDateTime finishedAt,
                          LocalDateTime createdAt) {

    public static IndexTaskVO from(IndexTask task) {
        return new IndexTaskVO(task.getId(), task.getRepositoryId(), task.getTaskNo(), task.getTriggerType(),
                task.getStatus(), task.getTotalFiles(), task.getProcessedFiles(), task.getTotalChunks(),
                task.getErrorMessage(), task.getStartedAt(), task.getFinishedAt(), task.getCreatedAt());
    }
}
