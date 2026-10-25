package com.devpilot.module.defect.dto;

import com.devpilot.module.defect.Defect;

import java.time.LocalDateTime;

public record DefectVO(Long id, Long projectId, String title, String description, String severity,
                       String priority, String status, Long assigneeId, String assigneeName,
                       Long reporterId, String reporterName, Long analysisId,
                       LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static DefectVO from(Defect defect, String assigneeName, String reporterName) {
        return new DefectVO(defect.getId(), defect.getProjectId(), defect.getTitle(), defect.getDescription(),
                defect.getSeverity(), defect.getPriority(), defect.getStatus(), defect.getAssigneeId(), assigneeName,
                defect.getReporterId(), reporterName, defect.getAnalysisId(), defect.getCreatedAt(), defect.getUpdatedAt());
    }
}
