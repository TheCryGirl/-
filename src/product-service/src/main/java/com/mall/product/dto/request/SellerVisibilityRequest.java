package com.mall.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 店铺营业状态同步入参（#49）。
 *
 * <p>由 user-service 在管理员停用 / 恢复店铺后调用，把该卖家的在售商品
 * 在 ON_SALE 与 SUSPENDED 之间迁移。
 */
@Data
public class SellerVisibilityRequest {

    @NotBlank(message = "卖家 ID 不能为空")
    private String sellerId;

    /** true 表示恢复可见，false 表示停用 */
    @NotNull(message = "可见性不能为空")
    private Boolean visible;
}
