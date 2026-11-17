package com.mall.user.dto.response;

import lombok.Data;

/** 「关注的店铺」列表项（#142）。 */
@Data
public class FollowedShopResponse {

    private String shopId;
    private String sellerId;
    private String name;
    private String logo;
    private String description;
    private String status;
    private String followedAt;
}