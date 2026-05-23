package com.heecomou.service;

import com.heecomou.model.dto.LoginRequest;
import com.heecomou.model.dto.RegisterRequest;
import com.heecomou.model.vo.UserVO;

public interface UserService {

    UserVO register(RegisterRequest request);

    String login(LoginRequest request);

    UserVO getByUsername(String username);
}