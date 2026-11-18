package com.mall.product.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 秒杀下单请求（#140）。件数不允许超过活动限购，由服务层按活动配置校验。
 */
@Data
public class FlashSaleReserveRequest {

    @NotNull(message = "购买件数不能为空")
    @Min(value = 1, message = "购买件数必须大于 0")
    private Integer quantity;
}
