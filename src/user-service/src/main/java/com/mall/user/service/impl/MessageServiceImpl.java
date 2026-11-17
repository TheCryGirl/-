package com.mall.user.service.impl;

import com.mall.user.client.dto.ProductBriefDTO;
import com.mall.user.common.*;
import com.mall.user.dto.request.SendMessageRequest;
import com.mall.user.dto.response.ConversationResponse;
import com.mall.user.dto.response.MessageResponse;
import com.mall.user.dto.response.PageResponse;
import com.mall.user.entity.Conversation;
import com.mall.user.entity.Message;
import com.mall.user.entity.Shop;
import com.mall.user.entity.User;
import com.mall.user.mapper.ConversationMapper;
import com.mall.user.mapper.MessageMapper;
import com.mall.user.mapper.ShopMapper;
import com.mall.user.mapper.UserMapper;
import com.mall.user.service.MessageService;
import com.mall.user.service.ProductInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {
    private final ConversationMapper conversations;
    private final MessageMapper messages;
    private final UserMapper users;
    private final ShopMapper shops;
    private final ProductInfoService products;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ConversationResponse> conversations(String userId, int page, int size) {
        requireMessagingUser(userId);
        int safePage = Math.max(1, page), safeSize = Math.min(100, Math.max(1, size));
        long total = conversations.countByParticipant(userId);
        List<ConversationResponse> list = conversations.selectPage(userId, offset(safePage, safeSize), safeSize);
        Map<String, String> names = new HashMap<>();
        for (ConversationResponse item : list) {
            item.setBuyerName(names.computeIfAbsent(item.getBuyerId(), this::displayName));
            item.setSellerName(names.computeIfAbsent(item.getSellerId(), this::displayName));
        }
        return PageResponse.of(safePage, safeSize, total, list);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MessageResponse> messages(String userId, String conversationId, int page, int size) {
        requireMessagingUser(userId);
        requireParticipant(conversations.selectById(requireId(conversationId, "会话ID不能为空")), userId);
        int safePage = Math.max(1, page), safeSize = Math.min(100, Math.max(1, size));
        long total = messages.countByConversation(conversationId);
        List<MessageResponse> list = messages.selectPage(conversationId, offset(safePage, safeSize), safeSize)
                .stream().map(MessageServiceImpl::toResponse).toList();
        return PageResponse.of(safePage, safeSize, total, list);
    }

    @Override
    @Transactional
    public MessageResponse send(String userId, SendMessageRequest request) {
        requireMessagingUser(userId);
        if (request == null) throw new BizException(ErrorCode.PARAM_INVALID, "消息不能为空");
        String content = request.getContent() == null ? "" : request.getContent().strip();
        int length = content.codePointCount(0, content.length());
        if (length < 1 || length > 500) {
            throw new BizException(ErrorCode.PARAM_INVALID, "消息内容须为1至500字");
        }
        String conversationId = optionalId(request.getConversationId());
        String productId = optionalId(request.getContextProductId());
        Conversation conversation;
        if (conversationId == null) {
            String sellerId = requireId(request.getSellerId(), "首次发送消息必须提供卖家ID");
            if (userId.equals(sellerId)) throw new BizException(ErrorCode.PARAM_INVALID, "不能给自己发送消息");
            requireActiveSeller(sellerId);
            requireProductOwner(productId, sellerId);
            Conversation created = new Conversation();
            created.setConversationId(IdGenerator.conversationId());
            created.setBuyerId(userId);
            created.setSellerId(sellerId);
            created.setCreatedAt(now());
            created.setLastMessageAt(created.getCreatedAt());
            conversations.ensureConversation(created);
            conversation = conversations.selectByParticipantsForUpdate(userId, sellerId);
        } else {
            conversation = conversations.selectByIdForUpdate(conversationId);
            requireParticipant(conversation, userId);
            String suppliedSeller = optionalId(request.getSellerId());
            if (suppliedSeller != null && !suppliedSeller.equals(conversation.getSellerId())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "卖家与会话不匹配");
            }
            requireMessagingUser(conversation.getBuyerId());
            requireActiveSeller(conversation.getSellerId());
            requireProductOwner(productId, conversation.getSellerId());
        }

        // Both send and read hold this same conversation row until their transaction commits.
        // Allocate a strictly increasing microsecond to preserve send order even within one clock tick.
        LocalDateTime sentAt = now();
        if (!sentAt.isAfter(conversation.getLastMessageAt())) {
            sentAt = conversation.getLastMessageAt().plusNanos(1000);
        }
        Message message = new Message();
        message.setMessageId(IdGenerator.messageId());
        message.setConversationId(conversation.getConversationId());
        message.setSenderId(userId);
        message.setContent(content);
        message.setContextProductId(productId);
        message.setCreatedAt(sentAt);
        messages.insert(message);

        conversation.setLastMessageAt(sentAt);
        int previewLength = Math.min(120, length);
        conversation.setLastPreview(content.substring(0, content.offsetByCodePoints(0, previewLength)));
        if (userId.equals(conversation.getBuyerId())) {
            conversation.setSellerUnread(conversation.getSellerUnread() + 1);
        } else {
            conversation.setBuyerUnread(conversation.getBuyerUnread() + 1);
        }
        conversations.updateAfterSend(conversation);
        return toResponse(message);
    }

    @Override
    @Transactional
    public void markRead(String userId, String conversationId) {
        requireMessagingUser(userId);
        Conversation conversation = conversations.selectByIdForUpdate(requireId(conversationId, "会话ID不能为空"));
        requireParticipant(conversation, userId);
        LocalDateTime readAt = now();
        if (readAt.isBefore(conversation.getLastMessageAt())) readAt = conversation.getLastMessageAt();
        messages.markReceivedRead(conversation.getConversationId(), userId, readAt);
        conversations.clearUnread(conversation.getConversationId(), userId.equals(conversation.getBuyerId()));
    }

    private User requireMessagingUser(String userId) {
        if (userId == null || userId.isBlank()) throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        User user = users.selectById(userId);
        if (user == null || user.getRole() == Role.ADMIN || user.getStatus() != UserStatus.ACTIVE) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前用户不能使用站内消息");
        }
        return user;
    }

    private void requireActiveSeller(String sellerId) {
        User seller = users.selectById(sellerId);
        if (seller == null || seller.getRole() != Role.SELLER) {
            throw new BizException(ErrorCode.SHOP_NOT_FOUND, "卖家或店铺不存在");
        }
        if (seller.getStatus() != UserStatus.ACTIVE) {
            throw new BizException(ErrorCode.FORBIDDEN, "卖家账号已冻结");
        }
        Shop shop = shops.selectBySellerId(sellerId);
        if (shop == null || shop.getStatus() != ShopStatus.ACTIVE) {
            throw new BizException(ErrorCode.SHOP_NOT_FOUND, "店铺不存在或已停用");
        }
    }

    private void requireProductOwner(String productId, String sellerId) {
        if (productId == null) return;
        ProductBriefDTO product = products.load(List.of(productId)).get(productId);
        if (product == null || !sellerId.equals(product.getSellerId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商品不存在或不属于该卖家");
        }
    }

    private String displayName(String userId) {
        User user = users.selectById(userId);
        return user == null ? "用户已注销" : user.getNickname();
    }

    private static void requireParticipant(Conversation conversation, String userId) {
        if (conversation == null) throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND, "会话不存在");
        if (!userId.equals(conversation.getBuyerId()) && !userId.equals(conversation.getSellerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权访问此会话");
        }
    }

    private static String optionalId(String id) {
        if (id == null || id.isBlank()) return null;
        String value = id.strip();
        if (value.length() > 32) throw new BizException(ErrorCode.PARAM_INVALID, "ID长度不能超过32位");
        return value;
    }

    private static String requireId(String id, String message) {
        String value = optionalId(id);
        if (value == null) throw new BizException(ErrorCode.PARAM_INVALID, message);
        return value;
    }

    private static long offset(int page, int size) { return ((long) page - 1) * size; }
    private static LocalDateTime now() { return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS); }

    private static MessageResponse toResponse(Message message) {
        MessageResponse response = new MessageResponse();
        response.setMessageId(message.getMessageId());
        response.setConversationId(message.getConversationId());
        response.setSenderId(message.getSenderId());
        response.setContent(message.getContent());
        response.setContextProductId(message.getContextProductId());
        response.setReadAt(message.getReadAt());
        response.setCreatedAt(message.getCreatedAt());
        return response;
    }
}
