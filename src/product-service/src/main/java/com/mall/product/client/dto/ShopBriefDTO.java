package com.mall.product.client.dto;

import lombok.Data;

/**
 * 店铺简要信息，只关心营业状态。
 */
@Data
public class ShopBriefDTO {

    private String shopId;
    private String sellerId;
    private String name;
    /** ACTIVE / SUSPENDED */
    private String status;
}
