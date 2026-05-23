package com.heecomou.service.impl;

import com.heecomou.exception.BusinessException;
import com.heecomou.mapper.UserMapper;
import com.heecomou.model.dto.ChangePasswordRequest;
import com.heecomou.model.dto.UpdateProfileRequest;
import com.heecomou.model.entity.User;
import com.heecomou.model.vo.UserVO;
import com.heecomou.security.JwtUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("UserServiceImpl 单元测试")
class UserServiceImplTest {

    private UserMapper userMapper;
    private JwtUtil jwtUtil;
    private UserServiceImpl userService;
    private User existingUser;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        jwtUtil = new JwtUtil("test_secret_key_for_user_service_test_min32chars", 300000L, 3600000L);
        userService = new UserServiceImpl(userMapper, jwtUtil);

        existingUser = new User();
        existingUser.setId(1L);
        existingUser.setUsername("testuser");
        existingUser.setPasswordHash("$2a$10$dummyhashdummyhashdummyhashdummyhashdummyhashdummy");
        existingUser.setEmail("test@example.com");
    }

    @Test
    @DisplayName("updateProfile 更新昵称成功")
    void shouldUpdateNickname() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setNickname("小明");

        when(userMapper.selectById(1L)).thenReturn(existingUser);
        doReturn(1).when(userMapper).updateById(ArgumentMatchers.<User>any());
        when(userMapper.selectById(1L)).thenReturn(existingUser);

        UserVO result = userService.updateProfile(1L, request);

        verify(userMapper, times(1)).updateById(ArgumentMatchers.<User>any());
        assertEquals("小明", existingUser.getNickname());
    }

    @Test
    @DisplayName("updateProfile 更新邮箱成功")
    void shouldUpdateEmail() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setEmail("newemail@example.com");

        when(userMapper.selectById(1L)).thenReturn(existingUser);
        doReturn(1).when(userMapper).updateById(ArgumentMatchers.<User>any());
        when(userMapper.selectById(1L)).thenReturn(existingUser);

        UserVO result = userService.updateProfile(1L, request);

        verify(userMapper, times(1)).updateById(ArgumentMatchers.<User>any());
        assertEquals("newemail@example.com", existingUser.getEmail());
    }

    @Test
    @DisplayName("updateProfile 无效邮箱格式抛出 BusinessException")
    void shouldThrowOnInvalidEmail() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setEmail("notanemail");

        when(userMapper.selectById(1L)).thenReturn(existingUser);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.updateProfile(1L, request));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("邮箱格式不正确"));
        verify(userMapper, never()).updateById(ArgumentMatchers.<User>any());
    }

    @Test
    @DisplayName("updateProfile 用户不存在抛出 404")
    void shouldThrow404OnUpdateProfileUserNotFound() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setNickname("test");

        when(userMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.updateProfile(999L, request));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("updateProfile 空字段不触发更新")
    void shouldNotUpdateEmptyFields() {
        UpdateProfileRequest request = new UpdateProfileRequest();

        when(userMapper.selectById(1L)).thenReturn(existingUser);

        UserVO result = userService.updateProfile(1L, request);

        verify(userMapper, never()).updateById(ArgumentMatchers.<User>any());
        assertEquals("testuser", result.getUsername());
    }

    @Test
    @DisplayName("changePassword 旧密码正确时修改成功")
    void shouldChangePasswordWithCorrectOldPassword() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("oldpass123");

        String encodedOld = userService.encodePassword("oldpass123");
        existingUser.setPasswordHash(encodedOld);

        request.setNewPassword("newpass456");

        when(userMapper.selectById(1L)).thenReturn(existingUser);
        doReturn(1).when(userMapper).updateById(ArgumentMatchers.<User>any());

        assertDoesNotThrow(() -> userService.changePassword(1L, request));
        verify(userMapper, times(1)).updateById(ArgumentMatchers.<User>any());
    }

    @Test
    @DisplayName("changePassword 旧密码错误抛出 BusinessException")
    void shouldThrowOnWrongOldPassword() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("wrongpass");

        String encodedCorrect = userService.encodePassword("correctpass");
        existingUser.setPasswordHash(encodedCorrect);

        request.setNewPassword("newpass456");

        when(userMapper.selectById(1L)).thenReturn(existingUser);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.changePassword(1L, request));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("旧密码不正确"));
        verify(userMapper, never()).updateById(ArgumentMatchers.<User>any());
    }

    @Test
    @DisplayName("changePassword 用户不存在抛出 404")
    void shouldThrow404OnChangePasswordUserNotFound() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("oldpass123");
        request.setNewPassword("newpass456");

        when(userMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.changePassword(999L, request));
        assertEquals(404, ex.getCode());
    }
}
