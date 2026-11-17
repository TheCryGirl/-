package com.mall.order.controller;

import com.mall.order.common.BizException;
import com.mall.order.common.ErrorCode;
import com.mall.order.common.PageResult;
import com.mall.order.common.Result;
import com.mall.order.config.InternalTokenVerifier;
import com.mall.order.dto.request.CreateOrderRequest;
import com.mall.order.dto.request.PreviewOrderRequest;
import com.mall.order.dto.request.LogisticsTrackRequest;
import com.mall.order.dto.request.ShipOrderRequest;
import com.mall.order.dto.response.AdminFinanceSummaryResponse;
import com.mall.order.dto.response.CreateOrderResponse;
import com.mall.order.dto.response.MainOrderDetailResponse;
import com.mall.order.dto.response.MainOrderListItem;
import com.mall.order.dto.response.OrderDetailResponse;
import com.mall.order.dto.response.OrderListItem;
import com.mall.order.dto.response.OrderPreviewResponse;
import com.mall.order.dto.response.OrderLogisticsResponse;
import com.mall.order.dto.response.LogisticsTrackResponse;
import com.mall.order.service.LogisticsService;
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
    private final LogisticsService logisticsService;
    private final InternalTokenVerifier internalTokenVerifier;

    @PostMapping
    public Result<CreateOrderResponse> create(
            @RequestHeader(value = "X-User-Id", required = false) String buyerId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        return Result.success(orderService.create(buyerId, idempotencyKey, request));
    }

    /**
     * 下单预览（#135）：按店铺分组返回商品与金额，前端不得自行计算应付。
     * 路径写在 /{orderId} 之前，避免被详情路由吞掉。
     */
    @PostMapping("/preview")
    public Result<OrderPreviewResponse> preview(
            @RequestHeader(value = "X-User-Id", required = false) String buyerId,
            @Valid @RequestBody PreviewOrderRequest request) {
        return Result.success(orderService.preview(buyerId, request));
    }

    /** 主订单列表（#135）：买家跨店订单按主订单聚合。 */
    @GetMapping("/main")
    public Result<PageResult<MainOrderListItem>> listMain(
            @RequestHeader(value = "X-User-Id", required = false) String buyerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(orderService.listMain(buyerId, status, page, size));
    }

    /** 主订单详情（#135）：聚合支付视角，子订单按店铺展开。 */
    @GetMapping("/main/{mainOrderId}")
    public Result<MainOrderDetailResponse> mainDetail(
            @PathVariable String mainOrderId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return Result.success(orderService.mainDetail(mainOrderId, userId, role));
    }

    /** 取消主订单（#135）：所有未支付子订单一起取消并释放库存预占。 */
    @PutMapping("/main/{mainOrderId}/cancel")
    public Result<MainOrderDetailResponse> cancelMain(
            @PathVariable String mainOrderId,
            @RequestHeader(value = "X-User-Id", required = false) String buyerId) {
        return Result.success(orderService.cancelMain(mainOrderId, buyerId));
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

    /**
     * 管理员资金/风控汇总（订单口径）。
     *
     * <p>必须带 X-User-Id 与 X-User-Role: ADMIN，且角色严格等于 ADMIN；
     * 不接受 query 参数授权。路径写在 /{orderId} 之前，避免被详情路由吞掉。
     */
    @GetMapping("/admin/finance-summary")
    public Result<AdminFinanceSummaryResponse> adminFinanceSummary(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!"ADMIN".equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅管理员可查看资金汇总");
        }
        if (userId == null || userId.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或用户信息缺失");
        }
        return Result.success(orderService.adminFinanceSummary());
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
    public Result<Void> paid(@PathVariable String orderId,
                             @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        internalTokenVerifier.verify(token);
        orderService.markPaid(orderId);
        return Result.success();
    }

    /** 预留的退款完成通知入口，供 payment-service 后续接入。 */
    @PostMapping("/{orderId}/refunded")
    public Result<Void> refunded(@PathVariable String orderId,
                                 @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        internalTokenVerifier.verify(token);
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

    /**
     * 订单物流信息（#138）：快递公司、单号与轨迹时间线。
     *
     * <p>未发货订单也返回 200，只是 tracks 为空并带提示 message，
     * 前端按 shipped / tracks 判断即可，不必把「未发货」当成错误处理。
     */
    @GetMapping("/{orderId}/logistics")
    public Result<OrderLogisticsResponse> logistics(
            @PathVariable String orderId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return Result.success(logisticsService.detail(orderId, userId, role));
    }

    /**
     * 手工补录一条物流轨迹（#138）：本期不接第三方快递 API。
     * 卖家只能补自己店铺的订单，管理员不受限。
     */
    @PostMapping("/{orderId}/logistics/tracks")
    public Result<LogisticsTrackResponse> appendTrack(
            @PathVariable String orderId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody LogisticsTrackRequest request) {
        return Result.success(logisticsService.append(orderId, userId, role, request));
    }

    @PutMapping("/{orderId}/confirm")
    public Result<OrderDetailResponse> confirm(
            @PathVariable String orderId,
            @RequestHeader(value = "X-User-Id", required = false) String buyerId) {
        return Result.success(orderService.confirm(orderId, buyerId));
    }
}
