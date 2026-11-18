package com.mall.payment.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 结算单（#144 第二块）。每个卖家每个周期一张，金额单位分。
 */
@Data
public class Settlement {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_PAID = "PAID";

    private String settlementId;
    private String sellerId;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private Long grossAmount;
    private Long commissionAmount;
    private Long netAmount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private Integer isDeleted;
}
