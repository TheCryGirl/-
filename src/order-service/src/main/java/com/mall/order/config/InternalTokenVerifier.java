package com.mall.order.config;

import com.mall.order.common.BizException;
import com.mall.order.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies the token used by trusted service-to-service calls.
 * Development deployments may leave the token empty to keep local calls open.
 */
@Component
public class InternalTokenVerifier {

    @Value("${order.internal-token:}")
    private String expectedToken;

    public void verify(String actualToken) {
        if (expectedToken != null && !expectedToken.isBlank()
                && !expectedToken.equals(actualToken)) {
            throw new BizException(ErrorCode.FORBIDDEN, "内部调用令牌无效");
        }
    }
}
