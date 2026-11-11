package com.mall.order.config;

import com.mall.order.common.BizException;
import com.mall.order.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies the token used by trusted service-to-service calls.
 * An empty configured token denies all internal calls.
 */
@Component
public class InternalTokenVerifier {

    @Value("${order.internal-token:}")
    private String expectedToken;

    public void verify(String actualToken) {
        if (expectedToken == null || expectedToken.isBlank()
                || actualToken == null || !java.security.MessageDigest.isEqual(
                    expectedToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    actualToken.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new BizException(ErrorCode.FORBIDDEN, "内部调用令牌无效");
        }
    }
}
