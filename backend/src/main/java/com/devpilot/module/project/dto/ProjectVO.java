package com.devpilot.module.project.dto;

import com.devpilot.module.project.Project;

import java.time.LocalDateTime;

public record ProjectVO(Long id, String name, String description, Long ownerId,
                        LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static ProjectVO from(Project project) {
        return new ProjectVO(project.getId(), project.getName(), project.getDescription(),
                project.getOwnerId(), project.getCreatedAt(), project.getUpdatedAt());
    }
}
