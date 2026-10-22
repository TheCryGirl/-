package com.mall.user.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人档案入参。
 */
@Data
public class UpdateProfileRequest {

    @Size(min = 2, max = 20, message = "昵称长度须为 2-20 位")
    private String nickname;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Size(max = 255, message = "头像地址过长")
    private String avatar;
}
