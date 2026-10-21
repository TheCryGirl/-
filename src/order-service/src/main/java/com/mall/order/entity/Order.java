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
    private OrderStatus status;
    private Long totalAmount;
    private Long payAmount;
    private String receiver;
    private String receiverPhone;
    private String addressFull;
    private String trackingNo;
    private LocalDateTime expireAt;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private Integer isDeleted;
    private Integer inventoryReleased;
}
