package com.mall.order.entity;

import com.mall.order.common.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 主订单（#135 多店铺合并结算）。
 *
 * <p>跨店勾选一次支付时，生成一条主订单与若干店铺子订单（{@code order.main_order_id} 指向这里）。
 * 主订单只承载「支付聚合」语义：金额、支付状态；发货、退款仍按子订单独立流转。
 * 历史单店订单不产生主订单（子订单 main_order_id 为 null），按原单店逻辑兼容。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MainOrder {
    private String mainOrderId;
    private String mainOrderNo;
    private String buyerId;
    /** PENDING_PAYMENT / PAID / CANCELLED；子订单独立流转不影响主订单 */
    private OrderStatus status;
    /** 应付合计，单位分，等于各子订单 pay_amount 之和 */
    private Long payAmount;
    private LocalDateTime paidAt;
    private LocalDateTime expireAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private Integer isDeleted;
}