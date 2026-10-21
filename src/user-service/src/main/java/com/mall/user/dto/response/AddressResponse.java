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
    private Boolean isDefault;
    private LocalDateTime createdAt;
}
