package com.mall.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 头像上传结果，返回可直接放进 img src 的站内路径。
 */
@Data
@AllArgsConstructor
public class AvatarUploadResponse {

    private String avatar;
}
