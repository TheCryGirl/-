package com.mall.order.service;

import com.mall.order.common.BizException;
import com.mall.order.common.ErrorCode;
import com.mall.order.common.IdGenerator;
import com.mall.order.common.PageResult;
import com.mall.order.dto.request.AuditLogRequest;
import com.mall.order.entity.AuditLog;
import com.mall.order.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 操作审计日志的写入与查询（任务 #58）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    /** 供其他服务通过内部接口写入。 */
    public void record(AuditLogRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审计内容不能为空");
        }
        String result = trimToNull(request.getResult());
        if (result == null) {
            result = "SUCCESS";
        } else if (!"SUCCESS".equals(result) && !"FAIL".equals(result)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "result 只能是 SUCCESS 或 FAIL");
        }
        record(request.getOperatorId(), request.getOperatorRole(), request.getAction(),
                request.getTargetType(), request.getTargetId(), request.getDetail(), result);
    }

    /** 本服务内直接记录。 */
    public void record(String operatorId, String operatorRole, String action,
                       String targetType, String targetId, String detail) {
        record(operatorId, operatorRole, action, targetType, targetId, detail, "SUCCESS");
    }

    private void record(String operatorId, String operatorRole, String action,
                        String targetType, String targetId, String detail, String result) {
        AuditLog entity = AuditLog.builder()
                .auditId("A" + IdGenerator.randomDigits(12))
                .operatorId(requireText(operatorId, "操作人", 32))
                .operatorRole(limitText(operatorRole, 16))
                .action(requireText(action, "操作类型", 32))
                .targetType(limitText(targetType, 32))
                .targetId(limitText(targetId, 64))
                .detail(limitText(detail, 1000))
                .result(result)
                .createdAt(LocalDateTime.now())
                .build();
        auditLogMapper.insert(entity);
        log.info("操作审计, operator={}, action={}, target={}/{}", operatorId, action, targetType, targetId);
    }

    public PageResult<AuditLog> list(String operatorId, String action, String from, String to,
                                     int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        String operatorFilter = trimToNull(operatorId);
        String actionFilter = trimToNull(action);
        LocalDateTime fromTime = parseBoundary(from, false);
        LocalDateTime toTime = parseBoundary(to, true);
        List<AuditLog> list = auditLogMapper.selectPage(operatorFilter, actionFilter, fromTime, toTime,
                (safePage - 1) * safeSize, safeSize);
        long total = auditLogMapper.countPage(operatorFilter, actionFilter, fromTime, toTime);
        return PageResult.of(list, total, safePage, safeSize);
    }

    private static LocalDateTime parseBoundary(String raw, boolean endOfDay) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            if (value.length() == 10) {
                return LocalDateTime.parse(value + (endOfDay ? "T23:59:59" : "T00:00:00"));
            }
            return LocalDateTime.parse(value.replace(' ', 'T'));
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "时间格式应为 yyyy-MM-dd 或 yyyy-MM-ddTHH:mm:ss");
        }
    }

    private static String requireText(String value, String field, int maxLength) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + "不能为空");
        }
        if (trimmed.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + "长度不能超过 " + maxLength);
        }
        return trimmed;
    }

    private static String limitText(String value, int maxLength) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
