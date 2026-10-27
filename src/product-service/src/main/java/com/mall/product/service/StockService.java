package com.mall.product.service;

import com.mall.product.common.BizException;
import com.mall.product.common.ErrorCode;
import com.mall.product.common.IdGenerator;
import com.mall.product.common.ReservationStatus;
import com.mall.product.common.TimeUtils;
import com.mall.product.dto.request.ConfirmStockRequest;
import com.mall.product.dto.request.ReleaseStockRequest;
import com.mall.product.dto.request.ReserveItem;
import com.mall.product.dto.request.ReserveStockRequest;
import com.mall.product.dto.response.ConfirmResponse;
import com.mall.product.dto.response.ReleaseResponse;
import com.mall.product.dto.response.ReserveItemResponse;
import com.mall.product.dto.response.ReserveStockResponse;
import com.mall.product.entity.Sku;
import com.mall.product.entity.StockReservation;
import com.mall.product.entity.StockReservationItem;
import com.mall.product.mapper.ProductMapper;
import com.mall.product.mapper.SkuMapper;
import com.mall.product.mapper.StockReservationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 库存预占的核心实现。
 *
 * <p>防超卖依靠 sku 表的条件 UPDATE；预占单头 order_id 唯一，负责接口幂等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private static final int MAX_LINES = 50;

    private final StockReservationMapper reservationMapper;
    private final SkuMapper skuMapper;
    private final ProductMapper productMapper;
    private final TransactionTemplate transactionTemplate;

    public ReserveStockResponse reserve(ReserveStockRequest request) {
        String orderId = requireText(request.getOrderId(), "orderId");
        LocalDateTime expireAt = TimeUtils.parse(request.getExpireAt());
        if (expireAt == null || !expireAt.isAfter(LocalDateTime.now())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "expireAt 必须是未来时间");
        }
        Map<String, Integer> items = normalizeItems(request.getItems());

        StockReservation existing = reservationMapper.selectByOrderId(orderId);
        if (existing != null) {
            return replay(existing);
        }

        try {
            ReserveStockResponse response = transactionTemplate.execute(status -> doReserve(orderId, expireAt, items));
            if (response == null) {
                throw new IllegalStateException("reserve transaction returned null");
            }
            return response;
        } catch (DataIntegrityViolationException e) {
            // 并发重复预占：唯一索引冲突后回读首个预占结果。
            StockReservation winner = reservationMapper.selectByOrderId(orderId);
            if (winner != null) {
                return replay(winner);
            }
            throw e;
        }
    }

    private ReserveStockResponse doReserve(String orderId, LocalDateTime expireAt, Map<String, Integer> items) {
        StockReservation again = reservationMapper.selectByOrderId(orderId);
        if (again != null) {
            return replay(again);
        }
        LocalDateTime now = LocalDateTime.now();
        String reservationId = IdGenerator.reservationId();
        reservationMapper.insertReservation(StockReservation.builder()
                .reservationId(reservationId)
                .orderId(orderId)
                .status(ReservationStatus.LOCKED)
                .expireAt(expireAt)
                .createdAt(now)
                .updatedAt(now)
                .build());

        List<ReserveItemResponse> responses = new ArrayList<>();
        // TreeMap 按 skuId 升序，固定加锁顺序，降低死锁概率。
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            reservationMapper.insertItem(StockReservationItem.builder()
                    .reservationId(reservationId)
                    .skuId(entry.getKey())
                    .quantity(entry.getValue())
                    .build());
            int updated = skuMapper.reserve(entry.getKey(), entry.getValue());
            if (updated == 0) {
                throw new BizException(ErrorCode.STOCK_INSUFFICIENT,
                        "SKU " + entry.getKey() + " 库存不足或商品不在售");
            }
            responses.add(new ReserveItemResponse(entry.getKey(), entry.getValue()));
        }
        return new ReserveStockResponse(reservationId, responses);
    }

    public ReleaseResponse release(ReleaseStockRequest request) {
        return release(requireText(request.getOrderId(), "orderId"));
    }

    public ReleaseResponse release(String orderId) {
        Boolean released = transactionTemplate.execute(status -> doRelease(orderId));
        return new ReleaseResponse(Boolean.TRUE.equals(released));
    }

    private boolean doRelease(String orderId) {
        StockReservation reservation = reservationMapper.selectByOrderId(orderId);
        if (reservation == null) {
            throw new BizException(ErrorCode.RESERVATION_NOT_FOUND, "预占记录不存在");
        }
        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            return true;
        }
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            throw new BizException(ErrorCode.RESERVATION_NOT_FOUND, "预占已确认扣减，不能释放");
        }
        List<StockReservationItem> items = reservationMapper.selectItems(reservation.getReservationId());
        int updated = reservationMapper.updateStatus(orderId, ReservationStatus.LOCKED,
                ReservationStatus.RELEASED, LocalDateTime.now());
        if (updated == 0) {
            StockReservation latest = reservationMapper.selectByOrderId(orderId);
            if (latest != null && latest.getStatus() == ReservationStatus.RELEASED) {
                return true;
            }
            throw new BizException(ErrorCode.RESERVATION_NOT_FOUND, "预占状态已变化，不能释放");
        }
        for (StockReservationItem item : items) {
            int restored = skuMapper.restore(item.getSkuId(), item.getQuantity());
            if (restored == 0) {
                throw new BizException(ErrorCode.SYSTEM_ERROR,
                        "库存回补失败，SKU " + item.getSkuId() + " 的 locked 库存不足");
            }
        }
        return true;
    }

    public ConfirmResponse confirm(ConfirmStockRequest request) {
        return confirm(requireText(request.getOrderId(), "orderId"));
    }

    public ConfirmResponse confirm(String orderId) {
        Boolean confirmed = transactionTemplate.execute(status -> doConfirm(orderId));
        return new ConfirmResponse(Boolean.TRUE.equals(confirmed));
    }

    private boolean doConfirm(String orderId) {
        StockReservation reservation = reservationMapper.selectByOrderId(orderId);
        if (reservation == null) {
            throw new BizException(ErrorCode.RESERVATION_NOT_FOUND, "预占记录不存在");
        }
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            return true;
        }
        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            throw new BizException(ErrorCode.RESERVATION_NOT_FOUND, "预占已释放，不能确认扣减");
        }
        List<StockReservationItem> items = reservationMapper.selectItems(reservation.getReservationId());
        int updated = reservationMapper.updateStatus(orderId, ReservationStatus.LOCKED,
                ReservationStatus.CONFIRMED, LocalDateTime.now());
        if (updated == 0) {
            StockReservation latest = reservationMapper.selectByOrderId(orderId);
            if (latest != null && latest.getStatus() == ReservationStatus.CONFIRMED) {
                return true;
            }
            throw new BizException(ErrorCode.RESERVATION_NOT_FOUND, "预占状态已变化，不能确认扣减");
        }
        for (StockReservationItem item : items) {
            int deducted = skuMapper.confirm(item.getSkuId(), item.getQuantity());
            if (deducted == 0) {
                throw new BizException(ErrorCode.SYSTEM_ERROR,
                        "库存扣减失败，SKU " + item.getSkuId() + " 的 locked 库存不足");
            }
            Sku sku = skuMapper.selectById(item.getSkuId());
            if (sku != null) {
                productMapper.incrementSales(sku.getProductId(), item.getQuantity());
            }
        }
        return true;
    }

    /**
     * 兜底释放过期预占，避免下单方崩溃后库存永久 locked。
     */
    /**
     * 退款回补库存（BR-28）。
     *
     * <p>退款成功后把已扣减的库存加回可售数量。按预占明细逐 SKU 回补，
     * 以实际退款成功的明细为准。幂等：同一订单重复调用时，只在预占状态为
     * CONFIRMED 时回补一次，回补后置为 REFUNDED，避免重复加库存。
     */
    public void refundRestock(String orderId) {
        transactionTemplate.executeWithoutResult(status -> {
            StockReservation reservation = reservationMapper.selectByOrderId(orderId);
            if (reservation == null) {
                throw new BizException(ErrorCode.RESERVATION_NOT_FOUND, "预占记录不存在");
            }
            // 只有已确认扣减的预占需要回补
            if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
                return;
            }
            // 先原子地把状态从 CONFIRMED 改成 REFUNDED，成功者才回补，保证幂等
            int updated = reservationMapper.updateStatus(orderId, ReservationStatus.CONFIRMED,
                    ReservationStatus.REFUNDED, LocalDateTime.now());
            if (updated == 0) {
                return;
            }
            List<StockReservationItem> items = reservationMapper.selectItems(reservation.getReservationId());
            for (StockReservationItem item : items) {
                skuMapper.refundRestock(item.getSkuId(), item.getQuantity());
            }
        });
    }

    @Scheduled(fixedDelayString = "${product.reservation.scan-interval-ms:60000}", initialDelayString = "30000")
    public void releaseExpiredReservations() {
        List<String> orderIds = reservationMapper.selectExpiredOrderIds(LocalDateTime.now(), 100);
        for (String orderId : orderIds) {
            try {
                release(orderId);
                log.info("过期预占已释放, orderId={}", orderId);
            } catch (BizException e) {
                log.warn("过期预占释放失败, orderId={}, code={}, message={}",
                        orderId, e.getCode(), e.getMessage());
            } catch (Exception e) {
                log.error("过期预占释放异常, orderId={}", orderId, e);
            }
        }
    }

    private ReserveStockResponse replay(StockReservation reservation) {
        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            throw new BizException(ErrorCode.RESERVATION_NOT_FOUND, "预占记录已释放，不能重复预占");
        }
        List<ReserveItemResponse> items = reservationMapper.selectItems(reservation.getReservationId())
                .stream()
                .map(item -> new ReserveItemResponse(item.getSkuId(), item.getQuantity()))
                .toList();
        return new ReserveStockResponse(reservation.getReservationId(), items);
    }

    private static Map<String, Integer> normalizeItems(List<ReserveItem> rawItems) {
        if (rawItems == null || rawItems.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预占明细不能为空");
        }
        if (rawItems.size() > MAX_LINES) {
            throw new BizException(ErrorCode.PARAM_INVALID, "一次最多预占 " + MAX_LINES + " 个 SKU");
        }
        Map<String, Integer> merged = new TreeMap<>();
        for (ReserveItem item : rawItems) {
            if (item == null || item.getSkuId() == null || item.getSkuId().isBlank()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "skuId 不能为空");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "预占数量必须大于 0");
            }
            int total = merged.merge(item.getSkuId().trim(), item.getQuantity(), Integer::sum);
            if (total > 999) {
                throw new BizException(ErrorCode.PARAM_INVALID, "单个 SKU 最多预占 999 件");
            }
        }
        return merged;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 不能为空");
        }
        return value.trim();
    }
}