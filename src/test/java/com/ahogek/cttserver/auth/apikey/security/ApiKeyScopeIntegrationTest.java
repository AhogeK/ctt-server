package com.ahogek.cttserver.auth.apikey.security;

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
 * End-to-end integration tests for API key scope enforcement on sync endpoints.
 *
 * <p>Verifies that {@link RequiresApiKeyScope} authorization works correctly through the full
 * request pipeline: API key authentication filter → scope aspect → controller.
 *
 * <p>Uses full Spring context with PostgreSQL, Redis, and GreenMail test containers.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-13
 */
@BaseIntegrationTest
@TestPropertySource(properties = {"ctt.mail.outbox.poll-interval-ms=999999999"})
@DisplayName("API Key Scope Enforcement Integration Tests")
class ApiKeyScopeIntegrationTest {

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ObjectMapper objectMapper;

    private static final String PASSWORD = "StrongPass123!";
    private static final String DISPLAY_NAME = "ScopeTestUser";
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");

    private static final String PULL_ENDPOINT = "/api/v1/sync/pull";
    private static final String PUSH_ENDPOINT = "/api/v1/sync/push";

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM api_keys").update();
        jdbcClient.sql("DELETE FROM mail_outbox").update();
        jdbcClient.sql("DELETE FROM email_verification_tokens").update();
        jdbcClient.sql("DELETE FROM refresh_tokens").update();
        jdbcClient.sql("DELETE FROM users").update();
    }

    private String uniqueEmail() {
        return "scope-test." + UUID.randomUUID() + "@test.example";
    }

    private String uniqueDeviceId() {
        return "device-" + UUID.randomUUID();
    }

    private String registerRequestJson(String email) throws Exception {
        UserRegisterRequest request =
                new UserRegisterRequest(email, DISPLAY_NAME, PASSWORD, "1.0.0", null);
        return objectMapper.writeValueAsString(request);
    }

    private String loginRequestJson(String email) throws Exception {
        LoginRequest request = new LoginRequest(email, PASSWORD, uniqueDeviceId(), null);
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

    /**
     * Registers a user, verifies email, accepts terms, and returns a JWT access token.
     *
     * <p>Full flow: register → extract verification token → verify email → login → accept terms →
     * return new access token
     */
    private String registerVerifyAndLogin(String email) throws Exception {
        // Register
        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/register")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerRequestJson(email)))
                .hasStatus(200);

        // Extract and verify email
        String token = extractTokenFromMailOutbox(email);
        assertThat(mvc.get().uri("/api/v1/auth/verify-email?token=" + token)).hasStatus(200);

        // Login
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
        assertThat(accessToken).isNotBlank();

        // Accept terms to get a fully authorized token
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

        return newAccessToken;
    }

    /**
     * Creates an API key with the given scopes and returns the raw key value.
     *
     * <p>The raw key is only returned once at creation time.
     */
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
    @DisplayName("POST /api/v1/sync/pull - Scope Enforcement")
    class PullScopeTests {

        @Test
        @DisplayName("Should return 200 when API key has SYNC scope")
        void shouldReturn200_whenApiKeyHasSyncScope() throws Exception {
            String email = uniqueEmail();
            String jwt = registerVerifyAndLogin(email);
            String rawKey = createApiKey(jwt, "Sync Key", "SYNC");

            assertThat(
                            mvc.post()
                                    .uri(PULL_ENDPOINT)
                                    .with(csrf())
                                    .header("Authorization", "Bearer " + rawKey)
                                    .exchange())
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.success")
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("Should return 403 AUTH_020 when API key lacks SYNC scope")
        void shouldReturn403_whenApiKeyLacksSyncScope() throws Exception {
            String email = uniqueEmail();
            String jwt = registerVerifyAndLogin(email);
            String rawKey = createApiKey(jwt, "Read-Only Key", "READ");

            assertThat(
                            mvc.post()
                                    .uri(PULL_ENDPOINT)
                                    .with(csrf())
                                    .header("Authorization", "Bearer " + rawKey)
                                    .exchange())
                    .hasStatus(403)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("AUTH_020");
        }

        @Test
        @DisplayName("Should return 200 when API key has ADMIN scope")
        void shouldReturn200_whenApiKeyHasAdminScope() throws Exception {
            String email = uniqueEmail();
            String jwt = registerVerifyAndLogin(email);
            String rawKey = createApiKey(jwt, "Admin Key", "ADMIN");

            assertThat(
                            mvc.post()
                                    .uri(PULL_ENDPOINT)
                                    .with(csrf())
                                    .header("Authorization", "Bearer " + rawKey)
                                    .exchange())
                    .hasStatusOk();
        }

        @Test
        @DisplayName("Should return 200 when JWT user bypasses scope check")
        void shouldReturn200_whenJwtUserBypassesScopeCheck() throws Exception {
            String email = uniqueEmail();
            String jwt = registerVerifyAndLogin(email);

            assertThat(
                            mvc.post()
                                    .uri(PULL_ENDPOINT)
                                    .with(csrf())
                                    .header("Authorization", "Bearer " + jwt)
                                    .exchange())
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.success")
                    .isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/sync/push - Scope Enforcement")
    class PushScopeTests {

        @Test
        @DisplayName("Should return 200 when API key has SYNC scope")
        void shouldReturn200_whenApiKeyHasSyncScope() throws Exception {
            String email = uniqueEmail();
            String jwt = registerVerifyAndLogin(email);
            String rawKey = createApiKey(jwt, "Sync Key", "SYNC");

            assertThat(
                            mvc.post()
                                    .uri(PUSH_ENDPOINT)
                                    .with(csrf())
                                    .header("Authorization", "Bearer " + rawKey)
                                    .exchange())
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.success")
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("Should return 403 AUTH_020 when API key lacks SYNC scope")
        void shouldReturn403_whenApiKeyLacksSyncScope() throws Exception {
            String email = uniqueEmail();
            String jwt = registerVerifyAndLogin(email);
            String rawKey = createApiKey(jwt, "Write-Only Key", "WRITE");

            assertThat(
                            mvc.post()
                                    .uri(PUSH_ENDPOINT)
                                    .with(csrf())
                                    .header("Authorization", "Bearer " + rawKey)
                                    .exchange())
                    .hasStatus(403)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("AUTH_020");
        }

        @Test
        @DisplayName("Should return 200 when API key has ADMIN scope")
        void shouldReturn200_whenApiKeyHasAdminScope() throws Exception {
            String email = uniqueEmail();
            String jwt = registerVerifyAndLogin(email);
            String rawKey = createApiKey(jwt, "Admin Key", "ADMIN");

            assertThat(
                            mvc.post()
                                    .uri(PUSH_ENDPOINT)
                                    .with(csrf())
                                    .header("Authorization", "Bearer " + rawKey)
                                    .exchange())
                    .hasStatusOk();
        }

        @Test
        @DisplayName("Should return 200 when JWT user bypasses scope check")
        void shouldReturn200_whenJwtUserBypassesScopeCheck() throws Exception {
            String email = uniqueEmail();
            String jwt = registerVerifyAndLogin(email);

            assertThat(
                            mvc.post()
                                    .uri(PUSH_ENDPOINT)
                                    .with(csrf())
                                    .header("Authorization", "Bearer " + jwt)
                                    .exchange())
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.success")
                    .isEqualTo(true);
        }
    }
}
