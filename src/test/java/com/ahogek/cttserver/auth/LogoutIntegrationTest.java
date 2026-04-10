package com.ahogek.cttserver.auth;

import com.ahogek.cttserver.auth.dto.LoginRequest;
import com.ahogek.cttserver.auth.dto.LogoutRequest;
import com.ahogek.cttserver.auth.dto.RefreshTokenRequest;
import com.ahogek.cttserver.common.BaseIntegrationTest;
import com.ahogek.cttserver.common.utils.TokenUtils;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for logout and session revocation functionality.
 *
 * <p>Tests the complete lifecycle: single session logout, global logout (kill switch), and
 * unauthenticated access protection.
 *
 * <p>Uses full Spring context with PostgreSQL, Redis, and Testcontainers.
 *
 * @author AhogeK
 * @since 2026-04-10
 */
@BaseIntegrationTest
@TestPropertySource(properties = {"ctt.mail.outbox.poll-interval-ms=999999999"})
@DisplayName("E2E: Logout and Session Revocation Flow")
class LogoutIntegrationTest {

    @Autowired private MockMvcTester mvc;
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;

    private static final String TEST_PASSWORD = "TestPassword123!";

    record LoginTokens(String accessToken, String refreshToken) {}

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM audit_logs").update();
        jdbcClient.sql("DELETE FROM refresh_tokens").update();
        jdbcClient.sql("DELETE FROM login_attempts").update();
        jdbcClient.sql("DELETE FROM users").update();
    }

    private String uniqueEmail() {
        return "logout." + UUID.randomUUID() + "@test.example";
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

    private LoginTokens loginAndGetTokens(String email) throws Exception {
        return loginAndGetTokens(email, "test-device-" + UUID.randomUUID());
    }

    private LoginTokens loginAndGetTokens(String email, String deviceName) throws Exception {
        LoginRequest request =
                new LoginRequest(email, LogoutIntegrationTest.TEST_PASSWORD, deviceName);
        String loginBody =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                        .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        var jsonNode = objectMapper.readTree(loginBody);
        String accessToken = jsonNode.path("data").path("accessToken").asText();
        String refreshToken = jsonNode.path("data").path("refreshToken").asText();

        return new LoginTokens(accessToken, refreshToken);
    }

    private String logoutRequestJson(String refreshToken) throws Exception {
        LogoutRequest request = new LogoutRequest(refreshToken);
        return objectMapper.writeValueAsString(request);
    }

    private String refreshTokenRequestJson(String refreshToken) throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);
        return objectMapper.writeValueAsString(request);
    }

    private long countActiveTokensForUser(UUID userId) {
        return jdbcClient
                .sql(
                        "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL AND expires_at > NOW()")
                .param(userId)
                .query(Long.class)
                .single();
    }

    private boolean isTokenRevoked(String rawRefreshToken) {
        String tokenHash = TokenUtils.hashToken(rawRefreshToken);
        Long revokedCount =
                jdbcClient
                        .sql(
                                "SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = ? AND revoked_at IS NOT NULL")
                        .param(tokenHash)
                        .query(Long.class)
                        .single();
        return revokedCount > 0;
    }

    private void assertTokenRejected(String refreshToken) throws Exception {
        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(refreshTokenRequestJson(refreshToken)))
                .hasStatus(403)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_009");
    }

    @Nested
    @DisplayName("Single Session Logout")
    class SingleSessionLogout {

        @Test
        @DisplayName("should revoke refresh token when single logout")
        void shouldRevokeRefreshToken_whenSingleLogout() throws Exception {
            // Given
            User user = createAndPersistUser(uniqueEmail());
            LoginTokens tokens = loginAndGetTokens(user.getEmail());

            // Verify 1 active token exists
            assertThat(countActiveTokensForUser(user.getId())).isEqualTo(1);

            // When: POST /logout with refresh token + Bearer JWT
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/logout")
                                    .header("Authorization", "Bearer " + tokens.accessToken())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(logoutRequestJson(tokens.refreshToken())))
                    .hasStatus(200);

            // Then 1: Token revoked in DB
            assertThat(isTokenRevoked(tokens.refreshToken())).isTrue();

            // Then 2: 0 active tokens
            assertThat(countActiveTokensForUser(user.getId())).isZero();

            // Then 3: POST /refresh with same token returns 403 AUTH_009 (reuse detection)
            assertTokenRejected(tokens.refreshToken());

            // Then 4: Audit log contains LOGOUT_SUCCESS
            Long auditCount =
                    jdbcClient
                            .sql("SELECT COUNT(*) FROM audit_logs WHERE action = 'LOGOUT_SUCCESS'")
                            .query(Long.class)
                            .single();
            assertThat(auditCount).isPositive();
        }

        @Test
        @DisplayName("should return 200 when logout with already revoked token")
        void shouldReturn200_whenLogoutWithAlreadyRevokedToken() throws Exception {
            // Given
            User user = createAndPersistUser(uniqueEmail());
            LoginTokens tokens = loginAndGetTokens(user.getEmail());

            // When: First logout
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/logout")
                                    .header("Authorization", "Bearer " + tokens.accessToken())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(logoutRequestJson(tokens.refreshToken())))
                    .hasStatus(200);

            // When: Second logout with same token
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/logout")
                                    .header("Authorization", "Bearer " + tokens.accessToken())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(logoutRequestJson(tokens.refreshToken())))
                    .hasStatus(200);

            // Then: Token still revoked
            assertThat(isTokenRevoked(tokens.refreshToken())).isTrue();
        }

        @Test
        @DisplayName("should return 400 when logout with blank token")
        void shouldReturn400_whenLogoutWithBlankToken() throws Exception {
            // Given
            User user = createAndPersistUser(uniqueEmail());
            LoginTokens tokens = loginAndGetTokens(user.getEmail());

            // When: POST /logout with blank refresh token
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/logout")
                                    .header("Authorization", "Bearer " + tokens.accessToken())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"refreshToken\": \"\"}"))
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("COMMON_003");
        }

        @Test
        @DisplayName("should silently intercept when user tries to revoke another user's token")
        void shouldSilentlyIntercept_whenUserTriesToRevokeAnotherUsersToken() throws Exception {
            // Given: User A and User B
            User userA = createAndPersistUser(uniqueEmail());
            User userB = createAndPersistUser(uniqueEmail());

            LoginTokens tokensA = loginAndGetTokens(userA.getEmail());
            LoginTokens tokensB = loginAndGetTokens(userB.getEmail());

            // Verify both have active tokens
            assertThat(countActiveTokensForUser(userB.getId())).isEqualTo(1);

            // When: User A POST /logout with User B's refresh token
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/logout")
                                    .header("Authorization", "Bearer " + tokensA.accessToken())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(logoutRequestJson(tokensB.refreshToken())))
                    .hasStatus(200);

            // Then 1: User B's token still active
            assertThat(countActiveTokensForUser(userB.getId())).isEqualTo(1);

            // Then 2: User B's token not revoked in DB
            assertThat(isTokenRevoked(tokensB.refreshToken())).isFalse();

            // Then 3: Audit log contains SECURITY_ALERT for BOLA attempt
            Long alertCount =
                    jdbcClient
                            .sql("SELECT COUNT(*) FROM audit_logs WHERE action = 'SECURITY_ALERT'")
                            .query(Long.class)
                            .single();
            assertThat(alertCount).isPositive();
        }

        @Test
        @DisplayName("should return 200 when logout with non-existent token")
        void shouldReturn200_whenLogoutWithNonExistentToken() throws Exception {
            // Given
            User user = createAndPersistUser(uniqueEmail());
            LoginTokens tokens = loginAndGetTokens(user.getEmail());

            // When: POST /logout with a forged token that doesn't exist in DB
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/logout")
                                    .header("Authorization", "Bearer " + tokens.accessToken())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(logoutRequestJson("completely-forged-token-xyz")))
                    .hasStatus(200);

            // Then: Original token still active (not revoked)
            assertThat(isTokenRevoked(tokens.refreshToken())).isFalse();
            assertThat(countActiveTokensForUser(user.getId())).isEqualTo(1);
        }

        @Test
        @DisplayName("should return 200 when logout with expired token")
        void shouldReturn200_whenLogoutWithExpiredToken() throws Exception {
            // Given
            User user = createAndPersistUser(uniqueEmail());
            LoginTokens tokens = loginAndGetTokens(user.getEmail());

            // Expire the token via direct DB manipulation
            jdbcClient
                    .sql(
                            "UPDATE refresh_tokens SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 hour' WHERE user_id = ?")
                    .param(user.getId())
                    .update();

            // When: POST /logout with expired token
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/logout")
                                    .header("Authorization", "Bearer " + tokens.accessToken())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(logoutRequestJson(tokens.refreshToken())))
                    .hasStatus(200);

            // Then: Token not revoked (expired tokens are not re-revoked)
            assertThat(isTokenRevoked(tokens.refreshToken())).isFalse();
        }

        @Test
        @DisplayName("should return 400 when logout with null refreshToken field")
        void shouldReturn400_whenLogoutWithNullRefreshToken() throws Exception {
            // Given
            User user = createAndPersistUser(uniqueEmail());
            LoginTokens tokens = loginAndGetTokens(user.getEmail());

            // When: POST /logout with null refreshToken (field omitted)
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/logout")
                                    .header("Authorization", "Bearer " + tokens.accessToken())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}"))
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("COMMON_003");
        }
    }

    @Nested
    @DisplayName("Global Logout / Kill Switch")
    class GlobalLogout {

        @Test
        @DisplayName("should revoke all tokens when logout all")
        void shouldRevokeAllTokens_whenLogoutAll() throws Exception {
            // Given
            User user = createAndPersistUser(uniqueEmail());

            // Login 3 times with different device names
            LoginTokens tokens1 = loginAndGetTokens(user.getEmail(), "device-1");
            LoginTokens tokens2 = loginAndGetTokens(user.getEmail(), "device-2");
            LoginTokens tokens3 = loginAndGetTokens(user.getEmail(), "device-3");

            // Verify 3 active tokens
            assertThat(countActiveTokensForUser(user.getId())).isEqualTo(3);

            // When: POST /logout-all with Bearer JWT
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/logout-all")
                                    .header("Authorization", "Bearer " + tokens1.accessToken()))
                    .hasStatus(200);

            // Then 1: 0 active tokens
            assertThat(countActiveTokensForUser(user.getId())).isZero();

            // Then 2: All 3 tokens rejected on /refresh (403 AUTH_009)
            assertTokenRejected(tokens1.refreshToken());
            assertTokenRejected(tokens2.refreshToken());
            assertTokenRejected(tokens3.refreshToken());

            // Then 3: Audit log contains LOGOUT_ALL_DEVICES
            Long auditCount =
                    jdbcClient
                            .sql(
                                    "SELECT COUNT(*) FROM audit_logs WHERE action = 'LOGOUT_ALL_DEVICES'")
                            .query(Long.class)
                            .single();
            assertThat(auditCount).isPositive();
        }

        @Test
        @DisplayName("should return 200 when logout all with no active tokens")
        void shouldReturn200_whenLogoutAllWithNoActiveTokens() throws Exception {
            // Given
            User user = createAndPersistUser(uniqueEmail());
            LoginTokens tokens = loginAndGetTokens(user.getEmail());

            // When: First logout-all
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/logout-all")
                                    .header("Authorization", "Bearer " + tokens.accessToken()))
                    .hasStatus(200);

            // When: Second logout-all
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/logout-all")
                                    .header("Authorization", "Bearer " + tokens.accessToken()))
                    .hasStatus(200);

            // Then: 0 active tokens
            assertThat(countActiveTokensForUser(user.getId())).isZero();
        }
    }

    @Nested
    @DisplayName("Unauthenticated Access")
    class UnauthenticatedAccess {

        @Test
        @DisplayName("should return 401 when logout without auth")
        void shouldReturn401_whenLogoutWithoutAuth() {
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/logout")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"refreshToken\": \"some-token\"}"))
                    .hasStatus(401);
        }

        @Test
        @DisplayName("should return 401 when logout all without auth")
        void shouldReturn401_whenLogoutAllWithoutAuth() {
            assertThat(mvc.post().uri("/api/v1/auth/logout-all")).hasStatus(401);
        }
    }
}
