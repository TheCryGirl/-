package com.mall.payment.service;

import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.entity.AfterSaleAttachment;
import com.mall.payment.entity.AfterSaleEvent;
import com.mall.payment.entity.AfterSaleTicket;
import com.mall.payment.mapper.AfterSaleAttachmentMapper;
import com.mall.payment.mapper.AfterSaleEventMapper;
import com.mall.payment.mapper.AfterSaleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 售后单创建事务。
 *
 * <p>两个唯一键各司其职：
 * <ul>
 *   <li>uk_ticket_idem：同一个 Idempotency-Key 只创建一次</li>
 *   <li>uk_order_active：同一订单同时只允许一条进行中的售后单</li>
 * </ul>
 *
 * <p>#143 增量：售后单与创建时的凭证、首条时间线事件在同一事务落库；
 * 幂等命中已存在售后单时不重复绑定凭证、不重复记录事件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AfterSaleTxService {

    private final AfterSaleMapper afterSaleMapper;
    private final AfterSaleAttachmentMapper attachmentMapper;
    private final AfterSaleEventMapper eventMapper;

    @Transactional(rollbackFor = Exception.class)
    public AfterSaleTicket insertIfAbsent(AfterSaleTicket ticket,
                                          List<AfterSaleAttachment> attachments,
                                          AfterSaleEvent createEvent) {
        AfterSaleTicket byKey = afterSaleMapper.selectByIdempotencyKey(ticket.getIdempotencyKey());
        if (byKey != null) {
            return byKey;
        }
        AfterSaleTicket saved;
        try {
            afterSaleMapper.insert(ticket);
            saved = ticket;
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
        if (attachments != null) {
            for (AfterSaleAttachment attachment : attachments) {
                attachment.setTicketId(saved.getTicketId());
                attachmentMapper.insert(attachment);
            }
        }
        if (createEvent != null) {
            createEvent.setTicketId(saved.getTicketId());
            eventMapper.insert(createEvent);
        }
        return saved;
    }
}