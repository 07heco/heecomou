package com.heecomou.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtUtil 单元测试")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil("test_secret_key_for_jwt_unit_test_min_32chars", 3600000L);
    }

    @Test
    @DisplayName("生成 JWT Token 并解析 userId 和 username")
    void shouldGenerateAndParseToken() {
        Long userId = 1L;
        String username = "testuser";

        String token = jwtUtil.generateToken(userId, username);

        assertNotNull(token);
        assertEquals(userId, jwtUtil.getUserIdFromToken(token));
        assertEquals(username, jwtUtil.getUsernameFromToken(token));
    }

    @Test
    @DisplayName("验证有效的 Token 返回 true")
    void shouldValidateValidToken() {
        String token = jwtUtil.generateToken(1L, "testuser");
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    @DisplayName("验证无效的 Token 返回 false")
    void shouldRejectInvalidToken() {
        assertFalse(jwtUtil.validateToken("invalid.token.string"));
    }

    @Test
    @DisplayName("验证空 Token 返回 false")
    void shouldRejectEmptyToken() {
        assertFalse(jwtUtil.validateToken(""));
    }

    @Test
    @DisplayName("验证 null Token 返回 false")
    void shouldRejectNullToken() {
        assertFalse(jwtUtil.validateToken(null));
    }

    @Test
    @DisplayName("不同 secret 签发的 Token 验证失败")
    void shouldRejectTokenWithDifferentSecret() {
        JwtUtil otherJwtUtil = new JwtUtil("another_secret_key_for_testing_purposes_min32", 3600000L);
        String token = otherJwtUtil.generateToken(1L, "testuser");
        assertFalse(jwtUtil.validateToken(token));
    }
}