package com.mall.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 写入站内通知的请求体（#139）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {

    private String userId;
    private String type;
    private String title;
    private String content;
    private String bizId;
    private String bizType;
}