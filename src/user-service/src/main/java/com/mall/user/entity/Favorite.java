package com.mall.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品收藏实体，与 favorite 表一一对应。
 */
@Data
public class Favorite {

    private String favoriteId;
    private String userId;
    private String productId;
    private LocalDateTime createdAt;
}
