package com.mall.payment.dto.response;

import lombok.Data;

import java.time.LocalDate;

/**
 * 退款汇总的 SQL 聚合行，只在 Mapper 与服务层之间传递。
 *
 * <p>日期用 LocalDate 接收（CAST(audited_at AS DATE) 在 MySQL 与 H2 的 MySQL 模式都能映射），
 * 对外再格式化成 YYYY-MM-DD。
 */
public class FinanceSummaryRows {

    private FinanceSummaryRows() {
    }

    @Data
    public static class Totals {
        private Long refundedCount;
        private Long refundedAmount;
        private Long pendingRefundAmount;
    }

    @Data
    public static class DailyRefund {
        private LocalDate day;
        private Long refund;
    }
}
