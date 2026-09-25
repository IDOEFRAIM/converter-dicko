package com.converter.transfi.util;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureVerifierTest {

    private static final String SECRET = "test-webhook-secret";

    @Test
    void isValid_withCorrectSignature_returnsTrue() {
        byte[] body = "{\"eventId\":\"evt-1\"}".getBytes(StandardCharsets.UTF_8);
        String signature = hmacHex(body, SECRET);

        assertThat(WebhookSignatureVerifier.isValid(body, signature, SECRET)).isTrue();
    }

    @Test
    void isValid_withWrongSecret_returnsFalse() {
        byte[] body = "{\"eventId\":\"evt-1\"}".getBytes(StandardCharsets.UTF_8);
        String signature = hmacHex(body, "another-secret");

        assertThat(WebhookSignatureVerifier.isValid(body, signature, SECRET)).isFalse();
    }

    @Test
    void isValid_withTamperedBody_returnsFalse() {
        byte[] originalBody = "{\"eventId\":\"evt-1\"}".getBytes(StandardCharsets.UTF_8);
        String signature = hmacHex(originalBody, SECRET);
        byte[] tamperedBody = "{\"eventId\":\"evt-2\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(WebhookSignatureVerifier.isValid(tamperedBody, signature, SECRET)).isFalse();
    }

    @Test
    void isValid_withNonHexSignature_returnsFalseWithoutThrowing() {
        byte[] body = "{\"eventId\":\"evt-1\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(WebhookSignatureVerifier.isValid(body, "not-hex-at-all!!", SECRET)).isFalse();
    }

    @Test
    void isValid_withMissingSignatureOrSecret_returnsFalse() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        assertThat(WebhookSignatureVerifier.isValid(body, null, SECRET)).isFalse();
        assertThat(WebhookSignatureVerifier.isValid(body, "", SECRET)).isFalse();
        assertThat(WebhookSignatureVerifier.isValid(body, hmacHex(body, SECRET), null)).isFalse();
    }

    private static String hmacHex(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
