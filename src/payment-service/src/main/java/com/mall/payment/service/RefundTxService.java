package com.mall.payment.service;

import com.mall.payment.common.AfterSaleStatus;
import com.mall.payment.entity.AfterSaleEvent;
import com.mall.payment.entity.AfterSaleTicket;
import com.mall.payment.mapper.AfterSaleEventMapper;
import com.mall.payment.mapper.AfterSaleMapper;
import com.mall.payment.mapper.PaymentMapper;
import com.mall.payment.mapper.RefundMapper;
import com.mall.payment.entity.Refund;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

/**
 * 渠道退款终态落库（#137）。
 *
 * <p>成功与失败都是带前置状态的条件 UPDATE，回调、查单与人工重试
 * 并发到达时只有一方生效。全额退款的订单通知与库存回补任务与
 * SUCCESS 落库同事务，提交后立即投递，失败由持久任务调度补偿。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundTxService {

    private final RefundMapper refundMapper;
    private final PaymentMapper paymentMapper;
    private final AfterSaleMapper afterSaleMapper;
    private final AfterSaleEventMapper afterSaleEventMapper;
    private final RefundNotificationTxService refundNotificationTxService;
    private final RefundNotificationDispatcher refundNotificationDispatcher;
    private final CouponService couponService;

    /**
     * PROCESSING → SUCCESS。返回 true 表示本次调用完成了状态迁移。
     *
     * <p>用 REQUIRES_NEW：渠道执行器是在审核事务的 afterCommit 里调用本方法的，
     * 若用 REQUIRED 会并入已提交的审核事务，本方法注册的通知投递 afterCommit
     * 会被挂到正在遍历的同步器列表上而丢失。独立事务保证退款终态与通知任务先提交，
     * 再由本事务自己的 afterCommit 触发投递。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean completeSuccess(String refundId, String channelRefundNo, boolean notifyOrder) {
        int updated = refundMapper.completeSuccess(refundId, channelRefundNo, LocalDateTime.now());
        if (updated == 0) {
            return false;
        }
        Refund refund = refundMapper.selectById(refundId);
        boolean fullyRefunded = paymentMapper.markRefundedIfFullyRefunded(refund.getPaymentId()) > 0;
        // 售后单与退款单状态同步：渠道异步退款成功的售后单在这里置 REFUNDED
        // （#143 补记时间线：迁移前取售后单原状态作为事件起点，只有真实迁移才写事件）
        AfterSaleTicket ticket = afterSaleMapper.selectByRefundId(refundId);
        int synced = afterSaleMapper.markRefundedByRefundId(refundId, LocalDateTime.now());
        if (synced > 0 && ticket != null) {
            AfterSaleEvent event = new AfterSaleEvent();
            event.setTicketId(ticket.getTicketId());
            event.setFromStatus(ticket.getStatus());
            event.setToStatus(AfterSaleStatus.REFUNDED);
            event.setOperatorId("SYSTEM");
            event.setRemark("渠道退款成功，售后单同步退款完成");
            afterSaleEventMapper.insert(event);
        }
        log.info("渠道退款成功, refundId={}, channelRefundNo={}, amount={}",
                refundId, channelRefundNo, refund.getAmount());
        // #140 渠道异步退款成功同样返还订单占用的优惠券（已过期的不返还）
        couponService.release(refund.getOrderId());
        if (fullyRefunded && notifyOrder) {
            refundNotificationTxService.createTasks(refund.getOrderId());
            scheduleImmediateDelivery(refund.getOrderId());
        }
        return true;
    }

    /** PROCESSING → FAILED；预占额度保留，人工重试复用，不重复预占 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean markFailed(String refundId, String channelError) {
        int updated = refundMapper.markFailed(refundId, channelError);
        if (updated > 0) {
            log.error("渠道退款失败, refundId={}, error={}", refundId, channelError);
        }
        return updated > 0;
    }

    private void scheduleImmediateDelivery(String orderId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    refundNotificationDispatcher.dispatchAfterCommit(orderId);
                }
            });
        } else {
            refundNotificationDispatcher.dispatchAfterCommit(orderId);
        }
    }
}