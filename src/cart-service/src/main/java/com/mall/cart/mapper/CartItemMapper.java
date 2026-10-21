package com.mall.cart.mapper;

import com.mall.cart.entity.CartItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 购物车条目数据访问。
 */
public interface CartItemMapper {

    List<CartItem> selectActiveByCartId(@Param("cartId") String cartId);

    CartItem selectActiveByIdAndUser(@Param("cartItemId") String cartItemId, @Param("userId") String userId);

    /**
     * 包含已逻辑删除的条目，用于重新加购时复活原行，避免唯一键冲突。
     */
    CartItem selectByCartIdAndSkuForUpdate(@Param("cartId") String cartId, @Param("skuId") String skuId);

    int insert(CartItem cartItem);

    int updateQuantity(@Param("cartItemId") String cartItemId,
                       @Param("userId") String userId,
                       @Param("quantity") int quantity,
                       @Param("updatedBy") String updatedBy);

    int updateSelected(@Param("cartItemId") String cartItemId,
                       @Param("userId") String userId,
                       @Param("selected") int selected,
                       @Param("updatedBy") String updatedBy);

    int resurrect(@Param("cartItemId") String cartItemId,
                  @Param("userId") String userId,
                  @Param("quantity") int quantity,
                  @Param("updatedBy") String updatedBy);

    int softDelete(@Param("cartItemId") String cartItemId,
                   @Param("userId") String userId,
                   @Param("updatedBy") String updatedBy);

    int softDeleteByCartId(@Param("cartId") String cartId,
                           @Param("userId") String userId,
                           @Param("updatedBy") String updatedBy);
}
