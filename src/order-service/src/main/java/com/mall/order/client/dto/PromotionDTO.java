package com.mall.order.client.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 满减/满折活动信息（#140，payment-service 内部接口返回）。
 * scope=PLATFORM 平台 / SHOP 店铺；type=FULL_REDUCE 满减 / FULL_DISCOUNT 满折。
 */
@Data
public class PromotionDTO {

    private String promotionId;
    private String scope;
    private String shopId;
    private String type;
    private Long threshold;
    private Long value;
    private Boolean stackable;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endAt;
    private String status;
}
