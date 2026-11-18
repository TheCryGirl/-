package com.mall.payment.dto.request;

import lombok.Data;

/**
 * 生成结算单入参（#144 第二块）。period 格式 yyyy-MM，缺省为当前自然月。
 */
@Data
public class GenerateSettlementRequest {

    private String period;
}
