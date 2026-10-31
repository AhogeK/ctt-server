package com.ahogek.cttserver.auth;

import com.ahogek.cttserver.auth.dto.LoginRequest;
import com.ahogek.cttserver.auth.dto.UserRegisterRequest;
import com.ahogek.cttserver.auth.twofactor.service.TotpService;
import com.ahogek.cttserver.common.BaseIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@BaseIntegrationTest
@TestPropertySource(properties = {"ctt.mail.outbox.poll-interval-ms=999999999"})
@DisplayName("Two-factor login")
class LoginTwoFactorIntegrationTest {

    private static final String PASSWORD = "StrongPass123!";
    private static final String DISPLAY_NAME = "LoginTwoFactorUser";
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TotpService totpService;

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
    @DisplayName("Should return 202 and issue tokens only after the second step")
    void shouldReturnChallenge_thenIssueTokens_whenTwoFactorIsEnabled() throws Exception {
        UUID userId = register();
        String accessToken =
                login(userId, UUID.randomUUID().toString()).path("accessToken").asText();
        String secret = enableTwoFactor(accessToken);

        MvcTestResult loginResult = loginRaw(userId, UUID.randomUUID().toString());

        assertThat(loginResult).hasStatus(202);
        JsonNode challenge = body(loginResult);
        assertThat(challenge.path("twoFactorRequired").asBoolean()).isTrue();
        String challengeToken = challenge.path("challengeToken").asText();
        assertThat(challengeToken).isNotBlank();
        assertThat(loginResult.getResponse().getHeaders("Set-Cookie")).isEmpty();
        assertThat(loginResult.getResponse().getHeader("Authorization")).isNull();

        assertThat(secondStep(challengeToken, "000000"))
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_027");

        // The rejected attempt must leave a trace: the audit event is published from a method with
        // no surrounding transaction precisely so a rollback cannot swallow it.
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () ->
                                assertThat(
                                                jdbcClient
                                                        .sql(
                                                                "SELECT COUNT(*) FROM audit_logs"
                                                                        + " WHERE action ="
                                                                        + " 'TOTP_CHALLENGE_FAILED'")
                                                        .query(Long.class)
                                                        .single())
                                        .isPositive());

        String code = totpService.codeAt(secret, totpService.currentStep() + 1);
        MvcTestResult completed = secondStep(challengeToken, code);
        assertThat(completed).hasStatusOk();
        JsonNode tokens = body(completed);
        assertThat(tokens.path("accessToken").asText()).isNotBlank();
        assertThat(tokens.path("refreshToken").asText()).isNotBlank();
        assertThat(completed.getResponse().getHeaders("Set-Cookie")).isNotEmpty();

