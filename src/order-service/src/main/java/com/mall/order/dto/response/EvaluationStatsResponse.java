package com.mall.order.dto.response;

import lombok.Data;

/** 商品评价统计（任务 #57）：用于商品详情页展示平均分。 */
@Data
public class EvaluationStatsResponse {

    private long total;
    private double average;
}
