package com.mall.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 工单消息（#142）。sender_role 取 USER 或 ADMIN。 */
@Data
public class TicketMessage {

    private Long id;
    private String ticketId;
    private String senderId;
    private String senderRole;
    private String content;
    private LocalDateTime createdAt;
}