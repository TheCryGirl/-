package com.mall.payment.client.dto;

/**
 * product-service 确认扣减库存的返回数据。
 */
public record ConfirmStockResponse(boolean confirmed) {
}
