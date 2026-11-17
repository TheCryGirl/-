package com.mall.order.entity;

import com.mall.order.common.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    private String orderId;
    private String orderNo;
    private String buyerId;
    private String sellerId;
    /** 主订单ID（#135）；历史单店订单为 null */
    private String mainOrderId;
    private OrderStatus status;
    private Long totalAmount;
    /** 运费，单位分（#136） */
    private Long freightAmount;
    /** 优惠抵扣，单位分（#136） */
    private Long discountAmount;
    /** 应付金额 = totalAmount + freightAmount - discountAmount */
    private Long payAmount;
    private String receiver;
    private String receiverPhone;
    private String addressFull;
    private String trackingNo;
    /** 快递公司编码，见 ExpressCompany；未发货为 null */
    private String expressCompany;
    /** 发货时间：自动确认收货从这里起算 */
    private LocalDateTime shippedAt;
    private LocalDateTime expireAt;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private Integer isDeleted;
    private Integer inventoryReleased;
}
