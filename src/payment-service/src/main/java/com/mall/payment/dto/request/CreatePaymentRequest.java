package com.mall.payment.dto.request;

import com.mall.payment.common.PaymentChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发起支付请求。金额不在请求体里，由服务端向订单服务查询。
 */
@Data
public class CreatePaymentRequest {

    @NotBlank(message = "订单ID不能为空")
    @Size(max = 32, message = "订单ID过长")
    private String orderId;

    @NotNull(message = "支付渠道不能为空")
    private PaymentChannel channel;
}
