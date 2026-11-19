package com.mall.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {

    @NotBlank(message = "收货地址不能为空")
    private String addressId;

    private List<String> cartItemIds;
    private String flashSaleId;
    private Integer quantity;
    private Long expectedUnitPrice;
    private Long expectedPayAmount;

    /** 选中的用户优惠券ID（#140）；不传或为空表示不用券 */
    private String userCouponId;
}
