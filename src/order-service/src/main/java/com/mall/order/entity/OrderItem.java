package com.mall.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {
    private Long id;
    private String orderId;
    private String skuId;
    private String productId;
    private String productTitle;
    private String specName;
    private Long priceSnapshot;
    private Integer quantity;
    private Long subtotal;
    private String mainImage;
    private String sellerId;
    private LocalDateTime createdAt;
    private Integer isDeleted;
}
