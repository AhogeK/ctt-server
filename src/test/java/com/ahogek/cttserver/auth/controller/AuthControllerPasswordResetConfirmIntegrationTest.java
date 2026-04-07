package com.ahogek.cttserver.auth.controller;

import com.ahogek.cttserver.auth.entity.PasswordResetToken;
import com.ahogek.cttserver.auth.entity.RefreshToken;
import com.ahogek.cttserver.auth.repository.PasswordResetTokenRepository;
import com.ahogek.cttserver.auth.repository.RefreshTokenRepository;
import com.ahogek.cttserver.common.BaseIntegrationTest;
import com.ahogek.cttserver.common.utils.TokenUtils;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@BaseIntegrationTest
@DisplayName("AuthController Password Reset Confirm Integration Tests")
class AuthControllerPasswordResetConfirmIntegrationTest {

    @Autowired private MockMvcTester mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Nested
    @DisplayName("Successful password reset")
    class SuccessfulReset {

        @Test
        @DisplayName("Should reset password successfully via HTTP endpoint")
        void shouldResetPasswordSuccessfullyViaHttpEndpoint() {
            User user = createActiveUser("test@example.com", "OldPassword123!");
            userRepository.save(user);

            String rawToken = TokenUtils.generateRawToken();
            PasswordResetToken token = createValidToken(user.getId(), "test@example.com", rawToken);
            tokenRepository.save(token);

            String request =
                    """
                    {
                        "token": "%s",
                        "newPassword": "NewSecure@Pass123"
                    }
                    """
                            .formatted(rawToken);

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/password-reset/confirm")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(request))
                    .hasStatusOk();

            PasswordResetToken consumedToken =
                    tokenRepository.findByTokenHash(TokenUtils.hashToken(rawToken)).orElseThrow();
            assertThat(consumedToken.getConsumedAt()).isNotNull();

            User updatedUser = userRepository.findById(user.getId()).orElseThrow();
            assertThat(passwordEncoder.matches("NewSecure@Pass123", updatedUser.getPasswordHash()))
                    .isTrue();
        }

        @Test
        @DisplayName("Should revoke all refresh tokens after password reset")
        void shouldRevokeAllRefreshTokensAfterPasswordReset() {
            User user = createActiveUser("test@example.com", "OldPassword123!");
            userRepository.save(user);

            RefreshToken refreshToken1 = createActiveRefreshToken(user.getId());
            RefreshToken refreshToken2 = createActiveRefreshToken(user.getId());
            refreshTokenRepository.saveAll(List.of(refreshToken1, refreshToken2));

            String rawToken = TokenUtils.generateRawToken();
            PasswordResetToken token = createValidToken(user.getId(), "test@example.com", rawToken);
            tokenRepository.save(token);

            String request =
                    """
                    {
                        "token": "%s",
                        "newPassword": "NewSecure@Pass123"
                    }
                    """
                            .formatted(rawToken);

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/password-reset/confirm")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(request))
                    .hasStatusOk();

            List<RefreshToken> tokens = refreshTokenRepository.findAll();
            assertThat(tokens).isNotEmpty().allMatch(rt -> rt.getRevokedAt() != null);
        }

