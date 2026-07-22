package com.ahogek.cttserver.auth.apikey;

import com.ahogek.cttserver.auth.dto.LoginRequest;
import com.ahogek.cttserver.auth.dto.UserRegisterRequest;
import com.ahogek.cttserver.common.BaseIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * End-to-end integration tests for the API Key lifecycle.
 *
 * <p>Covers six security and correctness scenarios through the full request pipeline:
 *
 * <ul>
 *   <li>{@code happy_path} — create → authenticate → use → revoke
 *   <li>{@code revoke} — using a revoked key returns 403 AUTH_012
 *   <li>{@code expire} — using an expired key returns 401 AUTH_011 (via time travel)
 *   <li>{@code scope_deny} — accessing a SYNC endpoint with a READ-only key returns 403 AUTH_020
 *   <li>{@code BOLA} — User B trying to revoke User A's key returns 401 AUTH_010
 *   <li>{@code rate_limit} — the 11th failed authentication from the same IP returns 429
 * </ul>
 *
 * <p>Uses full Spring context with PostgreSQL, Redis, and GreenMail test containers.
 *
 * <p><b>Note on BOLA:</b> Per project consistency, this implementation returns {@code 401 AUTH_010}
 * rather than {@code 404} when a key does not exist or belongs to another user. The behavior is
 * documented in {@code ApiKeyController} and {@code ApiKeyServiceImpl} — both "not found" and "not
 * yours" are intentionally indistinguishable to prevent UUID enumeration attacks.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-21
 */
