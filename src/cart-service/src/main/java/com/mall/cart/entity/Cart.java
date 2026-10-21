package com.mall.cart.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 购物车。一个用户对应一辆购物车。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cart {

    private String cartId;
    private String userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private Integer isDeleted;
}
