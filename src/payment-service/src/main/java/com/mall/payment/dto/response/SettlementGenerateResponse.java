package com.mall.payment.dto.response;

import lombok.Data;

/**
 * 生成结算单结果（#144 第二块）。
 */
@Data
public class SettlementGenerateResponse {

    private String period;
    private int created;
    private int updated;
    private int skippedFrozen;
    private int orderCount;

    public static SettlementGenerateResponse of(String period, int created, int updated,
                                                int skippedFrozen, int orderCount) {
        SettlementGenerateResponse response = new SettlementGenerateResponse();
        response.setPeriod(period);
        response.setCreated(created);
        response.setUpdated(updated);
        response.setSkippedFrozen(skippedFrozen);
        response.setOrderCount(orderCount);
        return response;
    }
}
