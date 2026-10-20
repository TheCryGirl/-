package org.example.ssm1.Mapper;

import org.apache.ibatis.annotations.*;
import org.example.ssm1.Entity.Product;
import java.util.List;

@Mapper
public interface ProductMapper {

    @Select("SELECT product_id as productId, user_id as userId, name, price, description, status, classification, create_time as createTime, image_url as imageUrl, pending_buyer_id as pendingBuyerId, version FROM product WHERE product_id = #{productId}")
    Product selectById(String productId);

    @Select("SELECT product_id as productId, user_id as userId, name, price, description, status, classification, create_time as createTime, image_url as imageUrl, pending_buyer_id as pendingBuyerId, version FROM product")
    List<Product> selectAll();
    
    @Select("SELECT product_id as productId, user_id as userId, name, price, description, status, classification, create_time as createTime, image_url as imageUrl, pending_buyer_id as pendingBuyerId, version FROM product WHERE user_id = #{userId}")
    List<Product> selectByUserId(String userId);

    @Select("SELECT product_id as productId, user_id as userId, name, price, description, status, classification, create_time as createTime, image_url as imageUrl, pending_buyer_id as pendingBuyerId, version FROM product WHERE status = #{status}")
    List<Product> selectByStatus(String status);

    @Select("SELECT product_id as productId, user_id as userId, name, price, description, status, classification, create_time as createTime, image_url as imageUrl, pending_buyer_id as pendingBuyerId, version FROM product WHERE classification = #{classification} AND status = #{status}")
    List<Product> selectByClassification(String classification, String status);

    @Select("SELECT product_id as productId, user_id as userId, name, price, description, status, classification, create_time as createTime, image_url as imageUrl, pending_buyer_id as pendingBuyerId, version FROM product WHERE price >= #{low} AND price<=#{high} AND status = #{status}")
    List<Product> selectByPrice(double low, double high,String status);

    @Insert("INSERT INTO product (product_id, user_id, name, price, description, status, classification, create_time, image_url, pending_buyer_id, version) VALUES (#{productId}, #{userId}, #{name}, #{price}, #{description}, #{status}, #{classification}, #{createTime}, #{imageUrl}, #{pendingBuyerId}, 0)")
    int insert(Product product);

    @Update("UPDATE product SET user_id = #{userId}, name = #{name}, price = #{price}, description = #{description}, status = #{status}, classification = #{classification}, image_url = #{imageUrl}, pending_buyer_id = #{pendingBuyerId}, version = version + 1 WHERE product_id = #{productId} AND version = #{version}")
    int update(Product product);

    @Delete("DELETE FROM product WHERE product_id = #{productId}")
    int deleteById(String productId);

    @Delete("DELETE FROM product WHERE user_id = #{userId} AND name = #{name} AND status = #{status}")
    int deleteByUserIdAndName(String userId, String name, String status);

}
