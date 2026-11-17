package com.mall.user.controller;

import com.mall.user.common.Result;
import com.mall.user.dto.request.CreateTicketRequest;
import com.mall.user.dto.request.ReplyTicketRequest;
import com.mall.user.dto.request.UpdateTicketStatusRequest;
import com.mall.user.dto.response.PageResponse;
import com.mall.user.dto.response.TicketDetailResponse;
import com.mall.user.dto.response.TicketResponse;
import com.mall.user.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 客服工单（#142）：用户与管理员共用接口，权限在服务层校验。 */
@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @PostMapping
    public Result<TicketDetailResponse> create(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateTicketRequest request) {
        return Result.success(ticketService.create(userId, request));
    }

    @GetMapping
    public Result<PageResponse<TicketResponse>> list(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(ticketService.list(userId, role, status, page, size));
    }

    @GetMapping("/{ticketId}")
    public Result<TicketDetailResponse> detail(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String ticketId) {
        return Result.success(ticketService.detail(userId, role, ticketId));
    }

    @PostMapping("/{ticketId}/messages")
    public Result<TicketDetailResponse> reply(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String ticketId,
            @Valid @RequestBody ReplyTicketRequest request) {
        return Result.success(ticketService.reply(userId, role, ticketId, request));
    }

    @PutMapping("/{ticketId}/status")
    public Result<TicketDetailResponse> updateStatus(
            @RequestHeader("X-User-Id") String operatorId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String ticketId,
            @Valid @RequestBody UpdateTicketStatusRequest request) {
        return Result.success(ticketService.updateStatus(operatorId, role, ticketId, request));
    }
}