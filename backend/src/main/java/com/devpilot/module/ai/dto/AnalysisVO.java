package com.devpilot.module.ai.dto;

import com.devpilot.module.ai.AnalysisStep;
import com.devpilot.module.ai.AiAnalysis;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分析记录 VO：detail 视图额外携带证据与步骤轨迹。
 */
public record AnalysisVO(Long id, Long projectId, Long repositoryId, String type, String status,
                         String question, String answer, String mode, String provider, String model,
                         Double confidence, Integer evidenceCount, Long durationMs, String errorMessage,
                         Long createdBy, LocalDateTime createdAt,
                         List<AgentEvidenceDto> evidence, List<AgentStepDto> steps,
                         List<String> warnings, AgentReportDto report) {

    public static AnalysisVO summaryOf(AiAnalysis a) {
        return new AnalysisVO(a.getId(), a.getProjectId(), a.getRepositoryId(), a.getType(), a.getStatus(),
                a.getQuestion(), a.getAnswer(), a.getMode(), a.getProvider(), a.getModel(),
                a.getConfidence() == null ? null : a.getConfidence().doubleValue(),
                a.getEvidenceCount(), a.getDurationMs(), a.getErrorMessage(), a.getCreatedBy(), a.getCreatedAt(),
                null, null, null, null);
    }
}
