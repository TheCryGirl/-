package com.mall.order.client.dto;

import lombok.Data;

/**
 * 店铺简要信息（#49），下单前用它兜底校验店铺营业状态。
 */
@Data
public class ShopBriefDTO {

    private String shopId;
    private String sellerId;
    private String name;
    /** ACTIVE / SUSPENDED */
    private String status;
}
