package com.ahogek.cttserver.auth;

import com.ahogek.cttserver.auth.service.JwtTokenProvider;
import com.ahogek.cttserver.common.BaseIntegrationTest;
import com.ahogek.cttserver.fixtures.UserFixtures;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for the account-status gate on the endpoints that change credentials or
 * identity.
 *
 * <p>The gate reads the status the token carries — the status the account had when the token was
 * issued or refreshed — so a token holding a non-active status is the only way these endpoints
 * could be reached by an account that is no longer active.
 *
 * <p>The operations that protect an account are deliberately outside the gate; the last case here
 * pins that, because a locked user must keep a way to end their own sessions.
 *
 * <p>Uses the full Spring context with PostgreSQL, Redis, and Testcontainers.
 */
@BaseIntegrationTest
@DisplayName("Active account gate")
class ActiveAccountGateIntegrationTest {

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM refresh_tokens").update();
        userRepository.deleteAll();
    }

    private String bearerFor(UserStatus status) {
        User user = UserFixtures.regularUser().status(status).emailVerified(true).build();
        return "Bearer "
                + jwtTokenProvider.generateAccessToken(userRepository.saveAndFlush(user), null);
    }

    @Test
    @DisplayName("Should reject a locked account changing its password")
    void shouldRejectLockedAccount_whenChangingPassword() {
        assertThat(
                        mvc.post()
                                .uri("/api/v1/users/me/password/change")
                                .header("Authorization", bearerFor(UserStatus.LOCKED))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"currentPassword": "Test@1234", "newPassword": "NewPass@1234"}
                                        """)
                                .exchange())
                .hasStatus(403)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_004");
    }

    @Test
    @DisplayName("Should reject a locked account deleting itself")
    void shouldRejectLockedAccount_whenDeletingAccount() {
        assertThat(
                        mvc.delete()
                                .uri("/api/v1/users/me")
                                .header("Authorization", bearerFor(UserStatus.LOCKED))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"password": "Test@1234"}
                                        """)
                                .exchange())
                .hasStatus(403)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_004");
    }

    @Test
    @DisplayName("Should reject a deleted account changing its display name")
    void shouldRejectDeletedAccount_whenChangingDisplayName() {
        // A deleted account keeps its already-issued access token for up to the 15-minute TTL, so
        // this is the window the gate exists to close.
        assertThat(
                        mvc.patch()
                                .uri("/api/v1/users/me")
                                .header("Authorization", bearerFor(UserStatus.DELETED))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"displayName": "RenamedUser"}
                                        """)
                                .exchange())
                .hasStatus(403)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_022");
    }

    @Test
    @DisplayName("Should reject an unverified account minting an API key")
    void shouldRejectPendingAccount_whenCreatingApiKey() {
        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/api-keys")
                                .header("Authorization", bearerFor(UserStatus.PENDING_VERIFICATION))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"name": "gate-test", "scopes": ["READ"]}
                                        """)
                                .exchange())
                .hasStatus(403)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_006");
    }

    @Test
    @DisplayName("Should reject a suspended account removing a login method")
    void shouldRejectSuspendedAccount_whenUnbindingOAuth() {
        assertThat(
                        mvc.delete()
                                .uri("/api/v1/auth/oauth/accounts/github")
                                .header("Authorization", bearerFor(UserStatus.SUSPENDED))
                                .exchange())
                .hasStatus(403)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_005");
    }

    @Test
    @DisplayName("Should still let a locked account end its own sessions")
    void shouldAllowLockedAccount_whenEndingSessions() {
        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/logout-all")
                                .header("Authorization", bearerFor(UserStatus.LOCKED))
                                .exchange())
                .hasStatusOk();
    }
}
