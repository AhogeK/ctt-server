package com.ahogek.cttserver.sync;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * End-to-end integration tests for the bidirectional sync engine.
 *
 * <p>Verifies the full pull/push protocol through the real request pipeline with PostgreSQL, Redis,
 * and GreenMail test containers: push applies changes and advances the change log, pull delivers
 * increments and is idempotent, LWW converges on a single row, and the auth/validation boundaries
 * hold.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-25
 */
@BaseIntegrationTest
@TestPropertySource(properties = {"ctt.mail.outbox.poll-interval-ms=999999999"})
@DisplayName("Sync Engine Integration Tests")
class SyncIntegrationTest {

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ObjectMapper objectMapper;

    private static final String PASSWORD = "StrongPass123!";
    private static final String DISPLAY_NAME = "SyncTestUser";
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");

    private static final String PULL_ENDPOINT = "/api/v1/sync/pull";
    private static final String PUSH_ENDPOINT = "/api/v1/sync/push";

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM session_changes").update();
        jdbcClient.sql("DELETE FROM sync_cursors").update();
        jdbcClient.sql("DELETE FROM coding_sessions").update();
        jdbcClient.sql("DELETE FROM devices").update();
        jdbcClient.sql("DELETE FROM api_keys").update();
        jdbcClient.sql("DELETE FROM mail_outbox").update();
        jdbcClient.sql("DELETE FROM email_verification_tokens").update();
        jdbcClient.sql("DELETE FROM refresh_tokens").update();
        jdbcClient.sql("DELETE FROM users").update();
    }

    private String uniqueEmail() {
        return "sync-test." + UUID.randomUUID() + "@test.example";
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

    private UUID insertDevice(UUID userId) {
        UUID deviceId = UUID.randomUUID();
        jdbcClient
                .sql(
                        """
                        INSERT INTO devices
                            (id, user_id, device_name, platform, ide_name, ide_version, app_version,
                             last_ip, created_at, last_seen_at, updated_at)
                        VALUES (?, ?, 'sync-device', 'macos', 'IntelliJ IDEA', '2026.1', '1.0.0',
                                '127.0.0.1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                .param(deviceId)
                .param(userId)
                .update();
        return deviceId;
    }

    private String pushBody(UUID deviceId, String... sessionJson) {
        String sessions = String.join(", ", sessionJson);
        return """
                {"deviceId": "%s", "sessions": [%s]}
                """
                .formatted(deviceId, sessions);
    }

    private String sessionJson(
            String sessionUuid, int clientVersion, String clientModifiedAt, boolean deleted) {
        return """
                {
                  "sessionUuid": "%s",
                  "projectName": "ctt-server",
                  "language": "Java",
                  "startTime": "2026-08-25T09:00:00Z",
                  "endTime": "2026-08-25T10:00:00Z",
                  "clientModifiedAt": "%s",
                  "clientVersion": %d,
                  "deleted": %s
                }
                """
                .formatted(sessionUuid, clientModifiedAt, clientVersion, deleted);
    }

    private JsonNode pull(String authHeader, UUID deviceId, long cursor) throws Exception {
        String response =
                mvc.post()
                        .uri(PULL_ENDPOINT)
                        .with(csrf())
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"deviceId": "%s", "lastPulledChangeId": %d}
                                """
                                        .formatted(deviceId, cursor))
                        .exchange()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    @Nested
    @DisplayName("End-to-end pull/push protocol")
    class ProtocolTests {

        @Test
        @DisplayName("Should push a batch, pull increments, then pull empty (idempotent)")
        void shouldPushThenPullIncrementsThenPullEmpty() throws Exception {
            String email = uniqueEmail();
            String jwt = registerVerifyAndLogin(email);
            UUID userId =
                    jdbcClient
                            .sql("SELECT id FROM users WHERE email = ?")
                            .param(email)
                            .query(UUID.class)
                            .single();
            UUID deviceId = insertDevice(userId);

            String sessionUuid1 = UUID.randomUUID().toString();
            String sessionUuid2 = UUID.randomUUID().toString();

            String pushResponse =
                    mvc.post()
                            .uri(PUSH_ENDPOINT)
                            .with(csrf())
                            .header("Authorization", "Bearer " + jwt)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    pushBody(
                                            deviceId,
                                            sessionJson(
                                                    sessionUuid1, 1, "2026-08-25T10:00:00Z", false),
                                            sessionJson(
                                                    sessionUuid2,
                                                    1,
                                                    "2026-08-25T10:00:00Z",
                                                    false)))
                            .exchange()
                            .getResponse()
                            .getContentAsString();
            long pushCursor =
                    objectMapper.readTree(pushResponse).path("data").path("nextCursor").asLong();
            assertThat(pushCursor).isGreaterThan(0);

            JsonNode firstPull = pull("Bearer " + jwt, deviceId, 0);
            assertThat(firstPull.path("changes")).hasSize(2);
            assertThat(firstPull.path("nextCursor").asLong()).isEqualTo(pushCursor);
            assertThat(firstPull.path("changes").get(0).path("projectName").asText())
                    .isEqualTo("ctt-server");
            assertThat(firstPull.path("changes").get(0).path("op").asText()).isEqualTo("UPSERT");

            JsonNode secondPull = pull("Bearer " + jwt, deviceId, pushCursor);
            assertThat(secondPull.path("changes")).isEmpty();
            assertThat(secondPull.path("nextCursor").asLong()).isEqualTo(pushCursor);
        }

        @Test
        @DisplayName("Should converge on a single row when the same session is pushed twice")
        void shouldConvergeOnSingleRow_whenSameSessionPushedTwice() throws Exception {
            String email = uniqueEmail();
            String jwt = registerVerifyAndLogin(email);
            UUID userId =
                    jdbcClient
                            .sql("SELECT id FROM users WHERE email = ?")
                            .param(email)
                            .query(UUID.class)
                            .single();
            UUID deviceId = insertDevice(userId);
            String sessionUuid = UUID.randomUUID().toString();

            assertThat(
                            mvc.post()
                                    .uri(PUSH_ENDPOINT)
                                    .with(csrf())
                                    .header("Authorization", "Bearer " + jwt)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            pushBody(
                                                    deviceId,
                                                    sessionJson(
                                                            sessionUuid,
                                                            1,
                                                            "2026-08-25T09:30:00Z",
                                                            false)))
                                    .exchange())
                    .hasStatusOk();

            assertThat(
                            mvc.post()
                                    .uri(PUSH_ENDPOINT)
                                    .with(csrf())
                                    .header("Authorization", "Bearer " + jwt)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            pushBody(
                                                    deviceId,
                                                    sessionJson(
                                                            sessionUuid,
                                                            2,
                                                            "2026-08-25T10:00:00Z",
                                                            false)))
                                    .exchange())
                    .hasStatusOk();

            Long rowCount =
                    jdbcClient
                            .sql("SELECT COUNT(*) FROM coding_sessions WHERE session_uuid = ?")
                            .param(UUID.fromString(sessionUuid))
                            .query(Long.class)
                            .single();
            assertThat(rowCount).isEqualTo(1);

            Integer clientVersion =
                    jdbcClient
                            .sql(
                                    "SELECT client_version FROM coding_sessions WHERE session_uuid = ?")
                            .param(UUID.fromString(sessionUuid))
                            .query(Integer.class)
                            .single();
            assertThat(clientVersion).isEqualTo(2);

            JsonNode pull = pull("Bearer " + jwt, deviceId, 0);
            assertThat(pull.path("changes")).hasSize(2);
            assertThat(pull.path("changes").get(1).path("clientVersion").asInt()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Authentication and authorization boundaries")
    class AuthBoundaryTests {

        @Test
        @DisplayName("Should return 401 when no authentication is provided")
        void shouldReturn401_whenNotAuthenticated() {
            assertThat(mvc.post().uri(PULL_ENDPOINT).with(csrf()).exchange()).hasStatus(401);
            assertThat(mvc.post().uri(PUSH_ENDPOINT).with(csrf()).exchange()).hasStatus(401);
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
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"deviceId": "%s", "lastPulledChangeId": 0}
                                            """
                                                    .formatted(UUID.randomUUID()))
                                    .exchange())
                    .hasStatus(403)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("AUTH_020");
        }

        @Test
        @DisplayName("Should return 404 COMMON_002 when device is not owned by the caller")
        void shouldReturn404_whenDeviceNotOwned() throws Exception {
            String email = uniqueEmail();
            String jwt = registerVerifyAndLogin(email);
            UUID userId =
                    jdbcClient
                            .sql("SELECT id FROM users WHERE email = ?")
                            .param(email)
                            .query(UUID.class)
                            .single();
            insertDevice(userId);
            UUID foreignDeviceId = UUID.randomUUID();

            assertThat(
                            mvc.post()
                                    .uri(PULL_ENDPOINT)
                                    .with(csrf())
                                    .header("Authorization", "Bearer " + jwt)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"deviceId": "%s", "lastPulledChangeId": 0}
                                            """
                                                    .formatted(foreignDeviceId))
                                    .exchange())
                    .hasStatus(404)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("COMMON_002");

            assertThat(
                            mvc.post()
                                    .uri(PUSH_ENDPOINT)
                                    .with(csrf())
                                    .header("Authorization", "Bearer " + jwt)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            pushBody(
                                                    foreignDeviceId,
                                                    sessionJson(
                                                            UUID.randomUUID().toString(),
                                                            1,
                                                            "2026-08-25T10:00:00Z",
                                                            false)))
                                    .exchange())
                    .hasStatus(404)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("COMMON_002");
        }
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("Should return 400 when push sessions list is empty")
        void shouldReturn400_whenSessionsEmpty() throws Exception {
            String email = uniqueEmail();
            String jwt = registerVerifyAndLogin(email);

            assertThat(
                            mvc.post()
                                    .uri(PUSH_ENDPOINT)
                                    .with(csrf())
                                    .header("Authorization", "Bearer " + jwt)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"deviceId": "%s", "sessions": []}
                                            """
                                                    .formatted(UUID.randomUUID()))
                                    .exchange())
                    .hasStatus(400);
        }

        @Test
        @DisplayName("Should return 400 when pull cursor is negative")
        void shouldReturn400_whenPullCursorNegative() throws Exception {
            String email = uniqueEmail();
            String jwt = registerVerifyAndLogin(email);

            assertThat(
                            mvc.post()
                                    .uri(PULL_ENDPOINT)
                                    .with(csrf())
                                    .header("Authorization", "Bearer " + jwt)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"deviceId": "%s", "lastPulledChangeId": -1}
                                            """
                                                    .formatted(UUID.randomUUID()))
                                    .exchange())
                    .hasStatus(400);
        }
    }
}
