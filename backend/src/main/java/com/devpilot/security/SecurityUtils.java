package com.devpilot.security;

import com.devpilot.common.exception.BizException;
import com.devpilot.common.api.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 SecurityContext 读取当前用户。Controller/Service 层统一使用。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static UserPrincipal current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        throw new BizException(ErrorCode.UNAUTHORIZED);
    }

    public static Long currentUserId() {
        return current().id();
    }
}
