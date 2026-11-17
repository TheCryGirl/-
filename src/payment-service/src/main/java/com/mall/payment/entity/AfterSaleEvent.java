package com.mall.payment.entity;

import com.mall.payment.common.AfterSaleStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 售后进度时间线事件（#143），与 after_sale_event 表一一对应。
 * 每次状态变更写一条；仲裁期间双方补充材料记为 ARBITRATING -> ARBITRATING。
 * 买家、卖家、管理员均可按时间正序查看。
 */
@Data
public class AfterSaleEvent {

    private Long id;
    private String ticketId;
    private AfterSaleStatus fromStatus;
    private AfterSaleStatus toStatus;
    private String operatorId;
    private String remark;
    private LocalDateTime createdAt;
}