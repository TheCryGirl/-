package com.mall.cart.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 购物车条目。只存 skuId、productId、数量和勾选状态，不存商品快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {

    private String cartItemId;
    private String cartId;
    private String userId;
    private String productId;
    private String skuId;
    private Integer quantity;
    private Integer selected;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private Integer isDeleted;
}
