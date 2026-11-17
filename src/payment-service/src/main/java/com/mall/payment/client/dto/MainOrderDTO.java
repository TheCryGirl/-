package com.mall.payment.client.dto;

import com.mall.payment.common.OrderStatus;
import lombok.Data;

import java.util.List;

/**
 * order-service 主订单详情的裁剪视图（#135），只声明支付服务需要的字段。
 * 跨店合并结算时，支付单按主订单创建，金额取主订单 payAmount。
 */
@Data
public class MainOrderDTO {

    private String mainOrderId;
    private String mainOrderNo;
    private String buyerId;
    private OrderStatus status;
    private Long payAmount;
    /** 店铺子订单（按店铺展开） */
    private List<OrderDTO> subOrders;
}