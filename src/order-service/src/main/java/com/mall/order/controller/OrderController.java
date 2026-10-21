package com.mall.order.controller;

import com.mall.order.common.PageResult;
import com.mall.order.common.Result;
import com.mall.order.dto.request.CreateOrderRequest;
import com.mall.order.dto.request.ShipOrderRequest;
import com.mall.order.dto.response.CreateOrderResponse;
import com.mall.order.dto.response.OrderDetailResponse;
import com.mall.order.dto.response.OrderListItem;
import com.mall.order.service.OrderService;
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

@RestController
@RequestMapping({"/orders", "/api/orders"})
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public Result<CreateOrderResponse> create(
            @RequestHeader(value = "X-User-Id", required = false) String buyerId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        return Result.success(orderService.create(buyerId, idempotencyKey, request));
    }

    @GetMapping
    public Result<PageResult<OrderListItem>> list(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(defaultValue = "buyer") String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(orderService.list(userId, role, status, page, size));
    }

    @GetMapping("/{orderId}")
    public Result<OrderDetailResponse> detail(
            @PathVariable String orderId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return Result.success(orderService.detail(orderId, userId, role));
    }

    /** payment-service 内部调用：支付回调成功后标记订单已支付。 */
    @PostMapping("/{orderId}/paid")
    public Result<Void> paid(@PathVariable String orderId) {
        orderService.markPaid(orderId);
        return Result.success();
    }

    /** 预留的退款完成通知入口，供 payment-service 后续接入。 */
    @PostMapping("/{orderId}/refunded")
    public Result<Void> refunded(@PathVariable String orderId) {
        orderService.markRefunded(orderId);
        return Result.success();
    }

    @PutMapping("/{orderId}/cancel")
    public Result<OrderDetailResponse> cancel(
            @PathVariable String orderId,
            @RequestHeader(value = "X-User-Id", required = false) String buyerId) {
        return Result.success(orderService.cancel(orderId, buyerId));
    }

    @PutMapping("/{orderId}/ship")
    public Result<OrderDetailResponse> ship(
            @PathVariable String orderId,
            @RequestHeader(value = "X-User-Id", required = false) String operatorId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody ShipOrderRequest request) {
        return Result.success(orderService.ship(orderId, operatorId, role, request));
    }

    @PutMapping("/{orderId}/confirm")
    public Result<OrderDetailResponse> confirm(
            @PathVariable String orderId,
            @RequestHeader(value = "X-User-Id", required = false) String buyerId) {
        return Result.success(orderService.confirm(orderId, buyerId));
    }
}
