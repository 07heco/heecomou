package com.heecomou.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("JwtAuthenticationFilter 单元测试")
class JwtAuthenticationFilterTest {

    private JwtUtil jwtUtil;
    private TokenBlacklistService blacklistService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil("test_secret_key_for_jwt_filter_test_min32chars", 3600000L);
        blacklistService = mock(TokenBlacklistService.class);
        filter = new JwtAuthenticationFilter(jwtUtil, blacklistService);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("无 Authorization Header 时放行请求")
    void shouldPassThroughWithoutAuthHeader() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("有效 Bearer Token 设置认证上下文并放行")
    void shouldSetAuthenticationForValidToken() throws Exception {
        String token = jwtUtil.generateToken(1L, "testuser");

        HttpServletRequest request = mock(HttpServletRequest.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(blacklistService.isBlacklisted(token)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth instanceof UserPrincipal);
        assertEquals(1L, ((UserPrincipal) auth).getUserId());
        assertEquals("testuser", auth.getName());
    }

    @Test
    @DisplayName("无效 Token 返回 401 并拦截请求")
    void shouldReturn401ForInvalidToken() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(request.getHeader("Authorization")).thenReturn("Bearer invalid.token.here");

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getContentAsString().contains("401"));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("非 Bearer 格式的 Header 仅提取不到 token 不拦截")
    void shouldIgnoreNonBearerHeader() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(request.getHeader("Authorization")).thenReturn("Basic dGVzdDp0ZXN0");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("黑名单中的 Token 返回 401 并拦截")
    void shouldReturn401ForBlacklistedToken() throws Exception {
        String token = jwtUtil.generateToken(1L, "testuser");

        HttpServletRequest request = mock(HttpServletRequest.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(blacklistService.isBlacklisted(token)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getContentAsString().contains("Token 已失效"));
        verify(filterChain, never()).doFilter(any(), any());
    }
}
