package com.mall.payment.channel;

import com.mall.payment.common.BizException;
import com.mall.payment.common.ChannelType;
import com.mall.payment.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 渠道注册表：按配置收集已启用的渠道实现。MOCK 始终可用；
 * 支付宝沙箱由 payment.alipay.enabled 开关决定是否注册。
 */
@Component
public class PaymentChannelRegistry {

    private final Map<ChannelType, PaymentChannel> channels = new EnumMap<>(ChannelType.class);

    public PaymentChannelRegistry(List<PaymentChannel> implementations) {
        for (PaymentChannel channel : implementations) {
            channels.put(channel.type(), channel);
        }
    }

    public PaymentChannel get(ChannelType type) {
        PaymentChannel channel = channels.get(type);
        if (channel == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "支付渠道未启用: " + type);
        }
        return channel;
    }

    public boolean isEnabled(ChannelType type) {
        return channels.containsKey(type);
    }

    public Set<ChannelType> enabledChannels() {
        return channels.keySet();
    }
}