package com.mall.order.service;

import com.mall.order.client.ProductClient;
import com.mall.order.client.dto.ReleaseResponseDTO;
import com.mall.order.common.BizException;
import com.mall.order.common.ErrorCode;
import com.mall.order.common.Result;
import com.mall.order.entity.CompensationStatus;
import com.mall.order.entity.OrderCompensation;
import com.mall.order.mapper.CompensationMapper;
import com.mall.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 取消/超时后的库存释放补偿。
 *
 * <p>先尝试同步释放；失败时写 compensation 表，由定时任务指数退避重试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompensationService {

    private final CompensationMapper compensationMapper;
    private final OrderMapper orderMapper;
    private final ProductClient productClient;

    @Value("${order.internal-token:}")
    private String internalToken;

    @Value("${order.compensation.max-retry:8}")
    private int maxRetry;

    public void enqueue(String orderId) {
        if (compensationMapper.selectByOrderId(orderId) != null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        try {
            compensationMapper.insert(OrderCompensation.builder()
                    .orderId(orderId)
                    .status(CompensationStatus.PENDING)
                    .retryCount(0)
                    .nextRetryAt(now)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
            log.warn("已创建库存释放补偿任务, orderId={}", orderId);
        } catch (DataIntegrityViolationException e) {
            log.debug("补偿任务已存在, orderId={}", orderId);
        }
    }

    public void processDue() {
        List<OrderCompensation> tasks = compensationMapper.selectDue(LocalDateTime.now(), 50);
        for (OrderCompensation task : tasks) {
            process(task);
        }
    }

    private void process(OrderCompensation task) {
        try {
            Result<ReleaseResponseDTO> result = productClient.release(internalToken,
                    new ProductClient.ReleaseRequest(task.getOrderId()));
            if (result == null) {
                throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "product-service 无响应");
            }
            // 30004 表示没有预占记录，订单已经不需要释放，视为完成。
            if (result.getCode() == ErrorCode.SUCCESS || result.getCode() == ErrorCode.RESERVATION_NOT_FOUND) {
                compensationMapper.markDone(task.getId(), LocalDateTime.now());
                orderMapper.markInventoryReleased(task.getOrderId());
                log.info("库存释放补偿完成, orderId={}, code={}", task.getOrderId(), result.getCode());
                return;
            }
            retry(task, result.getMessage());
        } catch (BizException e) {
            retry(task, e.getMessage());
        } catch (Exception e) {
            retry(task, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void retry(OrderCompensation task, String error) {
        LocalDateTime now = LocalDateTime.now();
        int nextCount = task.getRetryCount() + 1;
        if (nextCount >= Math.max(1, maxRetry)) {
            compensationMapper.markDead(task.getId(), truncate(error), now);
            log.error("库存释放补偿达到上限, orderId={}, error={}", task.getOrderId(), error);
            return;
        }
        long delaySeconds = Math.min(300L, 30L << Math.min(4, nextCount - 1));
        compensationMapper.scheduleRetry(task.getId(), nextCount, now.plusSeconds(delaySeconds),
                truncate(error), now);
        log.warn("库存释放补偿重试安排, orderId={}, retry={}, delay={}s, error={}",
                task.getOrderId(), nextCount, delaySeconds, error);
    }


    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 512 ? value : value.substring(0, 512);
    }
}

