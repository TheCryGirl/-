package com.mall.payment.mapper;

import com.mall.payment.entity.Payment;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 支付单数据访问。
 */
public interface PaymentMapper {

    Payment selectById(@Param("paymentId") String paymentId);

    Payment selectByPayNo(@Param("payNo") String payNo);

    Payment selectByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    Payment selectLatestByOrderId(@Param("orderId") String orderId);

    Payment selectPendingByOrderId(@Param("orderId") String orderId);

    int insert(Payment payment);

    /**
     * 条件更新支付状态，影响行数为 0 说明已被并发修改或状态不允许。
     */
    int markSuccess(@Param("paymentId") String paymentId, @Param("paidAt") LocalDateTime paidAt);

    /**
     * 原子预占退款额度：累计退款额加上本次金额不得超过实付金额。
     */
    int reserveRefundAmount(@Param("paymentId") String paymentId, @Param("refundAmount") long refundAmount);

    /**
     * 退款被驳回时释放预占的退款额度。
     */
    int releaseRefundAmount(@Param("paymentId") String paymentId, @Param("refundAmount") long refundAmount);

    /**
     * 累计退款额达到实付金额时，把支付单置为已退款。
     */
    int markRefundedIfFullyRefunded(@Param("paymentId") String paymentId);

    /**
     * 已全额退款的支付单在退款被驳回后，恢复为支付成功状态。
     */
    int restoreSuccessIfNotFullyRefunded(@Param("paymentId") String paymentId);

    /**
     * 查询需要重新对账的支付单：状态 SUCCESS、支付时间在 from 之后、对账时间点为 NULL。
     *
     * <p>用于支付成功回调中「通知订单已支付」或「确认扣减库存」失败后的补偿：
     * 当时只记录了 error 日志，若不重试，支付单已是 SUCCESS 而订单仍停在待支付。
     */
    java.util.List<Payment> selectUnreconciledPaid(@Param("from") LocalDateTime from,
                                                   @Param("limit") int limit);

    /**
     * 对账处理完成后写入时间点，避免重复处理。
     */
    int markReconciled(@Param("paymentId") String paymentId,
                       @Param("reconciledAt") LocalDateTime reconciledAt);
}
