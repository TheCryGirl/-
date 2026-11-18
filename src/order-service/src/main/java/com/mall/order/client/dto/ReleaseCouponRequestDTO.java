package com.mall.order.client.dto;

import lombok.Data;

/**
 * 释放订单占用的优惠券（#140，调用 payment-service 内部接口；取消订单时返还）。
 */
@Data
public class ReleaseCouponRequestDTO {

    private String orderId;
}
