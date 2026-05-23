package com.heecomou.controller;

import com.heecomou.model.dto.ApiResponse;
import com.heecomou.model.dto.ChangePasswordRequest;
import com.heecomou.model.dto.UpdateProfileRequest;
import com.heecomou.model.vo.UserVO;
import com.heecomou.security.UserPrincipal;
import com.heecomou.service.UserService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("UserController 单元测试")
class UserControllerTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /me 返回当前用户信息")
    void shouldReturnCurrentUser() {
        UserService userService = mock(UserService.class);
        UserController controller = new UserController(userService);

        UserPrincipal principal = new UserPrincipal(1L, "testuser");
        SecurityContextHolder.getContext().setAuthentication(principal);

        UserVO mockVO = new UserVO();
        mockVO.setId(1L);
        mockVO.setUsername("testuser");
        when(userService.getByUsername("testuser")).thenReturn(mockVO);

        ApiResponse<UserVO> response = controller.me();

        assertEquals(200, response.getCode());
        assertNotNull(response.getData());
        assertEquals(1L, response.getData().getId());
        assertEquals("testuser", response.getData().getUsername());
    }

    @Test
    @DisplayName("PUT /profile 更新用户资料成功")
    void shouldUpdateProfile() {
        UserService userService = mock(UserService.class);
        UserController controller = new UserController(userService);

        UserPrincipal principal = new UserPrincipal(1L, "testuser");
        SecurityContextHolder.getContext().setAuthentication(principal);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setNickname("小明");

        UserVO expectedVO = new UserVO();
        expectedVO.setId(1L);
        expectedVO.setUsername("testuser");
        expectedVO.setNickname("小明");
        when(userService.updateProfile(1L, request)).thenReturn(expectedVO);

        ApiResponse<UserVO> response = controller.updateProfile(request);

        assertEquals(200, response.getCode());
        assertNotNull(response.getData());
        assertEquals("小明", response.getData().getNickname());
        verify(userService, times(1)).updateProfile(1L, request);
    }

    @Test
    @DisplayName("PUT /password 修改密码成功")
    void shouldChangePassword() {
        UserService userService = mock(UserService.class);
        UserController controller = new UserController(userService);

        UserPrincipal principal = new UserPrincipal(1L, "testuser");
        SecurityContextHolder.getContext().setAuthentication(principal);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("oldpass123");
        request.setNewPassword("newpass456");

        doNothing().when(userService).changePassword(1L, request);

        ApiResponse<Void> response = controller.changePassword(request);

        assertEquals(200, response.getCode());
        assertEquals("密码修改成功", response.getMessage());
        verify(userService, times(1)).changePassword(1L, request);
    }
}
