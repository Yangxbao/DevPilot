package com.devpilot.module.auth.dto;

import com.devpilot.module.user.dto.UserVO;

public record LoginResponse(String accessToken, long expiresIn, UserVO user) {
}
