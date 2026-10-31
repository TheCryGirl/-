package com.mall.order.service;

import com.mall.order.client.CartClient;
import com.mall.order.client.ProductClient;
import com.mall.order.client.UserClient;
import com.mall.order.client.dto.AddressDTO;
import com.mall.order.client.dto.CartDTO;
import com.mall.order.client.dto.CartItemDTO;
import com.mall.order.client.dto.ProductDetailDTO;
import com.mall.order.client.dto.ReserveItemDTO;
import com.mall.order.client.dto.ReserveStockRequestDTO;
import com.mall.order.client.dto.ShopBriefDTO;
import com.mall.order.client.dto.SkuDTO;
import com.mall.order.common.BizException;
import com.mall.order.common.ErrorCode;
import com.mall.order.common.IdGenerator;
import com.mall.order.common.IdempotencyStatus;
import com.mall.order.common.OrderStateMachine;
import com.mall.order.common.OrderStatus;
import com.mall.order.common.PageResult;
import com.mall.order.common.Result;
import com.mall.order.common.TimeUtils;
import com.mall.order.dto.request.CreateOrderRequest;
import com.mall.order.dto.request.ShipOrderRequest;
import com.mall.order.dto.response.AddressSnapshot;
import com.mall.order.dto.response.CreateOrderResponse;
import com.mall.order.dto.response.OrderDetailResponse;
import com.mall.order.dto.response.OrderItemResponse;
import com.mall.order.dto.response.OrderListItem;
import com.mall.order.entity.Order;
import com.mall.order.entity.OrderIdempotency;
import com.mall.order.entity.OrderItem;
import com.mall.order.mapper.CompensationMapper;
import com.mall.order.mapper.IdempotencyMapper;
import com.mall.order.mapper.OrderItemMapper;
import com.mall.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final int MAX_CART_ITEMS = 50;

    /**
     * 幂等记录停留在 RESERVING 的容忍时长，超过即视为持有者已失效并回收执行权。
     * 正常预占在本项目实测中为毫秒级，60 秒足够容纳 GC 抖动与瞬时慢查询。
     */
    private static final long RESERVING_TIMEOUT_SECONDS = 60L;

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final IdempotencyMapper idempotencyMapper;
    private final CompensationMapper compensationMapper;
    private final CompensationService compensationService;
    private final CartClient cartClient;
    private final UserClient userClient;
    private final ProductClient productClient;
    private final TransactionTemplate transactionTemplate;
    private final PlatformConfigService platformConfigService;

    @Value("${order.timeout.minutes:30}")
    private long timeoutMinutes;

    @Value("${order.internal-token:}")
    private String internalToken;

    /**
     * 下单主流程。订单先落库，再调用库存预占；预占失败时逻辑删除订单，返回 30003。
     */
    public CreateOrderResponse create(String buyerId, String idempotencyKey, CreateOrderRequest request) {
        requireUser(buyerId);
        String key = requireIdempotencyKey(idempotencyKey);
        List<String> cartItemIds = normalizeCartItemIds(request.getCartItemIds());
        String addressId = requireText(request.getAddressId(), "收货地址");
        String requestHash = hashRequest(buyerId, addressId, cartItemIds);

        OrderIdempotency existing = idempotencyMapper.selectByKey(key);
        if (existing != null) {
            return resume(existing, requestHash, buyerId);
        }

        String orderId = IdGenerator.orderId();
        LocalDateTime now = LocalDateTime.now();
        try {
            // 直接以 RESERVING 入库：插入成功即表示本次调用取得了库存预占的执行权，
            // 后续 order 事务插入与 reserve 调用都在这份执行权之下，不会与 resume() 路径重叠。
            idempotencyMapper.insert(OrderIdempotency.builder()
                    .idemKey(key)
                    .buyerId(buyerId)
                    .requestHash(requestHash)
                    .orderId(orderId)
                    .status(IdempotencyStatus.RESERVING)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        } catch (DataIntegrityViolationException e) {
            OrderIdempotency winner = idempotencyMapper.selectByKey(key);
            if (winner != null) {
                return resume(winner, requestHash, buyerId);
            }
            throw new BizException(ErrorCode.IDEMPOTENCY_CONFLICT, "幂等令牌冲突");
        }

        try {
            return createFresh(orderId, key, buyerId, addressId, cartItemIds);
        } catch (RuntimeException e) {
            // 首次尝试失败时退回执行权并释放幂等键，允许用户用同一个 key 重试。
            idempotencyMapper.markFailed(key, LocalDateTime.now());
            throw e;
        }
    }

    private CreateOrderResponse createFresh(String orderId, String key, String buyerId,
                                            String addressId, List<String> cartItemIds) {
        CartDTO cart = callCart(buyerId);
        Map<String, CartItemDTO> cartById = new LinkedHashMap<>();
        if (cart.getItems() != null) {
            for (CartItemDTO item : cart.getItems()) {
                cartById.put(item.getCartItemId(), item);
            }
        }
        List<CartItemDTO> selectedItems = new ArrayList<>();
        for (String cartItemId : cartItemIds) {
            CartItemDTO item = cartById.get(cartItemId);
            if (item == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "购物车条目不存在: " + cartItemId);
            }
            if (Boolean.FALSE.equals(item.getSelected())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "存在未勾选的购物车条目");
            }
            if (Boolean.TRUE.equals(item.getInvalid())) {
                throw new BizException(ErrorCode.PRODUCT_OFF_SHELF,
                        item.getInvalidReason() == null ? "商品已下架或库存不足" : item.getInvalidReason());
            }
            selectedItems.add(item);
        }

        AddressDTO address = findAddress(buyerId, addressId);
        Map<String, ProductDetailDTO> productCache = new HashMap<>();
        List<OrderItem> orderItems = new ArrayList<>();
        String sellerId = null;
        long total = 0L;

        for (CartItemDTO cartItem : selectedItems) {
            ProductDetailDTO product = productCache.computeIfAbsent(cartItem.getProductId(),
                    productId -> callProduct(productId));
            if ("SUSPENDED".equalsIgnoreCase(product.getStatus())) {
                throw new BizException(ErrorCode.SHOP_SUSPENDED, "店铺已停用，商品暂不可购买: " + product.getTitle());
            }
            if (!"ON_SALE".equalsIgnoreCase(product.getStatus())) {
                throw new BizException(ErrorCode.PRODUCT_OFF_SHELF, "商品已下架: " + product.getTitle());
            }
            SkuDTO sku = product.getSkus() == null ? null : product.getSkus().stream()
                    .filter(item -> item.getSkuId().equals(cartItem.getSkuId()))
                    .findFirst().orElse(null);
            if (sku == null) {
                throw new BizException(ErrorCode.PRODUCT_NOT_FOUND, "SKU 不存在: " + cartItem.getSkuId());
            }
            int quantity = cartItem.getQuantity() == null ? 0 : cartItem.getQuantity();
            if (quantity <= 0 || quantity > 999) {
                throw new BizException(ErrorCode.PARAM_INVALID, "购买数量不合法");
            }
            if (sku.getStockAvailable() != null && sku.getStockAvailable() < quantity) {
                throw new BizException(ErrorCode.STOCK_INSUFFICIENT, "库存不足: " + product.getTitle());
            }
            if (sellerId == null) {
                sellerId = product.getSellerId();
                // 商品状态同步可能失败，下单前再向用户服务确认一次店铺营业状态（#49）
                if (!shopActive(sellerId)) {
                    throw new BizException(ErrorCode.SHOP_SUSPENDED, "店铺已停用，暂不能下单");
                }
            } else if (!sellerId.equals(product.getSellerId())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "一次只能结算同一卖家的商品");
            }
            long price = sku.getPrice();
            long subtotal = price * quantity;
            total += subtotal;
            orderItems.add(OrderItem.builder()
                    .orderId(orderId)
                    .skuId(sku.getSkuId())
                    .productId(product.getProductId())
                    .productTitle(product.getTitle())
                    .specName(sku.getSpecName())
                    .priceSnapshot(price)
                    .quantity(quantity)
                    .subtotal(subtotal)
                    .mainImage(product.getMainImage())
                    .sellerId(product.getSellerId())
                    .createdAt(LocalDateTime.now())
                    .isDeleted(0)
                    .build());
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = now.plusMinutes(Math.max(1, platformConfigService.currentLong("order.timeout.minutes")));
        Order order = Order.builder()
                .orderId(orderId)
                .orderNo(IdGenerator.orderNo())
                .buyerId(buyerId)
                .sellerId(sellerId)
                .status(OrderStatus.PENDING_PAYMENT)
                .totalAmount(total)
                .payAmount(total)
                .receiver(address.getReceiver())
                .receiverPhone(address.getPhone())
                .addressFull(fullAddress(address))
                .expireAt(expireAt)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(buyerId)
                .updatedBy(buyerId)
                .isDeleted(0)
                .inventoryReleased(0)
                .build();

        transactionTemplate.executeWithoutResult(status -> {
            orderMapper.insert(order);
            orderItemMapper.insertBatch(orderItems);
        });

        List<ReserveItemDTO> reserveItems = orderItems.stream()
                .map(item -> new ReserveItemDTO(item.getSkuId(), item.getQuantity()))
                .toList();
        try {
            reserveUnderOwnership(key, orderId, expireAt, reserveItems);
        } catch (BizException e) {
            // 若预占请求超时但下游稍后提交，补偿任务会继续尝试释放。
            compensationService.enqueue(orderId);
            // 契约要求预占失败删除订单主记录与明细。这里使用逻辑删除。
            orderMapper.softDelete(orderId);
            orderItemMapper.softDeleteByOrderId(orderId);
            idempotencyMapper.markFailed(key, LocalDateTime.now());
            throw e;
        }

        for (String cartItemId : cartItemIds) {
            try {
                Result<Void> result = cartClient.deleteItem(internalToken, buyerId, cartItemId);
                if (result != null && result.getCode() != ErrorCode.SUCCESS) {
                    log.warn("清理购物车失败, cartItemId={}, code={}", cartItemId, result.getCode());
                }
            } catch (Exception e) {
                log.warn("清理购物车异常, cartItemId={}", cartItemId, e);
            }
        }

        return new CreateOrderResponse(orderId, order.getOrderNo(), order.getPayAmount(),
                TimeUtils.format(expireAt));
    }

    /**
     * 在「已持有 RESERVING 执行权」的前提下预占库存并落终态。
     *
     * <p>这是全流程唯一调用 product-service 预占接口的位置，createFresh 与 resume 都必须
     * 先通过 {@link #claimReserving(String, OrderIdempotency)} 抢到执行权才能进入，
     * 因此同一幂等键在任意时刻至多一个线程执行预占，库存不会被重复扣减。
     *
     * @throws BizException 预占失败；执行权由调用方负责退回
     */
    private void reserveUnderOwnership(String key, String orderId, LocalDateTime expireAt,
                                       List<ReserveItemDTO> reserveItems) {
        callReserve(new ReserveStockRequestDTO(orderId, TimeUtils.format(expireAt), reserveItems));
        int updated = idempotencyMapper.markSuccessIfReserving(key, orderId, LocalDateTime.now());
        if (updated == 0) {
            // 执行权已被超时回收任务重置，说明本线程持锁时间超出阈值。
            // 此时库存已预占成功，必须交给补偿任务兜底，避免预占悬空。
            log.warn("幂等执行权已被回收，转补偿处理, idemKey={}, orderId={}", key, orderId);
            compensationService.enqueue(orderId);
        }
    }

    /**
     * 抢占库存预占的执行权，返回 true 表示本次调用可以执行预占。
     *
     * <p>已经处于 RESERVING 的调用者不再抢占，由 {@link #awaitOrderCreated(OrderIdempotency)}
     * 等待其完成，避免重复预占。
     */
    private boolean claimReserving(String key, OrderIdempotency existing) {
        if (existing != null && existing.getStatus() == IdempotencyStatus.RESERVING) {
            return false;
        }
        return idempotencyMapper.claimReserving(key, LocalDateTime.now()) == 1;
    }

    private CreateOrderResponse resume(OrderIdempotency existing, String requestHash, String buyerId) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new BizException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "同一 Idempotency-Key 不能用于不同的下单请求");
        }
        Order order = existing.getOrderId() == null ? null : orderMapper.selectById(existing.getOrderId());

        // 订单尚未落库：inserter（create 路径）持有执行权，resume 只负责等待。
        // 不再「重读状态后自行补做预占」——那正是 #74 重复预占的根因。
        if (order == null) {
            order = awaitOrderCreated(existing);
            if (order != null) {
                return createResponse(order);
            }
            throw new BizException(ErrorCode.IDEMPOTENCY_CONFLICT, "下单处理中，请稍后重试");
        }

        // 订单已落库：重读最新状态，已成功直接返回。
        OrderIdempotency latestState = idempotencyMapper.selectByKey(existing.getIdemKey());
        if (latestState != null && latestState.getStatus() == IdempotencyStatus.SUCCESS) {
            return createResponse(order);
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            idempotencyMapper.markSuccess(existing.getIdemKey(), order.getOrderId(), LocalDateTime.now());
            return createResponse(order);
        }

        List<OrderItem> items = orderItemMapper.selectByOrderId(order.getOrderId());
        if (items.isEmpty()) {
            idempotencyMapper.markFailed(existing.getIdemKey(), LocalDateTime.now());
            throw new BizException(ErrorCode.IDEMPOTENCY_CONFLICT, "订单明细缺失，请重新提交");
        }
        List<ReserveItemDTO> reserveItems = items.stream()
                .map(item -> new ReserveItemDTO(item.getSkuId(), item.getQuantity()))
                .toList();
        // 订单已落库但预占未完成（持有者崩溃在 order 事务与 reserve 之间）。
        // 必须重新抢占执行权后才能补做预占，否则多个线程会同时进入这里重复预占库存。
        if (!claimReserving(existing.getIdemKey(), latestState)) {
            order = awaitOrderCreated(existing);
            if (order != null) {
                return createResponse(order);
            }
            throw new BizException(ErrorCode.IDEMPOTENCY_CONFLICT, "下单处理中，请稍后重试");
        }
        try {
            reserveUnderOwnership(existing.getIdemKey(), order.getOrderId(), order.getExpireAt(), reserveItems);
        } catch (BizException e) {
            compensationService.enqueue(order.getOrderId());
            orderMapper.softDelete(order.getOrderId());
            orderItemMapper.softDeleteByOrderId(order.getOrderId());
            idempotencyMapper.markFailed(existing.getIdemKey(), LocalDateTime.now());
            throw e;
        }
        return createResponse(order);
    }

    /**
     * 并发下单时等待持有执行权的请求把订单落库，最多等 3 秒。
     * 超时返回 null，由调用方转成「处理中」提示。
     */
    private Order awaitOrderCreated(OrderIdempotency existing) {
        long deadline = System.currentTimeMillis() + 3000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            OrderIdempotency latest = idempotencyMapper.selectByKey(existing.getIdemKey());
            if (latest == null) {
                return null;
            }
            if (latest.getOrderId() != null) {
                Order order = orderMapper.selectById(latest.getOrderId());
                if (order != null) {
                    return order;
                }
            }
            // PROCESSING 与 RESERVING 都表示「仍在处理中」，后者是本次占用执行权的请求正在预占库存。
            // 只有 FAILED 才是真正结束（幂等键已退回，需重新提交）。
            if (latest.getStatus() == IdempotencyStatus.FAILED) {
                return null;
            }
        }
        return null;
    }

    public PageResult<OrderListItem> list(String userId, String role, String status, int page, int size) {
        requireUser(userId);
        String safeRole = role == null || role.isBlank() ? "buyer" : role.trim().toLowerCase();
        if (!"buyer".equals(safeRole) && !"seller".equals(safeRole)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "role 只能是 buyer 或 seller");
        }
        if (status != null && !status.isBlank()) {
            try {
                OrderStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BizException(ErrorCode.PARAM_INVALID, "订单状态不合法");
            }
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        String buyerId = "buyer".equals(safeRole) ? userId : null;
        String sellerId = "seller".equals(safeRole) ? userId : null;
        List<OrderListItem> items = orderMapper.selectList(buyerId, sellerId, trimToNull(status),
                        (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(this::toListItem)
                .toList();
        long total = orderMapper.countList(buyerId, sellerId, trimToNull(status));
        return PageResult.of(items, total, safePage, safeSize);
    }

    public OrderDetailResponse detail(String orderId, String userId, String role) {
        Order order = requireOrder(orderId);
        checkReadAccess(order, userId, role);
        return toDetail(order);
    }

    public void markPaid(String orderId) {
        Order order = requireOrder(orderId);
        if (order.getStatus() == OrderStatus.PAID) {
            return;
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BizException(ErrorCode.ORDER_STATE_INVALID, "订单已取消，不能标记为已支付");
        }
        OrderStateMachine.assertCanTransit(order.getStatus(), OrderStatus.PAID);
        int updated = orderMapper.markPaid(orderId, LocalDateTime.now());
        if (updated == 0) {
            Order latest = requireOrder(orderId);
            if (latest.getStatus() != OrderStatus.PAID) {
                throw new BizException(ErrorCode.ORDER_STATE_INVALID, "订单支付状态更新失败");
            }
        }
    }

    public void markRefunded(String orderId) {
        Order order = requireOrder(orderId);
        if (order.getStatus() == OrderStatus.REFUNDED) {
            return;
        }
        if (order.getStatus() != OrderStatus.PAID
                && order.getStatus() != OrderStatus.SHIPPED
                && order.getStatus() != OrderStatus.COMPLETED) {
            throw new BizException(ErrorCode.ORDER_STATE_INVALID, "订单当前状态不允许退款");
        }
        int updated = orderMapper.markRefunded(orderId, LocalDateTime.now());
        if (updated == 0) {
            Order latest = requireOrder(orderId);
            if (latest.getStatus() != OrderStatus.REFUNDED) {
                throw new BizException(ErrorCode.ORDER_STATE_INVALID, "订单退款状态更新失败");
            }
        }
    }

    public OrderDetailResponse cancel(String orderId, String buyerId) {
        Order order = requireOrder(orderId);
        if (!buyerId.equals(order.getBuyerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权取消他人的订单");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return toDetail(order);
        }
        OrderStateMachine.assertCanTransit(order.getStatus(), OrderStatus.CANCELLED);
        int updated = orderMapper.markCancelled(orderId, LocalDateTime.now());
        if (updated == 0) {
            Order latest = requireOrder(orderId);
            if (latest.getStatus() != OrderStatus.CANCELLED) {
                throw new BizException(ErrorCode.ORDER_STATE_INVALID, "订单状态不允许取消");
            }
        }
        releaseInventory(orderId);
        return detail(orderId, buyerId, "BUYER");
    }

    public OrderDetailResponse ship(String orderId, String operatorId, String role, ShipOrderRequest request) {
        Order order = requireOrder(orderId);
        if (operatorId == null || (!"ADMIN".equalsIgnoreCase(role)
                && !operatorId.equals(order.getSellerId()))) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该订单");
        }
        if (order.getStatus() == OrderStatus.SHIPPED) {
            return toDetail(order);
        }
        OrderStateMachine.assertCanTransit(order.getStatus(), OrderStatus.SHIPPED);
        int updated = orderMapper.markShipped(orderId, request.getTrackingNo().trim(), LocalDateTime.now());
        if (updated == 0) {
            Order latest = requireOrder(orderId);
            if (latest.getStatus() != OrderStatus.SHIPPED) {
                throw new BizException(ErrorCode.ORDER_STATE_INVALID, "订单状态不允许发货");
            }
        }
        return toDetail(requireOrder(orderId));
    }

    public OrderDetailResponse confirm(String orderId, String buyerId) {
        Order order = requireOrder(orderId);
        if (!buyerId.equals(order.getBuyerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权确认他人的订单");
        }
        if (order.getStatus() == OrderStatus.COMPLETED) {
            return toDetail(order);
        }
        OrderStateMachine.assertCanTransit(order.getStatus(), OrderStatus.COMPLETED);
        int updated = orderMapper.markCompleted(orderId, LocalDateTime.now());
        if (updated == 0) {
            Order latest = requireOrder(orderId);
            if (latest.getStatus() != OrderStatus.COMPLETED) {
                throw new BizException(ErrorCode.ORDER_STATE_INVALID, "订单状态不允许确认收货");
            }
        }
        return toDetail(requireOrder(orderId));
    }

    /**
     * 超时取消入口。扫描任务只找候选，状态 CAS 在这里完成。
     */
    public boolean cancelTimeout(String orderId) {
        int updated = orderMapper.markCancelled(orderId, LocalDateTime.now());
        if (updated == 0) {
            return false;
        }
        releaseInventory(orderId);
        log.info("订单超时取消, orderId={}", orderId);
        return true;
    }

    public void releaseInventory(String orderId) {
        // 抢占式：只有把 inventory_released 从 0 改成 1 的调用者才真正释放库存。
        // 买家取消与超时取消并发时，另一个调用者在这里返回 0，直接退出，避免重复释放。
        int claimed = orderMapper.markInventoryReleased(orderId);
        if (claimed == 0) {
            log.debug("库存已释放或无需释放，跳过, orderId={}", orderId);
            return;
        }
        try {
            Result<com.mall.order.client.dto.ReleaseResponseDTO> result =
                    productClient.release(internalToken, new ProductClient.ReleaseRequest(orderId));
            if (result == null) {
                throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "product-service 无响应");
            }
            // 30004 表示本来就没有预占记录，等同于释放完成。
            if (result.getCode() != ErrorCode.SUCCESS
                    && result.getCode() != ErrorCode.RESERVATION_NOT_FOUND) {
                throw new BizException(result.getCode(), result.getMessage());
            }
        } catch (Exception e) {
            // 远程释放失败：把标记回滚为 0，交给补偿任务重试。
            orderMapper.resetInventoryReleased(orderId);
            log.warn("释放库存失败，进入补偿, orderId={}", orderId, e);
            compensationService.enqueue(orderId);
        }
    }

    public void retryUnreleasedInventory() {
        for (String orderId : orderMapper.selectUnreleasedCancelled(100)) {
            releaseInventory(orderId);
        }
    }

    public void retryCompensations() {
        compensationService.processDue();
    }

    public void cleanStaleIdempotency() {
        idempotencyMapper.deleteStale(LocalDateTime.now().minusMinutes(10));
        // 回收长时间停留在 RESERVING 的记录：持锁进程崩溃或预占调用卡死时，
        // 该幂等键会永久无法再被抢占，必须由本任务重置为 FAILED 让调用方能重试。
        LocalDateTime reservingCutoff = LocalDateTime.now().minusSeconds(RESERVING_TIMEOUT_SECONDS);
        String staleKey;
        while ((staleKey = idempotencyMapper.selectStaleReservingKey(reservingCutoff)) != null) {
            int reset = idempotencyMapper.resetStaleReserving(staleKey, reservingCutoff, LocalDateTime.now());
            if (reset == 0) {
                break;
            }
            log.warn("回收超时的幂等执行权, idemKey={}, 超时阈值={}s", staleKey, RESERVING_TIMEOUT_SECONDS);
        }
    }

    private CartDTO callCart(String buyerId) {
        try {
            Result<CartDTO> result = cartClient.getCart(internalToken, buyerId);
            return unwrap(result, "cart-service");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "cart-service 调用失败", e);
        }
    }

    /**
     * 店铺是否在营业（#49）。用户服务不可用时按正常营业处理，
     * 正常路径下的停用已经通过商品状态 SUSPENDED 拦住。
     */
    private boolean shopActive(String sellerId) {
        try {
            Result<ShopBriefDTO> result = userClient.shopBySeller(sellerId);
            if (result == null || result.getCode() != ErrorCode.SUCCESS || result.getData() == null) {
                return true;
            }
            return !"SUSPENDED".equalsIgnoreCase(result.getData().getStatus());
        } catch (Exception e) {
            log.warn("查询店铺营业状态失败，按正常营业处理, sellerId={}", sellerId, e);
            return true;
        }
    }

    private AddressDTO findAddress(String buyerId, String addressId) {
        try {
            Result<List<AddressDTO>> result = userClient.addresses(buyerId, buyerId);
            List<AddressDTO> addresses = unwrap(result, "user-service");
            return addresses.stream().filter(address -> addressId.equals(address.getAddressId()))
                    .findFirst()
                    .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "收货地址不存在"));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "user-service 调用失败", e);
        }
    }

    private ProductDetailDTO callProduct(String productId) {
        try {
            return unwrap(productClient.getProduct(productId), "product-service");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "product-service 调用失败", e);
        }
    }

    private void callReserve(ReserveStockRequestDTO request) {
        try {
            unwrap(productClient.reserve(internalToken, request), "product-service");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "product-service 预占调用失败", e);
        }
    }

    private <T> T unwrap(Result<T> result, String service) {
        if (result == null) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, service + " 无响应");
        }
        if (result.getCode() != ErrorCode.SUCCESS) {
            throw new BizException(result.getCode(), result.getMessage());
        }
        return result.getData();
    }

    private void checkReadAccess(Order order, String userId, String role) {
        if (userId == null || userId.isBlank() || "ADMIN".equalsIgnoreCase(role)
                || userId.equals(order.getBuyerId()) || userId.equals(order.getSellerId())) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN, "无权查看该订单");
    }

    private Order requireOrder(String orderId) {
        Order order = orderId == null ? null : orderMapper.selectById(orderId.trim());
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private CreateOrderResponse createResponse(Order order) {
        return new CreateOrderResponse(order.getOrderId(), order.getOrderNo(), order.getPayAmount(),
                TimeUtils.format(order.getExpireAt()));
    }

    private OrderDetailResponse toDetail(Order order) {
        OrderDetailResponse response = new OrderDetailResponse();
        response.setOrderId(order.getOrderId());
        response.setOrderNo(order.getOrderNo());
        response.setStatus(order.getStatus());
        response.setBuyerId(order.getBuyerId());
        response.setSellerId(order.getSellerId());
        response.setTotalAmount(order.getTotalAmount());
        response.setPayAmount(order.getPayAmount());
        response.setTrackingNo(order.getTrackingNo());
        response.setAddressSnapshot(new AddressSnapshot(order.getReceiver(), order.getReceiverPhone(),
                order.getAddressFull()));
        response.setItems(orderItemMapper.selectByOrderId(order.getOrderId()).stream()
                .map(this::toItemResponse).toList());
        response.setCreatedAt(TimeUtils.format(order.getCreatedAt()));
        response.setPaidAt(TimeUtils.format(order.getPaidAt()));
        response.setExpireAt(TimeUtils.format(order.getExpireAt()));
        return response;
    }

    private OrderListItem toListItem(Order order) {
        OrderListItem item = new OrderListItem();
        item.setOrderId(order.getOrderId());
        item.setOrderNo(order.getOrderNo());
        item.setStatus(order.getStatus());
        item.setPayAmount(order.getPayAmount());
        item.setCreatedAt(TimeUtils.format(order.getCreatedAt()));
        item.setItems(orderItemMapper.selectByOrderId(order.getOrderId()).stream()
                .map(this::toItemResponse).toList());
        return item;
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(item.getSkuId(), item.getProductTitle(), item.getSpecName(),
                item.getPriceSnapshot(), item.getQuantity(), item.getSubtotal(), item.getMainImage());
    }

    private static String fullAddress(AddressDTO address) {
        return java.util.stream.Stream.of(address.getProvince(), address.getCity(), address.getDistrict(),
                        address.getDetail())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
    }

    private static List<String> normalizeCartItemIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择要结算的商品");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String id : raw) {
            if (id == null || id.isBlank()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "购物车条目 ID 不合法");
            }
            if (!unique.add(id.trim())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "购物车条目重复");
            }
        }
        if (unique.size() > MAX_CART_ITEMS) {
            throw new BizException(ErrorCode.PARAM_INVALID, "一次最多结算 " + MAX_CART_ITEMS + " 件商品");
        }
        return new ArrayList<>(unique);
    }

    private static String hashRequest(String buyerId, String addressId, List<String> cartItemIds) {
        String canonical = buyerId + "|" + addressId + "|" + String.join(",", cartItemIds);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String requireIdempotencyKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少 Idempotency-Key 请求头");
        }
        String key = raw.trim();
        if (key.length() < 8 || key.length() > 64) {
            throw new BizException(ErrorCode.PARAM_INVALID, "Idempotency-Key 长度必须在 8-64 之间");
        }
        return key;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + "不能为空");
        }
        return value.trim();
    }

    private static void requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或用户信息缺失");
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}