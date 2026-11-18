package com.mall.payment.dto.response;

import com.mall.payment.entity.Settlement;
import com.mall.payment.entity.SettlementItem;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 结算单视图（#144 第二块）：结算单 + 明细，金额单位分。
 */
@Data
public class SettlementResponse {

    private String settlementId;
    private String sellerId;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private Long grossAmount;
    private Long commissionAmount;
    private Long netAmount;
    private String status;
    private LocalDateTime createdAt;
    private boolean payable;
    private Payout payout;
    private List<Adjustment> adjustments = new ArrayList<>();

    public record Payout(String reference, String operatorId, long amount, LocalDateTime paidAt, String remark) {}
    public record Adjustment(String sourceSettlementId, String orderId, long amount) {}
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {

        private String orderId;
        private Long grossAmount;
        private Long commissionAmount;
    }

    public static SettlementResponse of(Settlement settlement, List<SettlementItem> items) {
        SettlementResponse response = new SettlementResponse();
        response.setSettlementId(settlement.getSettlementId());
        response.setSellerId(settlement.getSellerId());
        response.setPeriodStart(settlement.getPeriodStart());
        response.setPeriodEnd(settlement.getPeriodEnd());
        response.setGrossAmount(settlement.getGrossAmount());
        response.setCommissionAmount(settlement.getCommissionAmount());
        response.setNetAmount(settlement.getNetAmount());
        response.setStatus(settlement.getStatus());
        response.setCreatedAt(settlement.getCreatedAt());
        for (SettlementItem item : items) {
            Item view = new Item();
            view.setOrderId(item.getOrderId());
            view.setGrossAmount(item.getGrossAmount());
            view.setCommissionAmount(item.getCommissionAmount());
            response.getItems().add(view);
        }
        return response;
    }
}
