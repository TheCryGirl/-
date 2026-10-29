package com.mall.payment.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 售后处理时的可选备注。
 */
@Data
public class AfterSaleRemarkRequest {

    @Size(max = 255, message = "处理备注过长")
    private String remark;
}
