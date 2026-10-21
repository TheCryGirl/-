package com.mall.product.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReserveStockResponse {
    private String reservationId;
    private List<ReserveItemResponse> items;
}
