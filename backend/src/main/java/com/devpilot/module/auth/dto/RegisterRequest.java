package com.devpilot.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_]{3,32}$", message = "用户名仅限字母/数字/下划线，长度 3-32") String username,
        @NotBlank @Size(min = 8, max = 64, message = "密码长度 8-64") @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码需同时包含字母和数字") String password,
        @Size(max = 50) String nickname) {
}
