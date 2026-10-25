package com.devpilot.module.project.dto;

import com.devpilot.module.project.CodeRepository;

import java.time.LocalDateTime;

public record RepositoryVO(Long id, Long projectId, String name, String sourceType, String gitUrl, String gitRef,
                           String fileName, String importStatus, String errorMessage,
                           Integer fileCount, Long totalSize, LocalDateTime lastIndexedAt,
                           LocalDateTime createdAt) {

    public static RepositoryVO from(CodeRepository repo) {
        return new RepositoryVO(repo.getId(), repo.getProjectId(), repo.getName(), repo.getSourceType(),
                repo.getGitUrl(), repo.getGitRef(), repo.getFileName(), repo.getImportStatus(), repo.getErrorMessage(),
                repo.getFileCount(), repo.getTotalSize(), repo.getLastIndexedAt(), repo.getCreatedAt());
    }
}
