package com.mall.user.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ConversationResponse {
    private String conversationId;
    private String buyerId;
    private String sellerId;
    private String buyerName;
    private String sellerName;
    private LocalDateTime lastMessageAt;
    private String lastPreview;
    private int buyerUnread;
    private int sellerUnread;
    private int unreadCount;
}
