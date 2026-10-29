package com.mall.order.dto.response;

import lombok.Data;

import java.util.List;

/** 订单评价展示数据（任务 #57）。 */
@Data
public class EvaluationResponse {

    private String evaluationId;
    private String orderId;
    /** 仅管理端返回真实买家 ID，公开列表不返回。 */
    private String buyerId;
    private String buyerLabel;
    private Integer rating;
    private String content;
    private List<String> images;
    private Boolean anonymous;
    private String status;
    private String createdAt;
}
