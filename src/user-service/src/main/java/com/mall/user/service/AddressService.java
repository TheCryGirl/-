package com.mall.user.service;

import com.mall.user.dto.request.AddressRequest;
import com.mall.user.dto.response.AddressResponse;

import java.util.List;

/**
 * 收货地址服务接口。
 */
public interface AddressService {

    List<AddressResponse> listAddresses(String userId);

    AddressResponse createAddress(String userId, AddressRequest request);

    AddressResponse updateAddress(String userId, String addressId, AddressRequest request);

    void deleteAddress(String userId, String addressId);

    void setDefaultAddress(String userId, String addressId);
}
