package com.mall.product.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AuditProductRequest {

    @NotNull(message = "审核结果不能为空")
    private Boolean approved;

    private String remark;
}
