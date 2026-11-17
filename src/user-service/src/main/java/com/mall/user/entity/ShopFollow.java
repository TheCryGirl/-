package com.mall.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 店铺关注（#142）。同一用户同一店铺只有一条。 */
@Data
public class ShopFollow {

    private String followId;
    private String shopId;
    private String userId;
    private LocalDateTime createdAt;
}