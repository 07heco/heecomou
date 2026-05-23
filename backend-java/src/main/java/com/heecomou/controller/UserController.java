package com.heecomou.controller;

import com.heecomou.model.dto.ApiResponse;
import com.heecomou.model.dto.ChangePasswordRequest;
import com.heecomou.model.dto.UpdateProfileRequest;
import com.heecomou.model.vo.UserVO;
import com.heecomou.security.UserPrincipal;
import com.heecomou.service.UserService;

import jakarta.validation.Valid;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PutMapping("/profile")
    public ApiResponse<UserVO> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication();
        UserVO userVO = userService.updateProfile(principal.getUserId(), request);
        return ApiResponse.success(userVO);
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication();
        userService.changePassword(principal.getUserId(), request);
        return ApiResponse.success("密码修改成功", null);
    }
}
