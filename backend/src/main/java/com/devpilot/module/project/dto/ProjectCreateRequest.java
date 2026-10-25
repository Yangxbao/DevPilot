package com.devpilot.module.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectCreateRequest(
        @NotBlank @Size(min = 2, max = 100, message = "项目名长度 2-100") String name,
        @Size(max = 500) String description) {
}
