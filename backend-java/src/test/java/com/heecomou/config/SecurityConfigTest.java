package com.heecomou.config;

import com.heecomou.security.JwtAuthenticationFilter;
import com.heecomou.security.JwtUtil;
import com.heecomou.security.TokenBlacklistService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("SecurityConfig 单元测试")
class SecurityConfigTest {

    private JwtUtil jwtUtil;
    private JwtAuthenticationFilter jwtFilter;
    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil("test_secret_key_for_security_config_test_min32", 300000L, 3600000L);
        TokenBlacklistService blacklistService = mock(TokenBlacklistService.class);
        jwtFilter = new JwtAuthenticationFilter(jwtUtil, blacklistService);
        securityConfig = new SecurityConfig(jwtFilter);
    }

    @Test
    @DisplayName("AuthenticationEntryPoint 返回 401 JSON 响应")
    void shouldReturn401JsonForUnauthenticatedRequest() throws Exception {
        AuthenticationEntryPoint entryPoint = securityConfig.authenticationEntryPoint();

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new org.springframework.security.authentication.BadCredentialsException("bad"));

        assertEquals(401, response.getStatus());
        assertEquals("application/json;charset=UTF-8", response.getContentType());
        assertTrue(response.getContentAsString().contains("401"));
        assertTrue(response.getContentAsString().contains("未登录或 Token 无效"));
    }

    @Test
    @DisplayName("CORS 配置允许所有来源和方法")
    void shouldConfigureCorsCorrectly() {
        var source = (UrlBasedCorsConfigurationSource) securityConfig.corsConfigurationSource();
        var config = source.getCorsConfigurations().get("/**");

        assertNotNull(config);
        assertFalse(config.getAllowedOriginPatterns().isEmpty());
        assertTrue(config.getAllowedMethods().contains("GET"));
        assertTrue(config.getAllowedMethods().contains("POST"));
        assertTrue(config.getAllowedMethods().contains("PUT"));
        assertTrue(config.getAllowedMethods().contains("DELETE"));
        assertTrue(config.getAllowedMethods().contains("OPTIONS"));
        assertTrue(config.getAllowCredentials());
    }
}