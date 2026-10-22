package com.mall.product.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReservationItem {
    private Long id;
    private String reservationId;
    private String skuId;
    private Integer quantity;
}
