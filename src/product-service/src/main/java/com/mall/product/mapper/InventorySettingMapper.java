package com.mall.product.mapper;

import org.apache.ibatis.annotations.Param;

public interface InventorySettingMapper {
    Integer selectLowStockThreshold();
    int updateLowStockThreshold(@Param("threshold") int threshold);
}
