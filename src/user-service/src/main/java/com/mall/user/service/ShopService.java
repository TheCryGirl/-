package com.mall.user.service;

import com.mall.user.dto.request.ShopRequest;
import com.mall.user.dto.request.ShopStatusRequest;
import com.mall.user.dto.response.PageResponse;
import com.mall.user.dto.response.ShopResponse;

/**
 * 店铺资料与营业状态服务。
 */
public interface ShopService {

    /** 卖家查看自己的店铺，没有则按昵称初始化一个正常营业的店铺 */
    ShopResponse getMine(String sellerId);

    /** 卖家维护店铺名称、头像、简介与联系方式 */
    ShopResponse updateMine(String sellerId, ShopRequest request);

    /** 管理员分页查询店铺，可按营业状态与关键字筛选 */
    PageResponse<ShopResponse> list(String keyword, String status, int page, int size);

    /** 管理员停用 / 恢复店铺，并联动商品可见性 */
    ShopResponse updateStatus(String operatorId, String shopId, ShopStatusRequest request);

    /** 按卖家查店铺，供 product-service、order-service 内部校验营业状态 */
    ShopResponse getBySellerId(String sellerId);
}