        assertThat(secondStep(challengeToken, code))
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_026");
    }

    @Test
    @DisplayName("Should accept a recovery code once")
    void shouldAcceptRecoveryCodeOnce_whenSecondStepIsCompleted() throws Exception {
        UUID userId = register();
        String accessToken =
                login(userId, UUID.randomUUID().toString()).path("accessToken").asText();
        List<String> recoveryCodes = enableTwoFactorAndReturnCodes(accessToken);
        String recoveryCode = recoveryCodes.getFirst();

        String challengeToken =
                body(loginRaw(userId, UUID.randomUUID().toString()))
                        .path("challengeToken")
                        .asText();

        assertThat(secondStep(challengeToken, recoveryCode)).hasStatusOk();

        String nextChallenge =
                body(loginRaw(userId, UUID.randomUUID().toString()))
                        .path("challengeToken")
                        .asText();
        assertThat(secondStep(nextChallenge, recoveryCode))
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_027");
    }

    @Test
    @DisplayName("Should end the challenge with 429 after five failed codes")
    void shouldReturn429_whenFifthCodeIsWrong() throws Exception {
        UUID userId = register();
        String accessToken =
                login(userId, UUID.randomUUID().toString()).path("accessToken").asText();
        enableTwoFactor(accessToken);

        String challengeToken =
                body(loginRaw(userId, UUID.randomUUID().toString()))
                        .path("challengeToken")
                        .asText();

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThat(secondStep(challengeToken, "000000"))
                    .hasStatus(401)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("AUTH_027");
        }

        assertThat(secondStep(challengeToken, "000000"))
                .hasStatus(429)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("RATE_LIMIT_001");

        // The challenge is spent: a later correct code cannot resurrect it.
        assertThat(secondStep(challengeToken, "000000"))
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_026");
    }

    @Test
    @DisplayName("Should reject completion when the account is suspended after the challenge")
    void shouldRejectCompletion_whenAccountIsSuspendedAfterChallenge() throws Exception {
        UUID userId = register();
        String accessToken =
                login(userId, UUID.randomUUID().toString()).path("accessToken").asText();
        String secret = enableTwoFactor(accessToken);
        String challengeToken =
                body(loginRaw(userId, UUID.randomUUID().toString()))
                        .path("challengeToken")
                        .asText();

        jdbcClient.sql("UPDATE users SET status = 'SUSPENDED' WHERE id = ?").param(userId).update();

        String code = totpService.codeAt(secret, totpService.currentStep() + 1);
        assertThat(secondStep(challengeToken, code))
                .hasStatus(403)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_005");
    }

    private MvcTestResult loginRaw(UUID userId, String deviceId) throws Exception {
        String email =
                jdbcClient
                        .sql("SELECT email FROM users WHERE id = ?")
                        .param(userId)
                        .query(String.class)
                        .single();
        LoginRequest request = new LoginRequest(email, PASSWORD, deviceId, null);
        return mvc.post()
                .uri("/api/v1/auth/login")
                .with(csrf())
                .header("User-Agent", "ctt-test/1.0 (two-factor-login)")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .exchange();
    }

    private JsonNode login(UUID userId, String deviceId) throws Exception {
        MvcTestResult result = loginRaw(userId, deviceId);
        assertThat(result).hasStatusOk();
        return body(result);
    }

    private MvcTestResult secondStep(String challengeToken, String code) {
        return mvc.post()
                .uri("/api/v1/auth/login/2fa")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"challengeToken\":\"" + challengeToken + "\",\"code\":\"" + code + "\"}")
                .exchange();
    }

    private String enableTwoFactor(String accessToken) throws Exception {
        JsonNode started =
                body(
                        mvc.post()
                                .uri("/api/v1/users/me/2fa/totp/start")
                                .with(csrf())
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currentPassword\":\"" + PASSWORD + "\"}")
                                .exchange());
        String secret = started.path("secret").asText();
        String code = totpService.codeAt(secret, totpService.currentStep());
        assertThat(
                        mvc.post()
                                .uri("/api/v1/users/me/2fa/totp/confirm")
                                .with(csrf())
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\":\"" + code + "\"}")
                                .exchange())
                .hasStatusOk();
        return secret;
    }

    private List<String> enableTwoFactorAndReturnCodes(String accessToken) throws Exception {
        JsonNode started =
                body(
                        mvc.post()
                                .uri("/api/v1/users/me/2fa/totp/start")
                                .with(csrf())
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currentPassword\":\"" + PASSWORD + "\"}")
                                .exchange());
        String secret = started.path("secret").asText();
        String code = totpService.codeAt(secret, totpService.currentStep());
        JsonNode confirmed =
                body(
                        mvc.post()
                                .uri("/api/v1/users/me/2fa/totp/confirm")
                                .with(csrf())
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\":\"" + code + "\"}")
                                .exchange());
        return objectMapper.convertValue(
                confirmed.path("recoveryCodes"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
    }

    private JsonNode body(MvcTestResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private UUID register() throws Exception {
        String email = "login-2fa." + UUID.randomUUID() + "@test.example";
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
}
