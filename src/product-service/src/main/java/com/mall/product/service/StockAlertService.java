package com.mall.product.service;

import com.mall.product.common.BizException;
import com.mall.product.common.ErrorCode;
import com.mall.product.dto.response.LowStockAlertResponse;
import com.mall.product.entity.Sku;
import com.mall.product.mapper.InventorySettingMapper;
import com.mall.product.mapper.ProductMapper;
import com.mall.product.mapper.SkuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StockAlertService {

    private static final int DEFAULT_THRESHOLD = 5;

    private final InventorySettingMapper settingMapper;
    private final SkuMapper skuMapper;
    private final ProductMapper productMapper;

    public int globalThreshold() {
        Integer threshold = settingMapper.selectLowStockThreshold();
        return threshold == null ? DEFAULT_THRESHOLD : threshold;
    }

    @Transactional
    public int updateGlobalThreshold(int threshold) {
        validateThreshold(threshold);
        if (settingMapper.updateLowStockThreshold(threshold) == 0) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "低库存阈值配置不存在");
        }
        return threshold;
    }

    @Transactional
    public int updateSkuThreshold(String skuId, String sellerId, Integer threshold) {
        validateThreshold(threshold);
        Sku sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND, "SKU 不存在");
        }
        var product = productMapper.selectById(sku.getProductId());
        if (product == null || (sellerId != null && !sellerId.equals(product.getSellerId()))) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权修改该 SKU 的阈值");
        }
        if (skuMapper.updateLowStockThreshold(skuId, threshold) != 1) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "SKU 阈值更新失败");
        }        return threshold;
    }

    public List<LowStockAlertResponse> alerts(String sellerId, boolean admin) {
        if (!admin && (sellerId == null || sellerId.isBlank())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或用户信息缺失");
        }
        return skuMapper.selectLowStockAlerts(admin ? null : sellerId, globalThreshold());
    }

    private static void validateThreshold(Integer threshold) {
        if (threshold == null || threshold < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "低库存阈值不能为负数");
        }
    }
}



