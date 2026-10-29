package com.mall.payment.service;

import com.mall.payment.client.AuditClient;
import com.mall.payment.client.dto.AuditLogRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 操作审计上报（任务 #58）。尽力而为：审计中心不可用时只记日志，不影响业务结果。
 */
@Service
public class AuditLogPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditLogPublisher.class);

    private final AuditClient auditClient;
    private final boolean enabled;
    private final String internalToken;

    public AuditLogPublisher(AuditClient auditClient,
                             @Value("${audit.enabled:true}") boolean enabled,
                             @Value("${payment.internal-token:}") String internalToken) {
        this.auditClient = auditClient;
        this.enabled = enabled;
        this.internalToken = internalToken;
    }

    public void publish(String operatorId, String operatorRole, String action,
                        String targetType, String targetId, String detail) {
        if (!enabled) {
            return;
        }
        AuditLogRequest request = new AuditLogRequest();
        request.setOperatorId(operatorId);
        request.setOperatorRole(operatorRole);
        request.setAction(action);
        request.setTargetType(targetType);
        request.setTargetId(targetId);
        request.setDetail(detail);
        request.setResult("SUCCESS");
        try {
            auditClient.write(internalToken, request);
        } catch (Exception e) {
            log.warn("上报审计日志失败 action={} targetId={}: {}", action, targetId, e.getMessage());
        }
    }
}
