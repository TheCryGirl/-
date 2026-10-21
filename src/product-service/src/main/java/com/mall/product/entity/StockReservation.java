package com.mall.product.entity;

import com.mall.product.common.ReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 预占单头。order_id 唯一，保证同一订单只创建一次预占。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReservation {
    private String reservationId;
    private String orderId;
    private ReservationStatus status;
    private LocalDateTime expireAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
