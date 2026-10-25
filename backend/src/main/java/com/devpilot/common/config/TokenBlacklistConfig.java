package com.devpilot.common.config;

import com.devpilot.security.TokenBlacklist;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token 黑名单装配：优先 Redis；启动探测失败则降级为进程内实现（日志明确告知）。
 */
@Slf4j
@Configuration
public class TokenBlacklistConfig {

    @Bean
    public TokenBlacklist tokenBlacklist(StringRedisTemplate redisTemplate) {
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            if ("PONG".equalsIgnoreCase(pong)) {
                log.info("TokenBlacklist: 使用 Redis 实现");
                return new RedisTokenBlacklist(redisTemplate);
            }
        } catch (Exception e) {
            log.warn("TokenBlacklist: Redis 不可用({})，降级为进程内实现（仅单实例部署有效，生产集群请配置 Redis）", e.getClass().getSimpleName());
        }
        return new InMemoryTokenBlacklist();
    }

    static class RedisTokenBlacklist implements TokenBlacklist {
        private final StringRedisTemplate redis;

        RedisTokenBlacklist(StringRedisTemplate redis) {
            this.redis = redis;
        }

        @Override
        public void add(String tokenId, long remainingSeconds) {
            if (remainingSeconds <= 0) {
                return;
            }
            redis.opsForValue().set("devpilot:token-bl:" + tokenId, "1", java.time.Duration.ofSeconds(remainingSeconds));
        }

        @Override
        public boolean contains(String tokenId) {
            return Boolean.TRUE.equals(redis.hasKey("devpilot:token-bl:" + tokenId));
        }
    }

    static class InMemoryTokenBlacklist implements TokenBlacklist {
        private final Map<String, Long> store = new ConcurrentHashMap<>();

        @Override
        public void add(String tokenId, long remainingSeconds) {
            store.put(tokenId, System.currentTimeMillis() + remainingSeconds * 1000);
        }

        @Override
        public boolean contains(String tokenId) {
            Long expireAt = store.remove(tokenId);
            if (expireAt == null) {
                return false;
            }
            if (expireAt < System.currentTimeMillis()) {
                return false; // 已过期，顺带清理
            }
            store.put(tokenId, expireAt); // 未过期放回
            return true;
        }
    }
}
