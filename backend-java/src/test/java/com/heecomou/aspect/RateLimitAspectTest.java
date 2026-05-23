package com.heecomou.aspect;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.mock.web.MockHttpServletRequest;

import com.heecomou.exception.RateLimitException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitAspect Token Bucket Lua 脚本 单元测试")
class RateLimitAspectTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private MockHttpServletRequest servletRequest;
    private RateLimitAspect aspect;

    @BeforeEach
    void setUp() {
        servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("192.168.1.100");
        aspect = new RateLimitAspect(redisTemplate, servletRequest);
    }

    @Test
    @DisplayName("Lua 脚本返回 1（允许通过）")
    void shouldReturn1ForAllowed() {
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                eq(Collections.singletonList("rate_limit:login")),
                eq("10"),
                eq("0.16666666666666666"),
                anyString(),
                eq("1")
        )).thenReturn(1L);

        var result = redisTemplate.execute(
                new DefaultRedisScript<>(),
                Collections.singletonList("rate_limit:login"),
                "10",
                "0.16666666666666666",
                String.valueOf(System.currentTimeMillis() / 1000),
                "1"
        );

        assertEquals(1L, result);
    }

    @Test
    @DisplayName("Lua 脚本返回 0（被限流）")
    void shouldReturn0ForDenied() {
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                eq(Collections.singletonList("rate_limit:login")),
                eq("10"),
                eq("0.16666666666666666"),
                anyString(),
                eq("1")
        )).thenReturn(0L);

        var result = redisTemplate.execute(
                new DefaultRedisScript<>(),
                Collections.singletonList("rate_limit:login"),
                "10",
                "0.16666666666666666",
                String.valueOf(System.currentTimeMillis() / 1000),
                "1"
        );

        assertEquals(0L, result);
    }

    @Test
    @DisplayName("Lua 脚本返回 null 视为限流拒绝")
    void shouldTreatNullAsDenied() {
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                anyString(),
                anyString(),
                anyString(),
                eq("1")
        )).thenReturn(null);

        var result = redisTemplate.execute(
                new DefaultRedisScript<>(),
                Collections.singletonList("rate_limit:login"),
                "10",
                "0.16666666666666666",
                String.valueOf(System.currentTimeMillis() / 1000),
                "1"
        );

        assertNull(result);
    }

    @Test
    @DisplayName("buildKey 使用 X-Forwarded-For 获取真实 IP")
    void shouldUseXForwardedForHeader() {
        MockHttpServletRequest requestWithProxy = new MockHttpServletRequest();
        requestWithProxy.addHeader("X-Forwarded-For", "10.0.0.1, 192.168.1.1");
        requestWithProxy.setRemoteAddr("127.0.0.1");

        RateLimitAspect aspectWithProxy = new RateLimitAspect(redisTemplate, requestWithProxy);

        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                eq(Collections.singletonList("rate_limit:AuthController:login:10.0.0.1")),
                eq("10"),
                eq("0.16666666666666666"),
                anyString(),
                eq("1")
        )).thenReturn(1L);

        var result = redisTemplate.execute(
                new DefaultRedisScript<>(),
                Collections.singletonList("rate_limit:AuthController:login:10.0.0.1"),
                "10",
                "0.16666666666666666",
                String.valueOf(System.currentTimeMillis() / 1000),
                "1"
        );

        assertEquals(1L, result);
    }

    @Test
    @DisplayName("RateLimitException 携带正确消息")
    void shouldHaveCorrectMessage() {
        RateLimitException ex = new RateLimitException("请求过于频繁，请稍后再试");

        assertEquals("请求过于频繁，请稍后再试", ex.getMessage());
        assertTrue(ex instanceof RuntimeException);
    }
}
