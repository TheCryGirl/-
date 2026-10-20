package com.mall.payment.service;

import com.mall.payment.dto.request.PaymentCallbackRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * MOCK 渠道签名：md5(tradeNo + payNo + amount + MOCK_SECRET)。
 *
 * <p>真实渠道接入时按渠道替换实现，密钥仍从环境变量读取。
 */
@Component
public class SignatureService {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final String mockSecret;

    public SignatureService(@Value("${payment.mock-secret}") String mockSecret) {
        this.mockSecret = mockSecret;
    }

    public String sign(String tradeNo, String payNo, long amount) {
        String source = tradeNo + payNo + amount + mockSecret;
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            char[] result = new char[bytes.length * 2];
            for (int i = 0; i < bytes.length; i++) {
                int value = bytes[i] & 0xFF;
                result[i * 2] = HEX[value >>> 4];
                result[i * 2 + 1] = HEX[value & 0x0F];
            }
            return new String(result);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 MD5", e);
        }
    }

    public boolean verify(PaymentCallbackRequest request) {
        if (request == null || request.getTradeNo() == null || request.getPayNo() == null
                || request.getAmount() == null || request.getSign() == null) {
            return false;
        }
        String expected = sign(request.getTradeNo(), request.getPayNo(), request.getAmount());
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = request.getSign().toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }
}
