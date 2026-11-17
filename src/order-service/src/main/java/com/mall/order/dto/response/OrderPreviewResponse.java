package com.mall.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 下单预览响应（#135）：按店铺分组返回商品明细与金额。
 *
 * <p>前端不得自行计算应付金额，一律以本接口返回为准。
 * 本期 payAmount = totalAmount（运费与优惠在 #136 接入计价链）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderPreviewResponse {

    /** 商品总额，单位分 */
    private Long totalAmount;
    /** 应付合计，单位分 */
    private Long payAmount;
    /** 参与结算的店铺数 */
    private Integer shopCount;
    /** 按店铺分组的预览明细 */
    private List<ShopPreview> shops;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShopPreview {
        private String sellerId;
        /** 店铺名；user-service 不可用时回退为卖家ID，不阻塞预览 */
        private String shopName;
        /** 该店商品小计，单位分 */
        private Long totalAmount;
        private List<PreviewItem> items;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreviewItem {
        private String cartItemId;
        private String productId;
        private String skuId;
        private String productTitle;
        private String specName;
        private String mainImage;
        /** 下单瞬间单价快照，单位分 */
        private Long price;
        private Integer quantity;
        /** 小计 = price × quantity，单位分 */
        private Long subtotal;
    }
}