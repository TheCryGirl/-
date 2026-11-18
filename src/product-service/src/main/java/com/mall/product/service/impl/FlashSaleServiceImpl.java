package com.mall.product.service.impl;

import com.mall.product.common.BizException;
import com.mall.product.common.ErrorCode;
import com.mall.product.common.IdGenerator;
import com.mall.product.dto.response.FlashSaleReserveResponse;
import com.mall.product.dto.response.FlashSaleResponse;
import com.mall.product.entity.FlashSale;
import com.mall.product.entity.FlashSaleOrder;
import com.mall.product.entity.Product;
import com.mall.product.mapper.FlashSaleMapper;
import com.mall.product.mapper.FlashSaleOrderMapper;
import com.mall.product.mapper.ProductMapper;
import com.mall.product.service.FlashSaleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 秒杀实现（#140）。
 *
 * <p>并发安全：扣库存是条件 UPDATE（库存充足且在活动时间内才生效），绝不先查再减；
 * 限购靠 flash_sale_order 的 uk_sale_user 唯一键，重复下单整体回滚（含已扣库存）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlashSaleServiceImpl implements FlashSaleService {

    private final FlashSaleMapper flashSaleMapper;
    private final FlashSaleOrderMapper flashSaleOrderMapper;
    private final ProductMapper productMapper;

    @Override
    public List<FlashSaleResponse> ongoing() {
        List<FlashSale> sales = flashSaleMapper.selectOngoing(LocalDateTime.now());
        if (sales.isEmpty()) {
            return List.of();
        }
        Map<String, Product> products = sales.stream()
                .map(FlashSale::getProductId)
                .distinct()
                .map(productMapper::selectById)
                .filter(product -> product != null)
                .collect(Collectors.toMap(Product::getProductId, Function.identity()));
        List<FlashSaleResponse> result = new ArrayList<>(sales.size());
        for (FlashSale sale : sales) {
            Product product = products.get(sale.getProductId());
            result.add(FlashSaleResponse.from(sale,
                    product == null ? null : product.getTitle(),
                    product == null ? null : product.getMainImage()));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlashSaleReserveResponse reserve(String userId, String saleId, int quantity) {
        FlashSale sale = flashSaleMapper.selectById(saleId);
        if (sale == null) {
            throw new BizException(ErrorCode.FLASH_SALE_NOT_FOUND, "秒杀活动不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (sale.getStartAt() != null && sale.getStartAt().isAfter(now)) {
            throw new BizException(ErrorCode.FLASH_SALE_SOLD_OUT, "秒杀活动未开始");
        }
        if (sale.getEndAt() != null && sale.getEndAt().isBefore(now)) {
            throw new BizException(ErrorCode.FLASH_SALE_SOLD_OUT, "秒杀活动已结束");
        }
        if (quantity > sale.getLimitPerUser()) {
            throw new BizException(ErrorCode.FLASH_SALE_LIMIT_EXCEEDED,
                    "超过限购件数，每人限购 " + sale.getLimitPerUser() + " 件");
        }

        int decremented = flashSaleMapper.decrementStock(saleId, quantity, now);
        if (decremented == 0) {
            throw new BizException(ErrorCode.FLASH_SALE_SOLD_OUT, "秒杀库存不足或活动已结束");
        }

        FlashSaleOrder order = new FlashSaleOrder();
        order.setSaleOrderId(IdGenerator.flashSaleOrderId());
        order.setSaleId(saleId);
        order.setUserId(userId);
        order.setQuantity(quantity);
        try {
            flashSaleOrderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // 同事务回滚已扣的库存，再给用户明确提示
            throw new BizException(ErrorCode.FLASH_SALE_LIMIT_EXCEEDED, "您已参与过该秒杀，不能重复购买");
        }
        log.info("秒杀下单成功, saleId={}, userId={}, quantity={}, saleOrderId={}",
                saleId, userId, quantity, order.getSaleOrderId());
        return FlashSaleReserveResponse.builder()
                .saleOrderId(order.getSaleOrderId())
                .saleId(saleId)
                .salePrice(sale.getSalePrice())
                .quantity(quantity)
                .build();
    }
}
