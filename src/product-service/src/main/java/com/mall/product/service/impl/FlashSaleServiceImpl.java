package com.mall.product.service.impl;

import com.mall.product.common.BizException;
import com.mall.product.common.ErrorCode;
import com.mall.product.common.IdGenerator;
import com.mall.product.common.ProductStatus;
import com.mall.product.dto.request.ReserveStockRequest;
import com.mall.product.mapper.SkuMapper;
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
    private final SkuMapper skuMapper;

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
    public Quote quote(String buyerId, String saleId, int quantity) {
        FlashSale sale = flashSaleMapper.selectById(saleId);
        validate(sale, buyerId, quantity);
        var sku = skuMapper.selectById(sale.getSkuId());
        if (sku.getStockAvailable() < quantity) {
            throw new BizException(ErrorCode.STOCK_INSUFFICIENT, "商品库存不足");
        }
        return new Quote(sale.getProductId(), sale.getSkuId(), sale.getSalePrice());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(propagation =
            org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void reserveForOrder(String orderId, ReserveStockRequest.FlashSale flash, String skuId, int quantity) {
        FlashSale sale = flashSaleMapper.lockById(flash.saleId());
        LocalDateTime now = validate(sale, flash.buyerId(), quantity);
        if (!sale.getSkuId().equals(skuId) || sale.getSalePrice() != flash.unitPrice()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "秒杀商品或价格已变化，请重新预览");
        }
        if (flashSaleMapper.decrementStock(sale.getSaleId(), quantity, now) != 1) {
            throw new BizException(ErrorCode.FLASH_SALE_SOLD_OUT, "秒杀库存不足或活动已结束");
        }
        FlashSaleOrder row = new FlashSaleOrder();
        row.setSaleOrderId(IdGenerator.flashSaleOrderId());
        row.setSaleId(sale.getSaleId());
        row.setUserId(flash.buyerId());
        row.setQuantity(quantity);
        row.setOrderId(orderId);
        row.setUnitPrice(flash.unitPrice());
        try {
            flashSaleOrderMapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.FLASH_SALE_LIMIT_EXCEEDED, "您已参与该秒杀，不能重复购买");
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(propagation =
            org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void releaseForOrder(String orderId) {
        FlashSaleOrder row = flashSaleOrderMapper.selectByOrderId(orderId);
        if (row == null) return;
        if (flashSaleMapper.restoreStock(row.getSaleId(), row.getQuantity()) != 1
                || flashSaleOrderMapper.deleteByOrderId(orderId) != 1) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "秒杀库存回补失败");
        }
    }

    private LocalDateTime validate(FlashSale sale, String buyerId, int quantity) {
        if (buyerId == null || buyerId.isBlank() || quantity <= 0 || quantity > 999) {
            throw new BizException(ErrorCode.PARAM_INVALID, "买家或秒杀数量不合法");
        }
        if (sale == null) {
            throw new BizException(ErrorCode.FLASH_SALE_NOT_FOUND, "秒杀活动不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (sale.getStartAt().isAfter(now) || !sale.getEndAt().isAfter(now)) {
            throw new BizException(ErrorCode.FLASH_SALE_SOLD_OUT, "秒杀活动未开始或已结束");
        }
        if (quantity > sale.getLimitPerUser()
                || flashSaleOrderMapper.selectBySaleAndUser(sale.getSaleId(), buyerId) != null) {
            throw new BizException(ErrorCode.FLASH_SALE_LIMIT_EXCEEDED, "超过限购件数或已参与该秒杀");
        }
        if (sale.getSaleStock() < quantity) {
            throw new BizException(ErrorCode.FLASH_SALE_SOLD_OUT, "秒杀库存不足");
        }
        var product = productMapper.selectById(sale.getProductId());
        var sku = skuMapper.selectById(sale.getSkuId());
        if (product == null || product.getStatus() != ProductStatus.ON_SALE || sku == null
                || !sale.getProductId().equals(sku.getProductId()) || sale.getSalePrice() <= 0) {
            throw new BizException(ErrorCode.PRODUCT_OFF_SHELF, "秒杀商品不可购买");
        }
        if (buyerId.equals(product.getSellerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "不能购买自己发布的商品");
        }
        return now;
    }
}
