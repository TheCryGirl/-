package com.mall.order.service;

import com.mall.order.client.NotificationClient;
import com.mall.order.client.dto.NotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 站内通知 best-effort 发布：失败只记日志，不影响订单主流程（#139）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    private final NotificationClient notificationClient;

    public void notifyBuyer(String buyerId, String type, String title, String content,
                            String bizId, String bizType) {
        try {
            notificationClient.create(new NotificationRequest(buyerId, type, title, content, bizId, bizType));
        } catch (Exception e) {
            log.warn("站内通知投递失败, buyerId={}, type={}, bizId={}, reason={}",
                    buyerId, type, bizId, e.getMessage());
        }
    }
}