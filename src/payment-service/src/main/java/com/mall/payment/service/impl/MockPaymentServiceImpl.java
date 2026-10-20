package com.mall.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.IdGenerator;
import com.mall.payment.common.PaymentChannel;
import com.mall.payment.common.PaymentStatus;
import com.mall.payment.dto.request.MockDuplicateRequest;
import com.mall.payment.dto.request.MockPayRequest;
import com.mall.payment.dto.request.MockRefundRequest;
import com.mall.payment.dto.request.PaymentCallbackRequest;
import com.mall.payment.dto.request.RefundRequest;
import com.mall.payment.dto.response.MockDuplicateResponse;
import com.mall.payment.dto.response.MockPayResponse;
import com.mall.payment.dto.response.RefundResponse;
import com.mall.payment.entity.Payment;
import com.mall.payment.entity.PaymentCallback;
import com.mall.payment.mapper.PaymentCallbackMapper;
import com.mall.payment.mapper.PaymentMapper;
import com.mall.payment.service.CallbackResult;
import com.mall.payment.service.MockPaymentService;
import com.mall.payment.service.PaymentCallbackService;
import com.mall.payment.service.PaymentService;
import com.mall.payment.service.RefundService;
import com.mall.payment.service.SignatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * MOCK 渠道实现。模拟支付必须构造完整回调报文并复用回调处理链路，
 * 不能直接把支付单改成 SUCCESS，否则验签、去重、金额核对都测不到。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MockPaymentServiceImpl implements MockPaymentService {

    private static final DateTimeFormatter PAY_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;
    private final PaymentCallbackMapper callbackMapper;
    private final PaymentCallbackService callbackService;
    private final RefundService refundService;
    private final SignatureService signatureService;
    private final ObjectMapper objectMapper;

    @Override
    public MockPayResponse mockPay(String buyerId, MockPayRequest request) {
        Payment payment = paymentService.getPaymentEntityForBuyer(buyerId, request.getPaymentId());
        if (payment.getChannel() != PaymentChannel.MOCK) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该支付单不是 MOCK 渠道");
        }
        if (payment.getStatus() != PaymentStatus.PENDING
                && payment.getStatus() != PaymentStatus.CLOSED
                && payment.getStatus() != PaymentStatus.FAILED) {
            throw new BizException(ErrorCode.PARAM_INVALID, "支付单当前状态不可支付");
        }

        String tradeNo = IdGenerator.mockTradeNo();
        PaymentCallbackRequest callback = new PaymentCallbackRequest();
        callback.setTradeNo(tradeNo);
        callback.setPayNo(payment.getPayNo());
        callback.setAmount(payment.getAmount());
        callback.setStatus("SUCCESS");
        callback.setPayTime(LocalDateTime.now().format(PAY_TIME_FORMATTER));
        callback.setSign(signatureService.sign(tradeNo, payment.getPayNo(), payment.getAmount()));

        CallbackResult result = callbackService.process(callback);
        if (!result.isSuccess()) {
            throw new BizException(result.getCode(), result.getMessage());
        }

        Payment latest = paymentMapper.selectById(payment.getPaymentId());
        return MockPayResponse.from(latest, tradeNo);
    }

    @Override
    public MockDuplicateResponse duplicateCallback(MockDuplicateRequest request) {
        PaymentCallback callback = callbackMapper.selectByTradeNo(request.getTradeNo());
        if (callback == null || callback.getRawPayload() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "回调流水不存在");
        }

        PaymentCallbackRequest callbackRequest;
        try {
            callbackRequest = objectMapper.readValue(callback.getRawPayload(), PaymentCallbackRequest.class);
        } catch (Exception e) {
            log.error("解析历史回调报文失败, tradeNo={}", request.getTradeNo(), e);
            throw new BizException(ErrorCode.PARAM_INVALID, "回调报文解析失败");
        }

        CallbackResult result = callbackService.process(callbackRequest);
        return new MockDuplicateResponse(request.getTradeNo(), result.isDuplicate(),
                result.getCode(), result.getMessage());
    }

    @Override
    public RefundResponse mockRefund(String buyerId, MockRefundRequest request) {
        paymentService.getPaymentEntityForBuyer(buyerId, request.getPaymentId());

        RefundRequest refundRequest = new RefundRequest();
        refundRequest.setAmount(request.getAmount());
        refundRequest.setReason(request.getReason());

        RefundResponse created = refundService.createRefund(buyerId, request.getPaymentId(), refundRequest);
        return refundService.auditRefund(created.getRefundId(), true, "MOCK", "模拟退款成功");
    }
}
