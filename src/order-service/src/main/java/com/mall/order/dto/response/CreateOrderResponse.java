package com.mall.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderResponse {
    private String orderId;
    private String orderNo;
    private Long payAmount;
    private String expireAt;
    /** 主订单ID（#135）：跨店结算时返回；单店结算为 null，不产生多余层级 */
    private String mainOrderId;
    /** 主订单号（#135）：跨店结算时返回；单店结算为 null */
    private String mainOrderNo;
    /** 店铺子订单（#135）：跨店结算时按店铺返回各子订单号；单店结算为 null */
    private List<SubOrderBrief> subOrders;

    public CreateOrderResponse(String orderId, String orderNo, Long payAmount, String expireAt) {
        this(orderId, orderNo, payAmount, expireAt, null, null, null);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubOrderBrief {
        private String orderId;
        private String orderNo;
        private String sellerId;
        private Long payAmount;
    }
}
