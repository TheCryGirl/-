package com.mall.payment.client.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付成功后确认扣减库存的请求体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockConfirmRequest {

    @NotBlank
    private String orderId;
}
