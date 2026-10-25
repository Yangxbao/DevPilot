package com.devpilot.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BugAnalysisRequest(
        @NotNull Long repositoryId,
        @NotBlank @Size(max = 200, message = "标题长度不超过 200 字符") String title,
        @NotBlank @Size(max = 8000, message = "错误信息长度不超过 8000 字符") String errorMessage,
        @Size(max = 8000, message = "堆栈长度不超过 8000 字符") String stackTrace,
        @Size(max = 2000, message = "复现步骤长度不超过 2000 字符") String reproSteps) {
}
