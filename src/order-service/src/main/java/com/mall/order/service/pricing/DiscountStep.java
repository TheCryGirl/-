package com.mall.order.service.pricing;

/**
 * 优惠计算链上的一步（#136）。
 *
 * <p>链顺序固定，写进《05-接口契约》：商品小计 → 店铺/平台满减、满折 → 优惠券抵扣 → 加运费 → 应付合计。
 * 具体规则来源由 #140（促销工具）实现，本 issue 只提供链与两次调用位：
 * {@code order()=2} 是满减/满折，{@code order()=3} 是优惠券。
 *
 * <p>实现方必须保证返回值是「分」的整数，且不小于 0、不超过该店商品小计；
 * 需要按比例计算的折扣必须调用 {@link Money#applyRate(long, java.math.BigDecimal)}，由它统一四舍五入一次。
 */
public interface DiscountStep {

    /** 链上的位置：2 = 店铺/平台满减、满折；3 = 优惠券抵扣 */
    int order();

    String name();

    /**
     * @param sellerId    店铺（卖家ID）
     * @param goodsAmount 该店商品小计，单位分
     * @return 抵扣金额，单位分
     */
    long discountFor(String sellerId, long goodsAmount);
}
