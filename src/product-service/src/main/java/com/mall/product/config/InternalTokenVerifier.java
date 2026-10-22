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
     * 开发期默认不校验；配置了 INTERNAL_TOKEN 后，内部接口必须携带匹配的请求头。
     */
    public void verify(String actualToken) {
        if (expectedToken != null && !expectedToken.isBlank()
                && !expectedToken.equals(actualToken)) {
            throw new BizException(ErrorCode.FORBIDDEN, "内部调用令牌无效");
        }
    }
}
