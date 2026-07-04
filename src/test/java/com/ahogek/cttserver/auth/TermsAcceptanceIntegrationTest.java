package com.ahogek.cttserver.auth;

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
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * End-to-end integration tests for the terms acceptance flow.
 *
 * <p>Tests the complete lifecycle: registration → email verification → login → terms acceptance →
 * new token issuance, including edge cases like unauthorized access and user not found.
 *
 * <p>Uses full Spring context with PostgreSQL, Redis, and GreenMail test containers.
 *
 * @author AhogeK
 * @since 2026-05-05
 */
@BaseIntegrationTest
@DisplayName("Terms Acceptance Flow")
class TermsAcceptanceIntegrationTest {

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ObjectMapper objectMapper;

    private static final String DISPLAY_NAME = "TestUser";
    private static final String PASSWORD = "StrongPass123!";
    private static final String DEVICE_ID = "test-device-" + UUID.randomUUID();
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM mail_outbox").update();
        jdbcClient.sql("DELETE FROM email_verification_tokens").update();
        jdbcClient.sql("DELETE FROM refresh_tokens").update();
        jdbcClient.sql("DELETE FROM users").update();
    }

    private String uniqueEmail() {
        return "e2e." + UUID.randomUUID() + "@test.example";
    }

    private String registerRequestJson(String email) throws Exception {
        UserRegisterRequest request =
                new UserRegisterRequest(email, DISPLAY_NAME, PASSWORD, "1.0.0", null);
        return objectMapper.writeValueAsString(request);
    }

    private String loginRequestJson(String email) throws Exception {
        LoginRequest request = new LoginRequest(email, PASSWORD, DEVICE_ID, null);
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

    private String getTermsVersion(String email) {
        return jdbcClient
                .sql("SELECT terms_version FROM users WHERE email = ?")
                .param(email)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    @Nested
    @DisplayName("Full Terms Acceptance Flow")
    class FullTermsAcceptanceFlow {

        @Test
        @DisplayName("Should complete full terms acceptance flow when valid JWT token")
        void shouldCompleteFullTermsAcceptanceFlow_whenValidJwtToken() throws Exception {
            // Given
            String email = uniqueEmail();

            // Step 1: Register
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/register")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(registerRequestJson(email)))
                    .hasStatus(200)
                    .bodyJson()
                    .extractingPath("$.data.message")
                    .isEqualTo("User registered successfully");

            assertThat(getUserStatus(email)).isEqualTo("PENDING_VERIFICATION");

            // Step 2: Extract token and verify email
            String token = extractTokenFromMailOutbox(email);

            assertThat(mvc.get().uri("/api/v1/auth/verify-email?token=" + token))
                    .hasStatus(200)
                    .bodyJson()
                    .extractingPath("$.data.message")
                    .isEqualTo("Email verified successfully");

            assertThat(getUserStatus(email)).isEqualTo("ACTIVE");

            // Step 3: Login to get JWT tokens
            String loginBody =
                    mvc.post()
                            .uri("/api/v1/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginRequestJson(email))
                            .exchange()
                            .getResponse()
                            .getContentAsString();

            String accessToken =
                    objectMapper.readTree(loginBody).path("data").path("accessToken").asText();
            String refreshToken =
                    objectMapper.readTree(loginBody).path("data").path("refreshToken").asText();

            assertThat(accessToken).isNotBlank();
            assertThat(refreshToken).isNotBlank();

            // Step 4: Accept terms with JWT auth
            String acceptTermsBody =
                    mvc.post()
                            .uri("/api/v1/auth/terms/accept")
                            .with(csrf())
                            .header("Authorization", "Bearer " + accessToken)
                            .exchange()
                            .getResponse()
                            .getContentAsString();

            assertThat(objectMapper.readTree(acceptTermsBody).path("success").asBoolean()).isTrue();

            // Step 5: Verify new tokens are issued
            String newAccessToken =
                    objectMapper
                            .readTree(acceptTermsBody)
                            .path("data")
                            .path("accessToken")
                            .asText();
            String newRefreshToken =
                    objectMapper
                            .readTree(acceptTermsBody)
                            .path("data")
                            .path("refreshToken")
                            .asText();

            assertThat(newAccessToken).isNotBlank();
            assertThat(newRefreshToken).isNotBlank();

            // Step 6: Verify terms version updated in DB
            assertThat(getTermsVersion(email)).isEqualTo("1.0.0");

            // Step 7: Verify new tokens work for protected endpoints
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/logout-all")
                                    .with(csrf())
                                    .header("Authorization", "Bearer " + newAccessToken))
                    .hasStatus(200);
        }
    }

    @Nested
    @DisplayName("Unauthorized Access")
    class UnauthorizedAccess {

        @Test
        @DisplayName("Should return 403 when accept terms without JWT token (CSRF blocks first)")
        void shouldReturn403_whenAcceptTermsWithoutJwtToken() {
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/terms/accept")
                                    .contentType(MediaType.APPLICATION_JSON))
                    .hasStatus(403);
        }

        @Test
        @DisplayName("Should return 401 when accept terms with invalid JWT token")
        void shouldReturn401_whenAcceptTermsWithInvalidJwtToken() {
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/terms/accept")
                                    .with(csrf())
                                    .header("Authorization", "Bearer invalid-token"))
                    .hasStatus(401);
        }
    }
}
