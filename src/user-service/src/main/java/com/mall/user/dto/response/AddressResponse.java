package com.mall.user.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收货地址出参。
 */
@Data
public class AddressResponse {

    private String addressId;
    private String receiver;
    private String phone;
    private String province;
    private String city;
    private String district;
    private String detail;
    /**
     * 是否默认地址。
     *
     * <p>同时以 isDefault（与库字段同名）和 defaultAddress（与入参、Issue 术语一致）两个名字输出，
     * 避免前端在“提交用 defaultAddress、展示用 isDefault”之间反复转换。
     */
    private Boolean isDefault;
    private Boolean defaultAddress;
    private LocalDateTime createdAt;
}
