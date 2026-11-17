package com.mall.payment.service;

import com.mall.payment.client.NotificationClient;
import com.mall.payment.client.dto.NotificationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/** 站内通知 best-effort 发布：失败只记日志，不影响支付主流程（#139）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    private final NotificationClient notificationClient;

    @Value("${payment.internal-token:${INTERNAL_TOKEN:}}")
    private String internalToken;

    public void paySuccess(String buyerId, String orderId, Long amount) {
        publish(buyerId, "PAY_SUCCESS", "支付成功",
                "订单 " + orderId + " 已支付成功，金额 ¥" + yuan(amount) + "，卖家将尽快发货。",
                orderId, "ORDER");
    }

    public void refundDone(String buyerId, String orderId, Long amount) {
        publish(buyerId, "REFUND_DONE", "退款完成",
                "订单 " + orderId + " 的退款 ¥" + yuan(amount) + " 已原路退回。",
                orderId, "REFUND");
    }

    public void afterSale(String buyerId, String ticketId, String status) {
        publish(buyerId, "AFTER_SALE", "售后状态更新",
                "售后单 " + ticketId + " 状态更新为「" + afterSaleLabel(status) + "」。",
                ticketId, "AFTER_SALE");
    }

    private static String afterSaleLabel(String status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case "PENDING" -> "待卖家处理";
            case "APPROVED" -> "卖家已同意";
            case "REJECTED" -> "已驳回";
            case "BUYER_SHIPPED" -> "买家已退货";
            case "SELLER_RECEIVED" -> "卖家已收货";
            case "REFUNDED" -> "已退款";
            case "COMPLETED" -> "已完成";
            default -> status;
        };
    }

    private void publish(String userId, String type, String title, String content,
                         String bizId, String bizType) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        try {
            notificationClient.create(internalToken,
                    new NotificationRequest(userId, type, title, content, bizId, bizType));
        } catch (Exception e) {
            log.warn("站内通知投递失败, userId={}, type={}, bizId={}, reason={}",
                    userId, type, bizId, e.getMessage());
        }
    }

    private static String yuan(Long cents) {
        if (cents == null) {
            return "0.00";
        }
        return BigDecimal.valueOf(cents).movePointLeft(2).toPlainString();
    }
}