        @Test
        @DisplayName("Should unlock account if locked")
        void shouldUnlockAccountIfLocked() {
            User user = createLockedUser("locked@example.com", "OldPassword123!");
            userRepository.save(user);

            String rawToken = TokenUtils.generateRawToken();
            PasswordResetToken token =
                    createValidToken(user.getId(), "locked@example.com", rawToken);
            tokenRepository.save(token);

            String request =
                    """
                    {
                        "token": "%s",
                        "newPassword": "NewSecure@Pass123"
                    }
                    """
                            .formatted(rawToken);

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/password-reset/confirm")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(request))
                    .hasStatusOk();

            User updatedUser = userRepository.findById(user.getId()).orElseThrow();
            assertThat(updatedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("Validation errors")
    class ValidationErrors {

        @ParameterizedTest
        @MethodSource("validationErrorCases")
        @DisplayName("Should return 400 for validation errors")
        void shouldReturn400ForValidationErrors(
                String token, String newPassword, String expectedCode) {
            StringBuilder requestBuilder = new StringBuilder("{");
            boolean needsComma = false;

            if (token != null && !token.isEmpty()) {
                requestBuilder.append("\"token\": \"").append(token).append("\"");
                needsComma = true;
            }

            if (newPassword != null) {
                if (needsComma) {
                    requestBuilder.append(",");
                }
                requestBuilder.append("\"newPassword\": \"").append(newPassword).append("\"");
            }

            requestBuilder.append("}");

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/password-reset/confirm")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBuilder.toString()))
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo(expectedCode);
        }

        private static java.util.stream.Stream<Arguments> validationErrorCases() {
            return java.util.stream.Stream.of(
                    Arguments.of("", "NewSecure@Pass123", "COMMON_003"), // blank token
                    Arguments.of("valid-token", "weak", "COMMON_003"), // invalid password
                    Arguments.of("valid-token", null, "COMMON_003") // missing password
                    );
        }
    }

    @Nested
    @DisplayName("Token errors")
    class TokenErrors {

        @Test
        @DisplayName("Should return 401 when token not found")
        void shouldReturn401WhenTokenNotFound() {
            String request =
                    """
                    {
                        "token": "nonexistent-token",
                        "newPassword": "NewSecure@Pass123"
                    }
                    """;

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/password-reset/confirm")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(request))
                    .hasStatus(401)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("AUTH_003");
        }

        @Test
        @DisplayName("Should return 401 when token expired")
        void shouldReturn401WhenTokenExpired() {
            User user = createActiveUser("test@example.com", "OldPassword123!");
            userRepository.save(user);

            String rawToken = TokenUtils.generateRawToken();
            PasswordResetToken token =
                    createExpiredToken(user.getId(), "test@example.com", rawToken);
            tokenRepository.save(token);

            String request =
                    """
                    {
                        "token": "%s",
                        "newPassword": "NewSecure@Pass123"
                    }
                    """
                            .formatted(rawToken);

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/password-reset/confirm")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(request))
                    .hasStatus(401)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("AUTH_002");
        }

        @Test
        @DisplayName("Should return 401 when token already consumed")
        void shouldReturn401WhenTokenConsumed() {
            User user = createActiveUser("test@example.com", "OldPassword123!");
            userRepository.save(user);

            String rawToken = TokenUtils.generateRawToken();
            PasswordResetToken token =
                    createConsumedToken(user.getId(), "test@example.com", rawToken);
            tokenRepository.save(token);

            String request =
                    """
                    {
                        "token": "%s",
                        "newPassword": "NewSecure@Pass123"
                    }
                    """
                            .formatted(rawToken);

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/password-reset/confirm")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(request))
                    .hasStatus(401)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("AUTH_003");
        }

        @Test
        @DisplayName("Should return 401 when token revoked")
        void shouldReturn401WhenTokenRevoked() {
            User user = createActiveUser("test@example.com", "OldPassword123!");
            userRepository.save(user);

            String rawToken = TokenUtils.generateRawToken();
            PasswordResetToken token =
                    createRevokedToken(user.getId(), "test@example.com", rawToken);
            tokenRepository.save(token);

            String request =
                    """
                    {
                        "token": "%s",
                        "newPassword": "NewSecure@Pass123"
                    }
                    """
                            .formatted(rawToken);

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/password-reset/confirm")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(request))
                    .hasStatus(401)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("AUTH_003");
        }
    }

    @Nested
    @DisplayName("Password conflict")
    class PasswordConflict {

        @Test
        @DisplayName("Should return 409 when new password same as old password")
        void shouldReturn409WhenNewPasswordSameAsOld() {
            String oldPassword = "OldPassword123!";
            User user = createActiveUser("test@example.com", oldPassword);
            userRepository.save(user);

            String rawToken = TokenUtils.generateRawToken();
            PasswordResetToken token = createValidToken(user.getId(), "test@example.com", rawToken);
            tokenRepository.save(token);

            String request =
                    """
                    {
                        "token": "%s",
                        "newPassword": "%s"
                    }
                    """
                            .formatted(rawToken, oldPassword);

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/password-reset/confirm")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(request))
                    .hasStatus(409)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("PASSWORD_SAME_AS_OLD");

            PasswordResetToken unusedToken =
                    tokenRepository.findByTokenHash(TokenUtils.hashToken(rawToken)).orElseThrow();
            assertThat(unusedToken.getConsumedAt()).isNull();
        }
    }

    private User createActiveUser(String email, String password) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName("Test User");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.verifyEmail();
        return user;
    }

    private User createLockedUser(String email, String password) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName("Test User");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.verifyEmail();
        org.springframework.test.util.ReflectionTestUtils.setField(
                user, "status", UserStatus.LOCKED);
        return user;
    }

    private PasswordResetToken createValidToken(
            java.util.UUID userId, String email, String rawToken) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(userId);
        token.setEmail(email);
        token.setTokenHash(TokenUtils.hashToken(rawToken));
        token.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        return token;
    }

    private PasswordResetToken createExpiredToken(
            java.util.UUID userId, String email, String rawToken) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(userId);
        token.setEmail(email);
        token.setTokenHash(TokenUtils.hashToken(rawToken));
        token.setExpiresAt(Instant.now().minus(Duration.ofHours(1)));
        return token;
    }

    private PasswordResetToken createConsumedToken(
            java.util.UUID userId, String email, String rawToken) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(userId);
        token.setEmail(email);
        token.setTokenHash(TokenUtils.hashToken(rawToken));
        token.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        token.setConsumedAt(Instant.now().minus(Duration.ofMinutes(10)));
        return token;
    }

    private PasswordResetToken createRevokedToken(
            java.util.UUID userId, String email, String rawToken) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(userId);
        token.setEmail(email);
        token.setTokenHash(TokenUtils.hashToken(rawToken));
        token.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        token.setRevokedAt(Instant.now().minus(Duration.ofMinutes(10)));
        return token;
    }

    private RefreshToken createActiveRefreshToken(java.util.UUID userId) {
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(TokenUtils.generateRawToken());
        token.setIssuedFor("WEB");
        token.setExpiresAt(Instant.now().plus(Duration.ofDays(30)));
        return token;
    }
}
