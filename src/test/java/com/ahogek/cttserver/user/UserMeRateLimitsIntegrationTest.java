package com.ahogek.cttserver.user;

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

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * Rate-limit coverage for the two write routes under {@code /api/v1/users/me}.
 *
 * <p>Both confirm the account password with bcrypt (strength 12) — a deliberate CPU cost per
 * request — and the profile route additionally has no legitimate high-frequency caller. Without a
 * limit, a stolen session or a client stuck in a retry loop turns either one into a CPU-exhaustion
 * vector, so the limits are pinned by request-level tests rather than assumed from the annotations.
 * Rate limiting is off by default in the suite (see the project's test baseline), hence the
 * property override here.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-18
 */
@BaseIntegrationTest
@TestPropertySource(
        properties = {
            "ctt.security.rate-limit.enabled=true",
            "ctt.mail.outbox.poll-interval-ms=999999999"
        })
@DisplayName("User /me write route rate limits")
class UserMeRateLimitsIntegrationTest {

    private static final String PASSWORD = "StrongPass123!";
    private static final String DISPLAY_NAME = "RateLimitUser";
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");
    private static final int ALLOWED_DELETIONS_PER_HOUR = 5;
    private static final int ALLOWED_PROFILE_CHANGES_PER_HOUR = 10;

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM mail_outbox").update();
        jdbcClient.sql("DELETE FROM email_verification_tokens").update();
        jdbcClient.sql("DELETE FROM refresh_tokens").update();
        jdbcClient.sql("DELETE FROM audit_logs").update();
        jdbcClient.sql("DELETE FROM users").update();
    }

    @Test
    @DisplayName("Should reject the sixth password attempt within the hour with 429")
    void shouldRejectSixthAttempt_whenPasswordIsWrong() throws Exception {
        String email = uniqueEmail();
        String jwt = registerVerifyAndLogin(email);
        String wrongPassword = "{\"password\":\"WrongPass123!\"}";

        for (int attempt = 1; attempt <= ALLOWED_DELETIONS_PER_HOUR; attempt++) {
            assertThat(
                            mvc.delete()
                                    .uri("/api/v1/users/me")
                                    .header("Authorization", "Bearer " + jwt)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(wrongPassword)
                                    .with(csrf())
                                    .exchange())
                    .as(
                            "attempt %s of %s should still be allowed",
                            attempt, ALLOWED_DELETIONS_PER_HOUR)
                    .hasStatus(401)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("USER_014");
        }

        assertThat(
                        mvc.delete()
                                .uri("/api/v1/users/me")
                                .header("Authorization", "Bearer " + jwt)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(wrongPassword)
                                .with(csrf())
                                .exchange())
                .hasStatus(429)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("RATE_LIMIT_001");
    }

    @Test
    @DisplayName("Should reject the eleventh profile change within the hour with 429")
    void shouldRejectEleventhProfileChange_whenWithinTheHour() throws Exception {
        String jwt = registerVerifyAndLogin(uniqueEmail());
        String body = "{\"displayName\":\"RateLimitName\"}";

        for (int attempt = 1; attempt <= ALLOWED_PROFILE_CHANGES_PER_HOUR; attempt++) {
            assertThat(
                            mvc.patch()
                                    .uri("/api/v1/users/me")
                                    .header("Authorization", "Bearer " + jwt)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body)
                                    .with(csrf())
                                    .exchange())
                    .as("attempt %s should still be allowed", attempt)
                    .hasStatusOk();
        }

        assertThat(
                        mvc.patch()
                                .uri("/api/v1/users/me")
                                .header("Authorization", "Bearer " + jwt)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .with(csrf())
                                .exchange())
                .hasStatus(429)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("RATE_LIMIT_001");
    }

    private String uniqueEmail() {
        return "rate-limit." + UUID.randomUUID() + "@test.example";
    }

    private String registerVerifyAndLogin(String email) throws Exception {
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

        LoginRequest login = new LoginRequest(email, PASSWORD, "device-" + UUID.randomUUID(), null);
        String loginResponse =
                mvc.post()
                        .uri("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login))
                        .exchange()
                        .getResponse()
                        .getContentAsString();
        String accessToken =
                objectMapper.readTree(loginResponse).path("data").path("accessToken").asText();
        assertThat(accessToken).as("login failed: %s", loginResponse).isNotBlank();

        String termsResponse =
                mvc.post()
                        .uri("/api/v1/auth/terms/accept")
                        .with(csrf())
                        .header("Authorization", "Bearer " + accessToken)
                        .exchange()
                        .getResponse()
                        .getContentAsString();
        String refreshedToken =
                objectMapper.readTree(termsResponse).path("data").path("accessToken").asText();
        assertThat(refreshedToken).isNotBlank();
        return refreshedToken;
    }
}
