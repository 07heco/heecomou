package com.heecomou.model.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("参数校验（Bean Validation）单元测试")
class BeanValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("RegisterRequest 合法数据校验通过")
    void shouldPassValidRegisterRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("validUser");
        request.setPassword("validPass123");
        request.setEmail("test@example.com");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("RegisterRequest 用户名为空校验失败")
    void shouldFailBlankUsername() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("");
        request.setPassword("validPass123");
        request.setEmail("test@example.com");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("用户名不能为空")));
    }

    @Test
    @DisplayName("RegisterRequest 用户名过短校验失败")
    void shouldFailShortUsername() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("ab");
        request.setPassword("validPass123");
        request.setEmail("test@example.com");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("3~20")));
    }

    @Test
    @DisplayName("RegisterRequest 用户名含特殊字符校验失败")
    void shouldFailUsernameWithSpecialChars() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("user@name");
        request.setPassword("validPass123");
        request.setEmail("test@example.com");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("字母、数字和下划线")));
    }

    @Test
    @DisplayName("RegisterRequest 密码过短校验失败")
    void shouldFailShortPassword() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("validUser");
        request.setPassword("12");
        request.setEmail("test@example.com");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("6~30")));
    }

    @Test
    @DisplayName("RegisterRequest 邮箱格式错误校验失败")
    void shouldFailInvalidEmail() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("validUser");
        request.setPassword("validPass123");
        request.setEmail("notanemail");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("邮箱格式")));
    }

    @Test
    @DisplayName("RegisterRequest 邮箱为空校验失败")
    void shouldFailBlankEmail() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("validUser");
        request.setPassword("validPass123");
        request.setEmail("");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("邮箱不能为空")));
    }

    @Test
    @DisplayName("RegisterRequest 多项校验失败返回多条错误")
    void shouldReturnMultipleViolations() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("ab");
        request.setPassword("12");
        request.setEmail("bad");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertEquals(3, violations.size());

        Set<String> messages = violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
        assertTrue(messages.contains("用户名长度须在 3~20 之间"));
        assertTrue(messages.contains("密码长度须在 6~30 之间"));
        assertTrue(messages.contains("邮箱格式不正确"));
    }

    @Test
    @DisplayName("LoginRequest 合法数据校验通过")
    void shouldPassValidLoginRequest() {
        LoginRequest request = new LoginRequest();
        request.setUsername("validUser");
        request.setPassword("validPass123");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("LoginRequest 用户名为空校验失败")
    void shouldFailBlankLoginUsername() {
        LoginRequest request = new LoginRequest();
        request.setUsername("");
        request.setPassword("validPass123");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("用户名不能为空")));
    }

    @Test
    @DisplayName("LoginRequest 密码为空校验失败")
    void shouldFailBlankLoginPassword() {
        LoginRequest request = new LoginRequest();
        request.setUsername("validUser");
        request.setPassword("");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("密码不能为空")));
    }

    @Test
    @DisplayName("LoginRequest 密码过短校验失败")
    void shouldFailShortLoginPassword() {
        LoginRequest request = new LoginRequest();
        request.setUsername("validUser");
        request.setPassword("12");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("6~30")));
    }
}