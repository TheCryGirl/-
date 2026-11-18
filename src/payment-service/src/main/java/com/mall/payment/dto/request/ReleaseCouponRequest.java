package com.mall.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 释放订单占用的优惠券（#140，order-service 内部调用；取消订单时返还）。
 */
@Data
public class ReleaseCouponRequest {

    @NotBlank(message = "订单ID不能为空")
    @Size(max = 32, message = "订单ID过长")
    private String orderId;
}