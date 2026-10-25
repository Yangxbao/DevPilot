package com.devpilot.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record QaRequest(
        @NotNull Long repositoryId,
        @NotBlank @Size(max = 2000, message = "问题长度不超过 2000 字符") String question) {
}
