package com.devpilot.module.user.dto;

import com.devpilot.module.user.SysUser;

import java.time.LocalDateTime;

public record UserVO(Long id, String username, String nickname, String role, Integer status, LocalDateTime createdAt) {

    public static UserVO from(SysUser user) {
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getRole(), user.getStatus(), user.getCreatedAt());
    }
}
