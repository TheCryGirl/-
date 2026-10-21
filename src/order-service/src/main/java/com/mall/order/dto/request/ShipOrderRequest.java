package com.mall.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ShipOrderRequest {

    @NotBlank(message = "物流单号不能为空")
    private String trackingNo;
}
