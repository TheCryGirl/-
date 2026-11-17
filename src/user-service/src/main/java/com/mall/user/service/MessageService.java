package com.mall.user.service;

import com.mall.user.dto.request.SendMessageRequest;
import com.mall.user.dto.response.ConversationResponse;
import com.mall.user.dto.response.MessageResponse;
import com.mall.user.dto.response.PageResponse;

public interface MessageService {
    PageResponse<ConversationResponse> conversations(String userId, int page, int size);
    PageResponse<MessageResponse> messages(String userId, String conversationId, int page, int size);
    MessageResponse send(String userId, SendMessageRequest request);
    void markRead(String userId, String conversationId);
}
