package com.mall.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 站内通知（#139），平台推送给用户的单向消息。 */
@Data
public class Notification {

    private String notificationId;
    private String userId;
    private String type;
    private String title;
    private String content;
    private String bizId;
    private String bizType;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}