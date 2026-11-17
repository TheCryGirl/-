package com.mall.order.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 下单预览请求（#135）：只接收购物车项，按店铺分组返回预览。
 * 预览不校验收货地址（地址在提交订单时才需要），金额一律服务端计算。
 */
@Data
public class PreviewOrderRequest {

    @NotEmpty(message = "请选择要结算的商品")
    private List<String> cartItemIds;
}