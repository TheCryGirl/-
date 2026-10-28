package com.mall.user.service.impl;

import com.mall.user.client.dto.ProductBriefDTO;
import com.mall.user.common.BizException;
import com.mall.user.common.ErrorCode;
import com.mall.user.common.IdGenerator;
import com.mall.user.dto.response.FavoriteResponse;
import com.mall.user.dto.response.PageResponse;
import com.mall.user.entity.Favorite;
import com.mall.user.mapper.FavoriteMapper;
import com.mall.user.service.FavoriteService;
import com.mall.user.service.ProductInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 收藏夹实现。
 *
 * <p>收藏关系只落 mall_user，商品标题、价格、状态通过 product-service 批量接口取，
 * 不跨库 JOIN。重复收藏靠唯一索引 + DuplicateKeyException 兜底保证幂等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final ProductInfoService productInfoService;

    @Override
    public FavoriteResponse add(String userId, String productId) {
        requireUser(userId);
        String pid = requireProductId(productId);
        Map<String, ProductBriefDTO> products = productInfoService.load(List.of(pid));
        ProductBriefDTO product = products.get(pid);
        if (product == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商品不存在");
        }

        Favorite favorite = favoriteMapper.selectByUserAndProduct(userId, pid);
        if (favorite == null) {
            Favorite created = new Favorite();
            created.setFavoriteId(IdGenerator.favoriteId());
            created.setUserId(userId);
            created.setProductId(pid);
            created.setCreatedAt(LocalDateTime.now());
            try {
                favoriteMapper.insert(created);
                favorite = created;
            } catch (DuplicateKeyException e) {
                // 并发重复收藏：唯一索引挡住后读回已有记录，仍然算成功
                favorite = favoriteMapper.selectByUserAndProduct(userId, pid);
            }
        }
        if (favorite == null) {
            throw new BizException(ErrorCode.FAVORITE_NOT_FOUND, "收藏记录写入失败，请重试");
        }
        log.info("用户收藏商品, userId={}, productId={}", userId, pid);
        return toResponse(favorite, product);
    }

    @Override
    public void remove(String userId, String productId) {
        requireUser(userId);
        String pid = requireProductId(productId);
        favoriteMapper.deleteByUserAndProduct(userId, pid);
        log.info("用户取消收藏, userId={}, productId={}", userId, pid);
    }

    @Override
    public PageResponse<FavoriteResponse> list(String userId, int page, int size) {
        requireUser(userId);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        long total = favoriteMapper.countByUserId(userId);
        List<Favorite> favorites = favoriteMapper.selectPage(userId, (safePage - 1) * safeSize, safeSize);
        if (favorites.isEmpty()) {
            return PageResponse.of(safePage, safeSize, total, List.of());
        }

        List<String> productIds = favorites.stream().map(Favorite::getProductId).toList();
        Map<String, ProductBriefDTO> products = productInfoService.load(productIds);
        List<FavoriteResponse> list = new ArrayList<>(favorites.size());
        for (Favorite favorite : favorites) {
            list.add(toResponse(favorite, products.get(favorite.getProductId())));
        }
        return PageResponse.of(safePage, safeSize, total, list);
    }

    @Override
    public boolean isFavorited(String userId, String productId) {
        requireUser(userId);
        String pid = requireProductId(productId);
        return favoriteMapper.selectByUserAndProduct(userId, pid) != null;
    }

    private FavoriteResponse toResponse(Favorite favorite, ProductBriefDTO product) {
        String reason = ProductInfoService.invalidReason(product);
        FavoriteResponse response = new FavoriteResponse();
        response.setFavoriteId(favorite.getFavoriteId());
        response.setProductId(favorite.getProductId());
        response.setTitle(product == null ? "商品已删除" : product.getTitle());
        response.setMainImage(product == null ? null : product.getMainImage());
        response.setPrice(product == null || product.getPrice() == null ? 0L : product.getPrice());
        response.setStatus(product == null ? null : product.getStatus());
        response.setInvalid(reason != null);
        response.setInvalidReason(reason);
        response.setCreatedAt(favorite.getCreatedAt());
        return response;
    }

    private static void requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或用户信息缺失");
        }
    }

    private static String requireProductId(String productId) {
        if (productId == null || productId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商品ID不能为空");
        }
        return productId.trim();
    }
}
