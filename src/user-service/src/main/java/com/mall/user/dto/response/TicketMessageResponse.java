package com.mall.user.dto.response;

import com.mall.user.entity.TicketMessage;
import lombok.Data;

import java.time.LocalDateTime;

/** 工单消息出参（#142）。 */
@Data
public class TicketMessageResponse {

    private Long id;
    private String senderId;
    private String senderRole;
    private String content;
    private LocalDateTime createdAt;

    public static TicketMessageResponse from(TicketMessage message) {
        TicketMessageResponse response = new TicketMessageResponse();
        response.setId(message.getId());
        response.setSenderId(message.getSenderId());
        response.setSenderRole(message.getSenderRole());
        response.setContent(message.getContent());
        response.setCreatedAt(message.getCreatedAt());
        return response;
    }
}