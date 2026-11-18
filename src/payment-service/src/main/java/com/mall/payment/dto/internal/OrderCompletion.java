package com.mall.payment.dto.internal;

import lombok.Data;

/**
 * 结算候选子订单（#144 第二块）：跨库只读 order-service 的 order 表取回的最小投影。
 */
@Data
public class OrderCompletion {

    private String orderId;
    private String sellerId;
    private Long payAmount;
}
