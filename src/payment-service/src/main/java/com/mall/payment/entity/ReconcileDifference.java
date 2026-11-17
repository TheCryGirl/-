package com.mall.payment.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 渠道对账差异（#137）。只记录清单，不修改 payment/refund。
 */
@Data
public class ReconcileDifference {

    private Long differenceId;
    private String batchId;
    private String channel;
    private String billType;
    private String diffType;
    private String billTradeNo;
    private String localNo;
    private Long localAmount;
    private Long channelAmount;
    private String status;
    private String remark;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
}