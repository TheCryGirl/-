package com.mall.order.client.dto;

import lombok.Data;

import java.util.List;

@Data
public class CartDTO {
    private String cartId;
    private List<CartItemDTO> items;
}
