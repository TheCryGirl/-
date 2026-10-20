package org.example.ssm1.Mapper;

import org.apache.ibatis.annotations.*;
import org.example.ssm1.Entity.PurchaseRequest;

import java.util.List;

@Mapper
public interface PurchaseRequestMapper {

    @Select("SELECT request_id as requestId, product_id as productId, seller_id as sellerId, buyer_id as buyerId, status, create_time as createTime FROM purchase_request WHERE buyer_id = #{buyerId} ORDER BY create_time DESC")
    List<PurchaseRequest> selectByBuyerId(String buyerId);

    @Insert("INSERT INTO purchase_request (product_id, seller_id, buyer_id, status, create_time) VALUES (#{productId}, #{sellerId}, #{buyerId}, #{status}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "requestId")
    int insert(PurchaseRequest request);

    @Update("UPDATE purchase_request SET status = #{status} WHERE product_id = #{productId} AND buyer_id = #{buyerId} AND status = '确认中'")
    int updateStatusByProductAndBuyer(String productId, String buyerId, String status);
}
