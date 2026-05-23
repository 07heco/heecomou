package com.heecomou.controller;

import com.heecomou.model.dto.ApiResponse;
import com.heecomou.model.dto.LoginRequest;
import com.heecomou.model.dto.LoginResponse;
import com.heecomou.model.dto.RegisterRequest;
import com.heecomou.model.vo.UserVO;
import com.heecomou.security.JwtUtil;
import com.heecomou.security.TokenBlacklistService;
import com.heecomou.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService blacklistService;

    public AuthController(UserService userService, JwtUtil jwtUtil, TokenBlacklistService blacklistService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.blacklistService = blacklistService;
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

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String token = extractToken(request);
        if (token != null && jwtUtil.validateToken(token)) {
            long remainingTtl = jwtUtil.getRemainingTtl(token);
            blacklistService.blacklist(token, remainingTtl);
        }
        return ApiResponse.success("登出成功", null);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}