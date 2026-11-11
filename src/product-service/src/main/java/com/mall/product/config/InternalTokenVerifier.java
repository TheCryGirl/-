package com.mall.product.config;

import com.mall.product.common.BizException;
import com.mall.product.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InternalTokenVerifier {

    @Value("${product.internal-token:}")
    private String expectedToken;

    /**
     * 内部接口始终校验；空配置默认拒绝。
     */
    public void verify(String actualToken) {
        if (expectedToken == null || expectedToken.isBlank()
                || actualToken == null || !java.security.MessageDigest.isEqual(
                    expectedToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    actualToken.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new BizException(ErrorCode.FORBIDDEN, "内部调用令牌无效");
        }
    }
}
