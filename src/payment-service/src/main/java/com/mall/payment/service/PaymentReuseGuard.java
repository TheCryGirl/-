package com.mall.payment.service;

import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.PaymentChannel;
import com.mall.payment.entity.Payment;

/**
 * 支付单「复用已有单」的准入校验。
 *
 * <p>幂等键命中、订单已有可用支付单、以及唯一键冲突后的回读，都属于复用路径：
 * 在没有校验之前，调用方拿到的是别人或其他请求的支付单号与支付链接。
 * 复用前必须满足三个条件：支付单有买家信息且等于调用方、订单号与本次请求一致、
 * 支付渠道与本次请求一致。任一不满足即拒绝，不再继续回读别的候选单。
 *
 * <p>无状态工具类，不持有字段、不依赖外部服务。越权沿用 {@link ErrorCode#FORBIDDEN}，
 * 订单/渠道错配沿用 {@link ErrorCode#PARAM_INVALID}，不新增错误码。
 */
public final class PaymentReuseGuard {

    private PaymentReuseGuard() {
    }

    /**
     * 校验已有支付单能否被本次请求复用。
     *
     * @param existing 待复用的已有支付单，为 null 时直接返回
     * @param callerId 调用方买家 ID
     * @param orderId  本次请求的订单号
     * @param channel  本次请求的支付渠道
     * @throws BizException 支付单缺少买家、买家不是调用方，或订单/渠道与请求不一致
     */
    public static void requireReusable(Payment existing, String callerId, String orderId,
                                       PaymentChannel channel) {
        if (existing == null) {
            return;
        }
        String ownerId = existing.getBuyerId();
        if (callerId == null || callerId.isBlank() || ownerId == null || ownerId.isBlank() || !ownerId.equals(callerId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权复用该支付单");
        }
        if (orderId == null || !orderId.equals(existing.getOrderId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "支付单与订单不匹配");
        }
        if (channel == null || channel != existing.getChannel()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "支付单与支付渠道不匹配");
        }
    }
}
