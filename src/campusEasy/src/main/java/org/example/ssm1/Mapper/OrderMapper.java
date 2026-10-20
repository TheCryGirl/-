package org.example.ssm1.Mapper;

import org.apache.ibatis.annotations.*;
import org.example.ssm1.Entity.Order;
import java.util.List;

@Mapper
public interface OrderMapper {

    @Select("SELECT order_id as orderId, product_id as productId, seller_id as sellerId, buyer_id as buyerId, status, create_time as createTime FROM `order` WHERE order_id = #{orderId}")
    Order selectById(String orderId);

    @Select("SELECT order_id as orderId, product_id as productId, seller_id as sellerId, buyer_id as buyerId, status, create_time as createTime FROM `order` WHERE buyer_id = #{buyerId}")
    List<Order> selectByBuyerId(String buyerId);

    @Select("SELECT order_id as orderId, product_id as productId, seller_id as sellerId, buyer_id as buyerId, status, create_time as createTime FROM `order` WHERE seller_id = #{sellerId}")
    List<Order> selectBySellerId(String sellerId);

    @Insert("INSERT INTO `order` (order_id, product_id, seller_id, buyer_id, status, create_time) VALUES (#{orderId}, #{productId}, #{sellerId}, #{buyerId}, #{status}, #{createTime})")
    int insert(Order order);

    @Update("UPDATE `order` SET product_id = #{productId}, seller_id = #{sellerId}, buyer_id = #{buyerId}, status = #{status} WHERE order_id = #{orderId}")
    int update(Order order);

    @Delete("DELETE FROM `order` WHERE order_id = #{orderId}")
    int deleteById(String orderId);
}
