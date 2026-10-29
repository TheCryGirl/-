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
     * 开发期未配置令牌时放行；配置后内部接口必须携带匹配的 X-Internal-Token。
     */
    public void verify(String actualToken) {
        if (expectedToken != null && !expectedToken.isBlank()
                && !expectedToken.equals(actualToken)) {
            throw new BizException(ErrorCode.FORBIDDEN, "内部调用令牌无效");
        }
    }
}
