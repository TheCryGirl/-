package com.mall.product.config;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 浠庣綉鍏抽€忎紶鐨?{@code X-Trace-Id} 璇锋眰澶存仮澶?TraceId 鍒版棩蹇椾笂涓嬫枃銆? *
 * <p>閰嶅悎 logback 鐨?{@code %X{traceId}} 杈撳嚭锛屽悓涓€娆¤姹傚湪缃戝叧涓庡悇鏈嶅姟閲岀殑鏃ュ織
 * 鍙互鐢ㄥ悓涓€涓?ID 涓茶捣鏉ャ€傝姹傚ご缂哄け鏃讹紙渚嬪鐩磋繛鏈嶅姟銆佸唴閮ㄨ皟鐢級涓嶅啓鍏ワ紝
 * 鏃ュ織鏍煎紡閲屽搴斾綅缃暀绌猴紝涓嶅奖鍝嶅師鏈夎緭鍑恒€? */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(HEADER_TRACE_ID);
        boolean owned = traceId != null && !traceId.isBlank();
        if (owned) {
            MDC.put(MDC_KEY, traceId);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (owned) {
                MDC.remove(MDC_KEY);
            }
        }
    }
}
