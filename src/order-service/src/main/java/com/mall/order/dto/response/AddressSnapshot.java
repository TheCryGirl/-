package com.mall.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddressSnapshot {
    private String receiver;
    private String phone;
    private String fullAddress;
}
