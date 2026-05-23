package com.heecomou.controller;

import com.heecomou.model.dto.ApiResponse;
import com.heecomou.model.vo.UserVO;
import com.heecomou.security.UserPrincipal;
import com.heecomou.service.UserService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("UserController 单元测试")
class UserControllerTest {

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

        SecurityContextHolder.clearContext();
    }
}
