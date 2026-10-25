package com.devpilot.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与解析（HS256）。
 * Claims: sub=username, uid=用户ID, role=角色, jti=TokenID（登出黑名单用）。
 */
@Service
public class JwtService {

    private final SecretKey key;

    @Getter
    private final long expireMinutes;

    public JwtService(@Value("${devpilot.jwt.secret}") String secret,
                      @Value("${devpilot.jwt.expire-minutes:120}") long expireMinutes) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("devpilot.jwt.secret 至少需要 32 字节（当前 " + bytes.length + "）");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expireMinutes = expireMinutes;
    }

    public String generate(Long userId, String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .id(UUID.randomUUID().toString())
                .claim("uid", userId)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expireMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并验签。失败抛出 JwtException，由过滤器决定如何处理。
     */
    public Claims parse(String token) throws JwtException {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
