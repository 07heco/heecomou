package com.heecomou.controller;

import com.heecomou.annotation.RateLimit;
import com.heecomou.exception.BusinessException;
import com.heecomou.model.dto.ApiResponse;
import com.heecomou.model.dto.LoginRequest;
import com.heecomou.model.dto.LoginResponse;
import com.heecomou.model.dto.RefreshRequest;
import com.heecomou.model.dto.RegisterRequest;
import com.heecomou.model.vo.UserVO;
import com.heecomou.security.JwtUtil;
import com.heecomou.security.RefreshTokenService;
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
    private final RefreshTokenService refreshTokenService;

    public AuthController(UserService userService, JwtUtil jwtUtil,
                          TokenBlacklistService blacklistService,
                          RefreshTokenService refreshTokenService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.blacklistService = blacklistService;
        this.refreshTokenService = refreshTokenService;
    }

    @RateLimit(key = "register", capacity = 10, rate = 10, seconds = 60)
    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserVO userVO = userService.register(request);

        String accessToken = jwtUtil.generateToken(userVO.getId(), userVO.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(userVO.getId(), userVO.getUsername());
        long refreshTtl = jwtUtil.getRemainingTtl(refreshToken);
        refreshTokenService.save(refreshToken, userVO.getId(), refreshTtl);

        long expiresIn = jwtUtil.getRemainingTtl(accessToken);
        return ApiResponse.success("注册成功", LoginResponse.of(accessToken, refreshToken, expiresIn, userVO));
    }

    @RateLimit(key = "login", capacity = 10, rate = 10, seconds = 60)
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        String accessToken = userService.login(request);
        UserVO userVO = userService.getByUsername(request.getUsername());

        String refreshToken = jwtUtil.generateRefreshToken(userVO.getId(), userVO.getUsername());
        long refreshTtl = jwtUtil.getRemainingTtl(refreshToken);
        refreshTokenService.save(refreshToken, userVO.getId(), refreshTtl);

        long expiresIn = jwtUtil.getRemainingTtl(accessToken);
        return ApiResponse.success(LoginResponse.of(accessToken, refreshToken, expiresIn, userVO));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtUtil.validateRefreshToken(refreshToken)) {
            throw new BusinessException(401, "refreshToken 无效或已过期");
        }

        Long userId = jwtUtil.getUserIdFromToken(refreshToken);
        String username = jwtUtil.getUsernameFromToken(refreshToken);

        if (!refreshTokenService.isValid(refreshToken, userId)) {
            throw new BusinessException(401, "refreshToken 已失效，请重新登录");
        }

        String newAccessToken = jwtUtil.generateToken(userId, username);
        String newRefreshToken = jwtUtil.generateRefreshToken(userId, username);

        refreshTokenService.revoke(refreshToken, userId);

        long newRefreshTtl = jwtUtil.getRemainingTtl(newRefreshToken);
        refreshTokenService.save(newRefreshToken, userId, newRefreshTtl);

        UserVO userVO = userService.getByUsername(username);
        long expiresIn = jwtUtil.getRemainingTtl(newAccessToken);
        return ApiResponse.success(LoginResponse.of(newAccessToken, newRefreshToken, expiresIn, userVO));
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
