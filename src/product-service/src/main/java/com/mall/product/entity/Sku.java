package com.mall.product.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Sku {
    private String skuId;
    private String productId;
    private String specName;
    private Long price;
    private Integer stockAvailable;
    private Integer stockLocked;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer isDeleted;
}
