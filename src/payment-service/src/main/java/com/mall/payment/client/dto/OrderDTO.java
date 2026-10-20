package com.mall.payment.client.dto;

import com.mall.payment.common.OrderStatus;
import lombok.Data;

/**
 * order-service 订单详情的裁剪视图，只声明支付服务需要的字段。
 */
@Data
public class OrderDTO {

    private String orderId;
    private String orderNo;
    private OrderStatus status;
    private String buyerId;
    private Long payAmount;
}
