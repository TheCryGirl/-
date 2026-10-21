package com.mall.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册入参。
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "账号不能为空")
    @Size(min = 4, max = 20, message = "账号长度须为 4-20 位")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "账号只能包含字母、数字、下划线")
    private String account;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度须为 6-32 位")
    private String password;

    @NotBlank(message = "昵称不能为空")
    @Size(min = 2, max = 20, message = "昵称长度须为 2-20 位")
    private String nickname;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}
