package com.mall.payment.service;

import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.entity.AfterSaleTicket;
import com.mall.payment.mapper.AfterSaleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 售后单创建事务。
 *
 * <p>两个唯一键各司其职：
 * <ul>
 *   <li>uk_ticket_idem：同一个 Idempotency-Key 只创建一次</li>
 *   <li>uk_order_active：同一订单同时只允许一条进行中的售后单</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AfterSaleTxService {

    private final AfterSaleMapper afterSaleMapper;

    @Transactional(rollbackFor = Exception.class)
    public AfterSaleTicket insertIfAbsent(AfterSaleTicket ticket) {
        AfterSaleTicket byKey = afterSaleMapper.selectByIdempotencyKey(ticket.getIdempotencyKey());
        if (byKey != null) {
            return byKey;
        }
        try {
            afterSaleMapper.insert(ticket);
            return ticket;
        } catch (DuplicateKeyException e) {
            AfterSaleTicket existing = afterSaleMapper.selectByIdempotencyKey(ticket.getIdempotencyKey());
            if (existing != null) {
                log.info("售后幂等键重复，返回已有售后单, key={}, ticketId={}",
                        ticket.getIdempotencyKey(), existing.getTicketId());
                return existing;
            }
            AfterSaleTicket active = afterSaleMapper.selectActiveByOrderId(ticket.getOrderId());
            if (active != null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "该订单已有进行中的售后单");
            }
            throw e;
        }
    }
}
