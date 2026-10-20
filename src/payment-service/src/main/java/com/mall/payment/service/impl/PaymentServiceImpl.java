package com.mall.payment.service.impl;

import com.mall.payment.client.OrderClient;
import com.mall.payment.client.dto.OrderDTO;
import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.IdGenerator;
import com.mall.payment.common.OrderStatus;
import com.mall.payment.common.PaymentChannel;
import com.mall.payment.common.PaymentStatus;
import com.mall.payment.common.Result;
import com.mall.payment.dto.request.CreatePaymentRequest;
import com.mall.payment.dto.response.CreatePaymentResponse;
import com.mall.payment.dto.response.PaymentDetailResponse;
import com.mall.payment.entity.Payment;
import com.mall.payment.mapper.PaymentMapper;
import com.mall.payment.service.PaymentService;
import com.mall.payment.service.PaymentTxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 支付单创建与查询实现。
 *
 * <p>应付金额只从订单服务读取，不接受前端传入（BR-22）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 64;

    private final PaymentMapper paymentMapper;
    private final PaymentTxService paymentTxService;
    private final OrderClient orderClient;

    @Override
    public CreatePaymentResponse createPayment(String buyerId, String idempotencyKey,
                                               CreatePaymentRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "幂等键不能为空");
        }
        if (idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "幂等键过长");
        }
        if (request.getChannel() != PaymentChannel.MOCK) {
            throw new BizException(ErrorCode.PARAM_INVALID, "当前仅支持 MOCK 支付渠道");
        }

        Payment byKey = paymentMapper.selectByIdempotencyKey(idempotencyKey);
        if (byKey != null) {
            return toCreateResponse(byKey);
        }

        Payment latest = paymentMapper.selectLatestByOrderId(request.getOrderId());
        if (latest != null && (latest.getStatus() == PaymentStatus.PENDING
                || latest.getStatus() == PaymentStatus.SUCCESS
                || latest.getStatus() == PaymentStatus.REFUNDED)) {
            return toCreateResponse(latest);
        }

        OrderDTO order = fetchOrder(request.getOrderId());
        if (!buyerId.equals(order.getBuyerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权支付该订单");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BizException(ErrorCode.PARAM_INVALID, "订单当前状态不可支付");
        }
        if (order.getPayAmount() == null || order.getPayAmount() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "订单应付金额异常");
        }

        Payment payment = new Payment();
        payment.setPaymentId(IdGenerator.paymentId());
        payment.setPayNo(IdGenerator.payNo());
        payment.setOrderId(order.getOrderId());
        payment.setBuyerId(buyerId);
        payment.setChannel(request.getChannel());
        payment.setAmount(order.getPayAmount());
        payment.setRefundedAmount(0L);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setCreatedBy(buyerId);
        payment.setUpdatedBy(buyerId);
        payment.setIsDeleted(false);

        Payment saved = paymentTxService.insertIfAbsent(payment);
        log.info("支付单创建成功, paymentId={}, orderId={}, amount={}",
                saved.getPaymentId(), saved.getOrderId(), saved.getAmount());
        return toCreateResponse(saved);
    }

    @Override
    public PaymentDetailResponse getPayment(String buyerId, String paymentId) {
        return PaymentDetailResponse.from(getPaymentEntityForBuyer(buyerId, paymentId));
    }

    @Override
    public PaymentDetailResponse getPaymentByOrderId(String buyerId, String orderId) {
        Payment payment = paymentMapper.selectLatestByOrderId(orderId);
        if (payment == null) {
            throw new BizException(ErrorCode.PAYMENT_NOT_FOUND, "支付单不存在");
        }
        if (!buyerId.equals(payment.getBuyerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看该支付单");
        }
        return PaymentDetailResponse.from(payment);
    }

    @Override
    public Payment getPaymentEntityForBuyer(String buyerId, String paymentId) {
        Payment payment = paymentMapper.selectById(paymentId);
        if (payment == null) {
            throw new BizException(ErrorCode.PAYMENT_NOT_FOUND, "支付单不存在");
        }
        if (!buyerId.equals(payment.getBuyerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该支付单");
        }
        return payment;
    }

    private OrderDTO fetchOrder(String orderId) {
        Result<OrderDTO> result;
        try {
            result = orderClient.getOrder(orderId);
        } catch (Exception e) {
            log.error("调用订单服务失败, orderId={}", orderId, e);
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "订单服务暂不可用，请稍后重试");
        }
        if (result == null) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "订单服务返回为空");
        }
        if (result.getCode() != ErrorCode.SUCCESS) {
            throw new BizException(result.getCode(),
                    result.getMessage() == null ? "查询订单失败" : result.getMessage());
        }
        if (result.getData() == null) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "订单数据异常");
        }
        return result.getData();
    }

    private CreatePaymentResponse toCreateResponse(Payment payment) {
        return new CreatePaymentResponse(
                payment.getPaymentId(),
                payment.getPayNo(),
                payment.getAmount(),
                "mock://pay/" + payment.getPaymentId()
        );
    }
}
