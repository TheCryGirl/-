package com.mall.order.entity;

import com.mall.order.common.IdempotencyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderIdempotency {
    private Long id;
    private String idemKey;
    private String buyerId;
    private String requestHash;
    private String orderId;
    private IdempotencyStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
