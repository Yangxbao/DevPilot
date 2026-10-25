package com.devpilot.security;

/**
 * 当前登录用户的安全主体。
 */
public record UserPrincipal(Long id, String username, String role) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
