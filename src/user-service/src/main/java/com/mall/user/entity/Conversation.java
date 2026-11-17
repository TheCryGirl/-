package com.mall.user.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Conversation {
    private String conversationId;
    private String buyerId;
    private String sellerId;
    private LocalDateTime lastMessageAt;
    private String lastPreview;
    private int buyerUnread;
    private int sellerUnread;
    private LocalDateTime createdAt;
}
