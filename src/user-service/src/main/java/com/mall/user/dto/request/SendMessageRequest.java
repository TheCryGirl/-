package com.mall.user.dto.request;

import lombok.Data;

@Data
public class SendMessageRequest {
    private String conversationId;
    private String sellerId;
    private String content;
    private String contextProductId;
}
