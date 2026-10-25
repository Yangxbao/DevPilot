package com.devpilot.module.user.dto;

import jakarta.validation.constraints.NotNull;

public record UserUpdateStatusRequest(@NotNull Integer status) {
}
