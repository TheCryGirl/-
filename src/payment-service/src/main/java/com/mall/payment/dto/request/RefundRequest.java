package com.mall.payment.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 买家申请退款。
 */
@Data
public class RefundRequest {

    @NotNull(message = "退款金额不能为空")
    @Min(value = 1, message = "退款金额必须大于 0")
    private Long amount;

    @Size(max = 255, message = "退款原因过长")
    private String reason;
}
