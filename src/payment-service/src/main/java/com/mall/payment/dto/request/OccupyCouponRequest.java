package com.mall.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 下单占用优惠券（#140，order-service 内部调用）。
 */
@Data
public class OccupyCouponRequest {

    @NotBlank(message = "用户券ID不能为空")
    @Size(max = 32, message = "用户券ID过长")
    private String userCouponId;

    @NotBlank(message = "订单ID不能为空")
    @Size(max = 32, message = "订单ID过长")
    private String orderId;

    @NotNull(message = "占用者不能为空")
    private String buyerId;
}