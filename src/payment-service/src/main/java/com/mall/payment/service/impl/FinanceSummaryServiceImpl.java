package com.mall.payment.service.impl;

import com.mall.payment.dto.response.AdminFinanceSummaryResponse;
import com.mall.payment.dto.response.FinanceSummaryRows;
import com.mall.payment.mapper.RefundMapper;
import com.mall.payment.service.FinanceSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 退款资金汇总。全部统计在 SQL 完成，一次取回全量口径，不读实体、不分页。
 */
@Service
@RequiredArgsConstructor
public class FinanceSummaryServiceImpl implements FinanceSummaryService {

    private final RefundMapper refundMapper;

    @Override
    public AdminFinanceSummaryResponse summary() {
        AdminFinanceSummaryResponse response = new AdminFinanceSummaryResponse();
        FinanceSummaryRows.Totals totals = refundMapper.selectFinanceTotals();
        if (totals != null) {
            response.setRefundedCount(nullToZero(totals.getRefundedCount()));
            response.setRefundedAmount(nullToZero(totals.getRefundedAmount()));
            response.setPendingRefundAmount(nullToZero(totals.getPendingRefundAmount()));
        }
        List<AdminFinanceSummaryResponse.DailyRefund> daily = new ArrayList<>();
        for (FinanceSummaryRows.DailyRefund row : refundMapper.selectFinanceDaily()) {
            String day = row.getDay() == null ? null : row.getDay().format(DateTimeFormatter.ISO_LOCAL_DATE);
            daily.add(new AdminFinanceSummaryResponse.DailyRefund(day, nullToZero(row.getRefund())));
        }
        response.setDaily(daily);
        return response;
    }

    private static long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
