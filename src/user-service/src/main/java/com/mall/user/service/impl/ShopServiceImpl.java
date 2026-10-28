package com.mall.user.service.impl;

import com.mall.user.client.ProductClient;
import com.mall.user.client.dto.SellerVisibilityRequest;
import com.mall.user.common.BizException;
import com.mall.user.common.ErrorCode;
import com.mall.user.common.IdGenerator;
import com.mall.user.common.Result;
import com.mall.user.common.ShopStatus;
import com.mall.user.dto.request.ShopRequest;
import com.mall.user.dto.request.ShopStatusRequest;
import com.mall.user.dto.response.PageResponse;
import com.mall.user.dto.response.ShopResponse;
import com.mall.user.entity.Shop;
import com.mall.user.entity.User;
import com.mall.user.mapper.ShopMapper;
import com.mall.user.mapper.UserMapper;
import com.mall.user.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 店铺服务实现。
 *
 * <p>店铺状态是营业状态的唯一真相，商品是否对买家可见由 product-service 的
 * 商品状态体现（SUSPENDED）。停用 / 恢复时先落库再通知商品服务同步，
 * 同步失败则回滚本地状态并提示管理员重试，避免"店铺已停用但商品仍在售"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {

    private final ShopMapper shopMapper;
    private final UserMapper userMapper;
    private final ProductClient productClient;

    @Value("${user.internal-token:}")
    private String internalToken;

    @Override
    public ShopResponse getMine(String sellerId) {
        return toResponse(loadOrCreate(sellerId));
    }

    @Override
    public ShopResponse updateMine(String sellerId, ShopRequest request) {
        Shop shop = loadOrCreate(sellerId);
        shop.setName(request.getName().trim());
        shop.setLogo(trimToNull(request.getLogo()));
        shop.setDescription(trimToNull(request.getDescription()));
        shop.setContactPhone(trimToNull(request.getContactPhone()));
        shop.setContactWechat(trimToNull(request.getContactWechat()));
        shop.setUpdatedAt(LocalDateTime.now());
        shopMapper.updateInfo(shop);
        log.info("卖家更新店铺资料, sellerId={}, shopId={}", sellerId, shop.getShopId());
        return toResponse(shopMapper.selectById(shop.getShopId()));
    }

    @Override
    public PageResponse<ShopResponse> list(String keyword, String status, int page, int size) {
        String safeStatus = normalizeStatus(status);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        String safeKeyword = trimToNull(keyword);
        long total = shopMapper.countPage(safeKeyword, safeStatus);
        List<ShopResponse> list = shopMapper.selectPage(safeKeyword, safeStatus,
                        (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(this::toResponse)
                .toList();
        return PageResponse.of(safePage, safeSize, total, list);
    }

    @Override
    public ShopResponse updateStatus(String operatorId, String shopId, ShopStatusRequest request) {
        ShopStatus target = parseStatus(request.getStatus());
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            throw new BizException(ErrorCode.SHOP_NOT_FOUND, "店铺不存在");
        }
        if (shop.getStatus() == target) {
            return toResponse(shop);
        }

        shopMapper.updateStatus(shopId, target.name());
        try {
            syncVisibility(shop.getSellerId(), target == ShopStatus.ACTIVE);
        } catch (RuntimeException e) {
            // 商品可见性没同步成功就退回原状态，保证"店铺状态"与"商品是否可见"不打架
            shopMapper.updateStatus(shopId, shop.getStatus().name());
            log.error("同步商品可见性失败，已回滚店铺状态, shopId={}, sellerId={}", shopId, shop.getSellerId(), e);
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "商品服务暂不可用，营业状态未生效，请稍后重试");
        }
        log.info("管理员变更店铺营业状态, operatorId={}, shopId={}, sellerId={}, status={}",
                operatorId, shopId, shop.getSellerId(), target);
        return toResponse(shopMapper.selectById(shopId));
    }

    @Override
    public ShopResponse getBySellerId(String sellerId) {
        if (sellerId == null || sellerId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "卖家ID不能为空");
        }
        Shop shop = shopMapper.selectBySellerId(sellerId.trim());
        if (shop == null) {
            throw new BizException(ErrorCode.SHOP_NOT_FOUND, "店铺不存在");
        }
        return toResponse(shop);
    }

    /**
     * 取店铺，没有就按卖家昵称初始化。卖家第一次进入店铺页时不需要额外"开通"动作。
     */
    private Shop loadOrCreate(String sellerId) {
        if (sellerId == null || sellerId.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或用户信息缺失");
        }
        Shop shop = shopMapper.selectBySellerId(sellerId);
        if (shop != null) {
            return shop;
        }
        User seller = userMapper.selectById(sellerId);
        if (seller == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        Shop created = new Shop();
        created.setShopId(IdGenerator.shopId());
        created.setSellerId(sellerId);
        created.setName(defaultName(seller));
        created.setStatus(ShopStatus.ACTIVE);
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        created.setIsDeleted(Boolean.FALSE);
        try {
            shopMapper.insert(created);
        } catch (DuplicateKeyException e) {
            // 并发首次保存时另一请求已建店，直接读回
            return shopMapper.selectBySellerId(sellerId);
        }
        log.info("初始化卖家店铺, sellerId={}, shopId={}", sellerId, created.getShopId());
        return created;
    }

    private void syncVisibility(String sellerId, boolean visible) {
        Result<?> result = productClient.syncSellerVisibility(internalToken,
                new SellerVisibilityRequest(sellerId, visible));
        if (result == null || result.getCode() != ErrorCode.SUCCESS) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE,
                    result == null ? "product-service 无响应" : result.getMessage());
        }
    }

    private static String defaultName(User seller) {
        String nickname = seller.getNickname() == null || seller.getNickname().isBlank()
                ? seller.getAccount() : seller.getNickname();
        return nickname + "的店铺";
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ShopStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "营业状态不合法");
        }
    }

    private static ShopStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "营业状态不能为空");
        }
        try {
            return ShopStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "营业状态不合法，只允许 ACTIVE 或 SUSPENDED");
        }
    }

    private ShopResponse toResponse(Shop shop) {
        ShopResponse response = new ShopResponse();
        response.setShopId(shop.getShopId());
        response.setSellerId(shop.getSellerId());
        response.setName(shop.getName());
        response.setLogo(shop.getLogo());
        response.setDescription(shop.getDescription());
        response.setContactPhone(shop.getContactPhone());
        response.setContactWechat(shop.getContactWechat());
        response.setStatus(shop.getStatus());
        response.setCreatedAt(shop.getCreatedAt());
        response.setUpdatedAt(shop.getUpdatedAt());
        return response;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
