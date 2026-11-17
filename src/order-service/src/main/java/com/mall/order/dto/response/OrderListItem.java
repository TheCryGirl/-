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
    /** 主订单ID（#135）；历史单店订单为 null。买家列表据此按主订单聚合展示。 */
    private String mainOrderId;
    /** 主订单号（#135）；历史单店订单为 null */
    private String mainOrderNo;
    /**
     * 收货信息快照：仅卖家视角的列表返回，且手机号已掩码、fullAddress 为 null；
     * 买家视角为 null，不额外暴露。完整地址只在订单详情按归属返回。
     */
    private AddressSnapshot addressSnapshot;
}
