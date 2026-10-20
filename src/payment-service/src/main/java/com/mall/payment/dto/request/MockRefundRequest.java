package com.mall.payment.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * MOCK 渠道模拟退款并直接审核通过。
 */
@Data
public class MockRefundRequest {

    @NotBlank(message = "支付单ID不能为空")
    private String paymentId;

    @NotNull(message = "退款金额不能为空")
    @Min(value = 1, message = "退款金额必须大于 0")
    private Long amount;

    @Size(max = 255, message = "退款原因过长")
    private String reason;
}
