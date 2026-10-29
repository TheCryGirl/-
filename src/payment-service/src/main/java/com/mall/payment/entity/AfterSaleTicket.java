package com.mall.payment.entity;

import com.mall.payment.common.AfterSaleStatus;
import com.mall.payment.common.AfterSaleType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 售后单，与 after_sale_ticket 表一一对应。
 */
@Data
public class AfterSaleTicket {

    private String ticketId;
    private String orderId;
    private String paymentId;
    private String buyerId;
    private String sellerId;
    private AfterSaleType afterSaleType;
    private Long amount;
    private String reason;
    private AfterSaleStatus status;
    private String refundId;
    private String returnTrackingNo;
    private String exchangeTrackingNo;
    private String auditRemark;
    private String auditedBy;
    private LocalDateTime auditedAt;
    private LocalDateTime buyerShippedAt;
    private LocalDateTime sellerShippedAt;
    private String idempotencyKey;
    private Boolean activeFlag;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private Boolean isDeleted;
}
