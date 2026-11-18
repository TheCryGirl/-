package com.mall.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 店铺入驻审核流水，与 shop_apply_log 表一一对应（#144）。
 *
 * <p>只追加不修改，用于追溯「谁在什么时候把店铺从什么状态改到什么状态」。
 */
@Data
public class ShopApplyLog {

    private Long id;
    private String shopId;
    /** 变更前审核状态，首次申请为 null */
    private String fromStatus;
    private String toStatus;
    /** 操作人：卖家提交申请时是卖家 ID，审核时是管理员 ID */
    private String operatorId;
    /** 驳回理由或审核备注 */
    private String remark;
    private LocalDateTime createdAt;
}
