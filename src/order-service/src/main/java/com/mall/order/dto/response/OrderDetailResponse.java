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
    private Long totalAmount;
    private Long payAmount;
    private AddressSnapshot addressSnapshot;
    private List<OrderItemResponse> items;
    private String trackingNo;
    private String createdAt;
    private String paidAt;
    private String expireAt;
}
