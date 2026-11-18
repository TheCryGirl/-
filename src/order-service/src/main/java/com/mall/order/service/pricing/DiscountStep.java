package com.mall.order.service.pricing;

/**
 * 优惠计算链上的一步（#136，签名扩展于 #140）。
 *
 * <p>链顺序固定，写进《05-接口契约》：商品小计 → 店铺/平台满减、满折 → 优惠券抵扣 → 加运费 → 应付合计。
 * {@code order()=2} 是满减/满折，{@code order()=3} 是优惠券。
 *
 * <p>实现方必须保证返回值是「分」的整数，且不小于 0、不超过该店商品小计；
 * 需要按比例计算的折扣必须调用 {@link Money#applyRate(long, java.math.BigDecimal)}，由它统一四舍五入一次。
 * 业务规则拒绝（如券未达门槛、活动不可叠加）抛 {@link com.mall.order.common.BizException}，
 * 由计价链原样上抛；其余运行时异常按 0 处理并记 warn。
 */
public interface DiscountStep {

    /** 链上的位置：2 = 店铺/平台满减、满折；3 = 优惠券抵扣 */
    int order();

    String name();

    /**
     * @param ctx 单店计价上下文：店铺、商品小计、选中券与生效活动
     * @return 抵扣金额，单位分
     */
    long discountFor(PricingContext ctx);
}
