package com.mall.payment.dto.response;

import com.mall.payment.common.AfterSaleReasonCode;
import com.mall.payment.common.AfterSaleStatus;
import com.mall.payment.common.AfterSaleType;
import com.mall.payment.entity.AfterSaleAttachment;
import com.mall.payment.entity.AfterSaleEvent;
import com.mall.payment.entity.AfterSaleTicket;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 售后单返回结构。
 *
 * <p>#143 增量：reasonCode 原因枚举；attachments 凭证与 events 时间线仅在详情接口填充，
 * 列表接口为轻量结构（两者为 null），避免逐行查询。
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
    private AfterSaleReasonCode reasonCode;
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
    private List<AfterSaleAttachment> attachments;
    private List<AfterSaleEvent> events;

    public static AfterSaleResponse from(AfterSaleTicket ticket) {
        return from(ticket, null, null);
    }

    public static AfterSaleResponse from(AfterSaleTicket ticket,
                                         List<AfterSaleAttachment> attachments,
                                         List<AfterSaleEvent> events) {
        return AfterSaleResponse.builder()
                .ticketId(ticket.getTicketId())
                .orderId(ticket.getOrderId())
                .paymentId(ticket.getPaymentId())
                .buyerId(ticket.getBuyerId())
                .sellerId(ticket.getSellerId())
                .type(ticket.getAfterSaleType())
                .amount(ticket.getAmount())
                .reason(ticket.getReason())
                .reasonCode(ticket.getReasonCode())
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
                .attachments(attachments)
                .events(events)
                .build();
    }
}
