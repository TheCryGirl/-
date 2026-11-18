package com.mall.product.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀下单记录（#140），与 flash_sale_order 表一一对应。
 * 限购靠 uk_sale_user 唯一键：同一用户同一活动只允许一条记录。
 */
@Data
public class FlashSaleOrder {

    private String saleOrderId;
    private String saleId;
    private String userId;
    private Integer quantity;
    private LocalDateTime createdAt;
}
