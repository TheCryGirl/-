package com.mall.payment.channel;

import com.mall.payment.common.ChannelType;
import com.mall.payment.entity.Payment;
import com.mall.payment.entity.Refund;

import java.util.Map;

/**
 * 支付渠道抽象（#137）。
 *
 * <p>回调参数统一为「参数名 → 值」的 Map：MOCK 渠道由 JSON 报文转换而来，
 * 支付宝渠道直接取异步通知的 form 参数。验签与解析都由渠道自己实现，
 * 编排层只消费归一化后的 {@link ChannelNotification}。
 */
public interface PaymentChannel {

    ChannelType type();

    /** 创建渠道侧订单，返回收银台地址；不产生本地状态变更 */
    ChannelOrder createOrder(Payment payment);

    /** 主动查询渠道侧订单状态 */
    ChannelNotification queryOrder(Payment payment);

    /** 验签；失败时调用方不得更新任何本地状态 */
    boolean verifyCallback(Map<String, String> params);

    /** 验签通过后解析为归一化通知（支付或退款） */
    ChannelNotification parseCallback(Map<String, String> params);

    /**
     * 原路退款。out_request_no 固定使用本地 refundId，
     * 渠道侧按该单号幂等，人工重试不会重复退款。
     */
    ChannelRefundResult refund(Refund refund, Payment payment);

    /** 主动查询渠道侧退款结果 */
    ChannelRefundResult queryRefund(Refund refund, Payment payment);
}