package com.mall.order.entity;

import com.mall.order.common.EvaluationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 订单评价（任务 #57）。一个订单只能评价一次。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvaluation {

    private Long id;
    private String evaluationId;
    private String orderId;
    private String buyerId;
    private String sellerId;
    private Integer rating;
    private String content;
    /** 逗号分隔的图片地址，最多 3 张 */
    private String images;
    private Integer anonymous;
    private EvaluationStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
