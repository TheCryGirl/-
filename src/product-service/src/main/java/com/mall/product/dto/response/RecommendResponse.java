package com.mall.product.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 个性化推荐响应（#141）。
 *
 * <p>推荐理由随结果一起返回，便于前端解释「为什么推荐这些」，也方便排查问题：
 * {@code basis} 是按行为归纳出的分类，冷启动时是「全站热销」。
 */
@Data
public class RecommendResponse {

    /** 场景：HOME 首页猜你喜欢 / DETAIL 详情页相似商品 */
    private String scene;
    /** 是否冷启动（没有可用的浏览与购买行为） */
    private Boolean coldStart;
    /** 推荐依据，例如 ["数码电子", "电脑配件"] */
    private List<String> basis;
    private List<ProductListItem> items;
}
