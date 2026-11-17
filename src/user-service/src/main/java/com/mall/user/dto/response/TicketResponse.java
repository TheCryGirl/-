package com.mall.user.dto.response;

import com.mall.user.entity.Ticket;
import lombok.Data;

import java.time.LocalDateTime;

/** 工单列表项（#142）。 */
@Data
public class TicketResponse {

    private String ticketId;
    private String ticketNo;
    private String userId;
    private String type;
    private String title;
    private String status;
    private String assigneeId;
    private String bizId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;

    public static TicketResponse from(Ticket ticket) {
        TicketResponse response = new TicketResponse();
        response.setTicketId(ticket.getTicketId());
        response.setTicketNo(ticket.getTicketNo());
        response.setUserId(ticket.getUserId());
        response.setType(ticket.getType());
        response.setTitle(ticket.getTitle());
        response.setStatus(ticket.getStatus());
        response.setAssigneeId(ticket.getAssigneeId());
        response.setBizId(ticket.getBizId());
        response.setCreatedAt(ticket.getCreatedAt());
        response.setUpdatedAt(ticket.getUpdatedAt());
        response.setResolvedAt(ticket.getResolvedAt());
        return response;
    }
}