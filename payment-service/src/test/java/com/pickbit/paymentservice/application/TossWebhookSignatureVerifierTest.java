package com.pickbit.paymentservice.application;

import com.pickbit.paymentservice.exception.InvalidWebhookSignatureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

class TossWebhookSignatureVerifierTest {

    private static final String SECRET = "test_webhook_secret";
    private final TossWebhookSignatureVerifier verifier = new TossWebhookSignatureVerifier(SECRET);

    @Test
    @DisplayName("올바른 HMAC-SHA256 서명은 검증을 통과한다")
    void verify_success() {
        String body = "{\"status\":\"DONE\"}";
        String signature = hmacSha256(SECRET, body);

        assertThatCode(() -> verifier.verify(signature, body)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("서명 헤더가 비어 있으면 InvalidWebhookSignatureException")
    void verify_missing() {
        assertThatThrownBy(() -> verifier.verify(null, "{}"))
                .isInstanceOf(InvalidWebhookSignatureException.class);
        assertThatThrownBy(() -> verifier.verify("", "{}"))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    @DisplayName("서명이 일치하지 않으면 InvalidWebhookSignatureException")
    void verify_mismatch() {
        assertThatThrownBy(() -> verifier.verify("invalid", "{\"status\":\"DONE\"}"))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    private static String hmacSha256(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
