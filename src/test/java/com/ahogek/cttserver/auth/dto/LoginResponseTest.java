package com.ahogek.cttserver.auth.dto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.test.json.JsonContent;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class LoginResponseTest {

    @Autowired private JacksonTester<LoginResponse> json;

    @Test
    void shouldSerializeTermsExpiredTrue() throws Exception {
        UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        LoginResponse response =
                new LoginResponse(
                        userId, "accessToken123", "refreshToken456", 3600L, "Bearer", true);

        JsonContent<LoginResponse> jsonContent = json.write(response);

        assertThat(jsonContent).hasJsonPathStringValue("@.userId");
        assertThat(jsonContent).hasJsonPathStringValue("@.accessToken");
        assertThat(jsonContent).hasJsonPathStringValue("@.refreshToken");
        assertThat(jsonContent).hasJsonPathNumberValue("@.expiresIn");
        assertThat(jsonContent).hasJsonPathStringValue("@.tokenType");
        assertThat(jsonContent).hasJsonPathBooleanValue("@.termsExpired", true);
    }

    @Test
    void shouldSerializeTermsExpiredFalse() throws Exception {
        UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        LoginResponse response =
                new LoginResponse(
                        userId, "accessToken123", "refreshToken456", 3600L, "Bearer", false);

        JsonContent<LoginResponse> jsonContent = json.write(response);

        assertThat(jsonContent).hasJsonPathBooleanValue("@.termsExpired", false);
    }

    @Test
    void shouldDeserializeTermsExpiredTrue() throws Exception {
        String jsonContent =
                """
                {
                    "userId": "550e8400-e29b-41d4-a716-446655440000",
                    "accessToken": "accessToken123",
                    "refreshToken": "refreshToken456",
                    "expiresIn": 3600,
                    "tokenType": "Bearer",
                    "termsExpired": true
                }
                """;

        LoginResponse response = json.parse(jsonContent).getObject();

        assertThat(response.userId())
                .isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        assertThat(response.accessToken()).isEqualTo("accessToken123");
        assertThat(response.refreshToken()).isEqualTo("refreshToken456");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.termsExpired()).isTrue();
    }

    @Test
    void shouldDeserializeTermsExpiredFalse() throws Exception {
        String jsonContent =
                """
                {
                    "userId": "550e8400-e29b-41d4-a716-446655440000",
                    "accessToken": "accessToken123",
                    "refreshToken": "refreshToken456",
                    "expiresIn": 3600,
                    "tokenType": "Bearer",
                    "termsExpired": false
                }
                """;

        LoginResponse response = json.parse(jsonContent).getObject();

        assertThat(response.termsExpired()).isFalse();
    }
}
