package com.heecomou.controller;

import com.heecomou.model.dto.ApiResponse;
import com.heecomou.model.dto.LoginRequest;
import com.heecomou.model.dto.LoginResponse;
import com.heecomou.model.dto.RegisterRequest;
import com.heecomou.model.vo.UserVO;
import com.heecomou.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ApiResponse<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        UserVO userVO = userService.register(request);
        return ApiResponse.success(userVO);
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        String token = userService.login(request);
        UserVO userVO = userService.getByUsername(request.getUsername());
        return ApiResponse.success(LoginResponse.of(token, userVO));
    }
}