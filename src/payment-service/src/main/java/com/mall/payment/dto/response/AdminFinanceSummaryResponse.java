package com.mall.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理员资金/风控汇总（退款口径）。
 *
 * <p>口径：全部 is_deleted = 0 的退款单，SQL 聚合、不分页。
 * refundedCount / refundedAmount 只统计 refund.status = SUCCESS，按 audited_at 归日；
 * pendingRefundAmount 为 status = PENDING 的申请金额合计（未审核通过的都不计入已退）。
 * 金额单位：分。
 */
@Data
public class AdminFinanceSummaryResponse {

    /** 成功退款单数 */
    private long refundedCount;
    /** 成功退款金额合计，单位分 */
    private long refundedAmount;
    /** 待审核退款申请金额合计，单位分 */
    private long pendingRefundAmount;
    /** 成功退款按审核日聚合，按日期升序 */
    private List<DailyRefund> daily = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyRefund {
        /** YYYY-MM-DD，来源于 audited_at */
        private String day;
        /** 当日成功退款金额合计，单位分 */
        private long refund;
    }
}
