package com.ahogek.cttserver.auth;

import com.ahogek.cttserver.auth.dto.LoginRequest;
import com.ahogek.cttserver.auth.dto.UserRegisterRequest;
import com.ahogek.cttserver.auth.repository.RefreshTokenRepository;
import com.ahogek.cttserver.common.BaseIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * End-to-end tests for the login-session endpoints.
 *
 * <p>Listing reports one entry per login — rotation continues a session, so refreshing must not add
 * a row — and revoking one session must stop exactly that session: its next refresh fails without
 * taking the account's other sessions down with it.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-19
 */
@BaseIntegrationTest
@TestPropertySource(properties = {"ctt.mail.outbox.poll-interval-ms=999999999"})
@DisplayName("Login session endpoints")
class LoginSessionEndpointsIntegrationTest {

    private static final String PASSWORD = "StrongPass123!";
    private static final String DISPLAY_NAME = "SessionListUser";
    private static final String USER_AGENT = "ctt-test/1.0 (login-sessions)";
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM devices").update();
        jdbcClient.sql("DELETE FROM mail_outbox").update();
        jdbcClient.sql("DELETE FROM email_verification_tokens").update();
        jdbcClient.sql("DELETE FROM refresh_tokens").update();
        jdbcClient.sql("DELETE FROM audit_logs").update();
        jdbcClient.sql("DELETE FROM users").update();
    }

    @Test
    @DisplayName("Should report one entry per login, newest activity first")
    void shouldListOneEntryPerLogin_whenSessionsAreListed() throws Exception {
        UUID userId = register(userEmail());
        UUID deviceId = UUID.randomUUID();
        registerDevice(userId, deviceId);

        String[] firstLogin = login(userId, UUID.randomUUID().toString());
        String[] secondLogin = login(userId, deviceId.toString());
        refresh(secondLogin[1]);

        JsonNode sessions = listSessions(secondLogin[0]);

        assertThat(sessions).hasSize(2);
        JsonNode plugin = sessions.get(0);
        assertThat(plugin.path("id").asText()).isEqualTo(sessionIdOf(secondLogin[0]));
        assertThat(plugin.path("kind").asText()).isEqualTo("PLUGIN");
        assertThat(plugin.path("deviceName").asText()).isEqualTo("test-device");
        assertThat(plugin.path("ideName").asText()).isEqualTo("IntelliJ IDEA");
        assertThat(plugin.path("current").asBoolean()).as("session entry: %s", plugin).isTrue();
        assertThat(plugin.path("userAgent").asText()).isEqualTo(USER_AGENT);
        assertThat(plugin.path("lastUsedAt").isNull()).isFalse();

        JsonNode web = sessions.get(1);
        assertThat(web.path("kind").asText()).isEqualTo("WEB");
        assertThat(web.has("deviceName")).as("web entry: %s", web).isTrue();
        assertThat(web.get("deviceName").isNull()).isTrue();
        assertThat(web.has("lastUsedAt")).isTrue();
        assertThat(web.get("lastUsedAt").isNull()).isTrue();
        assertThat(web.path("current").asBoolean()).isFalse();
        assertThat(web.path("id").asText()).isNotEqualTo(plugin.path("id").asText());
        assertThat(firstLogin[0]).isNotBlank();
    }

    @Test
    @DisplayName("Should stop only the revoked session's refresh token")
    void shouldStopOnlyTheRevokedSession_whenOneIsRevoked() throws Exception {
        UUID userId = register(userEmail());
        String[] keeper = login(userId, UUID.randomUUID().toString());
        String[] victim = login(userId, UUID.randomUUID().toString());

        revokeSession(keeper[0], victimSessionId(keeper[0]));

        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/refresh")
                                .with(csrf())
                                .header("User-Agent", USER_AGENT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"" + victim[1] + "\"}")
                                .exchange())
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_003");

        refresh(keeper[1]);
    }

    @Test
    @DisplayName("Should revoke the calling session itself when asked")
    void shouldRevokeCurrentSession_whenAsked() throws Exception {
        UUID userId = register(userEmail());
        String[] tokens = login(userId, UUID.randomUUID().toString());

        revokeSession(tokens[0], currentSessionId(tokens[0]));

        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/refresh")
                                .with(csrf())
                                .header("User-Agent", USER_AGENT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"" + tokens[1] + "\"}")
                                .exchange())
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_003");
    }

    @Test
    @DisplayName("Should require authentication when listing sessions")
    void shouldRequireAuthentication_whenListingSessions() {
        assertThat(mvc.get().uri("/api/v1/users/me/sessions").exchange()).hasStatus(401);
    }

    @Test
    @DisplayName("Should serialize a refresh against a revocation that is in flight")
    void shouldSerializeRefresh_whenRevocationIsInFlight() throws Exception {
        UUID userId = register(userEmail());
        String[] tokens = login(userId, UUID.randomUUID().toString());
        UUID sessionId = UUID.fromString(currentSessionId(tokens[0]));

        CountDownLatch lockHeld = new CountDownLatch(1);
        Thread revoker =
                new Thread(
                        () ->
                                transactionTemplate.executeWithoutResult(
                                        status -> {
                                            refreshTokenRepository.lockSession(sessionId);
                                            lockHeld.countDown();
                                            try {
                                                Thread.sleep(400);
                                            } catch (InterruptedException e) {
                                                Thread.currentThread().interrupt();
                                            }
                                            refreshTokenRepository.revokeSession(
                                                    userId, sessionId, Instant.now());
                                        }));
        revoker.start();
        assertThat(lockHeld.await(5, TimeUnit.SECONDS)).as("lock not taken").isTrue();

        // Blocks on the session lock until the revocation commits, so it must observe the revoked
        // row rather than rotate it — the exact interleaving the lock exists to prevent.
        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/refresh")
                                .with(csrf())
                                .header("User-Agent", USER_AGENT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"" + tokens[1] + "\"}")
                                .exchange())
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_003");
        revoker.join(5000);
    }

    @Test
    @DisplayName("Should leave no sessions when logging out everywhere")
    void shouldLeaveNoSessions_whenLoggingOutEverywhere() throws Exception {
        UUID userId = register(userEmail());
        String[] first = login(userId, UUID.randomUUID().toString());
        login(userId, UUID.randomUUID().toString());
        assertThat(listSessions(first[0])).hasSize(2);

        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/logout-all")
                                .with(csrf())
                                .header("Authorization", "Bearer " + first[0])
                                .exchange())
                .hasStatusOk();

        assertThat(listSessions(first[0])).isEmpty();
    }

    @Test
    @DisplayName("Should not reveal a session owned by another account")
    void shouldNotRevealForeignSession_whenRevoking() throws Exception {
        UUID ownerId = register(userEmail());
        UUID strangerId = register(userEmail());
        String[] ownerTokens = login(ownerId, UUID.randomUUID().toString());
        String[] strangerTokens = login(strangerId, UUID.randomUUID().toString());
        String ownerSessionId = currentSessionId(ownerTokens[0]);

        assertThat(
                        mvc.delete()
                                .uri("/api/v1/users/me/sessions/" + ownerSessionId)
                                .with(csrf())
                                .header("Authorization", "Bearer " + strangerTokens[0])
                                .exchange())
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("COMMON_002");

        refresh(ownerTokens[1]);
    }

    private JsonNode listSessions(String accessToken) throws Exception {
        String response =
                mvc.get()
                        .uri("/api/v1/users/me/sessions")
                        .header("Authorization", "Bearer " + accessToken)
                        .exchange()
                        .getResponse()
                        .getContentAsString();
        JsonNode data = objectMapper.readTree(response).path("data");
        assertThat(data.isArray()).as("session list failed: %s", response).isTrue();
        return data;
    }

    private void revokeSession(String accessToken, String sessionId) throws Exception {
        assertThat(
                        mvc.delete()
                                .uri("/api/v1/users/me/sessions/" + sessionId)
                                .with(csrf())
                                .header("Authorization", "Bearer " + accessToken)
                                .exchange())
                .hasStatus(204);
    }

    private String currentSessionId(String accessToken) {
        return sessionIdOf(accessToken);
    }

    private String victimSessionId(String accessToken) throws Exception {
        JsonNode sessions = listSessions(accessToken);
        for (JsonNode session : sessions) {
            if (!session.path("current").asBoolean()) {
                return session.path("id").asText();
            }
        }
        throw new AssertionError("expected a session that is not the caller's own");
    }

    @Test
    @DisplayName("Should truncate an oversized User-Agent to the stored width")
    void shouldTruncateUserAgent_whenItExceedsTheColumnWidth() throws Exception {
        UUID userId = register(userEmail());
        String email =
                jdbcClient
                        .sql("SELECT email FROM users WHERE id = ?")
                        .param(userId)
                        .query(String.class)
                        .single();
        LoginRequest request =
                new LoginRequest(email, PASSWORD, UUID.randomUUID().toString(), null);

        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/login")
                                .with(csrf())
                                .header("User-Agent", "x".repeat(600))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .hasStatus(200);

        Integer stored =
                jdbcClient
                        .sql("SELECT length(user_agent) FROM refresh_tokens WHERE user_id = ?")
                        .param(userId)
                        .query(Integer.class)
                        .single();
        assertThat(stored).isEqualTo(512);
    }

    @Test
    @DisplayName("Should keep the session User-Agent across rotation")
    void shouldKeepUserAgent_whenTokenRotates() throws Exception {
        UUID userId = register(userEmail());
        String[] tokens = login(userId, UUID.randomUUID().toString());

        String rotated = refresh(tokens[1]);

        String stored =
                jdbcClient
                        .sql(
                                "SELECT user_agent FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL ORDER BY created_at DESC LIMIT 1")
                        .param(userId)
                        .query(String.class)
                        .single();
        assertThat(rotated).isNotBlank();
        assertThat(stored).isEqualTo(USER_AGENT);
    }

    /** Reads the session id out of the access token's {@code sid} claim without verifying it. */
    private String sessionIdOf(String accessToken) {
        String[] parts = accessToken.split("\\.");
        assertThat(parts).as("unexpected token shape").hasSize(3);
        try {
            String payload =
                    new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            return objectMapper.readTree(payload).path("sid").asText();
        } catch (Exception e) {
            throw new AssertionError("cannot read the sid claim", e);
        }
    }

    /**
     * A device row is what makes a login a plugin session — the audience rule reads the registry.
     */
    private void registerDevice(UUID userId, UUID deviceId) {
        jdbcClient
                .sql(
                        "INSERT INTO devices (id, user_id, device_name, platform, ide_name) VALUES (?, ?, ?, ?, ?)")
                .params(deviceId, userId, "test-device", "macOS", "IntelliJ IDEA")
                .update();
    }

    private String userEmail() {
        return "sessions." + UUID.randomUUID() + "@test.example";
    }

    private UUID register(String email) throws Exception {
        UserRegisterRequest register =
                new UserRegisterRequest(email, DISPLAY_NAME, PASSWORD, "1.0.0", null);
        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/register")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(register)))
                .hasStatus(200);

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
        assertThat(mvc.get().uri("/api/v1/auth/verify-email?token=" + matcher.group(1)))
                .hasStatus(200);

        return jdbcClient
                .sql("SELECT id FROM users WHERE email = ?")
                .param(email)
                .query(UUID.class)
                .single();
    }

    /** Returns the tokens a login issues: {@code [accessToken, refreshToken]}. */
    private String[] login(UUID userId, String deviceId) throws Exception {
        String email =
                jdbcClient
                        .sql("SELECT email FROM users WHERE id = ?")
                        .param(userId)
                        .query(String.class)
                        .single();
        LoginRequest request = new LoginRequest(email, PASSWORD, deviceId, null);
        String response =
                mvc.post()
                        .uri("/api/v1/auth/login")
                        .with(csrf())
                        .header("User-Agent", USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .exchange()
                        .getResponse()
                        .getContentAsString();
        String accessToken =
                objectMapper.readTree(response).path("data").path("accessToken").asText();
        String refreshToken =
                objectMapper.readTree(response).path("data").path("refreshToken").asText();
        assertThat(accessToken).as("login failed: %s", response).isNotBlank();
        assertThat(refreshToken).as("login failed: %s", response).isNotBlank();
        return new String[] {accessToken, refreshToken};
    }

    private String refresh(String rawRefreshToken) throws Exception {
        String body = "{\"refreshToken\":\"" + rawRefreshToken + "\"}";
        String response =
                mvc.post()
                        .uri("/api/v1/auth/refresh")
                        .with(csrf())
                        .header("User-Agent", USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .exchange()
                        .getResponse()
                        .getContentAsString();
        String rotated = objectMapper.readTree(response).path("data").path("refreshToken").asText();
        assertThat(rotated).as("refresh failed: %s", response).isNotBlank();
        return rotated;
    }
}
