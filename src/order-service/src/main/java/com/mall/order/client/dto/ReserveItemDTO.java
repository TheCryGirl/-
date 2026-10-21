package com.mall.order.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReserveItemDTO {
    private String skuId;
    private Integer quantity;
}
