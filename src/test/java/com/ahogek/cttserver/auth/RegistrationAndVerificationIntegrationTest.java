package com.ahogek.cttserver.auth;

import com.ahogek.cttserver.auth.dto.ResendVerificationRequest;
import com.ahogek.cttserver.auth.dto.UserRegisterRequest;
import com.ahogek.cttserver.common.BaseIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

/**
 * End-to-end integration tests for the registration and email verification flow.
 *
 * <p>Tests the complete lifecycle: registration → token generation → email outbox → verification →
 * user activation, including edge cases like duplicate registration, expired tokens, and resend
 * behavior.
 *
 * <p>Uses full Spring context with PostgreSQL, Redis, and GreenMail test containers.
 *
 * @author AhogeK
 * @since 2026-04-10
 */
@BaseIntegrationTest
@TestPropertySource(
        properties = {
            "ctt.mail.outbox.poll-interval-ms=999999999",
            "ctt.mail.outbox.zombie-interval-ms=999999999"
        })
@DisplayName("Registration and Email Verification Flow")
class RegistrationAndVerificationIntegrationTest {

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ObjectMapper objectMapper;

    private static final String DISPLAY_NAME = "TestUser";
    private static final String PASSWORD = "StrongPass123!";
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM mail_outbox").update();
        jdbcClient.sql("DELETE FROM email_verification_tokens").update();
        jdbcClient.sql("DELETE FROM users").update();
    }

    private String uniqueEmail() {
        return "e2e." + UUID.randomUUID() + "@test.example";
    }

    private String registerRequestJson(String email) throws Exception {
        UserRegisterRequest request = new UserRegisterRequest(email, DISPLAY_NAME, PASSWORD);
        return objectMapper.writeValueAsString(request);
    }

    private String resendRequestJson(String email) throws Exception {
        ResendVerificationRequest request = new ResendVerificationRequest(email);
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

    private String getUserStatus(String email) {
        return jdbcClient
                .sql("SELECT status FROM users WHERE email = ?")
                .param(email)
                .query(String.class)
                .single();
    }

    @Nested
    @DisplayName("Full Registration Flow")
    class FullRegistrationFlow {

        @Test
        @DisplayName("Should complete full registration flow when valid registration")
        void shouldCompleteFullRegistrationFlow_whenValidRegistration() throws Exception {
            // Given
            String email = uniqueEmail();

            // When: Register
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/register")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(registerRequestJson(email)))
                    .hasStatus(200)
                    .bodyJson()
                    .extractingPath("$.data.message")
                    .isEqualTo("User registered successfully");

            // Then 1: User status is PENDING_VERIFICATION
            assertThat(getUserStatus(email)).isEqualTo("PENDING_VERIFICATION");

            // Then 2: Extract token from mail_outbox
            String token = extractTokenFromMailOutbox(email);

            // When: Verify email with token
            assertThat(mvc.get().uri("/api/v1/auth/verify-email?token=" + token))
                    .hasStatus(200)
                    .bodyJson()
                    .extractingPath("$.data.message")
                    .isEqualTo("Email verified successfully");

            // Then 3: User status is ACTIVE
            assertThat(getUserStatus(email)).isEqualTo("ACTIVE");
        }
    }

    @Nested
    @DisplayName("Duplicate Registration")
    class DuplicateRegistration {

        @Test
        @DisplayName("Should return conflict when duplicate email registration")
        void shouldReturnConflict_whenDuplicateEmailRegistration() throws Exception {
            // Given
            String email = uniqueEmail();

            // When: First registration succeeds
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/register")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(registerRequestJson(email)))
                    .hasStatus(200);

            // When: Second registration with same email
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/register")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(registerRequestJson(email)))
                    .hasStatus(409)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("USER_001");
        }
    }

    @Nested
    @DisplayName("Expired Token and Resend Verification")
    class ExpiredTokenAndResend {

        @Test
        @DisplayName("Should reject expired token and accept new token when resend verification")
        void shouldRejectExpiredTokenAndAcceptNewToken_whenResendVerification() throws Exception {
            // Given
            String email = uniqueEmail();

            // When: Register
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/register")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(registerRequestJson(email)))
                    .hasStatus(200);

            // Then 1: Extract first token
            String firstToken = extractTokenFromMailOutbox(email);
            assertThat(firstToken).isNotBlank();

            // Given: Expire the first token
            jdbcClient
                    .sql(
                            "UPDATE email_verification_tokens SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 day' WHERE email = ?")
                    .param(email)
                    .update();

            // When: Verify with expired token
            assertThat(mvc.get().uri("/api/v1/auth/verify-email?token=" + firstToken))
                    .hasStatus(401)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("MAIL_005");

            // Given: Clear mail_outbox to bypass idempotency window
            jdbcClient.sql("DELETE FROM mail_outbox WHERE recipient = ?").param(email).update();

            // When: Resend verification
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/resend-verification")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(resendRequestJson(email)))
                    .hasStatus(200)
                    .bodyJson()
                    .extractingPath("$.data.message")
                    .isEqualTo("Email queued successfully");

            // Then 2: Extract new token
            String secondToken = extractTokenFromMailOutbox(email);
            assertThat(secondToken).isNotBlank().isNotEqualTo(firstToken);

            // Verify old token was revoked in DB
            Long expiredCount =
                    jdbcClient
                            .sql(
                                    "SELECT COUNT(*) FROM email_verification_tokens WHERE email = ? AND expires_at < CURRENT_TIMESTAMP")
                            .param(email)
                            .query(Long.class)
                            .single();
            assertThat(expiredCount).isGreaterThan(0);

            // When: Verify old token still fails
            assertThat(mvc.get().uri("/api/v1/auth/verify-email?token=" + firstToken))
                    .hasStatus(401)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("MAIL_005");

            // When: Verify new token succeeds
            assertThat(mvc.get().uri("/api/v1/auth/verify-email?token=" + secondToken))
                    .hasStatus(200)
                    .bodyJson()
                    .extractingPath("$.data.message")
                    .isEqualTo("Email verified successfully");

            // Then 3: User status is ACTIVE
            assertThat(getUserStatus(email)).isEqualTo("ACTIVE");
        }
    }

    @Nested
    @DisplayName("Registration Validation")
    class ValidationTests {

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "{\"email\": \"not-an-email\", \"displayName\": \"TestUser\", \"password\": \"StrongPass123!\"}",
                    "{\"email\": \"valid@test.example\", \"displayName\": \"TestUser\", \"password\": \"weak\"}",
                    "{\"email\": \"\", \"displayName\": \"TestUser\", \"password\": \"StrongPass123!\"}"
                })
        @DisplayName("Should return 400 when registration input is invalid")
        void shouldReturn400_whenRegistrationInputIsInvalid(String invalidRequest) {
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/register")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(invalidRequest))
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("COMMON_003");
        }
    }
}
