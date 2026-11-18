package com.mall.product.dto.response;

import com.mall.product.entity.FlashSale;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀活动信息（#140）。soldOut 为展示态：剩余库存为 0 即已抢光。
 * title/mainImage 来自商品表，商品缺失时为 null（前端给占位）。
 */
@Data
@Builder
public class FlashSaleResponse {

    private String saleId;
    private String productId;
    private String skuId;
    private Long salePrice;
    private Integer saleStock;
    private Integer limitPerUser;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String title;
    private String mainImage;
    private boolean soldOut;

    public static FlashSaleResponse from(FlashSale sale, String title, String mainImage) {
        return FlashSaleResponse.builder()
                .saleId(sale.getSaleId())
                .productId(sale.getProductId())
                .skuId(sale.getSkuId())
                .salePrice(sale.getSalePrice())
                .saleStock(sale.getSaleStock())
                .limitPerUser(sale.getLimitPerUser())
                .startAt(sale.getStartAt())
                .endAt(sale.getEndAt())
                .title(title)
                .mainImage(mainImage)
                .soldOut(sale.getSaleStock() == null || sale.getSaleStock() <= 0)
                .build();
    }
}
