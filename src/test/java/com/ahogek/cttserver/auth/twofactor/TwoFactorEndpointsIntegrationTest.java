package com.ahogek.cttserver.auth.twofactor;

import com.ahogek.cttserver.auth.dto.LoginRequest;
import com.ahogek.cttserver.auth.dto.UserRegisterRequest;
import com.ahogek.cttserver.auth.service.JwtTokenProvider;
import com.ahogek.cttserver.auth.twofactor.service.TotpService;
import com.ahogek.cttserver.common.BaseIntegrationTest;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@BaseIntegrationTest
@TestPropertySource(properties = {"ctt.mail.outbox.poll-interval-ms=999999999"})
@DisplayName("Two-factor user endpoints")
class TwoFactorEndpointsIntegrationTest {

    private static final String PASSWORD = "StrongPass123!";
    private static final String DISPLAY_NAME = "TwoFactorUser";
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TotpService totpService;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM user_recovery_codes").update();
        jdbcClient.sql("DELETE FROM devices").update();
        jdbcClient.sql("DELETE FROM mail_outbox").update();
        jdbcClient.sql("DELETE FROM email_verification_tokens").update();
        jdbcClient.sql("DELETE FROM refresh_tokens").update();
        jdbcClient.sql("DELETE FROM audit_logs").update();
        jdbcClient.sql("DELETE FROM users").update();
    }

    @Test
    @DisplayName("Should complete the enable-confirm-disable lifecycle")
    void shouldCompleteLifecycle_whenPasswordAccountUsesTwoFactor() throws Exception {
        UUID userId = register();
        String[] first = login(userId);
        String token = first[0];
        String[] second = login(userId);
        String[] third = login(userId);

        assertThat(getStatus(token).path("enabled").asBoolean()).isFalse();
        assertThat(getStatus(token).path("hasPassword").asBoolean()).isTrue();

        assertThat(start(token, "wrong-password"))
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("USER_014");
        assertThat(start(token, null))
                .hasStatus(403)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("USER_013");

        JsonNode started = body(start(token, PASSWORD));
        String secret = started.path("secret").asText();
        assertThat(secret).isNotBlank();
        assertThat(started.path("otpauthUri").asText()).startsWith("otpauth://totp/");

        assertThat(confirm(token, "000000"))
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_027");

        String code = totpService.codeAt(secret, totpService.currentStep());
        JsonNode confirmed = body(confirm(token, code));
        assertThat(confirmed.path("recoveryCodes")).hasSize(10);
        assertThat(getStatus(token).path("enabled").asBoolean()).isTrue();
        assertThat(getStatus(token).path("recoveryCodesRemaining").asLong()).isEqualTo(10);

        assertThat(refreshRaw(second[1])).hasStatus(401);
        String rotatedRefresh = refresh(first[1]);

        String export =
                mvc.get()
                        .uri("/api/v1/users/me/export?timezoneOffset=0")
                        .header("Authorization", "Bearer " + token)
                        .exchange()
                        .getResponse()
                        .getContentAsString();
        assertThat(export).doesNotContain("totp").doesNotContain("recoveryCodes");

        assertThat(confirm(token, code))
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_028");

        String recoveryCode = confirmed.path("recoveryCodes").get(0).asText();
        assertThat(disable(token, "000000", null))
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_027");

        assertThat(disable(token, null, recoveryCode)).hasStatus(204);
        assertThat(refreshRaw(third[1])).hasStatus(401);
        assertThat(refresh(rotatedRefresh)).isNotBlank();
        List<String> actions =
                jdbcClient
                        .sql("SELECT action FROM audit_logs WHERE user_id = ?")
                        .param(userId)
                        .query(String.class)
                        .list();
        assertThat(actions).contains("TOTP_ENABLED", "TOTP_DISABLED");
        String auditDetails =
                jdbcClient
                        .sql(
                                "SELECT COALESCE(string_agg(details::text, ''), '')"
                                        + " FROM audit_logs WHERE user_id = ?")
                        .param(userId)
                        .query(String.class)
                        .single();
        assertThat(auditDetails).doesNotContain(secret).doesNotContain(recoveryCode);
        assertThat(getStatus(token).path("enabled").asBoolean()).isFalse();
    }

    private MvcTestResult refreshRaw(String refreshToken) {
        return mvc.post()
                .uri("/api/v1/auth/refresh")
                .with(csrf())
                .header("User-Agent", "ctt-test/1.0 (two-factor)")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}")
                .exchange();
    }

    private String refresh(String refreshToken) throws Exception {
        var result = refreshRaw(refreshToken);
        assertThat(result).hasStatusOk();
        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("refreshToken")
                .asText();
    }

    @Test
    @DisplayName("Should invalidate old recovery codes when they are regenerated")
    void shouldInvalidateOldCodes_whenRecoveryCodesAreRegenerated() throws Exception {
        UUID userId = register();
        String token = login(userId)[0];
        List<String> codes = enable(token);
        String oldCode = codes.getFirst();

        JsonNode regenerated = regenerate(token, oldCode);
        assertThat(regenerated.path("recoveryCodes")).hasSize(10);
        String newCode = regenerated.path("recoveryCodes").get(0).asText();

        assertThat(disable(token, null, oldCode))
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_027");
        assertThat(disable(token, null, newCode)).hasStatus(204);
    }

    @Test
    @DisplayName("Should reject API-key callers")
    void shouldRejectApiKey_whenTwoFactorStatusIsRequested() throws Exception {
        UUID userId = register();
        String token = login(userId)[0];
        String rawKey = createApiKey(token);

        assertThat(
                        mvc.get()
                                .uri("/api/v1/users/me/2fa")
                                .header("Authorization", "Bearer " + rawKey)
                                .exchange())
                .hasStatus(403)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_025");
    }

    @Test
    @DisplayName("Should require a web session and second factor to create an API key")
    void shouldGuardApiKeyCreation_whenTwoFactorIsEnabled() throws Exception {
        UUID userId = register();
        String token = login(userId)[0];
        String existingKey = createApiKey(token);

        assertThat(createApiKeyWithToken(existingKey, null))
                .hasStatus(403)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_025");

        JsonNode started = body(start(token, PASSWORD));
        String secret = started.path("secret").asText();
        String confirmCode = totpService.codeAt(secret, totpService.currentStep());
        body(confirm(token, confirmCode));

        assertThat(createApiKeyWithToken(token, null))
                .hasStatus(403)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_030");
        assertThat(createApiKeyWithToken(token, "000000"))
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_027");

        String nextCode = totpService.codeAt(secret, totpService.currentStep() + 1);
        assertThat(createApiKeyWithToken(token, nextCode)).hasStatus(201);
        assertThat(createApiKeyWithToken(token, nextCode))
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_027");
    }

    @Test
    @DisplayName("Should reject disable and regenerate when two-factor is not enabled")
    void shouldRejectManagement_whenTwoFactorIsNotEnabled() throws Exception {
        UUID userId = register();
        String token = login(userId)[0];

        assertThat(disable(token, "000000", null))
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_029");

        assertThat(
                        mvc.post()
                                .uri("/api/v1/users/me/2fa/recovery-codes/regenerate")
                                .with(csrf())
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\":\"000000\"}")
                                .exchange())
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_029");
    }

    @Test
    @DisplayName("Should reject a second credential kind when both are supplied")
    void shouldRejectBothCredentials_whenEnablingIsAmbiguous() throws Exception {
        UUID userId = register();
        String token = login(userId)[0];
        List<String> codes = enable(token);

        assertThat(
                        mvc.post()
                                .uri("/api/v1/users/me/2fa/recovery-codes/regenerate")
                                .with(csrf())
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"code\":\"000000\",\"recoveryCode\":\""
                                                + codes.getFirst()
                                                + "\"}")
                                .exchange())
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("COMMON_003");
    }

    private JsonNode getStatus(String token) throws Exception {
        var result =
                mvc.get()
                        .uri("/api/v1/users/me/2fa")
                        .header("Authorization", "Bearer " + token)
                        .exchange();
        assertThat(result).hasStatusOk();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode body(MvcTestResult result) throws Exception {
        assertThat(result).hasStatusOk();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private MvcTestResult start(String token, String password) {
        String body =
                password == null
                        ? "{}"
                        : objectMapper
                                .createObjectNode()
                                .put("currentPassword", password)
                                .toString();
        return mvc.post()
                .uri("/api/v1/users/me/2fa/totp/start")
                .with(csrf())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult confirm(String token, String code) {
        return mvc.post()
                .uri("/api/v1/users/me/2fa/totp/confirm")
                .with(csrf())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}")
                .exchange();
    }

    private MvcTestResult disable(String token, String code, String recoveryCode) {
        String body =
                recoveryCode == null
                        ? "{\"code\":\"" + code + "\"}"
                        : "{\"recoveryCode\":\"" + recoveryCode + "\"}";
        return mvc.post()
                .uri("/api/v1/users/me/2fa/disable")
                .with(csrf())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private JsonNode regenerate(String token, String recoveryCode) throws Exception {
        var result =
                mvc.post()
                        .uri("/api/v1/users/me/2fa/recovery-codes/regenerate")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryCode\":\"" + recoveryCode + "\"}")
                        .exchange();
        assertThat(result).hasStatusOk();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    @Test
    @DisplayName("Should require a recent session for OAuth-only accounts")
    void shouldRejectStaleSession_whenOAuthOnlyAccountStartsSetup() throws Exception {
        UUID userId = createOAuthOnlyUser();
        UUID sessionId = UUID.randomUUID();
        insertSession(userId, sessionId, Instant.now().minus(Duration.ofHours(2)));

        assertThat(
                        mvc.post()
                                .uri("/api/v1/users/me/2fa/totp/start")
                                .with(csrf())
                                .header("Authorization", "Bearer " + jwtFor(userId, sessionId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                                .exchange())
                .hasStatus(403)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_031");
    }

    @Test
    @DisplayName("Should accept a recent session for OAuth-only accounts")
    void shouldAcceptFreshSession_whenOAuthOnlyAccountStartsSetup() throws Exception {
        UUID userId = createOAuthOnlyUser();
        UUID sessionId = UUID.randomUUID();
        insertSession(userId, sessionId, Instant.now().minus(Duration.ofMinutes(1)));

        assertThat(
                        mvc.post()
                                .uri("/api/v1/users/me/2fa/totp/start")
                                .with(csrf())
                                .header("Authorization", "Bearer " + jwtFor(userId, sessionId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data.secret")
                .asString()
                .isNotBlank();
    }

    private UUID createOAuthOnlyUser() {
        UUID userId = UUID.randomUUID();
        jdbcClient
                .sql(
                        "INSERT INTO users (id, email, display_name, status, email_verified,"
                                + " email_verified_at, terms_accepted_at, terms_version)"
                                + " VALUES (?, ?, ?, 'ACTIVE', TRUE, CURRENT_TIMESTAMP,"
                                + " CURRENT_TIMESTAMP, '1.0.0')")
                .params(userId, "oauth.only." + userId + "@test.example", "OAuth Only")
                .update();
        return userId;
    }

    private void insertSession(UUID userId, UUID sessionId, Instant createdAt) {
        jdbcClient
                .sql(
                        "INSERT INTO refresh_tokens"
                                + " (user_id, token_hash, issued_for, session_id, expires_at,"
                                + " created_at)"
                                + " VALUES (?, ?, 'WEB', ?, ?, ?)")
                .params(
                        userId,
                        UUID.randomUUID().toString().replace("-", "")
                                + UUID.randomUUID().toString().replace("-", ""),
                        sessionId,
                        Timestamp.from(createdAt.plus(Duration.ofDays(7))),
                        Timestamp.from(createdAt))
                .update();
    }

    private String jwtFor(UUID userId, UUID sessionId) {
        return jwtTokenProvider.generateAccessToken(
                userRepository.findById(userId).orElseThrow(), sessionId);
    }

    private List<String> enable(String token) throws Exception {
        JsonNode started = body(start(token, PASSWORD));
        String secret = started.path("secret").asText();
        String code = totpService.codeAt(secret, totpService.currentStep());
        JsonNode confirmed = body(confirm(token, code));
        assertThat(confirmed.path("recoveryCodes")).hasSize(10);
        return objectMapper.convertValue(
                confirmed.path("recoveryCodes"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
    }

    private String createApiKey(String token) throws Exception {
        String response =
                mvc.post()
                        .uri("/api/v1/auth/api-keys")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"2fa-test\",\"scopes\":[\"READ\",\"WRITE\"]}")
                        .exchange()
                        .getResponse()
                        .getContentAsString();
        String rawKey = objectMapper.readTree(response).path("data").path("rawKey").asText();
        assertThat(rawKey).isNotBlank();
        return rawKey;
    }

    private MvcTestResult createApiKeyWithToken(String token, String code) {
        String body =
                code == null
                        ? "{\"name\":\"guard-test\",\"scopes\":[\"READ\",\"WRITE\"]}"
                        : "{\"name\":\"guard-test\",\"scopes\":[\"READ\",\"WRITE\"],\"code\":\""
                                + code
                                + "\"}";
        return mvc.post()
                .uri("/api/v1/auth/api-keys")
                .with(csrf())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private UUID register() throws Exception {
        String email = "twofactor." + UUID.randomUUID() + "@test.example";
        UserRegisterRequest request =
                new UserRegisterRequest(email, DISPLAY_NAME, PASSWORD, "1.0.0", null);
        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/register")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .hasStatus(200);

        String bodyHtml =
                jdbcClient
                        .sql(
                                "SELECT body_html FROM mail_outbox WHERE recipient = ?"
                                        + " ORDER BY created_at DESC LIMIT 1")
                        .param(email)
                        .query(String.class)
                        .single();
        Matcher matcher = TOKEN_PATTERN.matcher(bodyHtml);
        assertThat(matcher.find()).as("verification token missing for %s", email).isTrue();
        assertThat(mvc.get().uri("/api/v1/auth/verify-email?token=" + matcher.group(1)))
                .hasStatus(200);

        return jdbcClient
                .sql("SELECT id FROM users WHERE email = ?")
                .param(email)
                .query(UUID.class)
                .single();
    }

    private String[] login(UUID userId) throws Exception {
        String email =
                jdbcClient
                        .sql("SELECT email FROM users WHERE id = ?")
                        .param(userId)
                        .query(String.class)
                        .single();
        LoginRequest request =
                new LoginRequest(email, PASSWORD, UUID.randomUUID().toString(), null);
        String response =
                mvc.post()
                        .uri("/api/v1/auth/login")
                        .with(csrf())
                        .header("User-Agent", "ctt-test/1.0 (two-factor)")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .exchange()
                        .getResponse()
                        .getContentAsString();
        JsonNode data = objectMapper.readTree(response).path("data");
        String accessToken = data.path("accessToken").asText();
        String refreshToken = data.path("refreshToken").asText();
        assertThat(accessToken).as("login failed: %s", response).isNotBlank();
        return new String[] {accessToken, refreshToken};
    }
}
