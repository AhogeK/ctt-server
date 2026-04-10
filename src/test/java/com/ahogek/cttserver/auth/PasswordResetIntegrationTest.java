package com.ahogek.cttserver.auth;

import com.ahogek.cttserver.auth.dto.LoginRequest;
import com.ahogek.cttserver.auth.dto.PasswordResetRequest;
import com.ahogek.cttserver.auth.dto.ResetPasswordRequest;
import com.ahogek.cttserver.common.BaseIntegrationTest;
import com.ahogek.cttserver.fixtures.UserFixtures;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for the password reset and session revocation flow.
 *
 * <p>Tests the complete lifecycle: password reset request → token generation → email outbox →
 * password reset confirm → session revocation, including token expiry handling.
 *
 * <p>Uses full Spring context with PostgreSQL, Redis, and Testcontainers.
 *
 * @author AhogeK
 * @since 2026-04-10
 */
@BaseIntegrationTest
@TestPropertySource(properties = {"ctt.mail.outbox.poll-interval-ms=999999999"})
@DisplayName("E2E: Password Reset and Session Revocation Flow")
class PasswordResetIntegrationTest {

    @Autowired private MockMvcTester mvc;
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private UserRepository userRepository;
    @Autowired private ObjectMapper objectMapper;

    private static final String OLD_PASSWORD = "OldPassword123!";
    private static final String NEW_PASSWORD = "NewSecure456!";
    private static final String TEST_PASSWORD = "TestPassword123!";
    private static final String ANOTHER_NEW_PASSWORD = "NewPass789!";

    private static final Pattern RESET_TOKEN_PATTERN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM audit_logs").update();
        jdbcClient.sql("DELETE FROM mail_outbox").update();
        jdbcClient.sql("DELETE FROM password_reset_tokens").update();
        jdbcClient.sql("DELETE FROM refresh_tokens").update();
        jdbcClient.sql("DELETE FROM login_attempts").update();
        jdbcClient.sql("DELETE FROM users").update();
    }

    private String uniqueEmail() {
        return "pwd-reset." + UUID.randomUUID() + "@test.example";
    }

    private void createAndPersistUser(String email, String password) {
        User user =
                UserFixtures.regularUser()
                        .email(email)
                        .rawPassword(password)
                        .status(UserStatus.ACTIVE)
                        .emailVerified(true)
                        .build();
        userRepository.saveAndFlush(user);
    }

    private String loginRequestJson(String email, String password) throws Exception {
        LoginRequest request =
                new LoginRequest(email, password, "test-device-" + UUID.randomUUID());
        return objectMapper.writeValueAsString(request);
    }

    private String passwordResetRequestJson(String email) throws Exception {
        PasswordResetRequest request = new PasswordResetRequest(email);
        return objectMapper.writeValueAsString(request);
    }

    private String resetPasswordConfirmRequestJson(String token, String newPassword)
            throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest(token, newPassword);
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

        Matcher matcher = RESET_TOKEN_PATTERN.matcher(bodyHtml);
        assertThat(matcher.find()).as("Reset token not found in email body for %s", email).isTrue();
        return matcher.group(1);
    }

    private long countActiveRefreshTokens(String email) {
        return jdbcClient
                .sql(
                        "SELECT COUNT(*) FROM refresh_tokens rt "
                                + "JOIN users u ON rt.user_id = u.id "
                                + "WHERE u.email = ? AND rt.revoked_at IS NULL")
                .param(email)
                .query(Long.class)
                .single();
    }

    @Nested
    @DisplayName("Full Reset Flow")
    class FullResetFlow {

        @Test
        @DisplayName("Should reset password and revoke all sessions when full flow")
        void shouldResetPasswordAndRevokeAllSessions_whenFullFlow() throws Exception {
            // Given
            String email = uniqueEmail();
            createAndPersistUser(email, OLD_PASSWORD);

            // When: Login with old credentials
            String loginBody =
                    mockMvc.perform(
                                    post("/api/v1/auth/login")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(loginRequestJson(email, OLD_PASSWORD)))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                            .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();

            // Then 1: Verify active refresh tokens exist
            assertThat(countActiveRefreshTokens(email)).isGreaterThan(0);

            // When: Request password reset
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/password-reset/request")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(passwordResetRequestJson(email)))
                    .hasStatus(200);

            // Then 2: Extract token from mail_outbox
            String resetToken = extractTokenFromMailOutbox(email);
            assertThat(resetToken).isNotBlank();

            // When: Confirm password reset
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/password-reset/confirm")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            resetPasswordConfirmRequestJson(
                                                    resetToken, NEW_PASSWORD)))
                    .hasStatus(200);

            // Then 3: All refresh tokens revoked
            assertThat(countActiveRefreshTokens(email)).isZero();

            // Then 4: Revoked refresh token is rejected
            String refreshToken =
                    com.jayway.jsonpath.JsonPath.parse(loginBody).read("$.data.refreshToken");
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/refresh")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"refreshToken\": \"" + refreshToken + "\"}"))
                    .hasStatus(403)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("AUTH_009");

            // Then 5: Old password fails
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(loginRequestJson(email, OLD_PASSWORD)))
                    .hasStatus(401);

            // Then 6: New password succeeds
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(loginRequestJson(email, NEW_PASSWORD)))
                    .hasStatus(200)
                    .bodyJson()
                    .extractingPath("$.data.accessToken")
                    .isNotEmpty();

            // Then 7: Reusing consumed token fails
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/password-reset/confirm")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            resetPasswordConfirmRequestJson(
                                                    resetToken, NEW_PASSWORD)))
                    .hasStatus(401)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("AUTH_003");
        }
    }

    @Nested
    @DisplayName("Token Expiry")
    class TokenExpiry {

        @Test
        @DisplayName("Should return 401 when reset token is expired")
        void shouldReturn401_whenResetTokenIsExpired() throws Exception {
            // Given
            String email = uniqueEmail();
            createAndPersistUser(email, TEST_PASSWORD);

            // When: Request password reset
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/password-reset/request")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(passwordResetRequestJson(email)))
                    .hasStatus(200);

            // Then 1: Extract token from mail_outbox
            String resetToken = extractTokenFromMailOutbox(email);
            assertThat(resetToken).isNotBlank();

            // Given: Time travel - expire the token
            jdbcClient
                    .sql(
                            "UPDATE password_reset_tokens SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 hour' WHERE email = ?")
                    .param(email)
                    .update();

            // When: Attempt reset with expired token
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/password-reset/confirm")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            resetPasswordConfirmRequestJson(
                                                    resetToken, ANOTHER_NEW_PASSWORD)))
                    .hasStatus(401)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("AUTH_002");
        }
    }

    @Nested
    @DisplayName("Anti-Enumeration")
    class AntiEnumeration {

        @Test
        @DisplayName("Should return 200 for non-existent email to prevent enumeration")
        void shouldReturn200ForNonExistentEmailToPreventEnumeration() throws Exception {
            // Given
            String nonExistentEmail = "does-not-exist-" + UUID.randomUUID() + "@test.example";

            // When
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/password-reset/request")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(passwordResetRequestJson(nonExistentEmail)))
                    .hasStatus(200);

            // Then: No email sent to mail_outbox
            Long mailCount =
                    jdbcClient
                            .sql("SELECT COUNT(*) FROM mail_outbox WHERE recipient = ?")
                            .param(nonExistentEmail)
                            .query(Long.class)
                            .single();
            assertThat(mailCount).isZero();
        }
    }
}
