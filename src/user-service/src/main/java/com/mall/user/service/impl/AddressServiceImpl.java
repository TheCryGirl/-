package com.mall.user.service.impl;

import com.mall.user.common.BizException;
import com.mall.user.common.ErrorCode;
import com.mall.user.common.IdGenerator;
import com.mall.user.dto.request.AddressRequest;
import com.mall.user.dto.response.AddressResponse;
import com.mall.user.entity.Address;
import com.mall.user.mapper.AddressMapper;
import com.mall.user.service.AddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 收货地址服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final AddressMapper addressMapper;

    @Override
    public List<AddressResponse> listAddresses(String userId) {
        List<Address> addresses = addressMapper.selectByUserId(userId);
        return addresses.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AddressResponse createAddress(String userId, AddressRequest request) {
        Address address = new Address();
        address.setAddressId(IdGenerator.addressId());
        address.setUserId(userId);
        address.setReceiver(request.getReceiver());
        address.setPhone(request.getPhone());
        address.setProvince(request.getProvince());
        address.setCity(request.getCity());
        address.setDistrict(request.getDistrict());
        address.setDetail(request.getDetail());
        address.setIsDefault(Boolean.TRUE.equals(request.getDefaultAddress()));
        address.setCreatedAt(LocalDateTime.now());
        address.setUpdatedAt(LocalDateTime.now());
        address.setIsDeleted(Boolean.FALSE);

        if (Boolean.TRUE.equals(address.getIsDefault())) {
            addressMapper.clearDefaultByUserId(userId);
        }
        addressMapper.insert(address);
        return toResponse(address);
    }

    @Override
    @Transactional
    public AddressResponse updateAddress(String userId, String addressId, AddressRequest request) {
        Address address = addressMapper.selectById(addressId);
        if (address == null || !address.getUserId().equals(userId) || Boolean.TRUE.equals(address.getIsDeleted())) {
            throw new BizException(ErrorCode.ADDRESS_NOT_FOUND, "地址不存在");
        }

        address.setReceiver(request.getReceiver());
        address.setPhone(request.getPhone());
        address.setProvince(request.getProvince());
        address.setCity(request.getCity());
        address.setDistrict(request.getDistrict());
        address.setDetail(request.getDetail());
        address.setUpdatedAt(LocalDateTime.now());

        boolean shouldBeDefault = Boolean.TRUE.equals(request.getDefaultAddress());
        if (shouldBeDefault && !Boolean.TRUE.equals(address.getIsDefault())) {
            addressMapper.clearDefaultByUserId(userId);
            address.setIsDefault(Boolean.TRUE);
        }
        addressMapper.updateById(address);
        return toResponse(address);
    }

    @Override
    @Transactional
    public void deleteAddress(String userId, String addressId) {
        Address address = addressMapper.selectById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.ADDRESS_NOT_FOUND, "地址不存在");
        }
        addressMapper.deleteById(addressId);
    }

    @Override
    @Transactional
    public void setDefaultAddress(String userId, String addressId) {
        Address address = addressMapper.selectById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.ADDRESS_NOT_FOUND, "地址不存在");
        }
        addressMapper.clearDefaultByUserId(userId);
        addressMapper.setDefaultById(addressId);
    }

    private AddressResponse toResponse(Address address) {
        AddressResponse response = new AddressResponse();
        response.setAddressId(address.getAddressId());
        response.setReceiver(address.getReceiver());
        response.setPhone(address.getPhone());
        response.setProvince(address.getProvince());
        response.setCity(address.getCity());
        response.setDistrict(address.getDistrict());
        response.setDetail(address.getDetail());
        response.setIsDefault(address.getIsDefault());
        response.setCreatedAt(address.getCreatedAt());
        return response;
    }
}
