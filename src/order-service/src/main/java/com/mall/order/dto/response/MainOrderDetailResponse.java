package com.mall.order.dto.response;

import com.mall.order.common.OrderStatus;
import lombok.Data;

import java.util.List;

/**
 * 主订单详情（#135）：聚合支付视角，子订单按店铺展开。
 */
@Data
public class MainOrderDetailResponse {
    private String mainOrderId;
    private String mainOrderNo;
    private String buyerId;
    /** PENDING_PAYMENT / PAID / CANCELLED */
    private OrderStatus status;
    /** 应付合计，单位分 */
    private Long payAmount;
    private String paidAt;
    private String expireAt;
    private String createdAt;
    /** 店铺子订单列表 */
    private List<OrderDetailResponse> subOrders;
}