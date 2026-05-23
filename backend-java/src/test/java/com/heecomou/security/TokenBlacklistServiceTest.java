package com.heecomou.security;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("TokenBlacklistService 单元测试")
class TokenBlacklistServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private TokenBlacklistService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new TokenBlacklistService(redisTemplate);
    }

    @Test
    @DisplayName("blacklist 将 token 存入 Redis 并设置 TTL")
    void shouldStoreTokenWithTtl() {
        String token = "test.jwt.token";
        long ttl = 60000L;

        service.blacklist(token, ttl);

        verify(valueOperations, times(1)).set(
                eq("token:blacklist:" + token),
                eq("1"),
                eq(ttl),
                eq(TimeUnit.MILLISECONDS)
        );
    }

    @Test
    @DisplayName("blacklist 在 TTL <= 0 时不存入 Redis")
    void shouldNotStoreWhenTtlIsZeroOrNegative() {
        service.blacklist("test.jwt.token", 0);
        service.blacklist("test.jwt.token", -1);

        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("isBlacklisted 在黑名单中返回 true")
    void shouldReturnTrueWhenBlacklisted() {
        String token = "test.jwt.token";
        when(redisTemplate.hasKey("token:blacklist:" + token)).thenReturn(true);

        assertTrue(service.isBlacklisted(token));
    }

    @Test
    @DisplayName("isBlacklisted 不在黑名单中返回 false")
    void shouldReturnFalseWhenNotBlacklisted() {
        String token = "test.jwt.token";
        when(redisTemplate.hasKey("token:blacklist:" + token)).thenReturn(false);

        assertFalse(service.isBlacklisted(token));
    }
}
