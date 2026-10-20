package com.mall.payment.service;

import com.mall.payment.dto.request.PaymentCallbackRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureServiceTest {

    private final SignatureService signatureService = new SignatureService("unit-secret");

    @Test
    void signMatchesContractFormula() throws Exception {
        long amount = 11800L;
        String expected = md5("T20261020001" + "P202610200001" + amount + "unit-secret");
        assertEquals(expected, signatureService.sign("T20261020001", "P202610200001", amount));
    }

    @Test
    void verifyAcceptsCorrectSignature() {
        PaymentCallbackRequest request = callback(11800L);
        request.setSign(signatureService.sign(request.getTradeNo(), request.getPayNo(), request.getAmount()));
        assertTrue(signatureService.verify(request));
    }

    @Test
    void verifyRejectsWrongSignature() {
        PaymentCallbackRequest request = callback(11800L);
        request.setSign("00000000000000000000000000000000");
        assertFalse(signatureService.verify(request));
    }

    @Test
    void verifyRejectsMissingFields() {
        assertFalse(signatureService.verify(null));
        PaymentCallbackRequest request = callback(11800L);
        request.setSign(null);
        assertFalse(signatureService.verify(request));
    }

    private PaymentCallbackRequest callback(long amount) {
        PaymentCallbackRequest request = new PaymentCallbackRequest();
        request.setTradeNo("T20261020001");
        request.setPayNo("P202610200001");
        request.setAmount(amount);
        request.setStatus("SUCCESS");
        return request;
    }

    private String md5(String source) throws Exception {
        byte[] bytes = MessageDigest.getInstance("MD5").digest(source.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
