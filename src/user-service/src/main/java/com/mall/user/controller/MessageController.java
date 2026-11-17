package com.mall.user.controller;

import com.mall.user.common.Result;
import com.mall.user.dto.request.SendMessageRequest;
import com.mall.user.dto.response.ConversationResponse;
import com.mall.user.dto.response.MessageResponse;
import com.mall.user.dto.response.PageResponse;
import com.mall.user.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {
    private final MessageService messageService;

    @GetMapping("/conversations")
    public Result<PageResponse<ConversationResponse>> conversations(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return Result.success(messageService.conversations(userId, page, size));
    }

    @GetMapping("/conversations/{conversationId}")
    public Result<PageResponse<MessageResponse>> messages(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable("conversationId") String conversationId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "30") int size) {
        return Result.success(messageService.messages(userId, conversationId, page, size));
    }

    @PostMapping
    public Result<MessageResponse> send(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody SendMessageRequest request) {
        return Result.success(messageService.send(userId, request));
    }

    @PutMapping("/conversations/{conversationId}/read")
    public Result<Void> read(@RequestHeader(value = "X-User-Id", required = false) String userId,
                             @PathVariable("conversationId") String conversationId) {
        messageService.markRead(userId, conversationId);
        return Result.success(null);
    }
}
