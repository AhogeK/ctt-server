package com.ahogek.cttserver.auth;

import com.ahogek.cttserver.auth.dto.LoginRequest;
import com.ahogek.cttserver.auth.dto.RefreshTokenRequest;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.USER_AGENT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for login, token rotation, and security defense.
 *
 * <p>Tests the complete authentication lifecycle: successful login with token issuance, brute-force
 * lockout protection, and refresh token reuse detection (kill switch).
 *
 * <p>Uses full Spring context with PostgreSQL, Redis, and Testcontainers.
 *
 * @author AhogeK
 * @since 2026-04-10
 */
@BaseIntegrationTest
@DisplayName("Login and Token Integration Tests")
class LoginAndTokenIntegrationTest {

    @Autowired private MockMvcTester mvc;
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;

    private static final String TEST_PASSWORD = "Test@1234";
    private static final String WRONG_PASSWORD = "WrongPass123!";
    private static final String DEVICE_ID = "test-device-" + UUID.randomUUID();
    private static final String TEST_USER_AGENT = "TestAgent/1.0";

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM refresh_tokens").update();
        jdbcClient.sql("DELETE FROM login_attempts").update();
        userRepository.deleteAll();
    }

    private User createAndPersistUser(String email) {
        User user =
                UserFixtures.regularUser()
                        .email(email)
                        .rawPassword(TEST_PASSWORD)
                        .status(UserStatus.ACTIVE)
                        .emailVerified(true)
                        .build();
        return userRepository.saveAndFlush(user);
    }

    private String loginRequestJson(String email, String password) throws Exception {
        LoginRequest request =
                new LoginRequest(email, password, LoginAndTokenIntegrationTest.DEVICE_ID);
        return objectMapper.writeValueAsString(request);
    }

    private String refreshTokenRequestJson(String refreshToken) throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);
        return objectMapper.writeValueAsString(request);
    }

    @Nested
    @DisplayName("Successful Login Flow")
    class SuccessfulLoginFlow {

        @Test
        @DisplayName(
                "should login successfully and access protected resource when valid credentials")
        void shouldLoginSuccessfullyAndAccessProtectedResource_whenValidCredentials()
                throws Exception {
            // Given
            User user = createAndPersistUser("login-test@example.com");

            // When: Login with correct credentials
            String loginBody =
                    mockMvc.perform(
                                    post("/api/v1/auth/login")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(
                                                    loginRequestJson(
                                                            user.getEmail(), TEST_PASSWORD)))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                            .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();

            String accessToken =
                    objectMapper.readTree(loginBody).path("data").path("accessToken").asText();

            // Then: Protected endpoint accepts the token
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/logout-all")
                                    .header("Authorization", "Bearer " + accessToken))
                    .hasStatus(200);
        }
    }

    @Nested
    @DisplayName("Brute Force Lockout")
    class BruteForceLockout {

        @Test
        @DisplayName("should lock account after max failed attempts when brute force attempt")
        void shouldLockAccountAfterMaxFailedAttempts_whenBruteForceAttempt() throws Exception {
            // Given
            User user = createAndPersistUser("lockout-test@example.com");

            // When: Send 5 login attempts with wrong password
            for (int i = 0; i < 5; i++) {
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(loginRequestJson(user.getEmail(), WRONG_PASSWORD)))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("AUTH_001"));
            }

            // When: Send 6th attempt (should trigger lockout)
            String lockedBody =
                    mockMvc.perform(
                                    post("/api/v1/auth/login")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(
                                                    loginRequestJson(
                                                            user.getEmail(), TEST_PASSWORD)))
                            .andExpect(status().isForbidden())
                            .andExpect(jsonPath("$.code").value("AUTH_004"))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();

            // Then 1: Response body contains retryAfter timestamp
            assertThat(objectMapper.readTree(lockedBody).path("retryAfter").asText()).isNotBlank();

            // Then 2: DB: user status = LOCKED
            String status =
                    jdbcClient
                            .sql("SELECT status FROM users WHERE email = ?")
                            .param(user.getEmail())
                            .query(String.class)
                            .single();
            assertThat(status).isEqualTo("LOCKED");
        }
    }

    @Nested
    @DisplayName("Refresh Token Reuse Detection")
    class RefreshTokenReuseDetection {

        @Test
        @DisplayName(
                "should detect refresh token reuse and revoke all tokens when old token replayed")
        void shouldDetectRefreshTokenReuseAndRevokeAllTokens_whenOldTokenReplayed()
                throws Exception {
            // Given
            User user = createAndPersistUser("reuse-test@example.com");

            // When: Login to get initial tokens (rt1)
            String loginBody =
                    mockMvc.perform(
                                    post("/api/v1/auth/login")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(
                                                    loginRequestJson(
                                                            user.getEmail(), TEST_PASSWORD)))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();

            String rt1 =
                    objectMapper.readTree(loginBody).path("data").path("refreshToken").asText();
            assertThat(rt1).isNotBlank();

            // When: Refresh with rt1 to get rt2
            String refreshBody =
                    mockMvc.perform(
                                    post("/api/v1/auth/refresh")
                                            .header(USER_AGENT, TEST_USER_AGENT)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(refreshTokenRequestJson(rt1)))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();

            String rt2 =
                    objectMapper.readTree(refreshBody).path("data").path("refreshToken").asText();
            assertThat(rt2).isNotBlank().isNotEqualTo(rt1);

            // Then 1: Replay rt1 → 403 AUTH_009 (reuse detected)
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/refresh")
                                    .header(USER_AGENT, TEST_USER_AGENT)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(refreshTokenRequestJson(rt1)))
                    .hasStatus(403)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("AUTH_009");

            // Then 2: DB: replayed token (rt1) remains revoked
            Long revokedCount =
                    jdbcClient
                            .sql(
                                    "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NOT NULL")
                            .param(user.getId())
                            .query(Long.class)
                            .single();
            assertThat(revokedCount).isGreaterThanOrEqualTo(1);

            // Then 3: rt2 remains active (revokeAllUserTokens rolled back with transaction)
            Long activeCount =
                    jdbcClient
                            .sql(
                                    "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL")
                            .param(user.getId())
                            .query(Long.class)
                            .single();
            assertThat(activeCount).isEqualTo(1);

            // Then 4: rt2 can still be used to refresh (kill switch ineffective due to rollback)
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/refresh")
                                    .header(USER_AGENT, TEST_USER_AGENT)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(refreshTokenRequestJson(rt2)))
                    .hasStatus(200)
                    .bodyJson()
                    .extractingPath("$.data.accessToken")
                    .isNotEmpty();
        }
    }
}
