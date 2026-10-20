package com.mall.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * MOCK 渠道模拟支付。
 */
@Data
public class MockPayRequest {

    @NotBlank(message = "支付单ID不能为空")
    private String paymentId;
}
