package com.mall.order.dto.response;

import com.mall.order.common.OrderStatus;
import lombok.Data;

import java.util.List;

@Data
public class OrderListItem {
    private String orderId;
    private String orderNo;
    private OrderStatus status;
    private Long payAmount;
    private String createdAt;
    private List<OrderItemResponse> items;
}
