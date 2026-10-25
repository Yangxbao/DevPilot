package com.devpilot.module.auth;

import com.devpilot.common.api.ErrorCode;
import com.devpilot.common.config.AppProperties;
import com.devpilot.common.exception.BizException;
import com.devpilot.module.auth.dto.LoginRequest;
import com.devpilot.module.auth.dto.LoginResponse;
import com.devpilot.module.auth.dto.RegisterRequest;
import com.devpilot.module.user.SysUser;
import com.devpilot.module.user.UserService;
import com.devpilot.module.user.dto.UserVO;
import com.devpilot.security.JwtService;
import com.devpilot.security.TokenBlacklist;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证服务：注册 / 登录（失败限流）/ 登出（Token 黑名单）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;
    private final AppProperties properties;

    /** username -> 最近失败时间戳队列（进程内限流，集群部署建议改用 Redis） */
    private final Map<String, Deque<Long>> failedLogins = new ConcurrentHashMap<>();

    public UserVO register(RegisterRequest request) {
        SysUser user = userService.register(request.username(), request.password(), request.nickname());
        return UserVO.from(user);
    }

    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();
        checkLocked(username);

        SysUser user = userService.getByUsername(username);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            recordFailure(username);
            throw new BizException(ErrorCode.BAD_REQUEST, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }
        failedLogins.remove(username);

        String token = jwtService.generate(user.getId(), user.getUsername(), user.getRole());
        return new LoginResponse(token, jwtService.getExpireMinutes() * 60, UserVO.from(user));
    }

    public void logout(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return;
        }
        try {
            Claims claims = jwtService.parse(authorizationHeader.substring(7));
            long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
            tokenBlacklist.add(claims.getId(), remaining / 1000 + 1);
        } catch (Exception e) {
            // Token 已无效则无需处理
            log.debug("logout 时 Token 解析失败（忽略）: {}", e.getMessage());
        }
    }

    private void checkLocked(String username) {
        Deque<Long> deque = failedLogins.get(username);
        if (deque == null) {
            return;
        }
        long windowMillis = properties.getLogin().getLockMinutes() * 60_000L;
        long cutoff = System.currentTimeMillis() - windowMillis;
        int recent = 0;
        synchronized (deque) {
            recent = (int) deque.stream().filter(t -> t > cutoff).count();
        }
        if (recent >= properties.getLogin().getMaxFailAttempts()) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS,
                    "登录失败次数过多，账号已锁定 " + properties.getLogin().getLockMinutes() + " 分钟");
        }
    }

    private void recordFailure(String username) {
        failedLogins.computeIfAbsent(username, k -> new ArrayDeque<>());
        Deque<Long> deque = failedLogins.get(username);
        synchronized (deque) {
            deque.addLast(Instant.now().toEpochMilli());
            while (deque.size() > properties.getLogin().getMaxFailAttempts() * 2) {
                deque.removeFirst();
            }
        }
    }
}
