package com.devpilot.module.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Git 导入请求：MVP 仅支持公开 https 仓库（不托管凭据）。
 */
public record GitImportRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "^https://\\S+$", message = "仅支持 https:// 开头的公开仓库地址") String gitUrl,
        @Size(max = 100) String gitRef) {
}
