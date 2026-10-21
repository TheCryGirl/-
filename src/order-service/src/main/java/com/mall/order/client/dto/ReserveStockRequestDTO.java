package com.mall.order.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReserveStockRequestDTO {
    private String orderId;
    private String expireAt;
    private List<ReserveItemDTO> items;
}
