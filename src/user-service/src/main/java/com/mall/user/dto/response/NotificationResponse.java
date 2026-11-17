package com.mall.user.dto.response;

import com.mall.user.entity.Notification;
import lombok.Data;

import java.time.LocalDateTime;

/** 站内通知出参（#139）。 */
@Data
public class NotificationResponse {

    private String notificationId;
    private String type;
    private String title;
    private String content;
    private String bizId;
    private String bizType;
    private Boolean read;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification item) {
        NotificationResponse response = new NotificationResponse();
        response.setNotificationId(item.getNotificationId());
        response.setType(item.getType());
        response.setTitle(item.getTitle());
        response.setContent(item.getContent());
        response.setBizId(item.getBizId());
        response.setBizType(item.getBizType());
        response.setRead(item.getReadAt() != null);
        response.setReadAt(item.getReadAt());
        response.setCreatedAt(item.getCreatedAt());
        return response;
    }
}