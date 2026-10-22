package com.mall.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ReserveStockRequest {

    @NotBlank(message = "orderId 不能为空")
    private String orderId;

    /** 格式 yyyy-MM-dd HH:mm:ss */
    @NotBlank(message = "expireAt 不能为空")
    private String expireAt;

    @Valid
    @NotEmpty(message = "预占明细不能为空")
    private List<ReserveItem> items;
}
