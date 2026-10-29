package com.mall.user.entity;

import com.mall.user.common.ShopStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 卖家店铺实体，与 shop 表一一对应。
 */
@Data
public class Shop {

    private String shopId;
    private String sellerId;
    private String name;
    private String logo;
    private String description;
    private String contactPhone;
    private String contactWechat;
    private ShopStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isDeleted;
}
