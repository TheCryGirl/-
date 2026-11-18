package com.mall.user.entity;

import com.mall.user.common.ShopApplyStatus;
import com.mall.user.common.ShopStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 卖家店铺实体，与 shop 表一一对应。
 */
@Data
public class Shop {

    private String shopId;
    private String sellerId;
    private String name;
    private String logo;
    private String description;
    private String contactPhone;
    private String contactWechat;
    private ShopStatus status;
    /** 当前生效的运费模板ID（#136） */
    private String freightTemplateId;
    /** 入驻审核状态（#144），null 表示还没提交过申请 */
    private ShopApplyStatus applyStatus;
    /** 保证金金额（分），管理员审核时登记；0 表示尚未缴纳，仅作记录不拦截发布 */
    private Long depositAmount;
    /** 主体类型、证照编号与资质材料，JSON 文本 */
    private String qualificationJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isDeleted;
}
