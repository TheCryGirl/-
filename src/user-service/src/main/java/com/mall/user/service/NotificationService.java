package com.mall.user.service;

import com.mall.user.common.BizException;
import com.mall.user.common.ErrorCode;
import com.mall.user.common.IdGenerator;
import com.mall.user.dto.request.CreateNotificationRequest;
import com.mall.user.dto.response.NotificationResponse;
import com.mall.user.dto.response.PageResponse;
import com.mall.user.entity.Notification;
import com.mall.user.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 站内通知（#139）：写入与查询，纯单向消息。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Set<String> TYPES = Set.of("PAY_SUCCESS", "SHIPPED", "REFUND_DONE", "AFTER_SALE");

    private final NotificationMapper notificationMapper;

    /** 服务间写入通知，失败由调用方吞掉，不影响业务主流程。 */
    public void create(CreateNotificationRequest request) {
        if (request == null || request.getUserId() == null || request.getUserId().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "接收人不能为空");
        }
        String type = request.getType() == null ? "" : request.getType().trim().toUpperCase();
        if (!TYPES.contains(type)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不支持的通知类型");
        }
        Notification notification = new Notification();
        notification.setNotificationId("N" + IdGenerator.randomDigits(14));
        notification.setUserId(request.getUserId().trim());
        notification.setType(type);
        notification.setTitle(request.getTitle());
        notification.setContent(request.getContent());
        notification.setBizId(request.getBizId());
        notification.setBizType(request.getBizType());
        notification.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(notification);
        log.info("写入站内通知, userId={}, type={}, bizId={}", notification.getUserId(), type, notification.getBizId());
    }

    public PageResponse<NotificationResponse> list(String userId, int page, int size, boolean unreadOnly) {
        requireUser(userId);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, size));
        int unread = unreadOnly ? 1 : 0;
        List<NotificationResponse> list = notificationMapper
                .selectPage(userId, unread, (safePage - 1) * safeSize, safeSize)
                .stream().map(NotificationResponse::from).toList();
        long total = notificationMapper.countPage(userId, unread);
        return PageResponse.of(safePage, safeSize, total, list);
    }

    public long unreadCount(String userId) {
        requireUser(userId);
        return notificationMapper.countUnread(userId);
    }

    public void markRead(String userId, String notificationId) {
        requireUser(userId);
        if (notificationMapper.countOwned(notificationId, userId) == 0) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该通知");
        }
        notificationMapper.markRead(notificationId, userId);
    }

    public int markAllRead(String userId) {
        requireUser(userId);
        return notificationMapper.markAllRead(userId);
    }

    private static void requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或用户信息缺失");
        }
    }
}