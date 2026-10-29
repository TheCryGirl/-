package com.mall.payment.common;

/**
 * 售后类型。
 */
public enum AfterSaleType {
    /** 仅退款：卖家同意后直接退款。 */
    REFUND_ONLY,
    /** 退货退款：买家先退货，卖家确认收货后退款。 */
    RETURN_REFUND,
    /** 换货：买家退货，卖家重新发货。 */
    EXCHANGE
}
