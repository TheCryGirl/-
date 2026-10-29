package com.mall.payment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 卖家/管理员处理售后单。
 */
@Data
public class AuditAfterSaleRequest {

    @NotNull(message = "处理结果不能为空")
    private Boolean approved;

    @Size(max = 255, message = "处理备注过长")
    private String remark;
}
