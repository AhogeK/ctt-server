package com.ahogek.cttserver.auth.oauth.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OAuth token attribute conversion")
class OAuthTokenConverterTest {

    private static final String OLD_KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String NEW_KEY =
            Base64.getEncoder()
                    .encodeToString(
                            new byte[] {
                                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                                1, 1, 1, 1, 1, 1, 1, 1, 1
                            });

    @Test
    @DisplayName("Should treat ciphertext from an old key as absent instead of failing the request")
    void shouldReturnNull_whenCiphertextUsesAnotherKey() {
        AesGcmTokenEncryptor oldEncryptor = new AesGcmTokenEncryptor(OLD_KEY);
        AesGcmTokenEncryptor currentEncryptor = new AesGcmTokenEncryptor(NEW_KEY);
        OAuthTokenConverter converter = new OAuthTokenConverter();
        converter.setEncryptor(currentEncryptor);

        String legacyCiphertext = oldEncryptor.encrypt("gho_legacy-token");

        assertThat(converter.convertToEntityAttribute(legacyCiphertext)).isNull();
    }

    @Test
    @DisplayName("Should round-trip a token encrypted with the current key")
    void shouldRoundTrip_whenCiphertextUsesCurrentKey() {
        AesGcmTokenEncryptor currentEncryptor = new AesGcmTokenEncryptor(NEW_KEY);
        OAuthTokenConverter converter = new OAuthTokenConverter();
        converter.setEncryptor(currentEncryptor);

        String ciphertext = currentEncryptor.encrypt("gho_current-token");

        assertThat(converter.convertToEntityAttribute(ciphertext)).isEqualTo("gho_current-token");
    }
}
