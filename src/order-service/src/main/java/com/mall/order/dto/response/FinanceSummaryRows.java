package com.mall.order.dto.response;

import lombok.Data;

import java.time.LocalDate;

/**
 * 财务汇总的 SQL 聚合行，只在 Mapper 与服务层之间传递。
 *
 * <p>日期列统一用 LocalDate 接收（MySQL / H2 下 CAST(timestamp AS DATE) 都能映射），
 * 对外输出时再格式化为 YYYY-MM-DD 字符串，避免跨库的时间串格式差异。
 */
public class FinanceSummaryRows {

    private FinanceSummaryRows() {
    }

    @Data
    public static class Totals {
        private Long gmv;
        private Long orderCount;
        private Long paidCount;
        private Long paidAmount;
        private Long cancelledCount;
    }

    @Data
    public static class StatusCount {
        private String status;
        private Long count;
    }

    @Data
    public static class DailyPaid {
        private LocalDate day;
        private Long count;
        private Long amount;
    }
}
