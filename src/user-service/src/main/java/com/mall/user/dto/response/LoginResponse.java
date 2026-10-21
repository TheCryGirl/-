package com.mall.user.dto.response;

import lombok.Data;

/**
 * 登录出参。
 */
@Data
public class LoginResponse {

    private String token;
    private UserResponse user;
}
