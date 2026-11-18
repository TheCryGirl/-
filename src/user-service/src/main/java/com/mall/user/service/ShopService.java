package com.mall.user.service;

import com.mall.user.dto.request.ShopApplyRequest;
import com.mall.user.dto.request.ShopAuditRequest;
import com.mall.user.dto.request.ShopRequest;
import com.mall.user.dto.request.ShopStatusRequest;
import com.mall.user.dto.response.PageResponse;
import com.mall.user.dto.response.ShopResponse;

/**
 * 店铺资料、入驻审核与营业状态服务。
 */
public interface ShopService {

    /** 卖家查看自己的店铺；未提交过申请时返回「未申请」视图，不再自动建店 */
    ShopResponse getMine(String sellerId);

    /** 卖家维护店铺名称、头像、简介与联系方式（仅审核通过的店铺） */
    ShopResponse updateMine(String sellerId, ShopRequest request);

    /** 卖家提交 / 重新提交入驻申请，提交后进入待审核且不能发布商品 */
    ShopResponse apply(String sellerId, ShopApplyRequest request);

    /** 管理员审核入驻申请，驳回必须带理由，通过时可登记保证金金额 */
    ShopResponse audit(String operatorId, String shopId, ShopAuditRequest request);

    /** 管理员分页查询店铺，可按营业状态、审核状态与关键字筛选 */
    PageResponse<ShopResponse> list(String keyword, String status, String applyStatus, int page, int size);

    /** 管理员停用 / 恢复店铺，并联动商品可见性 */
    ShopResponse updateStatus(String operatorId, String shopId, ShopStatusRequest request);

    /** 按店铺ID查询店铺，供店铺主页公开访问 */
    ShopResponse getById(String shopId);

    /** 按卖家查店铺，供 product-service、order-service 内部校验营业状态 */
    ShopResponse getBySellerId(String sellerId);
}
