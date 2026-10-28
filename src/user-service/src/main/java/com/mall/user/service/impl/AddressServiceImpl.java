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
        List<Address> existing = addressMapper.selectByUserId(userId);
        // 第一条地址自动成为默认，避免下单页出现“没有默认地址”的情况
        boolean isDefault = existing.isEmpty() || Boolean.TRUE.equals(request.getDefaultAddress());

        Address address = new Address();
        address.setAddressId(IdGenerator.addressId());
        address.setUserId(userId);
        address.setReceiver(request.getReceiver());
        address.setPhone(request.getPhone());
        address.setProvince(request.getProvince());
        address.setCity(request.getCity());
        address.setDistrict(request.getDistrict());
        address.setDetail(request.getDetail());
        address.setIsDefault(isDefault);
        address.setCreatedAt(LocalDateTime.now());
        address.setUpdatedAt(LocalDateTime.now());
        address.setIsDeleted(Boolean.FALSE);

        if (isDefault) {
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
        // 默认地址不允许通过编辑取消，换默认请调设置默认接口，保证列表里始终有且只有一条默认
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
        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());
        addressMapper.deleteById(addressId);
        if (wasDefault) {
            // 删掉默认地址后自动提升一条（列表已按 is_default、created_at 排好），保证始终有默认
            List<Address> remaining = addressMapper.selectByUserId(userId);
            if (!remaining.isEmpty()) {
                addressMapper.setDefaultById(remaining.get(0).getAddressId());
            }
        }
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
        response.setDefaultAddress(address.getIsDefault());
        response.setCreatedAt(address.getCreatedAt());
        return response;
    }
}
