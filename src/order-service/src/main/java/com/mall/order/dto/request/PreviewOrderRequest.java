package com.mall.order.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 下单预览请求（#136）：只接收购物车项与收货地址。
 *
 * <p>{@code addressId} 可空：为空时用默认地址；既没传也没有默认地址时提示「请先选择收货地址」，
 * 因为运费要按收货地区计算。
 */
@Data
public class PreviewOrderRequest {

    @NotEmpty(message = "请选择要结算的商品")
    private List<String> cartItemIds;

    /** 收货地址ID；为空时取默认地址 */
    private String addressId;

    /** 选中的用户优惠券ID（#140）；不传或为空表示不用券 */
    private String userCouponId;
}
