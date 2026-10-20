package com.mall.payment.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 第三方支付回调报文（MOCK 渠道）。
 */
@Data
public class PaymentCallbackRequest {

    @NotBlank(message = "交易号不能为空")
    @Size(max = 64, message = "交易号过长")
    private String tradeNo;

    @NotBlank(message = "支付单号不能为空")
    @Size(max = 32, message = "支付单号过长")
    private String payNo;

    @NotNull(message = "回调金额不能为空")
    @Min(value = 1, message = "回调金额必须大于 0")
    private Long amount;

    @NotBlank(message = "回调状态不能为空")
    private String status;

    private String payTime;

    @NotBlank(message = "签名不能为空")
    private String sign;
}
