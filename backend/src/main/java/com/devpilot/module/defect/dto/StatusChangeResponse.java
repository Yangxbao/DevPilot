package com.devpilot.module.defect.dto;

import java.util.List;

public record StatusChangeResponse(Long id, String status, List<String> allowedNextStatuses) {
}
