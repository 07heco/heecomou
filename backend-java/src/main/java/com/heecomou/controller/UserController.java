package com.heecomou.controller;

import com.heecomou.model.dto.ApiResponse;
import com.heecomou.model.vo.UserVO;
import com.heecomou.security.UserPrincipal;
import com.heecomou.service.UserService;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserVO> me() {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication();
        UserVO userVO = userService.getByUsername(principal.getName());
        return ApiResponse.success(userVO);
    }
}
