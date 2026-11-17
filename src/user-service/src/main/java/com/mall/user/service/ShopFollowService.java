package com.mall.user.service;

import com.mall.user.common.BizException;
import com.mall.user.common.ErrorCode;
import com.mall.user.common.IdGenerator;
import com.mall.user.dto.response.FollowedShopResponse;
import com.mall.user.dto.response.PageResponse;
import com.mall.user.entity.Shop;
import com.mall.user.entity.ShopFollow;
import com.mall.user.mapper.ShopFollowMapper;
import com.mall.user.mapper.ShopMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 店铺关注（#142）：关注、取消与列表。 */
@Service
@RequiredArgsConstructor
public class ShopFollowService {

    private final ShopMapper shopMapper;
    private final ShopFollowMapper shopFollowMapper;

    /** 关注店铺：重复关注幂等；卖家不能关注自己的店。 */
    public long follow(String userId, String shopId) {
        Shop shop = requireShop(shopId);
        requireUser(userId);
        if (userId.equals(shop.getSellerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "不能关注自己的店铺");
        }
        ShopFollow follow = new ShopFollow();
        follow.setFollowId("SF" + IdGenerator.randomDigits(14));
        follow.setShopId(shopId);
        follow.setUserId(userId);
        follow.setCreatedAt(LocalDateTime.now());
        shopFollowMapper.insert(follow);
        return shopFollowMapper.countByShop(shopId);
    }

    public long unfollow(String userId, String shopId) {
        requireUser(userId);
        requireShop(shopId);
        shopFollowMapper.delete(shopId, userId);
        return shopFollowMapper.countByShop(shopId);
    }

    public boolean isFollowing(String userId, String shopId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return shopFollowMapper.countByUserAndShop(shopId, userId) > 0;
    }

    public PageResponse<FollowedShopResponse> listFollowing(String userId, int page, int size) {
        requireUser(userId);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, size));
        List<FollowedShopResponse> list = shopFollowMapper
                .selectFollowing(userId, (safePage - 1) * safeSize, safeSize)
                .stream().map(this::toResponse).toList();
        long total = shopFollowMapper.countFollowing(userId);
        return PageResponse.of(safePage, safeSize, total, list);
    }

    private FollowedShopResponse toResponse(Map<String, Object> row) {
        FollowedShopResponse response = new FollowedShopResponse();
        response.setShopId(asString(row.get("shopId")));
        response.setSellerId(asString(row.get("sellerId")));
        response.setName(asString(row.get("name")));
        response.setLogo(asString(row.get("logo")));
        response.setDescription(asString(row.get("description")));
        response.setStatus(asString(row.get("status")));
        Object created = row.get("createdAt");
        response.setFollowedAt(created == null ? null : created.toString().replace('T', ' '));
        return response;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Shop requireShop(String shopId) {
        if (shopId == null || shopId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "店铺ID不能为空");
        }
        Shop shop = shopMapper.selectById(shopId.trim());
        if (shop == null || Boolean.TRUE.equals(shop.getIsDeleted())) {
            throw new BizException(ErrorCode.SHOP_NOT_FOUND, "店铺不存在");
        }
        return shop;
    }

    private static void requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或用户信息缺失");
        }
    }
}