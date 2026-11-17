package com.mall.user.service;

import com.mall.user.common.BizException;
import com.mall.user.common.ErrorCode;
import com.mall.user.common.IdGenerator;
import com.mall.user.dto.request.CreateTicketRequest;
import com.mall.user.dto.request.ReplyTicketRequest;
import com.mall.user.dto.request.UpdateTicketStatusRequest;
import com.mall.user.dto.response.PageResponse;
import com.mall.user.dto.response.TicketDetailResponse;
import com.mall.user.dto.response.TicketMessageResponse;
import com.mall.user.dto.response.TicketResponse;
import com.mall.user.entity.Ticket;
import com.mall.user.entity.TicketMessage;
import com.mall.user.mapper.TicketMapper;
import com.mall.user.mapper.TicketMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/** 客服工单（#142）：创建、列表、详情、回复与状态流转。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    private static final Set<String> TYPES = Set.of("ORDER", "REFUND", "TECH", "OTHER");
    private static final Set<String> STATUSES = Set.of("OPEN", "PROCESSING", "RESOLVED", "CLOSED");
    private static final DateTimeFormatter NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TicketMapper ticketMapper;
    private final TicketMessageMapper messageMapper;

    @Transactional
    public TicketDetailResponse create(String userId, CreateTicketRequest request) {
        requireUser(userId);
        String type = request.getType() == null ? "" : request.getType().trim().toUpperCase();
        if (!TYPES.contains(type)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "工单类型不支持");
        }
        LocalDateTime now = LocalDateTime.now();
        Ticket ticket = new Ticket();
        ticket.setTicketId("TK" + IdGenerator.randomDigits(14));
        ticket.setTicketNo("T" + now.format(NO_FORMAT) + IdGenerator.randomDigits(4));
        ticket.setUserId(userId);
        ticket.setType(type);
        ticket.setTitle(request.getTitle().trim());
        ticket.setStatus("OPEN");
        ticket.setBizId(trimToNull(request.getBizId()));
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        ticketMapper.insert(ticket);
        if (request.getContent() != null && !request.getContent().isBlank()) {
            insertMessage(ticket.getTicketId(), userId, "USER", request.getContent());
        }
        log.info("创建客服工单, ticketId={}, userId={}, type={}", ticket.getTicketId(), userId, type);
        return detail(userId, "USER", ticket.getTicketId());
    }

    public PageResponse<TicketResponse> list(String userId, String role, String status, int page, int size) {
        requireUser(userId);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, size));
        boolean admin = "ADMIN".equalsIgnoreCase(role);
        String safeStatus = trimToNull(status);
        if (safeStatus != null) {
            safeStatus = safeStatus.toUpperCase();
            if (!STATUSES.contains(safeStatus)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "工单状态不支持");
            }
        }
        List<TicketResponse> list = ticketMapper
                .selectPage(admin ? null : userId, safeStatus, (safePage - 1) * safeSize, safeSize)
                .stream().map(TicketResponse::from).toList();
        long total = ticketMapper.countPage(admin ? null : userId, safeStatus);
        return PageResponse.of(safePage, safeSize, total, list);
    }

    public TicketDetailResponse detail(String userId, String role, String ticketId) {
        requireUser(userId);
        Ticket ticket = requireTicket(ticketId);
        requireAccess(ticket, userId, role);
        List<TicketMessageResponse> messages = messageMapper.selectByTicket(ticketId)
                .stream().map(TicketMessageResponse::from).toList();
        TicketDetailResponse response = new TicketDetailResponse();
        response.setTicket(TicketResponse.from(ticket));
        response.setMessages(messages);
        return response;
    }

    @Transactional
    public TicketDetailResponse reply(String userId, String role, String ticketId, ReplyTicketRequest request) {
        requireUser(userId);
        Ticket ticket = requireTicket(ticketId);
        requireAccess(ticket, userId, role);
        if ("CLOSED".equals(ticket.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "工单已关闭，不能再回复");
        }
        boolean admin = "ADMIN".equalsIgnoreCase(role);
        insertMessage(ticketId, userId, admin ? "ADMIN" : "USER", request.getContent());
        if (admin && "OPEN".equals(ticket.getStatus())) {
            ticketMapper.updateStatus(ticketId, "PROCESSING", userId, null);
        } else {
            ticketMapper.touch(ticketId);
        }
        return detail(userId, role, ticketId);
    }

    @Transactional
    public TicketDetailResponse updateStatus(String operatorId, String role, String ticketId,
                                             UpdateTicketStatusRequest request) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅管理员可变更工单状态");
        }
        requireUser(operatorId);
        Ticket ticket = requireTicket(ticketId);
        String status = request.getStatus() == null ? "" : request.getStatus().trim().toUpperCase();
        if (!STATUSES.contains(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "工单状态不支持");
        }
        LocalDateTime resolvedAt = ("RESOLVED".equals(status) || "CLOSED".equals(status))
                ? LocalDateTime.now() : null;
        ticketMapper.updateStatus(ticketId, status, operatorId, resolvedAt);
        log.info("工单状态变更, ticketId={}, status={}, operator={}", ticketId, status, operatorId);
        return detail(operatorId, role, ticketId);
    }

    private void insertMessage(String ticketId, String senderId, String senderRole, String content) {
        TicketMessage message = new TicketMessage();
        message.setTicketId(ticketId);
        message.setSenderId(senderId);
        message.setSenderRole(senderRole);
        message.setContent(content.trim());
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
    }

    private Ticket requireTicket(String ticketId) {
        if (ticketId == null || ticketId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "工单ID不能为空");
        }
        Ticket ticket = ticketMapper.selectById(ticketId.trim());
        if (ticket == null) {
            throw new BizException(ErrorCode.TICKET_NOT_FOUND, "工单不存在");
        }
        return ticket;
    }

    private static void requireAccess(Ticket ticket, String userId, String role) {
        if (!"ADMIN".equalsIgnoreCase(role) && !userId.equals(ticket.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权访问该工单");
        }
    }

    private static void requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或用户信息缺失");
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}