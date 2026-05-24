package com.heecomou.security;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class RefreshTokenService {

    private static final String REFRESH_PREFIX = "refresh:token:";

    private final StringRedisTemplate redisTemplate;

    public RefreshTokenService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(String token, Long userId, long ttlMillis) {
        redisTemplate.opsForValue().set(
                REFRESH_PREFIX + userId + ":" + token,
                "1",
                ttlMillis,
                TimeUnit.MILLISECONDS
        );
    }

    public boolean isValid(String token, Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(REFRESH_PREFIX + userId + ":" + token));
    }

    public void revoke(String token, Long userId) {
        redisTemplate.delete(REFRESH_PREFIX + userId + ":" + token);
    }

    public void revokeAll(Long userId) {
        redisTemplate.delete(redisTemplate.keys(REFRESH_PREFIX + userId + ":*"));
    }
}
