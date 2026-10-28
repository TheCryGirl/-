package com.mall.user.service;

import com.mall.user.client.ProductClient;
import com.mall.user.client.dto.ProductBriefDTO;
import com.mall.user.common.BizException;
import com.mall.user.common.ErrorCode;
import com.mall.user.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 商品信息查询封装。
 *
 * <p>收藏夹、浏览足迹只存 productId，展示时按《05-接口契约》10.1 调 product-service
 * 批量取标题、图片、价格与状态，不跨库 JOIN。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductInfoService {

    /** 单次批量查询上限，与 product-service 的 batch 接口保持一致 */
    private static final int MAX_BATCH = 100;

    private final ProductClient productClient;

    /**
     * 批量取商品信息，返回 productId → 商品。查不到的商品不会出现在结果里。
     *
     * @throws BizException 商品服务不可用时抛出，避免把"服务挂了"误报成"商品已下架"
     */
    public Map<String, ProductBriefDTO> load(Collection<String> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String id : productIds) {
            if (id != null && !id.isBlank()) {
                ids.add(id.trim());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        if (ids.size() > MAX_BATCH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "一次最多查询 " + MAX_BATCH + " 个商品");
        }

        Result<List<ProductBriefDTO>> result;
        try {
            result = productClient.batchGet(String.join(",", ids));
        } catch (Exception e) {
            log.error("调用 product-service 批量查询商品失败, ids={}", ids, e);
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "商品服务暂不可用，请稍后重试");
        }
        if (result == null || result.getCode() != ErrorCode.SUCCESS || result.getData() == null) {
            log.warn("product-service 批量查询返回失败, code={}, message={}",
                    result == null ? null : result.getCode(), result == null ? null : result.getMessage());
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "商品服务暂不可用，请稍后重试");
        }
        Map<String, ProductBriefDTO> map = new LinkedHashMap<>();
        for (ProductBriefDTO item : result.getData()) {
            if (item != null && item.getProductId() != null) {
                map.put(item.getProductId(), item);
            }
        }
        return map;
    }

    /**
     * 商品是否可购买。不可购买时返回给用户看的原因，可购买返回 null。
     */
    public static String invalidReason(ProductBriefDTO product) {
        if (product == null) {
            return "商品已删除或不存在";
        }
        String status = product.getStatus() == null ? "" : product.getStatus().toUpperCase();
        return switch (status) {
            case "ON_SALE" -> null;
            case "PENDING" -> "商品待审核，暂不可购买";
            case "OFF_SHELF" -> "商品已下架";
            case "REJECTED" -> "商品审核未通过";
            case "SUSPENDED" -> "店铺已停用";
            default -> "商品暂不可购买";
        };
    }
}
