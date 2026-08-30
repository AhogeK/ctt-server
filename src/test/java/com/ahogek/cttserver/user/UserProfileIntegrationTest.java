package com.ahogek.cttserver.user;

import com.ahogek.cttserver.auth.dto.LoginRequest;
import com.ahogek.cttserver.auth.dto.UserRegisterRequest;
import com.ahogek.cttserver.common.BaseIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * End-to-end integration tests for the current-user profile endpoint.
 *
 * <p>Verifies that {@code GET /api/v1/users/me} exposes the authenticated user's id and email for
 * both authentication paths: JWT (web) and SYNC-scoped API key (plugin). The plugin relies on this
 * endpoint for account-dimension data isolation — it must resolve the server user id from a
 * SYNC-scoped key.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-29
 */
@BaseIntegrationTest
@TestPropertySource(properties = {"ctt.mail.outbox.poll-interval-ms=999999999"})
@DisplayName("User Profile Integration Tests")
class UserProfileIntegrationTest {

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ObjectMapper objectMapper;

    private static final String PASSWORD = "StrongPass123!";
    private static final String DISPLAY_NAME = "ProfileTestUser";
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM devices").update();
        jdbcClient.sql("DELETE FROM api_keys").update();
        jdbcClient.sql("DELETE FROM mail_outbox").update();
        jdbcClient.sql("DELETE FROM email_verification_tokens").update();
        jdbcClient.sql("DELETE FROM refresh_tokens").update();
        jdbcClient.sql("DELETE FROM audit_logs").update();
        jdbcClient.sql("DELETE FROM users").update();
    }

    private String uniqueEmail() {
        return "profile-test." + UUID.randomUUID() + "@test.example";
    }

    private String registerRequestJson(String email) throws Exception {
        UserRegisterRequest request =
                new UserRegisterRequest(email, DISPLAY_NAME, PASSWORD, "1.0.0", null);
        return objectMapper.writeValueAsString(request);
    }

    private String loginRequestJson(String email) throws Exception {
        LoginRequest request =
                new LoginRequest(email, PASSWORD, "device-" + UUID.randomUUID(), null);
        return objectMapper.writeValueAsString(request);
    }

    private String extractTokenFromMailOutbox(String email) {
        String bodyHtml =
                jdbcClient
                        .sql(
                                "SELECT body_html FROM mail_outbox WHERE recipient = ? ORDER BY created_at DESC LIMIT 1")
                        .param(email)
                        .query(String.class)
                        .single();
        Matcher matcher = TOKEN_PATTERN.matcher(bodyHtml);
        assertThat(matcher.find())
                .as("Verification token not found in email body for %s", email)
                .isTrue();
        return matcher.group(1);
    }

    /** Registers, verifies, logs in and accepts terms; returns the access token and user id. */
    private String[] registerVerifyAndLogin(String email) throws Exception {
        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/register")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerRequestJson(email)))
                .hasStatus(200);

        String token = extractTokenFromMailOutbox(email);
        assertThat(mvc.get().uri("/api/v1/auth/verify-email?token=" + token)).hasStatus(200);

        String loginResponse =
                mvc.post()
                        .uri("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestJson(email))
                        .exchange()
                        .getResponse()
                        .getContentAsString();
        String accessToken =
                objectMapper.readTree(loginResponse).path("data").path("accessToken").asText();
        String userId = objectMapper.readTree(loginResponse).path("data").path("userId").asText();
        assertThat(accessToken).as("login failed: %s", loginResponse).isNotBlank();
        assertThat(userId).isNotBlank();

        String termsResponse =
                mvc.post()
                        .uri("/api/v1/auth/terms/accept")
                        .with(csrf())
                        .header("Authorization", "Bearer " + accessToken)
                        .exchange()
                        .getResponse()
                        .getContentAsString();
        String newAccessToken =
                objectMapper.readTree(termsResponse).path("data").path("accessToken").asText();
        assertThat(newAccessToken).isNotBlank();
        return new String[] {newAccessToken, userId};
    }

    private String createApiKey(String jwt, String name, String... scopes) throws Exception {
        String scopesJson =
                "["
                        + String.join(
                                ", ", Arrays.stream(scopes).map(s -> "\"" + s + "\"").toList())
                        + "]";
        String body =
                """
                {"name": "%s", "scopes": %s}
                """
                        .formatted(name, scopesJson);
        String response =
                mvc.post()
                        .uri("/api/v1/auth/api-keys")
                        .with(csrf())
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .exchange()
                        .getResponse()
                        .getContentAsString();
        String rawKey = objectMapper.readTree(response).path("data").path("rawKey").asText();
        assertThat(rawKey).as("API key creation should return rawKey").isNotBlank();
        return rawKey;
    }

    @Nested
    @DisplayName("GET /api/v1/users/me")
    class GetCurrentUserProfileTests {

        @Test
        @DisplayName("Should return current user id and email when authenticated with SYNC API key")
        void shouldReturnProfile_whenSyncApiKey() throws Exception {
            String email = uniqueEmail();
            String[] jwtAndUserId = registerVerifyAndLogin(email);
            String rawKey = createApiKey(jwtAndUserId[0], "Sync Key", "SYNC");

            var result =
                    mvc.get()
                            .uri("/api/v1/users/me")
                            .header("Authorization", "Bearer " + rawKey)
                            .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.data.id").isEqualTo(jwtAndUserId[1]);
            assertThat(result).bodyJson().extractingPath("$.data.email").isEqualTo(email);
        }

        @Test
        @DisplayName("Should return current user id and email when authenticated with JWT")
        void shouldReturnProfile_whenJwt() throws Exception {
            String email = uniqueEmail();
            String[] jwtAndUserId = registerVerifyAndLogin(email);

            var result =
                    mvc.get()
                            .uri("/api/v1/users/me")
                            .header("Authorization", "Bearer " + jwtAndUserId[0])
                            .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.data.id").isEqualTo(jwtAndUserId[1]);
            assertThat(result).bodyJson().extractingPath("$.data.email").isEqualTo(email);
        }

        @Test
        @DisplayName("Should return 401 when not authenticated")
        void shouldReturn401_whenNotAuthenticated() {
            assertThat(mvc.get().uri("/api/v1/users/me").exchange()).hasStatus(401);
        }
    }
}
