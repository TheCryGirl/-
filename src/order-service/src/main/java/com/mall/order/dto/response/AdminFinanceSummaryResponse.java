package com.mall.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理员资金/风控汇总（订单口径）。
 *
 * <p>口径：全部 is_deleted = 0 的订单，SQL 聚合、不分页。
 * gmv 为全部未删订单的 pay_amount 之和；paid* 只统计 PAID/SHIPPED/COMPLETED/REFUNDED；
 * daily 按 paid_at 归日（YYYY-MM-DD），只含 paid 状态订单。
 * 金额单位：分。
 */
@Data
public class AdminFinanceSummaryResponse {

    /** 全部未删订单 pay_amount 合计，单位分 */
    private long gmv;
    /** 全部未删订单数 */
    private long orderCount;
    /** pay 状态订单数：PAID/SHIPPED/COMPLETED/REFUNDED */
    private long paidCount;
    /** pay 状态订单 pay_amount 合计，单位分 */
    private long paidAmount;
    /** 已取消订单数 */
    private long cancelledCount;
    /** 各状态订单数，按状态名升序 */
    private List<StatusCount> byStatus = new ArrayList<>();
    /** 按支付日聚合，按日期升序 */
    private List<DailyAmount> daily = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusCount {
        private String status;
        private long count;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyAmount {
        /** YYYY-MM-DD，来源于 paid_at */
        private String day;
        /** 当日支付订单数 */
        private long count;
        /** 当日支付订单金额合计，单位分 */
        private long amount;
    }
}
