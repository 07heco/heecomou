package com.heecomou.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BusinessException 单元测试")
class BusinessExceptionTest {

    @Test
    @DisplayName("通过 code 和 message 构造异常")
    void shouldCreateWithCodeAndMessage() {
        BusinessException ex = new BusinessException(409, "用户名已存在");

        assertEquals(409, ex.getCode());
        assertEquals("用户名已存在", ex.getMessage());
    }

    @Test
    @DisplayName("仅用 message 构造时默认 code=500")
    void shouldDefaultCodeTo500() {
        BusinessException ex = new BusinessException("未知错误");

        assertEquals(500, ex.getCode());
        assertEquals("未知错误", ex.getMessage());
    }

    @Test
    @DisplayName("BusinessException 是 RuntimeException 子类")
    void shouldBeRuntimeException() {
        BusinessException ex = new BusinessException(400, "测试");
        assertTrue(ex instanceof RuntimeException);
    }
}