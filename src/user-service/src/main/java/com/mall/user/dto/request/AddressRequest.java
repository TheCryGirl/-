package com.mall.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 收货地址入参。
 */
@Data
public class AddressRequest {

    @NotBlank(message = "收货人不能为空")
    private String receiver;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "省/直辖市不能为空")
    private String province;

    @NotBlank(message = "市/区不能为空")
    private String city;

    private String district;

    @NotBlank(message = "详细地址不能为空")
    private String detail;

    private Boolean defaultAddress = Boolean.FALSE;
}