@BaseIntegrationTest
@TestPropertySource(properties = {"ctt.mail.outbox.poll-interval-ms=999999999"})
@DisplayName("API Key Lifecycle Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiKeyIntegrationTest {

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StringRedisTemplate redisTemplate;

    private static final String PASSWORD = "StrongPass123!";
    private static final String DISPLAY_NAME = "ApiKeyTestUser";
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");

    private static final String API_KEYS_ENDPOINT = "/api/v1/auth/api-keys";
    private static final String PULL_ENDPOINT = "/api/v1/sync/pull";
    private static final String AUTH_FAILURE_BUCKET_KEY = "api_key_auth_fail:127.0.0.1";

    @BeforeEach
    void clearAuthFailureBucket() {
        redisTemplate.delete(AUTH_FAILURE_BUCKET_KEY);
    }

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM api_keys").update();
        jdbcClient.sql("DELETE FROM mail_outbox").update();
        jdbcClient.sql("DELETE FROM email_verification_tokens").update();
        jdbcClient.sql("DELETE FROM refresh_tokens").update();
        jdbcClient.sql("DELETE FROM users").update();
        redisTemplate.delete(AUTH_FAILURE_BUCKET_KEY);
    }

    private String uniqueEmail() {
        return "apikey-integration." + UUID.randomUUID() + "@test.example";
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

    private String registerVerifyAndLogin(String email) throws Exception {
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
        assertThat(accessToken).isNotBlank();

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

    private CreatedKey createApiKey(String jwt, String name, String... scopes) throws Exception {
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
                        .uri(API_KEYS_ENDPOINT)
                        .with(csrf())
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .exchange()
                        .getResponse()
                        .getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");
        String rawKey = data.path("rawKey").asText();
        String id = data.path("apiKey").path("id").asText();
        assertThat(rawKey).as("API key creation must return rawKey").isNotBlank();
        assertThat(id).as("API key creation must return apiKey.id").isNotBlank();
        return new CreatedKey(rawKey, UUID.fromString(id));
    }

    private CreatedKey createApiKeyWithExpiry(
            String jwt, String name, OffsetDateTime expiresAt, String... scopes) throws Exception {
        String scopesJson =
                "["
                        + String.join(
                                ", ", Arrays.stream(scopes).map(s -> "\"" + s + "\"").toList())
                        + "]";

        String body =
                """
                {"name": "%s", "scopes": %s, "expiresAt": "%s"}
                """
                        .formatted(name, scopesJson, expiresAt.toString());

        String response =
                mvc.post()
                        .uri(API_KEYS_ENDPOINT)
                        .with(csrf())
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .exchange()
                        .getResponse()
                        .getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");
        String rawKey = data.path("rawKey").asText();
        String id = data.path("apiKey").path("id").asText();
        assertThat(rawKey).as("API key creation must return rawKey").isNotBlank();
        assertThat(id).as("API key creation must return apiKey.id").isNotBlank();
        return new CreatedKey(rawKey, UUID.fromString(id));
    }

    private record CreatedKey(String rawKey, UUID id) {}

    @Nested
    @Order(1)
    @DisplayName("happy_path: create → authenticate → use → revoke")
    class HappyPathTests {

        @Test
        @DisplayName("Should complete full lifecycle without auth errors")
        void shouldCompleteFullLifecycle() throws Exception {
            String email = uniqueEmail();
            String jwt = registerVerifyAndLogin(email);
            CreatedKey created = createApiKey(jwt, "MacBook Pro", "READ", "WRITE", "SYNC");

            assertThat(
                            mvc.get()
                                    .uri(API_KEYS_ENDPOINT)
                                    .header("Authorization", "Bearer " + created.rawKey()))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.success")
                    .isEqualTo(true);

            assertThat(
                            mvc.delete()
                                    .uri(API_KEYS_ENDPOINT + "/" + created.id())
                                    .with(csrf())
                                    .header("Authorization", "Bearer " + jwt))
                    .hasStatus(204);

            assertThat(
                            mvc.get()
                                    .uri(API_KEYS_ENDPOINT)
                                    .header("Authorization", "Bearer " + created.rawKey()))
                    .hasStatus(403)
                    .bodyJson()
                    .extractingPath("$.data.code")
                    .isEqualTo("AUTH_012");
        }
    }

    @Nested
    @Order(2)
    @DisplayName("revoke: using a revoked key returns 403 AUTH_012")
    class RevokeTests {

        @Test
        @DisplayName("Should return 403 AUTH_012 after revoke")
        void shouldReturn403_whenApiKeyRevoked() throws Exception {
            String email = uniqueEmail();
            String jwt = registerVerifyAndLogin(email);
            CreatedKey created = createApiKey(jwt, "Revoke Test Key", "READ");

            assertThat(
                            mvc.delete()
                                    .uri(API_KEYS_ENDPOINT + "/" + created.id())
                                    .with(csrf())
                                    .header("Authorization", "Bearer " + jwt))
                    .hasStatus(204);

            assertThat(
                            mvc.get()
                                    .uri(API_KEYS_ENDPOINT)
                                    .header("Authorization", "Bearer " + created.rawKey()))
                    .hasStatus(403)
                    .bodyJson()
                    .extractingPath("$.data.code")
                    .isEqualTo("AUTH_012");
        }
    }

    @Nested
    @Order(3)
    @DisplayName("expire: using an expired key returns 401 AUTH_011")
    class ExpireTests {

        @Test
        @DisplayName("Should return 401 AUTH_011 when key has expired")
        void shouldReturn401_whenApiKeyExpired() throws Exception {
            String email = uniqueEmail();
            String jwt = registerVerifyAndLogin(email);

            OffsetDateTime expiresAt =
                    OffsetDateTime.ofInstant(
                            Instant.now().plus(1, ChronoUnit.SECONDS), ZoneOffset.UTC);
            CreatedKey created = createApiKeyWithExpiry(jwt, "Expiring Key", expiresAt, "READ");

            Thread.sleep(1500);

            assertThat(
                            mvc.get()
                                    .uri(API_KEYS_ENDPOINT)
                                    .header("Authorization", "Bearer " + created.rawKey()))
                    .hasStatus(401)
                    .bodyJson()
                    .extractingPath("$.data.code")
                    .isEqualTo("AUTH_011");
        }

        @Test
        @DisplayName("Should return 401 AUTH_011 when key is backdated via time travel")
        void shouldReturn401_whenApiKeyBackdated() throws Exception {
            String email = uniqueEmail();
            String jwt = registerVerifyAndLogin(email);
            CreatedKey created = createApiKey(jwt, "Backdated Key", "READ");

            jdbcClient
                    .sql(
                            "UPDATE api_keys SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 hour' WHERE id = ?")
                    .param(created.id())
                    .update();

            assertThat(
                            mvc.get()
                                    .uri(API_KEYS_ENDPOINT)
                                    .header("Authorization", "Bearer " + created.rawKey()))
                    .hasStatus(401)
                    .bodyJson()
                    .extractingPath("$.data.code")
                    .isEqualTo("AUTH_011");
        }
    }

    @Nested
    @Order(4)
    @DisplayName("scope_deny: accessing SYNC endpoint with READ-only key returns 403 AUTH_020")
    class ScopeDenyTests {

        @Test
        @DisplayName("Should return 403 AUTH_020 when key has only READ scope")
        void shouldReturn403_whenApiKeyLacksRequiredScope() throws Exception {
            String email = uniqueEmail();
            String jwt = registerVerifyAndLogin(email);
            CreatedKey readOnly = createApiKey(jwt, "Read-Only Key", "READ");

            assertThat(
                            mvc.post()
                                    .uri(PULL_ENDPOINT)
                                    .with(csrf())
                                    .header("Authorization", "Bearer " + readOnly.rawKey()))
                    .hasStatus(403)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("AUTH_020");
        }
    }

    @Nested
    @Order(5)
    @DisplayName("BOLA: User B trying to revoke User A's key returns 401 AUTH_010")
    class BolaTests {

        @Test
        @DisplayName(
                "Should return 401 AUTH_010 (BOLA protection) when User B revokes User A's key")
        void shouldReturn401_whenUserBTriesToRevokeUserAKey() throws Exception {
            String emailA = uniqueEmail();
            String emailB = uniqueEmail();
            String jwtA = registerVerifyAndLogin(emailA);
            String jwtB = registerVerifyAndLogin(emailB);

            CreatedKey userAKey = createApiKey(jwtA, "User A's Key", "READ", "WRITE");

            assertThat(
                            mvc.delete()
                                    .uri(API_KEYS_ENDPOINT + "/" + userAKey.id())
                                    .with(csrf())
                                    .header("Authorization", "Bearer " + jwtB))
                    .hasStatus(401)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("AUTH_010");

            assertThat(
                            mvc.get()
                                    .uri(API_KEYS_ENDPOINT)
                                    .header("Authorization", "Bearer " + userAKey.rawKey()))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.success")
                    .isEqualTo(true);
        }
    }

    @Nested
    @Order(6)
    @DisplayName("rate_limit: 11th failed authentication from the same IP returns 429")
    class RateLimitTests {

        @Test
        @DisplayName("Should return 429 RATE_LIMIT_001 after 10 failed auth attempts")
        void shouldReturn429_when11thFailedAuthAttempt() throws Exception {
            for (int attempt = 1; attempt <= 10; attempt++) {
                String badKey = "cttak_" + UUID.randomUUID();
                assertThat(
                                mvc.get()
                                        .uri(API_KEYS_ENDPOINT)
                                        .header("Authorization", "Bearer " + badKey))
                        .as("attempt %d", attempt)
                        .hasStatus(401)
                        .bodyJson()
                        .extractingPath("$.data.code")
                        .isEqualTo("AUTH_010");
            }

            String eleventhKey = "cttak_" + UUID.randomUUID();
            assertThat(
                            mvc.get()
                                    .uri(API_KEYS_ENDPOINT)
                                    .header("Authorization", "Bearer " + eleventhKey))
                    .hasStatus(429)
                    .bodyJson()
                    .extractingPath("$.data.code")
                    .isEqualTo("RATE_LIMIT_001");
        }
    }
}
