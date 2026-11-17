package com.mall.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 下单预览响应（#136 接入运费与优惠后的版本）。
 *
 * <p>按店铺分组返回商品明细与金额，前端**不得自行计算**，一律以本接口为准。
 * 计价链顺序：商品小计 → 店铺/平台满减、满折 → 优惠券抵扣 → 加运费 → 应付合计；
 * 因此 {@code payAmount = totalAmount - discountAmount + freightAmount}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderPreviewResponse {

    /** 商品总额，单位分 */
    private Long totalAmount;
    /** 运费合计，单位分 */
    private Long freightAmount;
    /** 优惠抵扣合计，单位分 */
    private Long discountAmount;
    /** 应付合计 = 商品总额 - 优惠 + 运费，单位分 */
    private Long payAmount;
    /** 参与结算的店铺数 */
    private Integer shopCount;
    /** 本次预览使用的收货地区（收货地址的省份）；未选地址时为 null */
    private String region;
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
        /** 该店运费，单位分 */
        private Long freightAmount;
        /** 该店优惠抵扣，单位分 */
        private Long discountAmount;
        /** 该店应付 = 小计 - 优惠 + 运费，单位分 */
        private Long payAmount;
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
