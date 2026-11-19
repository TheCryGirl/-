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
    private boolean flashSale;
    private AddressSnapshot addressSnapshot;
    private List<OrderItemResponse> items;
    private String trackingNo;
    /** 快递公司编码与展示名（#138），未发货时均为 null */
    private String expressCompany;
    private String expressCompanyLabel;
    private String shippedAt;
    /**
     * 预计还有几分钟自动确认收货（向上取整，已到期为 0）；
     * 未发货或已不在 SHIPPED 状态时为 null，前端据此决定是否显示倒计时文案。
     * 单位取分钟是为了支持「配成 1 分钟」的验收场景，前端自行折算成天/小时。
     */
    private Long autoConfirmRemainingMinutes;
    private String createdAt;
    private String paidAt;
    private String expireAt;
}
