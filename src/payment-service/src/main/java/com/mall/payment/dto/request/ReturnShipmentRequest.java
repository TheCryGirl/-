package com.mall.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 买家回填退货物流单号。
 */
@Data
public class ReturnShipmentRequest {

    @NotBlank(message = "退货物流单号不能为空")
    @Size(max = 64, message = "物流单号过长")
    private String trackingNo;
}
