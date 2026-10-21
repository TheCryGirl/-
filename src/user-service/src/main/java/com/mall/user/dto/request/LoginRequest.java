package com.mall.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户登录入参。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "账号不能为空")
    private String account;

    @NotBlank(message = "密码不能为空")
    private String password;
}
