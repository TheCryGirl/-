package com.mall.payment.service;

import com.mall.payment.dto.response.AdminFinanceSummaryResponse;

/**
 * 管理员资金汇总（退款口径）。授权由 controller 负责，服务层只做全量 SQL 聚合。
 */
public interface FinanceSummaryService {

    AdminFinanceSummaryResponse summary();
}
