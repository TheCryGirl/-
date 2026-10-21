package com.mall.order.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReserveStockResponseDTO {
    private String reservationId;
    private List<ReserveItemDTO> items;
}

