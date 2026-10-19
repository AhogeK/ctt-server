package com.ahogek.cttserver.auth;

import com.ahogek.cttserver.auth.dto.LoginRequest;
import com.ahogek.cttserver.auth.dto.UserRegisterRequest;
import com.ahogek.cttserver.auth.entity.RefreshToken;
import com.ahogek.cttserver.common.BaseIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * End-to-end tests for the session identity carried by refresh tokens.
 *
 * <p>A login is a session: every row produced by that login — including the rows later created by
 * rotation — must carry the same {@code session_id}, so "one login = one session" survives
 * refreshing. The audience ({@code WEB} / {@code PLUGIN}) and its TTL are decided at login from the
 * device binding, not hardcoded, and the access token names its session through the {@code sid}
 * claim.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-19
 */
@BaseIntegrationTest
@TestPropertySource(properties = {"ctt.mail.outbox.poll-interval-ms=999999999"})
@DisplayName("Refresh-token session identity")
class SessionIdentityIntegrationTest {

    private static final String PASSWORD = "StrongPass123!";
    private static final String DISPLAY_NAME = "SessionTestUser";
    private static final String USER_AGENT = "ctt-test/1.0 (session-identity)";
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtDecoder jwtDecoder;

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
    @DisplayName("Should give each login its own session and record the client user agent")
    void shouldGiveEachLoginItsOwnSession_whenLoggingInTwice() throws Exception {
        UUID userId = register(userEmail());

        String deviceId = UUID.randomUUID().toString();
        String[] first = login(userId, deviceId);
        String[] second = login(userId, deviceId);

        List<Map<String, Object>> rows =
                jdbcClient
                        .sql(
                                "SELECT session_id, issued_for, user_agent FROM refresh_tokens WHERE user_id = ? ORDER BY created_at")
                        .param(userId)
                        .query()
                        .listOfRows();

        assertThat(first[0]).isNotBlank();
        assertThat(second[0]).isNotBlank();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("session_id")).isNotNull();
        assertThat(rows.get(1).get("session_id"))
                .isNotNull()
                .isNotEqualTo(rows.get(0).get("session_id"));
        assertThat(rows).allSatisfy(row -> assertThat(row.get("issued_for")).isEqualTo("WEB"));
        assertThat(rows).allSatisfy(row -> assertThat(row.get("user_agent")).isEqualTo(USER_AGENT));
    }

    @Test
    @DisplayName("Should mark a device-bound login as a plugin session with the plugin TTL")
    void shouldMarkDeviceBoundLoginAsPluginSession_whenDeviceIsRegistered() throws Exception {
        UUID userId = register(userEmail());
        UUID deviceId = UUID.randomUUID();
        registerDevice(userId, deviceId);

        login(userId, deviceId.toString());

        Map<String, Object> row =
                jdbcClient
                        .sql(
                                """
                                SELECT issued_for,
                                       expires_at > now() + interval '13 days' AS longer_than_13d,
                                       expires_at < now() + interval '15 days' AS shorter_than_15d
                                FROM refresh_tokens WHERE user_id = ?
                                """)
                        .param(userId)
                        .query()
                        .singleRow();

        assertThat(row.get("issued_for")).isEqualTo("PLUGIN");
        assertThat(row.get("longer_than_13d")).isEqualTo(true);
        assertThat(row.get("shorter_than_15d")).isEqualTo(true);
    }

    @Test
    @DisplayName("Should keep the session identity, audience and TTL when the token rotates")
    void shouldKeepSessionIdentity_whenTokenRotates() throws Exception {
        UUID userId = register(userEmail());
        UUID deviceId = UUID.randomUUID();
        registerDevice(userId, deviceId);
        String refreshToken = login(userId, deviceId.toString())[1];

        String rotated = refresh(refreshToken);
        assertThat(rotated).isNotBlank();

        List<Map<String, Object>> rows =
                jdbcClient
                        .sql(
                                """
                                SELECT session_id, issued_for, user_agent,
                                       expires_at > now() + interval '13 days' AS longer_than_13d,
                                       expires_at < now() + interval '15 days' AS shorter_than_15d
                                FROM refresh_tokens WHERE user_id = ? ORDER BY created_at
                                """)
                        .param(userId)
                        .query()
                        .listOfRows();

        assertThat(rows).hasSize(2);
        Map<String, Object> original = rows.get(0);
        Map<String, Object> rotatedRow = rows.get(1);
        assertThat(rotatedRow.get("session_id")).isEqualTo(original.get("session_id"));
        assertThat(rotatedRow.get("issued_for")).isEqualTo("PLUGIN");
        assertThat(rotatedRow.get("user_agent")).isEqualTo(original.get("user_agent"));
        assertThat(rotatedRow.get("longer_than_13d")).isEqualTo(true);
        assertThat(rotatedRow.get("shorter_than_15d")).isEqualTo(true);
    }

    @Test
    @DisplayName("Should name the session in the access token through the sid claim")
    void shouldEmbedSessionInAccessToken_whenLoginIssuesIt() throws Exception {
        UUID userId = register(userEmail());

        String accessToken = login(userId, UUID.randomUUID().toString())[0];

        UUID sessionId =
                jdbcClient
                        .sql("SELECT session_id FROM refresh_tokens WHERE user_id = ?")
                        .param(userId)
                        .query(UUID.class)
                        .single();
        assertThat(jwtDecoder.decode(accessToken).getClaimAsString("sid"))
                .isEqualTo(sessionId.toString());
    }

    @Test
    @DisplayName("Should keep the plugin audience when the session accepts new terms")
    void shouldKeepPluginAudience_whenAcceptingTerms() throws Exception {
        UUID userId = register(userEmail());
        UUID deviceId = UUID.randomUUID();
        registerDevice(userId, deviceId);
        String[] tokens = login(userId, deviceId.toString());

        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/terms/accept")
                                .with(csrf())
                                .header("User-Agent", USER_AGENT)
                                .header("Authorization", "Bearer " + tokens[0])
                                .exchange())
                .hasStatusOk();

        Map<String, Object> newest =
                jdbcClient
                        .sql(
                                """
                                 SELECT issued_for, device_id,
                                        expires_at > now() + interval '13 days' AS longer_than_13d,
                                        expires_at < now() + interval '15 days' AS shorter_than_15d
                                 FROM refresh_tokens WHERE user_id = ? ORDER BY created_at DESC LIMIT 1
                                 """)
                        .param(userId)
                        .query()
                        .singleRow();

        assertThat(newest.get("issued_for")).isEqualTo(RefreshToken.AUDIENCE_PLUGIN);
        assertThat(newest.get("device_id")).isEqualTo(deviceId);
        assertThat(newest.get("longer_than_13d")).isEqualTo(true);
        assertThat(newest.get("shorter_than_15d")).isEqualTo(true);
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
        return "session." + UUID.randomUUID() + "@test.example";
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
        return objectMapper.readTree(response).path("data").path("refreshToken").asText();
    }
}
