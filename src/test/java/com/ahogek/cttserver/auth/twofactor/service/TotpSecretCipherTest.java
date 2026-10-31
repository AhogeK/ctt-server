package com.ahogek.cttserver.auth.twofactor.service;

import com.ahogek.cttserver.common.config.properties.TwoFactorProperties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TOTP secret cipher")
class TotpSecretCipherTest {

    private static final String KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private final TotpSecretCipher cipher =
            new TotpSecretCipher(
                    new TwoFactorProperties(
                            "Code Time Tracker",
                            KEY,
                            6,
                            30,
                            1,
                            Duration.ofMinutes(10),
                            Duration.ofMinutes(5),
                            Duration.ofMinutes(10),
                            10));

    @Test
    @DisplayName("Should round-trip a secret")
    void shouldRoundTripSecret_whenEncryptedThenDecrypted() {
        String encrypted = cipher.encrypt("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");

        assertThat(encrypted).startsWith("v1:");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
    }

    @Test
    @DisplayName("Should reject a tampered ciphertext")
    void shouldRejectTamperedCiphertext_whenDecrypting() {
        String encrypted = cipher.encrypt("secret");
        String tampered = encrypted.substring(0, encrypted.length() - 2) + "AA";

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject an unsupported ciphertext version")
    void shouldRejectUnsupportedVersion_whenDecrypting() {
        assertThatThrownBy(() -> cipher.decrypt("v2:aaaa:bbbb"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
