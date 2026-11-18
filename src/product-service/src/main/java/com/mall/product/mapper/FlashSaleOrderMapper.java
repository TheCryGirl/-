package com.mall.product.mapper;

import com.mall.product.entity.FlashSaleOrder;
import org.apache.ibatis.annotations.Param;

/**
 * 秒杀下单记录 Mapper（#140）。限购靠 uk_sale_user 唯一键兜底。
 */
public interface FlashSaleOrderMapper {

    int insert(FlashSaleOrder order);

    FlashSaleOrder selectBySaleAndUser(@Param("saleId") String saleId, @Param("userId") String userId);
}
