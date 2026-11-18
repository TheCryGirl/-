package com.mall.order.client.dto;

import lombok.Data;

/**
 * 下单占用优惠券（#140，调用 payment-service 内部接口）。
 */
@Data
public class OccupyCouponRequestDTO {

    private String userCouponId;
    private String orderId;
    private String buyerId;
}
