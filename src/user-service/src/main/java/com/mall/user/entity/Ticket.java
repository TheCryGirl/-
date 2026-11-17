package com.mall.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 客服工单（#142），用户与平台客服之间的可追踪会话。 */
@Data
public class Ticket {

    private String ticketId;
    private String ticketNo;
    private String userId;
    private String type;
    private String title;
    private String status;
    private String assigneeId;
    private String bizId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
}