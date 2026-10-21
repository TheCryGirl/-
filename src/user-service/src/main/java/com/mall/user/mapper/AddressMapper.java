package com.mall.user.mapper;

import com.mall.user.entity.Address;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 收货地址数据访问层。
 */
@Mapper
public interface AddressMapper {

    int insert(Address address);

    Address selectById(@Param("addressId") String addressId);

    List<Address> selectByUserId(@Param("userId") String userId);

    int updateById(Address address);

    int deleteById(@Param("addressId") String addressId);

    int clearDefaultByUserId(@Param("userId") String userId);

    int setDefaultById(@Param("addressId") String addressId);
}
