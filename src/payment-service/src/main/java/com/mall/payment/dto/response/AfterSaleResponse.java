package com.mall.payment.dto.response;

import com.mall.payment.common.AfterSaleStatus;
import com.mall.payment.common.AfterSaleType;
import com.mall.payment.entity.AfterSaleTicket;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 售后单返回结构。
 */
@Data
@Builder
public class AfterSaleResponse {

    private String ticketId;
    private String orderId;
    private String paymentId;
    private String buyerId;
    private String sellerId;
    private AfterSaleType type;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AfterSaleResponse from(AfterSaleTicket ticket) {
        return AfterSaleResponse.builder()
                .ticketId(ticket.getTicketId())
                .orderId(ticket.getOrderId())
                .paymentId(ticket.getPaymentId())
                .buyerId(ticket.getBuyerId())
                .sellerId(ticket.getSellerId())
                .type(ticket.getAfterSaleType())
                .amount(ticket.getAmount())
                .reason(ticket.getReason())
                .status(ticket.getStatus())
                .refundId(ticket.getRefundId())
                .returnTrackingNo(ticket.getReturnTrackingNo())
                .exchangeTrackingNo(ticket.getExchangeTrackingNo())
                .auditRemark(ticket.getAuditRemark())
                .auditedBy(ticket.getAuditedBy())
                .auditedAt(ticket.getAuditedAt())
                .buyerShippedAt(ticket.getBuyerShippedAt())
                .sellerShippedAt(ticket.getSellerShippedAt())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .build();
    }
}
