package com.mall.payment.channel;

import com.mall.payment.common.ChannelType;
import com.mall.payment.dto.request.PaymentCallbackRequest;
import com.mall.payment.entity.Payment;
import com.mall.payment.entity.Refund;
import com.mall.payment.service.SignatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MOCK 渠道（默认）。签名沿用 md5(tradeNo + payNo + amount + MOCK_SECRET)，
 * 退款即时成功；渠道退款单号由 refundId 派生，重复执行结果一致。
 */
@Component
@RequiredArgsConstructor
public class MockChannel implements PaymentChannel {

    private final SignatureService signatureService;

    @Override
    public ChannelType type() {
        return ChannelType.MOCK;
    }

    @Override
    public ChannelOrder createOrder(Payment payment) {
        return new ChannelOrder("mock://pay/" + payment.getPaymentId(), null);
    }

    @Override
    public ChannelNotification queryOrder(Payment payment) {
        throw new UnsupportedOperationException("MOCK 渠道不支持主动查单，请通过模拟支付接口触发回调");
    }

    @Override
    public boolean verifyCallback(Map<String, String> params) {
        if (params.get("refundId") != null) {
            // 退款回调：md5(channelRefundNo + refundId + amount + MOCK_SECRET)
            Long amount = parseAmount(params.get("amount"));
            if (amount == null || params.get("channelRefundNo") == null || params.get("sign") == null) {
                return false;
            }
            String expected = signatureService.sign(params.get("channelRefundNo"), params.get("refundId"), amount);
            return expected.equalsIgnoreCase(params.get("sign"));
        }
        return signatureService.verify(fromParams(params));
    }

    @Override
    public ChannelNotification parseCallback(Map<String, String> params) {
        if (params.get("refundId") != null) {
            String channelRefundNo = params.get("channelRefundNo");
            String status = params.get("status");
            return new ChannelNotification(
                    ChannelNotification.Kind.REFUND,
                    "MOCK-" + channelRefundNo + "-" + status,
                    channelRefundNo,
                    params.get("refundId"),
                    parseAmount(params.get("amount")),
                    "SUCCESS".equalsIgnoreCase(status),
                    channelRefundNo,
                    params.get("payTime"));
        }
        PaymentCallbackRequest request = fromParams(params);
        Long amount = request.getAmount();
        return new ChannelNotification(
                ChannelNotification.Kind.PAYMENT,
                request.getTradeNo(),
                request.getTradeNo(),
                request.getPayNo(),
                amount,
                "SUCCESS".equalsIgnoreCase(request.getStatus()),
                null,
                request.getPayTime());
    }

    @Override
    public ChannelRefundResult refund(Refund refund, Payment payment) {
        return ChannelRefundResult.success(mockChannelRefundNo(refund.getRefundId()));
    }

    @Override
    public ChannelRefundResult queryRefund(Refund refund, Payment payment) {
        return ChannelRefundResult.success(mockChannelRefundNo(refund.getRefundId()));
    }

    public static String mockChannelRefundNo(String refundId) {
        return "MOCKRF-" + refundId;
    }

    /** JSON 报文与 form 参数共用同一组字段 */
    public static Map<String, String> toParams(PaymentCallbackRequest request) {
        Map<String, String> params = new HashMap<>();
        params.put("tradeNo", request.getTradeNo());
        params.put("payNo", request.getPayNo());
        params.put("amount", request.getAmount() == null ? null : String.valueOf(request.getAmount()));
        params.put("status", request.getStatus());
        params.put("payTime", request.getPayTime());
        params.put("sign", request.getSign());
        return params;
    }

    private static Long parseAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(amount);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static PaymentCallbackRequest fromParams(Map<String, String> params) {
        PaymentCallbackRequest request = new PaymentCallbackRequest();
        request.setTradeNo(params.get("tradeNo"));
        request.setPayNo(params.get("payNo"));
        String amount = params.get("amount");
        if (amount != null && !amount.isBlank()) {
            try {
                request.setAmount(Long.parseLong(amount));
            } catch (NumberFormatException ignored) {
                // 金额非法时保持 null，验签必然失败
            }
        }
        request.setStatus(params.get("status"));
        request.setPayTime(params.get("payTime"));
        request.setSign(params.get("sign"));
        return request;
    }
}