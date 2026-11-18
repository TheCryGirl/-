package com.mall.user.dto.response;

import com.mall.user.common.ShopApplyStatus;
import com.mall.user.common.ShopStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

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
    /** 当前生效的运费模板ID（#136）；未配置时为 null */
    private String freightTemplateId;
    /** 入驻审核状态，null 表示还没提交过申请（#144） */
    private ShopApplyStatus applyStatus;
    /** 最近一次驳回理由，卖家重新提交后仍保留，便于对照 */
    private String rejectReason;
    /** 保证金金额（分），管理员审核时登记；0 表示尚未缴纳 */
    private Long depositAmount;
    /** 保证金是否已缴纳：金额大于 0 视为已缴；仅作记录，不参与发布拦截 */
    private Boolean depositPaid;
    /** 是否允许发布商品：入驻审核通过 */
    private Boolean publishable;
    private String subjectType;
    private String licenseNo;
    private List<String> materials;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
