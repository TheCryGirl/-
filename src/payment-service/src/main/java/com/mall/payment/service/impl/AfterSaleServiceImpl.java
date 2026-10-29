package com.mall.payment.service.impl;

import com.mall.payment.client.OrderClient;
import com.mall.payment.client.dto.OrderDTO;
import com.mall.payment.common.AfterSaleStatus;
import com.mall.payment.common.AfterSaleType;
import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.IdGenerator;
import com.mall.payment.common.OrderStatus;
import com.mall.payment.common.PageResult;
import com.mall.payment.common.PaymentStatus;
import com.mall.payment.common.Result;
import com.mall.payment.dto.request.CreateAfterSaleRequest;
import com.mall.payment.dto.request.RefundRequest;
import com.mall.payment.dto.response.AfterSaleResponse;
import com.mall.payment.dto.response.RefundResponse;
import com.mall.payment.entity.AfterSaleTicket;
import com.mall.payment.entity.Payment;
import com.mall.payment.mapper.AfterSaleMapper;
import com.mall.payment.mapper.PaymentMapper;
import com.mall.payment.service.AfterSaleService;
import com.mall.payment.service.AfterSaleTxService;
import com.mall.payment.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 分类型售后实现。
 *
 * <p>并发安全依赖两层条件更新：
 * <ul>
 *   <li>售后单状态流转：WHERE 当前状态 = 期望前置状态</li>
 *   <li>退款金额：复用 RefundService 的 payment.refunded_amount 条件预占</li>
 * </ul>
 * 所有本地写操作在一个事务内完成；订单通知与库存回补由 RefundService 在事务提交后触发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AfterSaleServiceImpl implements AfterSaleService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 64;

    private final AfterSaleMapper afterSaleMapper;
    private final AfterSaleTxService afterSaleTxService;
    private final OrderClient orderClient;
    private final PaymentMapper paymentMapper;
    private final RefundService refundService;

    @Override
    public AfterSaleResponse create(String buyerId, String idempotencyKey,
                                    CreateAfterSaleRequest request) {
        String key = idempotencyKey == null ? null : idempotencyKey.trim();
        if (key == null || key.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "幂等键不能为空");
        }
        if (key.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "幂等键过长");
        }
        if (request.getType() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "售后类型不能为空");
        }

        AfterSaleTicket existing = afterSaleMapper.selectByIdempotencyKey(key);
        if (existing != null) {
            checkBuyerAccess(existing, buyerId);
            return AfterSaleResponse.from(existing);
        }
        AfterSaleTicket active = afterSaleMapper.selectActiveByOrderId(request.getOrderId());
        if (active != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该订单已有进行中的售后单");
        }

        long amount = 0;
        if (request.getType() != AfterSaleType.EXCHANGE) {
            if (request.getAmount() == null || request.getAmount() <= 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "退款金额必须大于 0");
            }
            amount = request.getAmount();
        }

        OrderDTO order = fetchOrder(request.getOrderId(), buyerId);
        if (!buyerId.equals(order.getBuyerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权对该订单申请售后");
        }
        if (order.getStatus() != OrderStatus.PAID
                && order.getStatus() != OrderStatus.SHIPPED
                && order.getStatus() != OrderStatus.COMPLETED) {
            throw new BizException(ErrorCode.PARAM_INVALID, "当前订单状态不支持售后");
        }
        if (order.getSellerId() == null || order.getSellerId().isBlank()) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "订单卖家信息缺失");
        }

        Payment payment = paymentMapper.selectLatestByOrderId(order.getOrderId());
        if (payment == null) {
            throw new BizException(ErrorCode.PAYMENT_NOT_FOUND, "支付单不存在");
        }
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new BizException(ErrorCode.REFUND_EXCEEDS_PAID, "支付单已全额退款");
        }
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new BizException(ErrorCode.PARAM_INVALID, "支付单当前状态不支持售后");
        }
        if (request.getType() != AfterSaleType.EXCHANGE) {
            long remaining = payment.getAmount() - payment.getRefundedAmount();
            if (remaining <= 0) {
                throw new BizException(ErrorCode.REFUND_EXCEEDS_PAID, "该订单已无可退金额");
            }
            if (amount > remaining) {
                throw new BizException(ErrorCode.REFUND_EXCEEDS_PAID, "售后金额超出剩余可退金额");
            }
        }

        AfterSaleTicket ticket = new AfterSaleTicket();
        ticket.setTicketId(IdGenerator.afterSaleTicketId());
        ticket.setOrderId(order.getOrderId());
        ticket.setPaymentId(payment.getPaymentId());
        ticket.setBuyerId(buyerId);
        ticket.setSellerId(order.getSellerId());
        ticket.setAfterSaleType(request.getType());
        ticket.setAmount(amount);
        ticket.setReason(request.getReason());
        ticket.setStatus(AfterSaleStatus.PENDING);
        ticket.setIdempotencyKey(key);
        ticket.setActiveFlag(true);
        ticket.setCreatedBy(buyerId);
        ticket.setUpdatedBy(buyerId);
        ticket.setIsDeleted(false);

        AfterSaleTicket saved = afterSaleTxService.insertIfAbsent(ticket);
        checkBuyerAccess(saved, buyerId);
        AfterSaleTicket latest = afterSaleMapper.selectById(saved.getTicketId());
        log.info("售后单创建成功, ticketId={}, orderId={}, type={}, amount={}",
                latest.getTicketId(), latest.getOrderId(), latest.getAfterSaleType(), latest.getAmount());
        return AfterSaleResponse.from(latest);
    }

    @Override
    public PageResult<AfterSaleResponse> list(String userId, String role, String status,
                                              String type, String orderId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        AfterSaleStatus statusFilter = parseStatus(status);
        AfterSaleType typeFilter = parseType(type);

        boolean admin = "ADMIN".equalsIgnoreCase(role);
        boolean seller = "SELLER".equalsIgnoreCase(role);
        String buyerFilter = null;
        String sellerFilter = null;
        if (seller) {
            if (userId == null || userId.isBlank()) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或用户信息缺失");
            }
            sellerFilter = userId;
        } else if (!admin) {
            if (userId == null || userId.isBlank()) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或用户信息缺失");
            }
            buyerFilter = userId;
        }

        long total = afterSaleMapper.count(statusFilter, typeFilter, orderId, buyerFilter, sellerFilter);
        List<AfterSaleResponse> list = afterSaleMapper
                .selectPage(statusFilter, typeFilter, orderId, buyerFilter, sellerFilter,
                        (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(AfterSaleResponse::from)
                .toList();
        return PageResult.of(list, total, safePage, safeSize);
    }

    @Override
    public AfterSaleResponse detail(String userId, String role, String ticketId) {
        AfterSaleTicket ticket = requireTicket(ticketId);
        checkDetailAccess(ticket, userId, role);
        return AfterSaleResponse.from(ticket);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AfterSaleResponse audit(String ticketId, String operatorId, String role,
                                   boolean approved, String remark) {
        AfterSaleTicket ticket = requireTicket(ticketId);
        checkSellerAccess(ticket, operatorId, role);

        if (ticket.getStatus() == AfterSaleStatus.REFUNDED
                || ticket.getStatus() == AfterSaleStatus.COMPLETED) {
            return AfterSaleResponse.from(ticket);
        }
        if (!approved) {
            if (ticket.getStatus() == AfterSaleStatus.REJECTED) {
                return AfterSaleResponse.from(ticket);
            }
            if (ticket.getStatus() != AfterSaleStatus.PENDING) {
                throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "当前售后状态不允许驳回");
            }
            int updated = afterSaleMapper.markRejected(
                    ticketId, ticket.getSellerId(), operatorId, remark, LocalDateTime.now());
            if (updated == 0) {
                AfterSaleTicket latest = requireTicket(ticketId);
                if (latest.getStatus() == AfterSaleStatus.REJECTED) {
                    return AfterSaleResponse.from(latest);
                }
                throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "售后状态已变化，请刷新后重试");
            }
            return AfterSaleResponse.from(requireTicket(ticketId));
        }

        if (ticket.getStatus() == AfterSaleStatus.REJECTED) {
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "已驳回的售后单不能再次通过");
        }
        if (ticket.getStatus() == AfterSaleStatus.PENDING) {
            int updated = afterSaleMapper.markApproved(
                    ticketId, ticket.getSellerId(), operatorId, remark, LocalDateTime.now());
            if (updated == 0) {
                AfterSaleTicket latest = requireTicket(ticketId);
                if (latest.getStatus() == AfterSaleStatus.REJECTED) {
                    throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "售后单已被驳回");
                }
            }
        }

        ticket = requireTicket(ticketId);
        if (ticket.getAfterSaleType() == AfterSaleType.REFUND_ONLY) {
            if (ticket.getStatus() == AfterSaleStatus.REFUNDED) {
                return AfterSaleResponse.from(ticket);
            }
            if (ticket.getStatus() != AfterSaleStatus.APPROVED) {
                throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "当前售后状态不允许退款");
            }
            return executeRefundOnly(ticket, operatorId, role, remark);
        }

        if (ticket.getStatus() == AfterSaleStatus.APPROVED
                || ticket.getStatus() == AfterSaleStatus.BUYER_SHIPPED
                || ticket.getStatus() == AfterSaleStatus.SELLER_RECEIVED
                || ticket.getStatus() == AfterSaleStatus.REFUNDED
                || ticket.getStatus() == AfterSaleStatus.COMPLETED) {
            return AfterSaleResponse.from(ticket);
        }
        throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "当前售后状态不允许该操作");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AfterSaleResponse shipReturn(String ticketId, String buyerId, String trackingNo) {
        AfterSaleTicket ticket = requireTicket(ticketId);
        checkBuyerAccess(ticket, buyerId);
        if (ticket.getAfterSaleType() == AfterSaleType.REFUND_ONLY) {
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "仅退款售后无需退货");
        }
        if (ticket.getStatus() == AfterSaleStatus.APPROVED) {
            int updated = afterSaleMapper.markBuyerShipped(
                    ticketId, buyerId, trackingNo, LocalDateTime.now());
            if (updated == 0) {
                AfterSaleTicket latest = requireTicket(ticketId);
                if (latest.getStatus() == AfterSaleStatus.BUYER_SHIPPED
                        && trackingNo.equals(latest.getReturnTrackingNo())) {
                    return AfterSaleResponse.from(latest);
                }
                if (isAfterBuyerShipped(latest.getStatus())) {
                    return AfterSaleResponse.from(latest);
                }
                throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "当前售后状态不允许提交退货物流");
            }
            return AfterSaleResponse.from(requireTicket(ticketId));
        }
        if (ticket.getStatus() == AfterSaleStatus.BUYER_SHIPPED) {
            if (trackingNo.equals(ticket.getReturnTrackingNo())) {
                return AfterSaleResponse.from(ticket);
            }
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "退货物流已提交，不能修改");
        }
        if (isAfterBuyerShipped(ticket.getStatus())) {
            return AfterSaleResponse.from(ticket);
        }
        throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "当前售后状态不允许提交退货物流");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AfterSaleResponse receiveReturn(String ticketId, String operatorId,
                                           String role, String remark) {
        AfterSaleTicket ticket = requireTicket(ticketId);
        checkSellerAccess(ticket, operatorId, role);
        if (ticket.getAfterSaleType() != AfterSaleType.RETURN_REFUND) {
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "仅退货退款需要确认收货");
        }
        if (ticket.getStatus() == AfterSaleStatus.REFUNDED) {
            return AfterSaleResponse.from(ticket);
        }
        if (ticket.getStatus() == AfterSaleStatus.BUYER_SHIPPED) {
            int updated = afterSaleMapper.markSellerReceived(
                    ticketId, ticket.getSellerId(), remark, LocalDateTime.now());
            if (updated == 0) {
                AfterSaleTicket latest = requireTicket(ticketId);
                if (latest.getStatus() != AfterSaleStatus.SELLER_RECEIVED
                        && latest.getStatus() != AfterSaleStatus.REFUNDED) {
                    throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "售后状态已变化，请刷新后重试");
                }
            }
        } else if (ticket.getStatus() != AfterSaleStatus.SELLER_RECEIVED) {
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "当前售后状态不允许确认收货");
        }

        ticket = requireTicket(ticketId);
        if (ticket.getStatus() == AfterSaleStatus.REFUNDED) {
            return AfterSaleResponse.from(ticket);
        }
        if (ticket.getStatus() != AfterSaleStatus.SELLER_RECEIVED) {
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "当前售后状态不允许退款");
        }
        String refundId = executeRefund(ticket, operatorId, role, remark);
        int updated = afterSaleMapper.markReturnRefunded(
                ticketId, ticket.getSellerId(), refundId, remark, LocalDateTime.now());
        if (updated == 0) {
            AfterSaleTicket latest = requireTicket(ticketId);
            if (latest.getStatus() == AfterSaleStatus.REFUNDED) {
                return AfterSaleResponse.from(latest);
            }
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "售后状态已变化，请刷新后重试");
        }
        return AfterSaleResponse.from(requireTicket(ticketId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AfterSaleResponse reshipExchange(String ticketId, String operatorId, String role,
                                            String trackingNo, String remark) {
        AfterSaleTicket ticket = requireTicket(ticketId);
        checkSellerAccess(ticket, operatorId, role);
        if (ticket.getAfterSaleType() != AfterSaleType.EXCHANGE) {
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "仅换货需要重新发货");
        }
        if (ticket.getStatus() == AfterSaleStatus.COMPLETED) {
            if (trackingNo.equals(ticket.getExchangeTrackingNo())) {
                return AfterSaleResponse.from(ticket);
            }
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "换货物流已提交，不能修改");
        }
        if (ticket.getStatus() != AfterSaleStatus.BUYER_SHIPPED) {
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "当前售后状态不允许重新发货");
        }
        int updated = afterSaleMapper.markExchangeCompleted(
                ticketId, ticket.getSellerId(), trackingNo, remark, LocalDateTime.now());
        if (updated == 0) {
            AfterSaleTicket latest = requireTicket(ticketId);
            if (latest.getStatus() == AfterSaleStatus.COMPLETED) {
                return AfterSaleResponse.from(latest);
            }
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "售后状态已变化，请刷新后重试");
        }
        return AfterSaleResponse.from(requireTicket(ticketId));
    }

    private AfterSaleResponse executeRefundOnly(AfterSaleTicket ticket, String operatorId,
                                                String role, String remark) {
        String refundId = executeRefund(ticket, operatorId, role, remark);
        int updated = afterSaleMapper.markRefundOnlyRefunded(
                ticket.getTicketId(), ticket.getSellerId(), refundId, remark, LocalDateTime.now());
        if (updated == 0) {
            AfterSaleTicket latest = requireTicket(ticket.getTicketId());
            if (latest.getStatus() == AfterSaleStatus.REFUNDED) {
                return AfterSaleResponse.from(latest);
            }
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "售后状态已变化，请刷新后重试");
        }
        return AfterSaleResponse.from(requireTicket(ticket.getTicketId()));
    }

    private String executeRefund(AfterSaleTicket ticket, String operatorId, String role, String remark) {
        RefundRequest request = new RefundRequest();
        request.setAmount(ticket.getAmount());
        request.setReason(ticket.getReason() == null ? "售后退款" : ticket.getReason());
        RefundResponse refund = refundService.createRefund(
                ticket.getBuyerId(), ticket.getPaymentId(), request, ticket.getSellerId());
        refundService.auditRefund(refund.getRefundId(), true, operatorId, role, remark);
        return refund.getRefundId();
    }

    private AfterSaleTicket requireTicket(String ticketId) {
        AfterSaleTicket ticket = ticketId == null ? null : afterSaleMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BizException(ErrorCode.AFTER_SALE_NOT_FOUND, "售后单不存在");
        }
        return ticket;
    }

    private void checkDetailAccess(AfterSaleTicket ticket, String userId, String role) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return;
        }
        if ("SELLER".equalsIgnoreCase(role)) {
            if (!userId.equals(ticket.getSellerId())) {
                throw new BizException(ErrorCode.FORBIDDEN, "无权查看该售后单");
            }
            return;
        }
        if (!userId.equals(ticket.getBuyerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看该售后单");
        }
    }

    private void checkSellerAccess(AfterSaleTicket ticket, String userId, String role) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return;
        }
        if (!"SELLER".equalsIgnoreCase(role) || !userId.equals(ticket.getSellerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权处理该售后单");
        }
    }

    private void checkBuyerAccess(AfterSaleTicket ticket, String buyerId) {
        if (!buyerId.equals(ticket.getBuyerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该售后单");
        }
    }

    private boolean isAfterBuyerShipped(AfterSaleStatus status) {
        return status == AfterSaleStatus.SELLER_RECEIVED
                || status == AfterSaleStatus.REFUNDED
                || status == AfterSaleStatus.COMPLETED;
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

    private AfterSaleStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return AfterSaleStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "售后状态不合法");
        }
    }

    private AfterSaleType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return AfterSaleType.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "售后类型不合法");
        }
    }
}
