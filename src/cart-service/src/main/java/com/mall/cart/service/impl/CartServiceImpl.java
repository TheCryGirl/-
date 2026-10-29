package com.mall.cart.service.impl;

import com.mall.cart.client.ProductClient;
import com.mall.cart.client.dto.ProductDetailDTO;
import com.mall.cart.client.dto.ProductStatus;
import com.mall.cart.client.dto.SkuDTO;
import com.mall.cart.common.BizException;
import com.mall.cart.common.ErrorCode;
import com.mall.cart.common.IdGenerator;
import com.mall.cart.common.Result;
import com.mall.cart.dto.request.AddCartItemRequest;
import com.mall.cart.dto.response.CartItemResponse;
import com.mall.cart.dto.response.CartResponse;
import com.mall.cart.entity.Cart;
import com.mall.cart.entity.CartItem;
import com.mall.cart.mapper.CartItemMapper;
import com.mall.cart.mapper.CartMapper;
import com.mall.cart.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 购物车业务逻辑。
 *
 * <p>商品价格、标题与库存不落库，查询时实时调用商品服务；
 * 加购只做数量上限校验，不预占库存（BR-11）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private static final int MAX_QUANTITY = 999;

    private final CartMapper cartMapper;
    private final CartItemMapper cartItemMapper;
    private final ProductClient productClient;
    private final TransactionTemplate transactionTemplate;

    @Value("${cart.internal-token:}")
    private String internalToken;

    @Override
    public CartResponse getCart(String userId) {
        requireUser(userId);
        Cart cart = cartMapper.selectByUserId(userId);
        if (cart == null) {
            return CartResponse.builder()
                    .cartId(null)
                    .items(List.of())
                    .selectedTotal(0L)
                    .selectedCount(0)
                    .build();
        }

        List<CartItem> cartItems = cartItemMapper.selectActiveByCartId(cart.getCartId());
        Map<String, ProductSnapshot> productCache = new HashMap<>();
        List<CartItemResponse> items = new ArrayList<>(cartItems.size());
        long selectedTotal = 0L;
        int selectedCount = 0;

        for (CartItem cartItem : cartItems) {
            ProductSnapshot snapshot = productCache.computeIfAbsent(cartItem.getProductId(), this::loadProduct);
            CartItemResponse response = toResponse(cartItem, snapshot);
            if (Boolean.TRUE.equals(response.getSelected()) && !Boolean.TRUE.equals(response.getInvalid())) {
                selectedTotal += response.getPrice() * response.getQuantity();
                selectedCount++;
            }
            items.add(response);
        }

        return CartResponse.builder()
                .cartId(cart.getCartId())
                .items(items)
                .selectedTotal(selectedTotal)
                .selectedCount(selectedCount)
                .build();
    }

    @Override
    public void addCartItem(String userId, AddCartItemRequest request) {
        requireUser(userId);
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求参数不能为空");
        }
        String skuId = requireText(request.getSkuId(), "SKU");
        validateQuantity(request.getQuantity());
        String productId = trimToNull(request.getProductId());
        if (productId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "缺少 productId：购物车需要按商品 ID 调用商品详情接口");
        }

        // 远程调用放在事务外，避免长时间占用数据库连接
        ProductDetailDTO product = requireProduct(productId);
        SkuDTO sku = requireOnSaleSku(product, skuId);
        int maxQuantity = maxQuantity(sku);
        int quantity = request.getQuantity();
        if (quantity > maxQuantity) {
            throw new BizException(ErrorCode.QUANTITY_EXCEEDS_STOCK, "数量超出可售库存");
        }

        transactionTemplate.executeWithoutResult(status -> {
            Cart cart = lockOrCreateCart(userId);
            CartItem existing = cartItemMapper.selectByCartIdAndSkuForUpdate(cart.getCartId(), skuId);
            if (existing == null) {
                cartItemMapper.insert(CartItem.builder()
                        .cartItemId(IdGenerator.cartItemId())
                        .cartId(cart.getCartId())
                        .userId(userId)
                        .productId(productId)
                        .skuId(skuId)
                        .quantity(quantity)
                        .selected(1)
                        .createdBy(userId)
                        .updatedBy(userId)
                        .isDeleted(0)
                        .build());
                return;
            }
            if (existing.getIsDeleted() != null && existing.getIsDeleted() == 1) {
                cartItemMapper.resurrect(existing.getCartItemId(), userId, quantity, userId);
                return;
            }
            int newQuantity = existing.getQuantity() + quantity;
            if (newQuantity > maxQuantity) {
                throw new BizException(ErrorCode.QUANTITY_EXCEEDS_STOCK,
                        "数量超出可售库存，当前最多可购 " + maxQuantity + " 件");
            }
            cartItemMapper.updateQuantity(existing.getCartItemId(), userId, newQuantity, userId);
        });

        log.info("加入购物车, userId={}, productId={}, skuId={}, quantity={}", userId, productId, skuId, quantity);
    }

    @Override
    public void updateQuantity(String userId, String cartItemId, Integer quantity) {
        requireUser(userId);
        String itemId = requireText(cartItemId, "购物车条目");
        validateQuantity(quantity);

        CartItem item = cartItemMapper.selectActiveByIdAndUser(itemId, userId);
        if (item == null) {
            throw new BizException(ErrorCode.CART_ITEM_NOT_FOUND, "购物车条目不存在");
        }

        ProductDetailDTO product = requireProduct(item.getProductId());
        SkuDTO sku = requireOnSaleSku(product, item.getSkuId());
        int maxQuantity = maxQuantity(sku);
        if (quantity > maxQuantity) {
            throw new BizException(ErrorCode.QUANTITY_EXCEEDS_STOCK, "数量超出可售库存");
        }

        int updated = cartItemMapper.updateQuantity(itemId, userId, quantity, userId);
        if (updated == 0) {
            throw new BizException(ErrorCode.CART_ITEM_NOT_FOUND, "购物车条目不存在");
        }
        log.info("修改购物车数量, userId={}, cartItemId={}, quantity={}", userId, itemId, quantity);
    }

    @Override
    public void updateSelected(String userId, String cartItemId, Boolean selected) {
        requireUser(userId);
        String itemId = requireText(cartItemId, "购物车条目");
        if (selected == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "勾选状态不能为空");
        }
        int updated = cartItemMapper.updateSelected(itemId, userId, selected ? 1 : 0, userId);
        if (updated == 0) {
            throw new BizException(ErrorCode.CART_ITEM_NOT_FOUND, "购物车条目不存在");
        }
        log.info("修改购物车勾选状态, userId={}, cartItemId={}, selected={}", userId, itemId, selected);
    }

    @Override
    public void removeItem(String userId, String cartItemId) {
        requireUser(userId);
        String itemId = requireText(cartItemId, "购物车条目");
        int updated = cartItemMapper.softDelete(itemId, userId, userId);
        if (updated == 0) {
            throw new BizException(ErrorCode.CART_ITEM_NOT_FOUND, "购物车条目不存在");
        }
        log.info("移除购物车条目, userId={}, cartItemId={}", userId, itemId);
    }

    @Override
    public void clearCart(String userId) {
        requireUser(userId);
        Cart cart = cartMapper.selectByUserId(userId);
        if (cart == null) {
            return;
        }
        cartItemMapper.softDeleteByCartId(cart.getCartId(), userId, userId);
        log.info("清空购物车, userId={}, cartId={}", userId, cart.getCartId());
    }

    private Cart lockOrCreateCart(String userId) {
        Cart cart = cartMapper.selectByUserIdForUpdate(userId);
        if (cart != null) {
            return cart;
        }
        Cart created = Cart.builder()
                .cartId(IdGenerator.cartId())
                .userId(userId)
                .createdBy(userId)
                .updatedBy(userId)
                .isDeleted(0)
                .build();
        try {
            cartMapper.insert(created);
            return created;
        } catch (DuplicateKeyException e) {
            Cart existing = cartMapper.selectByUserIdForUpdate(userId);
            if (existing == null) {
                throw e;
            }
            return existing;
        }
    }

    private CartItemResponse toResponse(CartItem cartItem, ProductSnapshot snapshot) {
        CartItemResponse response = new CartItemResponse();
        response.setCartItemId(cartItem.getCartItemId());
        response.setSkuId(cartItem.getSkuId());
        response.setProductId(cartItem.getProductId());
        response.setQuantity(cartItem.getQuantity());
        response.setSelected(cartItem.getSelected() != null && cartItem.getSelected() == 1);
        response.setPrice(0L);
        response.setStockAvailable(0);
        response.setInvalid(false);

        if (snapshot.failed()) {
            response.setInvalid(true);
            response.setInvalidReason(snapshot.error());
            return response;
        }

        ProductDetailDTO product = snapshot.product();
        response.setTitle(product.getTitle());
        response.setMainImage(product.getMainImage());
        if (product.getStatus() != ProductStatus.ON_SALE) {
            response.setInvalid(true);
            // 店铺被停用与商品自身下架对用户是两件事，提示要分开（#49）
            response.setInvalidReason(product.getStatus() == ProductStatus.SUSPENDED
                    ? "店铺已停用" : "商品已下架");
        }

        SkuDTO sku = findSku(product, cartItem.getSkuId());
        if (sku == null) {
            response.setInvalid(true);
            response.setInvalidReason("商品规格已失效");
            return response;
        }

        response.setSpecName(sku.getSpecName());
        response.setPrice(sku.getPrice() == null ? 0L : sku.getPrice());
        response.setStockAvailable(sku.getStockAvailable() == null ? 0 : sku.getStockAvailable());

        if (!response.getInvalid() && response.getStockAvailable() < cartItem.getQuantity()) {
            response.setInvalid(true);
            response.setInvalidReason("库存不足，仅剩 " + response.getStockAvailable() + " 件");
        }
        return response;
    }

    private ProductSnapshot loadProduct(String productId) {
        if (productId == null || productId.isBlank()) {
            return new ProductSnapshot(null, "商品信息缺失");
        }
        try {
            Result<ProductDetailDTO> result = productClient.getProduct(productId, internalToken);
            if (result == null) {
                return new ProductSnapshot(null, "商品信息暂时无法获取");
            }
            if (result.getCode() != ErrorCode.SUCCESS) {
                return new ProductSnapshot(null,
                        result.getMessage() == null ? "商品不可用" : result.getMessage());
            }
            if (result.getData() == null) {
                return new ProductSnapshot(null, "商品不存在");
            }
            return new ProductSnapshot(result.getData(), null);
        } catch (Exception e) {
            log.warn("查询商品信息失败, productId={}", productId, e);
            return new ProductSnapshot(null, "商品信息暂时无法获取，请稍后重试");
        }
    }

    private ProductDetailDTO requireProduct(String productId) {
        if (productId == null || productId.isBlank()) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND, "商品不存在");
        }
        Result<ProductDetailDTO> result;
        try {
            result = productClient.getProduct(productId, internalToken);
        } catch (Exception e) {
            log.error("调用商品服务失败, productId={}", productId, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "商品服务暂不可用，请稍后重试");
        }
        if (result == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "商品服务无响应");
        }
        if (result.getCode() != ErrorCode.SUCCESS) {
            String message = result.getMessage() == null ? "商品不可用" : result.getMessage();
            throw new BizException(result.getCode(), message);
        }
        if (result.getData() == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND, "商品不存在");
        }
        return result.getData();
    }

    private SkuDTO requireOnSaleSku(ProductDetailDTO product, String skuId) {
        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw new BizException(ErrorCode.PRODUCT_OFF_SHELF,
                    product.getStatus() == ProductStatus.SUSPENDED
                            ? "店铺已停用，暂不可购买" : "商品已下架或尚未审核通过");
        }
        SkuDTO sku = findSku(product, skuId);
        if (sku == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND, "SKU 不存在");
        }
        return sku;
    }

    private static SkuDTO findSku(ProductDetailDTO product, String skuId) {
        if (product.getSkus() == null) {
            return null;
        }
        return product.getSkus().stream()
                .filter(sku -> skuId.equals(sku.getSkuId()))
                .findFirst()
                .orElse(null);
    }

    private static int maxQuantity(SkuDTO sku) {
        int stock = sku.getStockAvailable() == null ? 0 : sku.getStockAvailable();
        return Math.min(MAX_QUANTITY, Math.max(0, stock));
    }

    private static void requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或用户信息缺失");
        }
    }

    private static void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 1 || quantity > MAX_QUANTITY) {
            throw new BizException(ErrorCode.PARAM_INVALID, "数量必须在 1~" + MAX_QUANTITY + " 之间");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, name + "不能为空");
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ProductSnapshot(ProductDetailDTO product, String error) {

        boolean failed() {
            return error != null;
        }
    }
}
