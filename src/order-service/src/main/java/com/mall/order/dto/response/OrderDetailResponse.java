package com.mall.order.dto.response;

import com.mall.order.common.OrderStatus;
import lombok.Data;

import java.util.List;

@Data
public class OrderDetailResponse {
    private String orderId;
    private String orderNo;
    private OrderStatus status;
    private String buyerId;
    private String sellerId;
    /** 主订单ID（#135）；历史单店订单为 null */
    private String mainOrderId;
    /** 主订单号（#135）；历史单店订单为 null */
    private String mainOrderNo;
    private Long totalAmount;
    /** 运费，单位分（#136） */
    private Long freightAmount;
    /** 优惠抵扣，单位分（#136） */
    private Long discountAmount;
    private Long payAmount;
    private AddressSnapshot addressSnapshot;
    private List<OrderItemResponse> items;
    private String trackingNo;
    private String createdAt;
    private String paidAt;
    private String expireAt;
}
