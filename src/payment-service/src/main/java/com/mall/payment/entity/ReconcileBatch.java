package com.mall.payment.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 渠道对账批次（#137）。
 */
@Data
public class ReconcileBatch {

    private String batchId;
    private String channel;
    private String fileName;
    private Integer totalRows;
    private Integer diffCount;
    private String importedBy;
    private LocalDateTime createdAt;
}