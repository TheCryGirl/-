package com.mall.cart.mapper;

import com.mall.cart.entity.Cart;
import org.apache.ibatis.annotations.Param;

/**
 * 购物车数据访问。
 */
public interface CartMapper {

    Cart selectByUserId(@Param("userId") String userId);

    /**
     * 锁住用户购物车行，串行化同一用户的加购操作，防止并发生成重复条目。
     */
    Cart selectByUserIdForUpdate(@Param("userId") String userId);

    int insert(Cart cart);
}
