package com.heecomou.model.dto;

import com.heecomou.model.vo.UserVO;

public class LoginResponse {

    private String token;
    private Long userId;
    private String username;

    public static LoginResponse of(String token, UserVO userVO) {
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(userVO.getId());
        response.setUsername(userVO.getUsername());
        return response;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}