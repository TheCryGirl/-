package com.mall.payment.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 结算明细（#144 第二块）。order_id 全局唯一，一笔子订单最多计入一次结算。
 */
@Data
public class SettlementItem {

    private Long id;
    private String settlementId;
    private String orderId;
    private Long grossAmount;
    private Long commissionAmount;
    private LocalDateTime createdAt;
}
