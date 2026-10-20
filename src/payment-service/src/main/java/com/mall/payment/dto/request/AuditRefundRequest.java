package com.mall.payment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 卖家或管理员审核退款。
 */
@Data
public class AuditRefundRequest {

    @NotNull(message = "审核结果不能为空")
    private Boolean approved;

    @Size(max = 255, message = "审核备注过长")
    private String remark;
}
