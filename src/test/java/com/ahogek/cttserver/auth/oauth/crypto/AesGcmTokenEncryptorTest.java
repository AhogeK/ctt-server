package com.ahogek.cttserver.auth.oauth.crypto;

import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.exception.InternalServerErrorException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmTokenEncryptorTest {

    private AesGcmTokenEncryptor encryptor;

    @BeforeEach
    void setUp() {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        String base64Key = Base64.getEncoder().encodeToString(keyBytes);

        encryptor = new AesGcmTokenEncryptor(createSecurityProperties(base64Key));
    }

    private SecurityProperties createSecurityProperties(String base64Key) {
        SecurityProperties.JwtProperties jwt =
                new SecurityProperties.JwtProperties(
                        "test-secret",
                        "test-issuer",
                        Duration.ofMinutes(15),
                        Duration.ofDays(14),
                        Duration.ofDays(30));
        SecurityProperties.PasswordProperties password =
                new SecurityProperties.PasswordProperties(
                        12, 5, Duration.ofMinutes(30), 900, Duration.ofHours(720), "DB");
        SecurityProperties.RateLimitProperties rateLimit =
                new SecurityProperties.RateLimitProperties(true, 200);
        SecurityProperties.AuditProperties audit =
                new SecurityProperties.AuditProperties(true, List.of("password", "token"));
        SecurityProperties.OAuthProperties oauth =
                new SecurityProperties.OAuthProperties(base64Key);

        return new SecurityProperties(jwt, password, rateLimit, audit, oauth);
    }

    @Test
    void shouldDecryptCorrectly_whenEncryptThenDecrypt() {
        String originalToken = "gho_16C7e42F292c6912E7710c838347Ae178B4a";

        String cipherText = encryptor.encrypt(originalToken);
        String decryptedToken = encryptor.decrypt(cipherText);

        assertThat(decryptedToken).isEqualTo(originalToken);
    }

    @Test
    void shouldProduceDifferentCiphertexts_whenEncryptingSamePlaintext() {
        String plaintext = "SuperSecretData123";

        String cipherText1 = encryptor.encrypt(plaintext);
        String cipherText2 = encryptor.encrypt(plaintext);

        assertThat(cipherText1).isNotEqualTo(cipherText2);
        assertThat(encryptor.decrypt(cipherText1)).isEqualTo(plaintext);
        assertThat(encryptor.decrypt(cipherText2)).isEqualTo(plaintext);
    }

    @Test
    void shouldReturnInputUnchanged_whenNullOrEmpty() {
        assertThat(encryptor.encrypt(null)).isNull();
        assertThat(encryptor.decrypt(null)).isNull();
        assertThat(encryptor.encrypt("")).isEmpty();
        assertThat(encryptor.decrypt("")).isEmpty();
    }

    @Test
    void shouldThrowInternalServerErrorException_whenCiphertextTampered() {
        String plaintext = "SensitiveData";
        String cipherText = encryptor.encrypt(plaintext);

        char lastChar = cipherText.charAt(cipherText.length() - 1);
        char tamperedChar = lastChar == 'A' ? 'B' : 'A';
        String tamperedCipherText = cipherText.substring(0, cipherText.length() - 1) + tamperedChar;

        assertThatThrownBy(() -> encryptor.decrypt(tamperedCipherText))
                .isInstanceOf(InternalServerErrorException.class);
    }

    @Test
    void shouldThrowIllegalStateException_whenEncryptionKeyMissing() {
        SecurityProperties properties = createSecurityProperties(null);

        assertThatThrownBy(() -> new AesGcmTokenEncryptor(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ctt.security.oauth.token-encryption-key");
    }

    @Test
    void shouldThrowIllegalArgumentException_whenEncryptionKeyWrongSize() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        SecurityProperties properties = createSecurityProperties(shortKey);

        assertThatThrownBy(() -> new AesGcmTokenEncryptor(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }
}
