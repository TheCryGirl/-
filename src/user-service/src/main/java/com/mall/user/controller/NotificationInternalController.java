package com.mall.user.controller;

import com.mall.user.common.Result;
import com.mall.user.dto.request.CreateNotificationRequest;
import com.mall.user.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务间写入站内通知（#139）。
 *
 * <p>路径在 /internal 下，由 ServiceIngressFilter 校验 X-Internal-Token；
 * 调用方必须 best-effort，失败只记日志，不回滚主业务。
 */
@RestController
@RequestMapping("/internal/notifications")
@RequiredArgsConstructor
public class NotificationInternalController {

    private final NotificationService notificationService;

    @PostMapping
    public Result<Void> create(@Valid @RequestBody CreateNotificationRequest request) {
        notificationService.create(request);
        return Result.success((Void) null);
    }
}