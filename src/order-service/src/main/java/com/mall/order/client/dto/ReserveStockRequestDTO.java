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
    private FlashSale flashSale;

    public ReserveStockRequestDTO(String orderId, String expireAt, List<ReserveItemDTO> items) {
        this(orderId, expireAt, items, null);
    }

    public record FlashSale(String saleId, String buyerId, long unitPrice) {}
}
