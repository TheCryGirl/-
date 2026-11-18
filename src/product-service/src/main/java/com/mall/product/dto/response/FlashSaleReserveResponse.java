package com.mall.product.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 秒杀下单结果（#140）。
 */
@Data
@Builder
public class FlashSaleReserveResponse {

    private String saleOrderId;
    private String saleId;
    private Long salePrice;
    private Integer quantity;
}
