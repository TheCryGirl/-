package com.mall.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderResponse {
    private String orderId;
    private String orderNo;
    private Long payAmount;
    private String expireAt;
}
