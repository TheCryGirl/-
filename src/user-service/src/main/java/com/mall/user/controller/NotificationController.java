package com.mall.user.controller;

import com.mall.user.common.Result;
import com.mall.user.dto.response.NotificationResponse;
import com.mall.user.dto.response.PageResponse;
import com.mall.user.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 站内通知（#139）：用户侧查询与已读。 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public Result<PageResponse<NotificationResponse>> list(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return Result.success(notificationService.list(userId, page, size, unreadOnly));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount(@RequestHeader("X-User-Id") String userId) {
        return Result.success(Map.of("count", notificationService.unreadCount(userId)));
    }

    @PutMapping("/{notificationId}/read")
    public Result<Void> read(@RequestHeader("X-User-Id") String userId,
                             @PathVariable String notificationId) {
        notificationService.markRead(userId, notificationId);
        return Result.success((Void) null);
    }

    @PutMapping("/read-all")
    public Result<Map<String, Integer>> readAll(@RequestHeader("X-User-Id") String userId) {
        return Result.success(Map.of("updated", notificationService.markAllRead(userId)));
    }
}