package com.mall.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 结算单状态更新入参（#144 第二块）。目前仅支持标记为 PAID。
 */
@Data
public class UpdateSettlementStatusRequest {

    @NotBlank(message = "目标状态不能为空")
    private String status;
}
