package com.heecomou.controller;

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
    public UserVO register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }
}