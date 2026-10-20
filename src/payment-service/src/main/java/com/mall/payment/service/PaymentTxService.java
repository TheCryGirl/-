package com.mall.payment.service;

import com.mall.payment.common.PaymentStatus;
import com.mall.payment.entity.Payment;
import com.mall.payment.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付单落库的本地事务。
 *
 * <p>插入依赖数据库唯一约束处理并发：
 * <ul>
 *   <li>uk_idempotency_key：同一幂等键只创建一次</li>
 *   <li>uk_order_status：同一订单同一状态只允许一条，并发下至多一个 PENDING</li>
 * </ul>
 * 唯一键冲突时读取已存在的支付单返回，不再二次插入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentTxService {

    private final PaymentMapper paymentMapper;

    @Transactional(rollbackFor = Exception.class)
    public Payment insertIfAbsent(Payment payment) {
        try {
            paymentMapper.insert(payment);
            return payment;
        } catch (DuplicateKeyException e) {
            Payment byKey = payment.getIdempotencyKey() == null
                    ? null
                    : paymentMapper.selectByIdempotencyKey(payment.getIdempotencyKey());
            if (byKey != null) {
                log.info("幂等键重复，返回已有支付单, key={}, paymentId={}",
                        payment.getIdempotencyKey(), byKey.getPaymentId());
                return byKey;
            }

            Payment pending = paymentMapper.selectPendingByOrderId(payment.getOrderId());
            if (pending != null) {
                log.info("订单已有待支付单，返回已有支付单, orderId={}, paymentId={}",
                        payment.getOrderId(), pending.getPaymentId());
                return pending;
            }

            Payment latest = paymentMapper.selectLatestByOrderId(payment.getOrderId());
            if (latest != null && (latest.getStatus() == PaymentStatus.SUCCESS
                    || latest.getStatus() == PaymentStatus.REFUNDED)) {
                log.info("订单已支付或已退款，返回已有支付单, orderId={}, paymentId={}",
                        payment.getOrderId(), latest.getPaymentId());
                return latest;
            }
            throw e;
        }
    }
}
