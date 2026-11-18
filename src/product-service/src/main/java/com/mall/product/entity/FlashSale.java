package com.mall.product.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀活动（#140），与 flash_sale 表一一对应。
 * sale_price 为秒杀价（分）；sale_stock 为活动库存，靠条件 UPDATE 扣减防超卖。
 */
@Data
public class FlashSale {

    private String saleId;
    private String productId;
    private String skuId;
    private Long salePrice;
    private Integer saleStock;
    private Integer limitPerUser;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isDeleted;
}
