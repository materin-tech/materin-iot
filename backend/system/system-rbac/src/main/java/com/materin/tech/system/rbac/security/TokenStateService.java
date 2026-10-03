package com.materin.tech.system.rbac.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.materin.tech.common.redis.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Token 状态存取（Redis）：服务无状态，登录态相关状态全部外置 Redis，
 * 多实例水平扩展时共享同一份状态。
 */
@Service
@RequiredArgsConstructor
public class TokenStateService {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /** 登录失败锁定的阈值与时长 */
    private static final int MAX_FAILS = 5;
    private static final Duration FAIL_LOCK = Duration.ofMinutes(10);
    private static final Duration REFRESH_TTL = Duration.ofDays(7);

    public boolean isLoginLocked(String username) {
        String value = redis.opsForValue().get(RedisKeys.AUTH_FAIL + username);
        return value != null && Integer.parseInt(value) >= MAX_FAILS;
    }

    public void recordLoginFailure(String username) {
        Long count = redis.opsForValue().increment(RedisKeys.AUTH_FAIL + username);
        if (count != null && count == 1) {
            redis.expire(RedisKeys.AUTH_FAIL + username, FAIL_LOCK);
        }
    }

    public void clearLoginFailures(String username) {
        redis.delete(RedisKeys.AUTH_FAIL + username);
    }

    /** 生成 opaque refreshToken 存入 Redis（替代 JWT refresh：服务端可随时吊销）。 */
    public String issueRefreshToken(CurrentUser user) {
        String token = newOpaqueToken();
        try {
            String json = objectMapper.writeValueAsString(user);
            redis.opsForValue().set(RedisKeys.AUTH_REFRESH + token, json, REFRESH_TTL);
        } catch (Exception e) {
            throw new IllegalStateException("token 序列化失败", e);
        }
        return token;
    }

    /** 校验并消费（轮换）refreshToken，返回用户上下文。 */
    public Optional<CurrentUser> consumeRefreshToken(String token) {
        String key = RedisKeys.AUTH_REFRESH + token;
        String json = redis.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        // 轮换：一次性使用
        redis.delete(key);
        try {
            CurrentUser user = objectMapper.readValue(json, CurrentUser.class);
            return Optional.of(user);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void revokeRefreshToken(String token) {
        if (token != null) {
            redis.delete(RedisKeys.AUTH_REFRESH + token);
        }
    }

    /** 吊销 accessToken：jti 入黑名单，TTL 与 token 剩余寿命一致。 */
    public void revokeAccessToken(String jti, long remainingSeconds) {
        if (jti != null && remainingSeconds > 0) {
            redis.opsForValue().set(RedisKeys.AUTH_BLACKLIST + jti, "1",
                    Duration.ofSeconds(remainingSeconds));
        }
    }

    public boolean isAccessTokenRevoked(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(RedisKeys.AUTH_BLACKLIST + jti));
    }

    private String newOpaqueToken() {
        return java.util.UUID.randomUUID().toString().replace("-", "")
                + ThreadLocalRandom.current().nextInt(100000, 999999);
    }
}
