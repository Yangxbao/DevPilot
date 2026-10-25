package com.devpilot.module.defect.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DefectUpdateRequest(
        @Size(max = 200) String title,
        @Size(max = 10000) String description,
        @Pattern(regexp = "^(CRITICAL|MAJOR|MEDIUM|MINOR)$", message = "严重程度取值非法") String severity,
        @Pattern(regexp = "^P[0-3]$", message = "优先级取值非法") String priority,
        Long assigneeId) {
}
