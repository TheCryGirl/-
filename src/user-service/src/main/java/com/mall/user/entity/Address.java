package com.mall.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收货地址实体，与 address 表一一对应。
 */
@Data
public class Address {

    private String addressId;
    private String userId;
    private String receiver;
    private String phone;
    private String province;
    private String city;
    private String district;
    private String detail;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isDeleted;
}
