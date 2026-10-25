package com.devpilot.security;

import com.devpilot.module.user.SysUser;
import com.devpilot.module.user.mapper.SysUserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：解析 Bearer Token -> 校验黑名单 -> 加载用户 -> 写入 SecurityContext。
 * 任何解析失败都不抛异常，请求以匿名身份继续，由授权规则返回 401。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;
    private final SysUserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parse(token);
                String jti = claims.getId();
                if (jti != null && tokenBlacklist.contains(jti)) {
                    // 已登出的 Token：保持未认证状态
                    filterChain.doFilter(request, response);
                    return;
                }
                Long uid = claims.get("uid", Long.class);
                String role = claims.get("role", String.class);
                if (uid != null && role != null) {
                    SysUser user = userMapper.selectById(uid);
                    // 用户被禁用或删除后，已签发 Token 立即失效
                    if (user != null && user.getStatus() != null && user.getStatus() == 1) {
                        UserPrincipal principal = new UserPrincipal(user.getId(), user.getUsername(), user.getRole());
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (JwtException | IllegalArgumentException e) {
                log.debug("非法或过期的 Token: {}", e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }
}
