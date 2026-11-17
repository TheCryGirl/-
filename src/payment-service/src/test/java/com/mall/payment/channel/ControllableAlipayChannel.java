package com.mall.payment.channel;

import com.mall.payment.common.ChannelType;
import com.mall.payment.entity.Payment;
import com.mall.payment.entity.Refund;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试用可控支付宝渠道（#137）。type()=ALIPAY，注册进 PaymentChannelRegistry
 * 后让非 MOCK 退款走异步状态机；退款结果/异常/调用次数都由用例显式控制，
 * 不发起任何真实网络请求。
 */
public class ControllableAlipayChannel implements PaymentChannel {

    private volatile ChannelRefundResult nextResult = ChannelRefundResult.success("ALIPAYRF-TEST");
    private volatile boolean throwOnRefund = false;
    private final AtomicInteger refundCalls = new AtomicInteger();

    public void setNextResult(ChannelRefundResult result) {
        this.nextResult = result;
    }

    public void setThrowOnRefund(boolean throwOnRefund) {
        this.throwOnRefund = throwOnRefund;
    }

    public int refundCalls() {
        return refundCalls.get();
    }

    public void reset() {
        nextResult = ChannelRefundResult.success("ALIPAYRF-TEST");
        throwOnRefund = false;
        refundCalls.set(0);
    }

    @Override
    public ChannelType type() {
        return ChannelType.ALIPAY;
    }

    @Override
    public ChannelOrder createOrder(Payment payment) {
        return new ChannelOrder("https://sandbox.example/pay/" + payment.getPaymentId(), null);
    }

    @Override
    public ChannelNotification queryOrder(Payment payment) {
        throw new UnsupportedOperationException("测试渠道不查单");
    }

    @Override
    public boolean verifyCallback(Map<String, String> params) {
        return true;
    }

    @Override
    public ChannelNotification parseCallback(Map<String, String> params) {
        throw new UnsupportedOperationException("测试渠道不解析回调");
    }

    @Override
    public ChannelRefundResult refund(Refund refund, Payment payment) {
        refundCalls.incrementAndGet();
        if (throwOnRefund) {
            throw new IllegalStateException("模拟渠道网络异常");
        }
        return nextResult;
    }

    @Override
    public ChannelRefundResult queryRefund(Refund refund, Payment payment) {
        return nextResult;
    }
}