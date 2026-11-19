package com.mall.payment.service.impl;

import com.mall.payment.channel.PaymentChannelRegistry;
import com.mall.payment.client.OrderClient;
import com.mall.payment.client.dto.MainOrderDTO;
import com.mall.payment.client.dto.OrderDTO;
import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.IdGenerator;
import com.mall.payment.common.OrderStatus;
import com.mall.payment.common.ChannelType;
import com.mall.payment.common.PaymentStatus;
import com.mall.payment.common.Result;
import com.mall.payment.dto.request.CreatePaymentRequest;
import com.mall.payment.dto.response.CreatePaymentResponse;
import com.mall.payment.dto.response.PaymentDetailResponse;
import com.mall.payment.entity.Payment;
import com.mall.payment.mapper.PaymentMapper;
import com.mall.payment.service.PaymentReuseGuard;
import com.mall.payment.service.PaymentService;
import com.mall.payment.service.PaymentTxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
    private final PaymentChannelRegistry channelRegistry;

    @Override
    public CreatePaymentResponse createPayment(String buyerId, String idempotencyKey,
                                               CreatePaymentRequest request) {
        // 跨店合并结算（#135）：orderId 指向主订单时按主订单金额聚合支付
        if (request.getOrderId() != null && request.getOrderId().startsWith("M")) {
            return createMainPayment(buyerId, idempotencyKey, request.getOrderId(), request.getChannel());
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "幂等键不能为空");
        }
        if (idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "幂等键过长");
        }
        if (request.getChannel() == null || !channelRegistry.isEnabled(request.getChannel())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "支付渠道未启用");
        }

        Payment byKey = paymentMapper.selectByIdempotencyKey(idempotencyKey);
        if (byKey != null) {
            PaymentReuseGuard.requireReusable(byKey, buyerId, request.getOrderId(), request.getChannel());
            return toCreateResponse(byKey);
        }

        Payment latest = paymentMapper.selectLatestByOrderId(request.getOrderId());
        if (latest != null && (latest.getStatus() == PaymentStatus.PENDING
                || latest.getStatus() == PaymentStatus.SUCCESS
                || latest.getStatus() == PaymentStatus.REFUNDED)) {
            PaymentReuseGuard.requireReusable(latest, buyerId, request.getOrderId(), request.getChannel());
            return toCreateResponse(latest);
        }

        OrderDTO order = fetchOrder(request.getOrderId(), buyerId);
        if (!buyerId.equals(order.getBuyerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权支付该订单");
        }
        if (order.getSellerId() == null || order.getSellerId().isBlank()) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "订单卖家信息缺失");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BizException(ErrorCode.PARAM_INVALID, "订单当前状态不可支付");
        }
        if (order.isFlashSale() && (order.getExpireAt() == null || !LocalDateTime.parse(order.getExpireAt(),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).isAfter(LocalDateTime.now()))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "秒杀订单已超时，不能发起支付");
        }
        if (order.getPayAmount() == null || order.getPayAmount() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "订单应付金额异常");
        }

        Payment payment = new Payment();
        payment.setPaymentId(IdGenerator.paymentId());
        payment.setPayNo(IdGenerator.payNo());
        payment.setOrderId(order.getOrderId());
        payment.setBuyerId(buyerId);
        payment.setSellerId(order.getSellerId());
        payment.setChannel(request.getChannel());
        payment.setAmount(order.getPayAmount());
        payment.setRefundedAmount(0L);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setCreatedBy(buyerId);
        payment.setUpdatedBy(buyerId);
        payment.setIsDeleted(false);

        Payment saved = paymentTxService.insertIfAbsent(payment);
        log.info("支付单创建成功, paymentId={}, orderId={}, sellerId={}, amount={}",
                saved.getPaymentId(), saved.getOrderId(), saved.getSellerId(), saved.getAmount());
        return toCreateResponse(saved);
    }

    /**
     * 跨店合并结算（#135）：按主订单发起支付，金额用主订单 payAmount。
     *
     * <p>支付单 order_id 存主订单ID；回调成功后逐个通知子订单已支付并确认库存，
     * 主订单状态由 order-service 在「全部子订单 PAID」时自动置 PAID。
     * 幂等与复用规则与单店一致（BR-21/BR-22）：金额只从订单服务读取，不接受前端传入。
     */
    @Override
    public CreatePaymentResponse createMainPayment(String buyerId, String idempotencyKey,
                                                   String mainOrderId, ChannelType channel) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "幂等键不能为空");
        }
        if (idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "幂等键过长");
        }
        if (channel != ChannelType.MOCK) {
            throw new BizException(ErrorCode.PARAM_INVALID, "当前仅支持 MOCK 支付渠道");
        }
        if (mainOrderId == null || mainOrderId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "主订单ID不能为空");
        }

        Payment byKey = paymentMapper.selectByIdempotencyKey(idempotencyKey);
        if (byKey != null) {
            PaymentReuseGuard.requireReusable(byKey, buyerId, mainOrderId, channel);
            return toCreateResponse(byKey);
        }

        Payment latest = paymentMapper.selectLatestByOrderId(mainOrderId);
        if (latest != null && (latest.getStatus() == PaymentStatus.PENDING
                || latest.getStatus() == PaymentStatus.SUCCESS
                || latest.getStatus() == PaymentStatus.REFUNDED)) {
            PaymentReuseGuard.requireReusable(latest, buyerId, mainOrderId, channel);
            return toCreateResponse(latest);
        }

        MainOrderDTO mainOrder = fetchMainOrder(mainOrderId, buyerId);
        if (!buyerId.equals(mainOrder.getBuyerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权支付该订单");
        }
        if (mainOrder.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BizException(ErrorCode.PARAM_INVALID, "订单当前状态不可支付");
        }
        if (mainOrder.getPayAmount() == null || mainOrder.getPayAmount() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "订单应付金额异常");
        }
        if (mainOrder.getSubOrders() == null || mainOrder.getSubOrders().isEmpty()) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "主订单缺少子订单信息");
        }

        Payment payment = new Payment();
        payment.setPaymentId(IdGenerator.paymentId());
        payment.setPayNo(IdGenerator.payNo());
        payment.setOrderId(mainOrder.getMainOrderId());
        payment.setBuyerId(buyerId);
        // 跨店支付单没有单一卖家；退款按子订单归属卖家（见 RefundService）
        payment.setSellerId(null);
        payment.setChannel(channel);
        payment.setAmount(mainOrder.getPayAmount());
        payment.setRefundedAmount(0L);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setCreatedBy(buyerId);
        payment.setUpdatedBy(buyerId);
        payment.setIsDeleted(false);

        Payment saved = paymentTxService.insertIfAbsent(payment);
        log.info("主订单支付单创建成功, paymentId={}, mainOrderId={}, amount={}",
                saved.getPaymentId(), saved.getOrderId(), saved.getAmount());
        return toCreateResponse(saved);
    }

    private MainOrderDTO fetchMainOrder(String mainOrderId, String buyerId) {
        Result<MainOrderDTO> result;
        try {
            result = orderClient.getMainOrder(mainOrderId, buyerId);
        } catch (Exception e) {
            log.error("调用订单服务失败, mainOrderId={}, buyerId={}", mainOrderId, buyerId, e);
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "订单服务暂不可用，请稍后重试");
        }
        if (result == null) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "订单服务返回为空");
        }
        if (result.getCode() != ErrorCode.SUCCESS) {
            throw new BizException(result.getCode(),
                    result.getMessage() == null ? "查询主订单失败" : result.getMessage());
        }
        if (result.getData() == null) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "主订单数据异常");
        }
        return result.getData();
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

    private OrderDTO fetchOrder(String orderId, String buyerId) {
        Result<OrderDTO> result;
        try {
            result = orderClient.getOrder(orderId, buyerId);
        } catch (Exception e) {
            log.error("调用订单服务失败, orderId={}, buyerId={}", orderId, buyerId, e);
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
        String payUrl = channelRegistry.get(payment.getChannel()).createOrder(payment).payUrl();
        return new CreatePaymentResponse(
                payment.getPaymentId(),
                payment.getPayNo(),
                payment.getAmount(),
                payUrl
        );
    }
}
