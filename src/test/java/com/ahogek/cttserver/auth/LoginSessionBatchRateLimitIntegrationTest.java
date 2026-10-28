package com.ahogek.cttserver.auth;

import com.ahogek.cttserver.auth.dto.LoginRequest;
import com.ahogek.cttserver.auth.dto.UserRegisterRequest;
import com.ahogek.cttserver.common.BaseIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
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
 * Rate-limit boundary of batch session revocation.
 *
 * <p>Kept apart from {@code LoginSessionEndpointsIntegrationTest}: that class runs with rate
 * limiting disabled, and enabling it for every case there would re-baseline unrelated tests.
 */
@BaseIntegrationTest
@TestPropertySource(
        properties = {
            "ctt.mail.outbox.poll-interval-ms=999999999",
            "ctt.security.rate-limit.enabled=true"
        })
@DisplayName("Batch session revocation rate limit")
class LoginSessionBatchRateLimitIntegrationTest {

    private static final String PASSWORD = "StrongPass123!";
    private static final String DISPLAY_NAME = "BatchLimitUser";
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ObjectMapper objectMapper;

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
    @DisplayName("Should reject the eleventh batch call within the hour")
    void shouldRejectEleventhBatchCall_whenWithinTheHour() throws Exception {
        UUID userId = register("batch-limit-" + UUID.randomUUID() + "@example.com");
        String accessToken = login(userId);

        for (int call = 0; call < 10; call++) {
            revokeBatch(accessToken, UUID.randomUUID());
        }

        assertThat(
                        mvc.post()
                                .uri("/api/v1/users/me/sessions/revoke")
                                .with(csrf())
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(batchBody(UUID.randomUUID()))
                                .exchange())
                .hasStatus(429)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("RATE_LIMIT_001");
    }

    private void revokeBatch(String accessToken, UUID sessionId) throws Exception {
        assertThat(
                        mvc.post()
                                .uri("/api/v1/users/me/sessions/revoke")
                                .with(csrf())
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(batchBody(sessionId))
                                .exchange())
                .hasStatusOk();
    }

    private String batchBody(UUID sessionId) throws Exception {
        return objectMapper.writeValueAsString(Map.of("sessionIds", List.of(sessionId)));
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

    private String login(UUID userId) throws Exception {
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
                        .header("User-Agent", "ctt-test/1.0 (batch-limit)")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .exchange()
                        .getResponse()
                        .getContentAsString();
        String accessToken =
                objectMapper.readTree(response).path("data").path("accessToken").asText();
        assertThat(accessToken).as("login failed: %s", response).isNotBlank();
        return accessToken;
    }
}
