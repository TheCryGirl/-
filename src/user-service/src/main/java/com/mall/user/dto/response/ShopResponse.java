package com.mall.user.dto.response;

import com.mall.user.common.ShopStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 店铺资料出参。管理端店铺列表与卖家店铺页共用。
 */
@Data
public class ShopResponse {

    private String shopId;
    private String sellerId;
    private String name;
    private String logo;
    private String description;
    private String contactPhone;
    private String contactWechat;
    private ShopStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
