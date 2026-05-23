package com.heecomou.security;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("RefreshTokenService 单元测试")
class RefreshTokenServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RefreshTokenService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new RefreshTokenService(redisTemplate);
    }

    @Test
    @DisplayName("save 将 token 存入 Redis 并设置 TTL")
    void shouldSaveTokenWithTtl() {
        String token = "refresh.jwt.token";
        Long userId = 1L;
        long ttl = 604800000L;

        service.save(token, userId, ttl);

        verify(valueOperations, times(1)).set(
                eq("refresh:token:1:refresh.jwt.token"),
                eq("1"),
                eq(ttl),
                eq(TimeUnit.MILLISECONDS)
        );
    }

    @Test
    @DisplayName("isValid 存在时返回 true")
    void shouldReturnTrueWhenValid() {
        String token = "refresh.jwt.token";
        Long userId = 1L;

        when(redisTemplate.hasKey("refresh:token:1:refresh.jwt.token")).thenReturn(true);
        assertTrue(service.isValid(token, userId));
    }

    @Test
    @DisplayName("isValid 不存在时返回 false")
    void shouldReturnFalseWhenNotValid() {
        String token = "refresh.jwt.token";
        Long userId = 1L;

        when(redisTemplate.hasKey("refresh:token:1:refresh.jwt.token")).thenReturn(false);
        assertFalse(service.isValid(token, userId));
    }

    @Test
    @DisplayName("revoke 删除指定 refresh token")
    void shouldRevokeToken() {
        String token = "refresh.jwt.token";
        Long userId = 1L;

        service.revoke(token, userId);
        verify(redisTemplate, times(1)).delete("refresh:token:1:refresh.jwt.token");
    }
}
