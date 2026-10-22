package com.mall.payment.service.impl;

import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.IdGenerator;
import com.mall.payment.common.PageResult;
import com.mall.payment.common.PaymentStatus;
import com.mall.payment.common.RefundStatus;
import com.mall.payment.dto.request.RefundRequest;
import com.mall.payment.dto.response.RefundResponse;
import com.mall.payment.entity.Payment;
import com.mall.payment.entity.Refund;
import com.mall.payment.mapper.PaymentMapper;
import com.mall.payment.mapper.RefundMapper;
import com.mall.payment.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 退款实现。
 *
 * <p>退款额度通过 payment.refunded_amount 的条件 UPDATE 预占，
 * 并发下也不会超过实付金额（BR-26、BR-27）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentMapper paymentMapper;
    private final RefundMapper refundMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundResponse createRefund(String buyerId, String paymentId, RefundRequest request) {
        Payment payment = paymentMapper.selectById(paymentId);
        if (payment == null) {
            throw new BizException(ErrorCode.PAYMENT_NOT_FOUND, "支付单不存在");
        }
        if (!buyerId.equals(payment.getBuyerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该支付单");
        }
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new BizException(ErrorCode.REFUND_EXCEEDS_PAID, "支付单已全额退款");
        }
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new BizException(ErrorCode.PARAM_INVALID, "支付单当前状态不可退款");
        }

        int reserved = paymentMapper.reserveRefundAmount(paymentId, request.getAmount());
        if (reserved == 0) {
            Payment latest = paymentMapper.selectById(paymentId);
            if (latest == null) {
                throw new BizException(ErrorCode.PAYMENT_NOT_FOUND, "支付单不存在");
            }
            if (latest.getStatus() != PaymentStatus.SUCCESS) {
                throw new BizException(ErrorCode.PARAM_INVALID, "支付单当前状态不可退款");
            }
            throw new BizException(ErrorCode.REFUND_EXCEEDS_PAID, "退款金额超出实付金额");
        }

        Refund refund = new Refund();
        refund.setRefundId(IdGenerator.refundId());
        refund.setPaymentId(paymentId);
        refund.setOrderId(payment.getOrderId());
        refund.setBuyerId(buyerId);
        refund.setAmount(request.getAmount());
        refund.setReason(request.getReason());
        refund.setStatus(RefundStatus.PENDING);
        refund.setCreatedBy(buyerId);
        refund.setUpdatedBy(buyerId);
        refund.setIsDeleted(false);
        refundMapper.insert(refund);

        Refund saved = refundMapper.selectById(refund.getRefundId());
        log.info("退款申请已创建, refundId={}, paymentId={}, amount={}",
                saved.getRefundId(), saved.getPaymentId(), saved.getAmount());
        return RefundResponse.from(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundResponse auditRefund(String refundId, boolean approved, String auditedBy, String remark) {
        Refund refund = refundMapper.selectById(refundId);
        if (refund == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "退款单不存在");
        }
        if (refund.getStatus() != RefundStatus.PENDING) {
            throw new BizException(ErrorCode.PARAM_INVALID, "退款单已处理");
        }

        RefundStatus targetStatus = approved ? RefundStatus.SUCCESS : RefundStatus.REJECTED;
        int updated = refundMapper.updateAudit(refundId, targetStatus, auditedBy, remark, LocalDateTime.now());
        if (updated == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "退款单已被处理");
        }

        if (approved) {
            paymentMapper.markRefundedIfFullyRefunded(refund.getPaymentId());
            log.info("退款审核通过, refundId={}, paymentId={}, amount={}, auditedBy={}",
                    refundId, refund.getPaymentId(), refund.getAmount(), auditedBy);
        } else {
            paymentMapper.releaseRefundAmount(refund.getPaymentId(), refund.getAmount());
            paymentMapper.restoreSuccessIfNotFullyRefunded(refund.getPaymentId());
            log.info("退款审核驳回, refundId={}, paymentId={}, amount={}, auditedBy={}",
                    refundId, refund.getPaymentId(), refund.getAmount(), auditedBy);
        }

        return RefundResponse.from(refundMapper.selectById(refundId));
    }

    @Override
    public PageResult<RefundResponse> listRefunds(String userId, String role, String status,
                                                  int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        RefundStatus refundStatus = parseStatus(status);

        boolean reviewer = "SELLER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
        String buyerFilter = reviewer ? null : userId;
        if (!reviewer && (buyerFilter == null || buyerFilter.isBlank())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或用户信息缺失");
        }

        long total = refundMapper.count(refundStatus, buyerFilter);
        List<RefundResponse> list = refundMapper
                .selectPage(refundStatus, buyerFilter, (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(RefundResponse::from)
                .toList();
        return PageResult.of(list, total, safePage, safeSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundResponse autoRefund(Payment payment, String reason) {
        Payment latest = paymentMapper.selectById(payment.getPaymentId());
        if (latest == null || latest.getStatus() != PaymentStatus.SUCCESS) {
            log.warn("支付单状态不满足自动退款条件, paymentId={}", payment.getPaymentId());
            return null;
        }

        long remaining = latest.getAmount() - latest.getRefundedAmount();
        if (remaining <= 0) {
            log.warn("支付单已无可退金额, paymentId={}", payment.getPaymentId());
            return null;
        }

        RefundRequest request = new RefundRequest();
        request.setAmount(remaining);
        request.setReason(reason);
        RefundResponse created = createRefund(latest.getBuyerId(), latest.getPaymentId(), request);
        RefundResponse audited = auditRefund(created.getRefundId(), true, "SYSTEM", "订单取消自动退款");
        log.warn("订单取消自动退款完成, paymentId={}, refundId={}, amount={}",
                latest.getPaymentId(), audited.getRefundId(), audited.getAmount());
        return audited;
    }

    private RefundStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return RefundStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "退款状态不合法");
        }
    }
}
