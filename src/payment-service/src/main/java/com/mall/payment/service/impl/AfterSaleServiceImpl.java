package com.mall.payment.service.impl;

import com.mall.payment.client.OrderClient;
import com.mall.payment.client.dto.OrderDTO;
import com.mall.payment.common.AfterSaleReasonCode;
import com.mall.payment.common.AfterSaleStatus;
import com.mall.payment.common.AfterSaleType;
import com.mall.payment.common.ArbitrationDecision;
import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.IdGenerator;
import com.mall.payment.common.OrderStatus;
import com.mall.payment.common.PageResult;
import com.mall.payment.common.PaymentStatus;
import com.mall.payment.common.RefundStatus;
import com.mall.payment.common.Result;
import com.mall.payment.dto.request.ArbitrateRequest;
import com.mall.payment.dto.request.CreateAfterSaleRequest;
import com.mall.payment.dto.request.RefundRequest;
import com.mall.payment.dto.request.SupplementRequest;
import com.mall.payment.dto.response.AfterSaleResponse;
import com.mall.payment.dto.response.RefundResponse;
import com.mall.payment.entity.AfterSaleAttachment;
import com.mall.payment.entity.AfterSaleEvent;
import com.mall.payment.entity.AfterSaleTicket;
import com.mall.payment.entity.Payment;
import com.mall.payment.mapper.AfterSaleAttachmentMapper;
import com.mall.payment.mapper.AfterSaleEventMapper;
import com.mall.payment.mapper.AfterSaleMapper;
import com.mall.payment.mapper.PaymentMapper;
import com.mall.payment.service.AfterSaleAttachmentStorageService;
import com.mall.payment.service.AfterSaleService;
import com.mall.payment.service.AfterSaleTxService;
import com.mall.payment.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
 *
 * <p>#143 增量：每次状态变更写一条时间线事件（after_sale_event）；买家撤销
 * （PENDING -> CANCELLED）；平台仲裁（REJECTED -> ARBITRATING，管理员裁定
 * REFUNDED | REJECTED）；仲裁期间双方补充说明与凭证，材料对双方可见。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AfterSaleServiceImpl implements AfterSaleService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 64;
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_SELLER = "SELLER";
    private static final String ROLE_BUYER = "BUYER";

    private final AfterSaleMapper afterSaleMapper;
    private final AfterSaleTxService afterSaleTxService;
    private final OrderClient orderClient;
    private final PaymentMapper paymentMapper;
    private final RefundService refundService;
    private final AfterSaleAttachmentMapper attachmentMapper;
    private final AfterSaleEventMapper eventMapper;
    private final AfterSaleAttachmentStorageService attachmentStorageService;

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
            return full(existing);
        }
        AfterSaleTicket active = afterSaleMapper.selectActiveByOrderId(request.getOrderId());
        if (active != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该订单已有进行中的售后单");
        }

        validateReasonCode(request.getReasonCode(), request.getReason());
        attachmentStorageService.validateUrls(request.getAttachmentUrls());

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
        // 跨店合并结算（#135）：子订单没有独立支付单，支付单挂在主订单上
        if (payment == null && order.getMainOrderId() != null && !order.getMainOrderId().isBlank()) {
            payment = paymentMapper.selectLatestByOrderId(order.getMainOrderId());
        }
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
        ticket.setReasonCode(request.getReasonCode());
        ticket.setStatus(AfterSaleStatus.PENDING);
        ticket.setIdempotencyKey(key);
        ticket.setActiveFlag(true);
        ticket.setCreatedBy(buyerId);
        ticket.setUpdatedBy(buyerId);
        ticket.setIsDeleted(false);

        List<AfterSaleAttachment> attachments = toAttachments(
                buyerId, ROLE_BUYER, request.getAttachmentUrls());
        AfterSaleEvent createEvent = toEvent(null, AfterSaleStatus.PENDING, buyerId, request.getReason());
        AfterSaleTicket saved = afterSaleTxService.insertIfAbsent(ticket, attachments, createEvent);
        checkBuyerAccess(saved, buyerId);
        AfterSaleTicket latest = afterSaleMapper.selectById(saved.getTicketId());
        log.info("售后单创建成功, ticketId={}, orderId={}, type={}, amount={}, reasonCode={}",
                latest.getTicketId(), latest.getOrderId(), latest.getAfterSaleType(),
                latest.getAmount(), latest.getReasonCode());
        return full(latest);
    }

    @Override
    public PageResult<AfterSaleResponse> list(String userId, String role, String status,
                                              String type, String orderId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        AfterSaleStatus statusFilter = parseStatus(status);
        AfterSaleType typeFilter = parseType(type);

        boolean admin = ROLE_ADMIN.equalsIgnoreCase(role);
        boolean seller = ROLE_SELLER.equalsIgnoreCase(role);
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
        return full(ticket);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AfterSaleResponse audit(String ticketId, String operatorId, String role,
                                   boolean approved, String remark) {
        AfterSaleTicket ticket = requireTicket(ticketId);
        checkSellerAccess(ticket, operatorId, role);

        if (ticket.getStatus() == AfterSaleStatus.ARBITRATING) {
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "仲裁中的售后单卖家不能审核");
        }
        if (ticket.getStatus() == AfterSaleStatus.CANCELLED) {
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "售后单已撤销，不能审核");
        }
        if (ticket.getStatus() == AfterSaleStatus.REFUNDED
                || ticket.getStatus() == AfterSaleStatus.COMPLETED) {
            return full(ticket);
        }
        if (!approved) {
            if (ticket.getStatus() == AfterSaleStatus.REJECTED) {
                return full(ticket);
            }
            if (ticket.getStatus() != AfterSaleStatus.PENDING) {
                throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "当前售后状态不允许驳回");
            }
            int updated = afterSaleMapper.markRejected(
                    ticketId, ticket.getSellerId(), operatorId, remark, LocalDateTime.now());
            if (updated == 0) {
                AfterSaleTicket latest = requireTicket(ticketId);
                if (latest.getStatus() == AfterSaleStatus.REJECTED) {
                    return full(latest);
                }
                throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "售后状态已变化，请刷新后重试");
            }
            recordEvent(ticketId, AfterSaleStatus.PENDING, AfterSaleStatus.REJECTED, operatorId, remark);
            return full(requireTicket(ticketId));
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
            } else {
                recordEvent(ticketId, AfterSaleStatus.PENDING, AfterSaleStatus.APPROVED, operatorId, remark);
            }
        }

        ticket = requireTicket(ticketId);
        if (ticket.getAfterSaleType() == AfterSaleType.REFUND_ONLY) {
            if (ticket.getStatus() == AfterSaleStatus.REFUNDED) {
                return full(ticket);
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
            return full(ticket);
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
                    return full(latest);
                }
                if (isAfterBuyerShipped(latest.getStatus())) {
                    return full(latest);
                }
                throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "当前售后状态不允许提交退货物流");
            }
            recordEvent(ticketId, AfterSaleStatus.APPROVED, AfterSaleStatus.BUYER_SHIPPED,
                    buyerId, "买家提交退货物流：" + trackingNo);
            return full(requireTicket(ticketId));
        }
        if (ticket.getStatus() == AfterSaleStatus.BUYER_SHIPPED) {
            if (trackingNo.equals(ticket.getReturnTrackingNo())) {
                return full(ticket);
            }
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "退货物流已提交，不能修改");
        }
        if (isAfterBuyerShipped(ticket.getStatus())) {
            return full(ticket);
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
            return full(ticket);
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
            } else {
                recordEvent(ticketId, AfterSaleStatus.BUYER_SHIPPED, AfterSaleStatus.SELLER_RECEIVED,
                        operatorId, remark);
            }
        } else if (ticket.getStatus() != AfterSaleStatus.SELLER_RECEIVED) {
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "当前售后状态不允许确认收货");
        }

        ticket = requireTicket(ticketId);
        if (ticket.getStatus() == AfterSaleStatus.REFUNDED) {
            return full(ticket);
        }
        if (ticket.getStatus() != AfterSaleStatus.SELLER_RECEIVED) {
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "当前售后状态不允许退款");
        }
        if (ticket.getRefundId() != null) {
            // 渠道退款处理中，重复确认收货不再发起新退款
            return full(ticket);
        }
        RefundResponse refund = executeRefund(ticket, operatorId, role, remark);
        if (refund.getStatus() != RefundStatus.SUCCESS) {
            // 渠道异步退款：售后单停在 SELLER_RECEIVED，退款成功回调再置 REFUNDED
            afterSaleMapper.attachRefundId(ticketId, refund.getRefundId());
            return full(requireTicket(ticketId));
        }
        int updated = afterSaleMapper.markReturnRefunded(
                ticketId, ticket.getSellerId(), refund.getRefundId(), remark, LocalDateTime.now());
        if (updated == 0) {
            AfterSaleTicket latest = requireTicket(ticketId);
            if (latest.getStatus() == AfterSaleStatus.REFUNDED) {
                return full(latest);
            }
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "售后状态已变化，请刷新后重试");
        }
        recordEvent(ticketId, AfterSaleStatus.SELLER_RECEIVED, AfterSaleStatus.REFUNDED,
                operatorId, remark);
        return full(requireTicket(ticketId));
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
                return full(ticket);
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
                return full(latest);
            }
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "售后状态已变化，请刷新后重试");
        }
        recordEvent(ticketId, AfterSaleStatus.BUYER_SHIPPED, AfterSaleStatus.COMPLETED,
                operatorId, remark);
        return full(requireTicket(ticketId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AfterSaleResponse cancel(String ticketId, String buyerId, String remark) {
        AfterSaleTicket ticket = requireTicket(ticketId);
        checkBuyerAccess(ticket, buyerId);
        if (ticket.getStatus() == AfterSaleStatus.CANCELLED) {
            return full(ticket);
        }
        if (ticket.getStatus() != AfterSaleStatus.PENDING) {
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "仅待审核的售后单可撤销");
        }
        int updated = afterSaleMapper.markCancelled(
                ticketId, buyerId, remark, LocalDateTime.now());
        if (updated == 0) {
            AfterSaleTicket latest = requireTicket(ticketId);
            if (latest.getStatus() == AfterSaleStatus.CANCELLED) {
                return full(latest);
            }
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "售后状态已变化，请刷新后重试");
        }
        recordEvent(ticketId, AfterSaleStatus.PENDING, AfterSaleStatus.CANCELLED, buyerId, remark);
        return full(requireTicket(ticketId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AfterSaleResponse requestArbitration(String ticketId, String buyerId, String remark) {
        AfterSaleTicket ticket = requireTicket(ticketId);
        checkBuyerAccess(ticket, buyerId);
        if (ticket.getStatus() == AfterSaleStatus.ARBITRATING) {
            return full(ticket);
        }
        if (ticket.getStatus() != AfterSaleStatus.REJECTED) {
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "仅卖家驳回后可申请平台仲裁");
        }
        int updated;
        try {
            updated = afterSaleMapper.markArbitrating(
                    ticketId, buyerId, remark, LocalDateTime.now());
        } catch (DuplicateKeyException e) {
            // REJECTED 终态 active_flag 为 NULL，申请仲裁重新置 1；
            // 若同一订单已有另一条进行中售后单，uk_order_active 会拒绝本次恢复
            throw new BizException(ErrorCode.PARAM_INVALID, "该订单已有进行中的售后单，无法申请仲裁");
        }
        if (updated == 0) {
            AfterSaleTicket latest = requireTicket(ticketId);
            if (latest.getStatus() == AfterSaleStatus.ARBITRATING) {
                return full(latest);
            }
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "售后状态已变化，请刷新后重试");
        }
        recordEvent(ticketId, AfterSaleStatus.REJECTED, AfterSaleStatus.ARBITRATING, buyerId, remark);
        return full(requireTicket(ticketId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AfterSaleResponse supplement(String ticketId, String userId, String role,
                                        SupplementRequest request) {
        AfterSaleTicket ticket = requireTicket(ticketId);
        checkDetailAccess(ticket, userId, role);
        if (ticket.getStatus() != AfterSaleStatus.ARBITRATING) {
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "仅仲裁中的售后单可补充材料");
        }
        String remark = request.getRemark() == null ? null : request.getRemark().trim();
        boolean hasRemark = remark != null && !remark.isEmpty();
        List<String> urls = request.getAttachmentUrls();
        boolean hasAttachments = urls != null && !urls.isEmpty();
        if (!hasRemark && !hasAttachments) {
            throw new BizException(ErrorCode.PARAM_INVALID, "补充说明与凭证至少提供其一");
        }
        attachmentStorageService.validateUrls(urls);
        if (hasAttachments) {
            bindAttachments(ticketId, userId, role, urls);
        }
        recordEvent(ticketId, AfterSaleStatus.ARBITRATING, AfterSaleStatus.ARBITRATING,
                userId, hasRemark ? remark : "补充凭证");
        return full(requireTicket(ticketId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AfterSaleResponse arbitrate(String ticketId, String operatorId, String role,
                                       ArbitrateRequest request) {
        requireAdmin(role);
        AfterSaleTicket ticket = requireTicket(ticketId);
        if (ticket.getStatus() != AfterSaleStatus.ARBITRATING) {
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "仅仲裁中的售后单可裁定");
        }
        String remark = request.getRemark() == null ? null : request.getRemark().trim();
        if (request.getDecision() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "裁定结果不能为空");
        }
        if (remark == null || remark.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "裁定理由不能为空");
        }

        if (request.getDecision() == ArbitrationDecision.REJECT) {
            int updated = afterSaleMapper.markArbitrationRejected(
                    ticketId, operatorId, remark, LocalDateTime.now());
            if (updated == 0) {
                AfterSaleTicket latest = requireTicket(ticketId);
                if (latest.getStatus() == AfterSaleStatus.REJECTED) {
                    return full(latest);
                }
                throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "售后状态已变化，请刷新后重试");
            }
            recordEvent(ticketId, AfterSaleStatus.ARBITRATING, AfterSaleStatus.REJECTED,
                    operatorId, remark);
            return full(requireTicket(ticketId));
        }

        // REFUND：联动退款链路，售后单与退款单状态保持一致
        if (ticket.getAfterSaleType() == AfterSaleType.EXCHANGE) {
            throw new BizException(ErrorCode.PARAM_INVALID, "换货售后不支持退款裁定");
        }
        if (ticket.getAmount() == null || ticket.getAmount() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "售后金额异常，无法退款");
        }
        if (ticket.getRefundId() != null) {
            return full(ticket);
        }
        RefundResponse refund = executeRefund(ticket, operatorId, role, remark);
        if (refund.getStatus() != RefundStatus.SUCCESS) {
            // 渠道异步退款：售后单停在 ARBITRATING，退款成功回调再置 REFUNDED
            afterSaleMapper.attachRefundId(ticketId, refund.getRefundId());
            recordEvent(ticketId, AfterSaleStatus.ARBITRATING, AfterSaleStatus.ARBITRATING,
                    operatorId, "裁定支持退款，渠道退款处理中：" + remark);
            return full(requireTicket(ticketId));
        }
        int updated = afterSaleMapper.markArbitrationRefunded(
                ticketId, refund.getRefundId(), operatorId, remark, LocalDateTime.now());
        if (updated == 0) {
            // 并发裁定下，本事务可能已创建并审核了自己的退款单，但售后单已被他人裁定：
            // 这里必须抛异常回滚本事务的退款与额度预占，保证一笔售后单只产生一笔退款。
            // 顺序重试会命中上方 refundId 守卫直接返回，不会走到这里。
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "售后单已被裁定，请刷新后重试");
        }
        recordEvent(ticketId, AfterSaleStatus.ARBITRATING, AfterSaleStatus.REFUNDED,
                operatorId, remark);
        return full(requireTicket(ticketId));
    }

    @Override
    public List<String> uploadAttachments(String userId, List<MultipartFile> files) {
        if (userId == null || userId.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或用户信息缺失");
        }
        List<String> urls = attachmentStorageService.storeAll(files);
        log.info("售后凭证上传成功, userId={}, count={}", userId, urls.size());
        return urls;
    }

    private AfterSaleResponse executeRefundOnly(AfterSaleTicket ticket, String operatorId,
                                                String role, String remark) {
        if (ticket.getRefundId() != null) {
            // 渠道退款处理中，重复审核不再发起新退款
            return full(ticket);
        }
        RefundResponse refund = executeRefund(ticket, operatorId, role, remark);
        if (refund.getStatus() != RefundStatus.SUCCESS) {
            // 渠道异步退款：售后单停在 APPROVED，退款成功回调再置 REFUNDED
            afterSaleMapper.attachRefundId(ticket.getTicketId(), refund.getRefundId());
            return full(requireTicket(ticket.getTicketId()));
        }
        int updated = afterSaleMapper.markRefundOnlyRefunded(
                ticket.getTicketId(), ticket.getSellerId(), refund.getRefundId(), remark, LocalDateTime.now());
        if (updated == 0) {
            AfterSaleTicket latest = requireTicket(ticket.getTicketId());
            if (latest.getStatus() == AfterSaleStatus.REFUNDED) {
                return full(latest);
            }
            throw new BizException(ErrorCode.AFTER_SALE_STATE_INVALID, "售后状态已变化，请刷新后重试");
        }
        recordEvent(ticket.getTicketId(), AfterSaleStatus.APPROVED, AfterSaleStatus.REFUNDED,
                operatorId, remark);
        return full(requireTicket(ticket.getTicketId()));
    }

    private RefundResponse executeRefund(AfterSaleTicket ticket, String operatorId, String role, String remark) {
        RefundRequest request = new RefundRequest();
        request.setAmount(ticket.getAmount());
        request.setReason(ticket.getReason() == null ? "售后退款" : ticket.getReason());
        RefundResponse refund = refundService.createRefund(
                ticket.getBuyerId(), ticket.getPaymentId(), request, ticket.getSellerId());
        return refundService.auditRefund(refund.getRefundId(), true, operatorId, role, remark);
    }

    private void validateReasonCode(AfterSaleReasonCode reasonCode, String reason) {
        if (reasonCode == AfterSaleReasonCode.OTHER
                && (reason == null || reason.isBlank())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "选择其他原因时必须填写说明");
        }
    }

    private void bindAttachments(String ticketId, String userId, String role, List<String> urls) {
        for (AfterSaleAttachment attachment : toAttachments(userId, role, urls)) {
            attachment.setTicketId(ticketId);
            attachmentMapper.insert(attachment);
        }
    }

    private List<AfterSaleAttachment> toAttachments(String userId, String role, List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }
        return urls.stream().map(url -> {
            AfterSaleAttachment attachment = new AfterSaleAttachment();
            attachment.setUrl(url);
            attachment.setUploadedBy(userId);
            attachment.setUploaderRole(role);
            return attachment;
        }).toList();
    }

    private void recordEvent(String ticketId, AfterSaleStatus from, AfterSaleStatus to,
                             String operatorId, String remark) {
        AfterSaleEvent event = toEvent(from, to, operatorId, remark);
        event.setTicketId(ticketId);
        eventMapper.insert(event);
    }

    private AfterSaleEvent toEvent(AfterSaleStatus from, AfterSaleStatus to,
                                   String operatorId, String remark) {
        AfterSaleEvent event = new AfterSaleEvent();
        event.setFromStatus(from);
        event.setToStatus(to);
        event.setOperatorId(operatorId);
        event.setRemark(remark);
        return event;
    }

    private AfterSaleResponse full(AfterSaleTicket ticket) {
        return AfterSaleResponse.from(ticket,
                attachmentMapper.selectByTicketId(ticket.getTicketId()),
                eventMapper.selectByTicketId(ticket.getTicketId()));
    }

    private AfterSaleTicket requireTicket(String ticketId) {
        AfterSaleTicket ticket = ticketId == null ? null : afterSaleMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BizException(ErrorCode.AFTER_SALE_NOT_FOUND, "售后单不存在");
        }
        return ticket;
    }

    private void requireAdmin(String role) {
        if (!ROLE_ADMIN.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限执行平台仲裁");
        }
    }

    private void checkDetailAccess(AfterSaleTicket ticket, String userId, String role) {
        if (ROLE_ADMIN.equalsIgnoreCase(role)) {
            return;
        }
        if (ROLE_SELLER.equalsIgnoreCase(role)) {
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
        if (ROLE_ADMIN.equalsIgnoreCase(role)) {
            return;
        }
        if (!ROLE_SELLER.equalsIgnoreCase(role) || !userId.equals(ticket.getSellerId())) {
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