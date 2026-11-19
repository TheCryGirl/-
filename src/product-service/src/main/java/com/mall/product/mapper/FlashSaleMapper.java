package com.mall.product.mapper;

import com.mall.product.entity.FlashSale;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀活动 Mapper（#140）。
 */
public interface FlashSaleMapper {

    int insert(FlashSale flashSale);

    FlashSale selectById(@Param("saleId") String saleId);

    /** 进行中的活动：start_at <= now <= end_at 且未删除 */
    List<FlashSale> selectOngoing(@Param("now") LocalDateTime now);

    /** 条件扣减活动库存：库存充足且在活动时间内才生效，影响行数为 0 即失败 */
    int decrementStock(@Param("saleId") String saleId, @Param("quantity") int quantity,
                       @Param("now") LocalDateTime now);
    FlashSale lockById(@Param("saleId") String saleId);

    int restoreStock(@Param("saleId") String saleId, @Param("quantity") int quantity);
}
