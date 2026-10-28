package com.mall.user.client.dto;

import lombok.Data;

/**
 * 商品可见性同步结果，updated 为受影响商品数。
 */
@Data
public class VisibilitySyncResponse {

    private Integer updated;
}
