package com.devpilot.security;

/**
 * 登出 Token 黑名单。
 * 生产实现走 Redis（TTL=Token 剩余有效期）；Redis 不可用时自动降级为进程内实现（仅单实例有效）。
 */
public interface TokenBlacklist {

    void add(String tokenId, long remainingSeconds);

    boolean contains(String tokenId);
}
