package com.mall.payment.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 售后凭证附件（#143），与 after_sale_attachment 表一一对应。
 * 单次申请/补充最多 3 张；url 为站内相对路径 /after-sale/{uuid}.{ext}。
 */
@Data
public class AfterSaleAttachment {

    private Long id;
    private String ticketId;
    private String url;
    private String uploadedBy;
    private String uploaderRole;
    private LocalDateTime createdAt;
}