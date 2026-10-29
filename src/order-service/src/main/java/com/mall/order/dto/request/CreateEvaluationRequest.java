package com.mall.order.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 买家发表订单评价（任务 #57）。 */
@Data
public class CreateEvaluationRequest {

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最低 1 星")
    @Max(value = 5, message = "评分最高 5 星")
    private Integer rating;

    @Size(max = 500, message = "评价内容不能超过 500 字")
    private String content;

    @Size(max = 3, message = "最多上传 3 张图片")
    private List<String> images;

    private Boolean anonymous;
}
