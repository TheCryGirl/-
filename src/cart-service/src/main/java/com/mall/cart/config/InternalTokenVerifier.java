package com.mall.cart.config;

import com.mall.cart.common.BizException;
import com.mall.cart.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InternalTokenVerifier {

    @Value("${cart.internal-token:}")
    private String expectedToken;

    /**
     * 内部接口始终校验匹配的 X-Internal-Token；空配置默认拒绝。
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
