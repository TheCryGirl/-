package com.mall.order.dto.response;

import com.mall.order.common.OrderStatus;
import lombok.Data;

/**
 * 主订单列表项（#135）：买家订单列表按主订单聚合时使用。
 * 历史单店订单（main_order_id 为 null）不出现在主订单列表，仍走原订单列表。
 */
@Data
public class MainOrderListItem {
    private String mainOrderId;
    private String mainOrderNo;
    private OrderStatus status;
    private Long payAmount;
    /** 店铺子订单数量 */
    private Integer subOrderCount;
    /** 商品总件数（各子订单明细数量之和） */
    private Integer itemCount;
    private String createdAt;
    private String expireAt;
}