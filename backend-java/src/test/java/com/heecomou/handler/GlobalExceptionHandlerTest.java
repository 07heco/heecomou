package com.heecomou.handler;

import com.heecomou.exception.BusinessException;
import com.heecomou.model.dto.ApiResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GlobalExceptionHandler 单元测试")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("BusinessException 返回对应的 code 和 message")
    void shouldHandleBusinessException() {
        BusinessException ex = new BusinessException(409, "用户名已存在");

        ApiResponse<Void> response = handler.handleBusinessException(ex);

        assertEquals(409, response.getCode());
        assertEquals("用户名已存在", response.getMessage());
        assertNull(response.getData());
    }

    @Test
    @DisplayName("MethodArgumentNotValidException 返回 400 + 字段错误详情")
    void shouldHandleValidationException() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("registerRequest", "username", "用户名不能为空"));
        bindingResult.addError(new FieldError("registerRequest", "password", "密码长度须在 6~30 之间"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        @SuppressWarnings("unchecked")
        ApiResponse<Map<String, String>> response = (ApiResponse<Map<String, String>>) (ApiResponse<?>) handler.handleValidationException(ex);

        assertEquals(400, response.getCode());
        assertEquals("参数校验失败", response.getMessage());
        assertNotNull(response.getData());
        assertEquals("用户名不能为空", response.getData().get("username"));
        assertEquals("密码长度须在 6~30 之间", response.getData().get("password"));
    }

    @Test
    @DisplayName("通用 Exception 返回 500 服务器内部错误")
    void shouldHandleGenericException() {
        Exception ex = new Exception("something went wrong");

        ApiResponse<Void> response = handler.handleException(ex);

        assertEquals(500, response.getCode());
        assertEquals("服务器内部错误", response.getMessage());
        assertNull(response.getData());
    }
}