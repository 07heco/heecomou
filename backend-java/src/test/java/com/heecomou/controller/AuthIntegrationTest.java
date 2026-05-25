package com.heecomou.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heecomou.config.TestRedisConfig;
import com.heecomou.model.dto.ChangePasswordRequest;
import com.heecomou.model.dto.LoginRequest;
import com.heecomou.model.dto.RefreshRequest;
import com.heecomou.model.dto.RegisterRequest;
import com.heecomou.model.dto.UpdateProfileRequest;
import com.heecomou.security.RefreshTokenService;
import com.heecomou.security.TokenBlacklistService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Phase 1 全流程集成测试")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    private static String accessToken;
    private static String refreshToken;
    private static final String TEST_USERNAME = "itg_test";
    private static final String TEST_PASSWORD = "testPass123";
    private static final String TEST_EMAIL = "test@heecomou.com";

    @BeforeEach
    void resetMocks() {
        when(refreshTokenService.isValid(anyString(), anyLong())).thenReturn(true);
    }

    @AfterEach
    void cleanupMocks() {
        reset(tokenBlacklistService, refreshTokenService);
    }

    @Test
    @Order(1)
    @DisplayName("步骤1: 注册新用户 → 200 返回 Token 和用户信息")
    void step1_register() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(TEST_USERNAME);
        request.setPassword(TEST_PASSWORD);
        request.setEmail(TEST_EMAIL);

        String responseBody = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").isNumber())
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.username").value(TEST_USERNAME))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(responseBody);
        accessToken = root.get("data").get("accessToken").asText();
        refreshToken = root.get("data").get("refreshToken").asText();

        assertNotNull(accessToken);
        assertNotNull(refreshToken);
        assertTrue(accessToken.length() > 20);
        assertTrue(refreshToken.length() > 20);
    }

    @Test
    @Order(2)
    @DisplayName("步骤2: 登录 → 200 返回 accessToken + refreshToken")
    void step2_login() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername(TEST_USERNAME);
        request.setPassword(TEST_PASSWORD);

        String responseBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").isNumber())
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(responseBody);
        accessToken = root.get("data").get("accessToken").asText();
        refreshToken = root.get("data").get("refreshToken").asText();

        assertNotNull(accessToken);
        assertNotNull(refreshToken);
        assertTrue(accessToken.length() > 20);
        assertTrue(refreshToken.length() > 20);
    }

    @Test
    @Order(3)
    @DisplayName("步骤3: 获取个人信息 → 200 返回用户资料")
    void step3_getProfile() throws Exception {
        assertNotNull(accessToken, "需要先执行步骤2获取Token");

        mockMvc.perform(get("/api/v1/user/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value(TEST_USERNAME))
                .andExpect(jsonPath("$.data.email").value(TEST_EMAIL));
    }

    @Test
    @Order(4)
    @DisplayName("步骤4: 修改昵称 → 200 返回更新后资料")
    void step4_updateProfile() throws Exception {
        assertNotNull(accessToken, "需要先执行步骤2获取Token");

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setNickname("小明");

        mockMvc.perform(put("/api/v1/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + accessToken)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nickname").value("小明"));
    }

    @Test
    @Order(5)
    @DisplayName("步骤5: 修改密码 → 200 成功后用新密码登录")
    void step5_changePasswordAndRelogin() throws Exception {
        assertNotNull(accessToken, "需要先执行步骤2获取Token");

        ChangePasswordRequest changeRequest = new ChangePasswordRequest();
        changeRequest.setOldPassword(TEST_PASSWORD);
        changeRequest.setNewPassword("newPass456");

        mockMvc.perform(put("/api/v1/user/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + accessToken)
                        .content(objectMapper.writeValueAsString(changeRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("密码修改成功"));

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(TEST_USERNAME);
        loginRequest.setPassword("newPass456");

        String responseBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(responseBody);
        accessToken = root.get("data").get("accessToken").asText();
        refreshToken = root.get("data").get("refreshToken").asText();
    }

    @Test
    @Order(6)
    @DisplayName("步骤6: 错误密码登录 → 200 + code=401")
    void step6_loginWithWrongPassword() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername(TEST_USERNAME);
        request.setPassword("wrongPassword");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @Order(7)
    @DisplayName("步骤7: 无 Token 访问 /me → 401")
    void step7_unauthorizedAccess() throws Exception {
        mockMvc.perform(get("/api/v1/user/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @Order(8)
    @DisplayName("步骤8: 重复注册同一用户名 → 409")
    void step8_duplicateRegistration() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(TEST_USERNAME);
        request.setPassword("anotherPass123");
        request.setEmail("another@heecomou.com");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("用户名已存在"));
    }

    @Test
    @Order(9)
    @DisplayName("步骤9: 参数校验失败 → 400")
    void step9_validationFailure() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("ab");
        request.setPassword("12");
        request.setEmail("bad");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("参数校验失败"))
                .andExpect(jsonPath("$.data").isNotEmpty());
    }

    @Test
    @Order(10)
    @DisplayName("步骤10: 登出 → 200 成功")
    void step10_logout() throws Exception {
        assertNotNull(accessToken, "需要先执行步骤2获取Token");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("登出成功"));

        verify(tokenBlacklistService, atLeastOnce())
                .blacklist(eq(accessToken), anyLong());
    }
}
