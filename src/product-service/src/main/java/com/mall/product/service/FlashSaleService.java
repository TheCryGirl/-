package com.mall.product.service;

import com.mall.product.dto.response.FlashSaleReserveResponse;
import com.mall.product.dto.response.FlashSaleResponse;

import java.util.List;

/**
 * 限时秒杀（#140）：进行中活动列表与秒杀下单。
 */
public interface FlashSaleService {

    /** 进行中的秒杀活动（首页秒杀位），含商品标题/主图与已抢光展示态 */
    List<FlashSaleResponse> ongoing();

    /** 秒杀下单：条件 UPDATE 扣活动库存 + 唯一键限购 */
    FlashSaleReserveResponse reserve(String userId, String saleId, int quantity);
}
