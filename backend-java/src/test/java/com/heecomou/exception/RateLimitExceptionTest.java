package com.heecomou.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RateLimitException 单元测试")
class RateLimitExceptionTest {

    @Test
    @DisplayName("构造 RateLimitException 携带消息")
    void shouldCarryMessage() {
        RateLimitException ex = new RateLimitException("请求过于频繁，请稍后再试");

        assertEquals("请求过于频繁，请稍后再试", ex.getMessage());
    }

    @Test
    @DisplayName("RateLimitException 是 RuntimeException 子类")
    void shouldBeRuntimeException() {
        RateLimitException ex = new RateLimitException("test");
        assertTrue(ex instanceof RuntimeException);
    }
}
