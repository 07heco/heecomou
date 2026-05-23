package com.heecomou.handler;

import com.heecomou.exception.RateLimitException;
import com.heecomou.model.dto.ApiResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GlobalExceptionHandler 限流处理 单元测试")
class GlobalExceptionHandlerRateLimitTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("RateLimitException 返回 429 + 限流消息")
    void shouldHandleRateLimitException() {
        RateLimitException ex = new RateLimitException("请求过于频繁，请稍后再试");

        ApiResponse<Void> response = handler.handleRateLimitException(ex);

        assertEquals(429, response.getCode());
        assertEquals("请求过于频繁，请稍后再试", response.getMessage());
        assertNull(response.getData());
    }
}
