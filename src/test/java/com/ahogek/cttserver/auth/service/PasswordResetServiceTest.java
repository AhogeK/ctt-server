package com.ahogek.cttserver.auth.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.dto.ResetPasswordRequest;
import com.ahogek.cttserver.auth.entity.PasswordResetToken;
import com.ahogek.cttserver.auth.lockout.LoginAttemptService;
import com.ahogek.cttserver.auth.repository.PasswordResetTokenRepository;
import com.ahogek.cttserver.auth.repository.RefreshTokenRepository;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.common.utils.TokenUtils;
import com.ahogek.cttserver.mail.service.MailOutboxService;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private MailOutboxService mailOutboxService;
    @Mock private AuditLogService auditLogService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private LoginAttemptService loginAttemptService;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service =
                new PasswordResetService(
                        userRepository,
                        tokenRepository,
                        mailOutboxService,
                        auditLogService,
                        passwordEncoder,
                        refreshTokenRepository,
                        loginAttemptService);
    }

    private User createActiveUser(UUID userId, String email, String displayName) {
        User user = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", userId);
        user.setEmail(email);
        user.setDisplayName(displayName);
        org.springframework.test.util.ReflectionTestUtils.setField(
                user, "status", UserStatus.ACTIVE);
        return user;
    }

    private User createInactiveUser(UUID userId, String email, String displayName) {
        User user = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", userId);
        user.setEmail(email);
        user.setDisplayName(displayName);
        org.springframework.test.util.ReflectionTestUtils.setField(
                user, "status", UserStatus.PENDING_VERIFICATION);
        return user;
    }

    @Nested
    @DisplayName("requestReset")
    class RequestReset {

        @Test
        @DisplayName("should revoke old tokens and create new token for active user")
        void shouldRevokeOldTokensAndCreateNewTokenForActiveUser() {
            UUID userId = UUID.randomUUID();
            String email = "user@example.com";
            String displayName = "Test User";
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            User user = createActiveUser(userId, email, displayName);

            when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));
            when(tokenRepository.revokeActiveTokensByUserId(eq(userId), any(Instant.class)))
                    .thenReturn(2);

            service.requestReset(email, ip, userAgent);

            verify(tokenRepository).revokeActiveTokensByUserId(eq(userId), any(Instant.class));

            ArgumentCaptor<PasswordResetToken> tokenCaptor =
                    ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(tokenRepository).save(tokenCaptor.capture());

            PasswordResetToken savedToken = tokenCaptor.getValue();
            assertThat(savedToken.getUserId()).isEqualTo(userId);
            assertThat(savedToken.getEmail()).isEqualTo(email);
            assertThat(savedToken.getTokenHash()).isNotBlank();
            assertThat(savedToken.getExpiresAt()).isAfter(Instant.now());
            assertThat(savedToken.getRequestIp()).isEqualTo(ip);
            assertThat(savedToken.getUserAgent()).isEqualTo(userAgent);

            verify(mailOutboxService)
                    .enqueuePasswordResetEmail(
                            eq(userId), eq(displayName), eq(email), any(String.class));

            verify(auditLogService)
                    .logSuccess(
                            userId,
                            AuditAction.PASSWORD_RESET_REQUESTED,
                            ResourceType.USER,
                            userId.toString());
        }

        @Test
        @DisplayName(
                "should log PASSWORD_RESET_EMAIL_NOT_FOUND when user not found (anti-enumeration)")
        void shouldLogWarningWhenUserNotFound() {
            String email = "nonexistent@example.com";
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());

            service.requestReset(email, ip, userAgent);

            verify(auditLogService)
                    .logFailure(
                            null,
                            AuditAction.PASSWORD_RESET_EMAIL_NOT_FOUND,
                            ResourceType.UNKNOWN,
                            email,
                            "Email not found or user not active");

            verify(tokenRepository, never()).revokeActiveTokensByUserId(any(), any());
            verify(tokenRepository, never()).save(any());
            verify(mailOutboxService, never())
                    .enqueuePasswordResetEmail(any(), any(), any(), any());
        }

        @Test
        @DisplayName(
                "should log PASSWORD_RESET_EMAIL_NOT_FOUND when user not active (anti-enumeration)")
        void shouldLogWarningWhenUserNotActive() {
            UUID userId = UUID.randomUUID();
            String email = "inactive@example.com";
            String displayName = "Inactive User";
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            User user = createInactiveUser(userId, email, displayName);

            when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));

            service.requestReset(email, ip, userAgent);

            verify(auditLogService)
                    .logFailure(
                            null,
                            AuditAction.PASSWORD_RESET_EMAIL_NOT_FOUND,
                            ResourceType.UNKNOWN,
                            email,
                            "Email not found or user not active");

            verify(tokenRepository, never()).revokeActiveTokensByUserId(any(), any());
            verify(tokenRepository, never()).save(any());
            verify(mailOutboxService, never())
                    .enqueuePasswordResetEmail(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should generate secure token with SHA-256 hash")
        void shouldGenerateSecureToken() {
            UUID userId = UUID.randomUUID();
            String email = "user@example.com";
            String displayName = "Test User";
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            User user = createActiveUser(userId, email, displayName);

            when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));
            when(tokenRepository.revokeActiveTokensByUserId(eq(userId), any(Instant.class)))
                    .thenReturn(0);

            ArgumentCaptor<String> rawTokenCaptor = ArgumentCaptor.forClass(String.class);

            service.requestReset(email, ip, userAgent);

            verify(mailOutboxService)
                    .enqueuePasswordResetEmail(
                            eq(userId), eq(displayName), eq(email), rawTokenCaptor.capture());

            String rawToken = rawTokenCaptor.getValue();
            assertThat(rawToken).isNotBlank().matches("[A-Za-z0-9_-]+");

            ArgumentCaptor<PasswordResetToken> tokenCaptor =
                    ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(tokenRepository).save(tokenCaptor.capture());

            String tokenHash = tokenCaptor.getValue().getTokenHash();
            assertThat(tokenHash).hasSize(64).matches("[0-9a-f]{64}");
        }

        @Test
        @DisplayName("should set token expiration to 1 hour")
        void shouldSetTokenExpirationToOneHour() {
            UUID userId = UUID.randomUUID();
            String email = "user@example.com";
            String displayName = "Test User";
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            User user = createActiveUser(userId, email, displayName);

            when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));
            when(tokenRepository.revokeActiveTokensByUserId(eq(userId), any(Instant.class)))
                    .thenReturn(0);

            Instant beforeRequest = Instant.now();

            service.requestReset(email, ip, userAgent);

            Instant afterRequest = Instant.now();

            ArgumentCaptor<PasswordResetToken> tokenCaptor =
                    ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(tokenRepository).save(tokenCaptor.capture());

            Instant expiresAt = tokenCaptor.getValue().getExpiresAt();
            Instant expectedMin = beforeRequest.plus(1, ChronoUnit.HOURS);
            Instant expectedMax = afterRequest.plus(1, ChronoUnit.HOURS);

            assertThat(expiresAt).isBetween(expectedMin, expectedMax);
        }
    }

    @Nested
    @DisplayName("resetPassword")
    class ResetPassword {

        @Test
        @DisplayName("should reset password successfully with valid token")
        void shouldResetPasswordSuccessfully() {
            UUID userId = UUID.randomUUID();
            String email = "user@example.com";
            String rawToken = TokenUtils.generateRawToken();
            String tokenHash = TokenUtils.hashToken(rawToken);
            String newPassword = "NewSecure@Pass123";
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            User user = createActiveUser(userId, email, "Test User");
            user.setPasswordHash("oldHash");

            PasswordResetToken token = createValidToken(userId, email, tokenHash);

            when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(newPassword, "oldHash")).thenReturn(false);
            when(passwordEncoder.encode(newPassword)).thenReturn("newHash");

            ResetPasswordRequest request = new ResetPasswordRequest(rawToken, newPassword);
            service.resetPassword(request, ip, userAgent);

            assertThat(user.getPasswordHash()).isEqualTo("newHash");
            assertThat(token.getConsumedAt()).isNotNull();

            verify(refreshTokenRepository).revokeAllUserTokens(eq(userId), any(Instant.class));
            verify(auditLogService)
                    .logSuccess(
                            userId,
                            AuditAction.PASSWORD_RESET_COMPLETED,
                            ResourceType.USER,
                            userId.toString());
        }

        @Test
        @DisplayName("should throw UnauthorizedException when token not found")
        void shouldThrowWhenTokenNotFound() {
            String rawToken = TokenUtils.generateRawToken();
            String tokenHash = TokenUtils.hashToken(rawToken);
            String newPassword = "NewSecure@Pass123";
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

            ResetPasswordRequest request = new ResetPasswordRequest(rawToken, newPassword);

            assertThatThrownBy(() -> service.resetPassword(request, ip, userAgent))
                    .isInstanceOf(UnauthorizedException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUTH_003);

            verify(userRepository, never()).findById(any());
            verify(refreshTokenRepository, never()).revokeAllUserTokens(any(), any());
        }

        @Test
        @DisplayName("should throw UnauthorizedException when token expired")
        void shouldThrowWhenTokenExpired() {
            UUID userId = UUID.randomUUID();
            String email = "user@example.com";
            String rawToken = TokenUtils.generateRawToken();
            String tokenHash = TokenUtils.hashToken(rawToken);
            String newPassword = "NewSecure@Pass123";
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            PasswordResetToken token = createExpiredToken(userId, email, tokenHash);

            when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

            ResetPasswordRequest request = new ResetPasswordRequest(rawToken, newPassword);

            assertThatThrownBy(() -> service.resetPassword(request, ip, userAgent))
                    .isInstanceOf(UnauthorizedException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUTH_002);

            verify(userRepository, never()).findById(any());
            verify(refreshTokenRepository, never()).revokeAllUserTokens(any(), any());
        }

        @Test
        @DisplayName("should throw UnauthorizedException when token consumed")
        void shouldThrowWhenTokenConsumed() {
            UUID userId = UUID.randomUUID();
            String email = "user@example.com";
            String rawToken = TokenUtils.generateRawToken();
            String tokenHash = TokenUtils.hashToken(rawToken);
            String newPassword = "NewSecure@Pass123";
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            PasswordResetToken token = createConsumedToken(userId, email, tokenHash);

            when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

            ResetPasswordRequest request = new ResetPasswordRequest(rawToken, newPassword);

            assertThatThrownBy(() -> service.resetPassword(request, ip, userAgent))
                    .isInstanceOf(UnauthorizedException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUTH_003);

            verify(userRepository, never()).findById(any());
            verify(refreshTokenRepository, never()).revokeAllUserTokens(any(), any());
        }

        @Test
        @DisplayName("should throw UnauthorizedException when token revoked")
        void shouldThrowWhenTokenRevoked() {
            UUID userId = UUID.randomUUID();
            String email = "user@example.com";
            String rawToken = TokenUtils.generateRawToken();
            String tokenHash = TokenUtils.hashToken(rawToken);
            String newPassword = "NewSecure@Pass123";
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            PasswordResetToken token = createRevokedToken(userId, email, tokenHash);

            when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

            ResetPasswordRequest request = new ResetPasswordRequest(rawToken, newPassword);

            assertThatThrownBy(() -> service.resetPassword(request, ip, userAgent))
                    .isInstanceOf(UnauthorizedException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUTH_003);

            verify(userRepository, never()).findById(any());
            verify(refreshTokenRepository, never()).revokeAllUserTokens(any(), any());
        }

        @Test
        @DisplayName("should throw UnauthorizedException when user not found")
        void shouldThrowWhenUserNotFound() {
            UUID userId = UUID.randomUUID();
            String email = "user@example.com";
            String rawToken = TokenUtils.generateRawToken();
            String tokenHash = TokenUtils.hashToken(rawToken);
            String newPassword = "NewSecure@Pass123";
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            PasswordResetToken token = createValidToken(userId, email, tokenHash);

            when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            ResetPasswordRequest request = new ResetPasswordRequest(rawToken, newPassword);

            assertThatThrownBy(() -> service.resetPassword(request, ip, userAgent))
                    .isInstanceOf(UnauthorizedException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUTH_003);

            verify(refreshTokenRepository, never()).revokeAllUserTokens(any(), any());
        }

        @Test
        @DisplayName("should throw ConflictException when new password same as old")
        void shouldThrowWhenPasswordSameAsOld() {
            UUID userId = UUID.randomUUID();
            String email = "user@example.com";
            String rawToken = TokenUtils.generateRawToken();
            String tokenHash = TokenUtils.hashToken(rawToken);
            String newPassword = "SamePassword123";
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            User user = createActiveUser(userId, email, "Test User");
            user.setPasswordHash("oldHash");

            PasswordResetToken token = createValidToken(userId, email, tokenHash);

            when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(newPassword, "oldHash")).thenReturn(true);

            ResetPasswordRequest request = new ResetPasswordRequest(rawToken, newPassword);

            assertThatThrownBy(() -> service.resetPassword(request, ip, userAgent))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.PASSWORD_SAME_AS_OLD);

            assertThat(token.getConsumedAt()).isNull();
            verify(refreshTokenRepository, never()).revokeAllUserTokens(any(), any());
        }

        @Test
        @DisplayName("should revoke all refresh tokens after password reset")
        void shouldRevokeAllRefreshTokens() {
            UUID userId = UUID.randomUUID();
            String email = "user@example.com";
            String rawToken = TokenUtils.generateRawToken();
            String tokenHash = TokenUtils.hashToken(rawToken);
            String newPassword = "NewSecure@Pass123";
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            User user = createActiveUser(userId, email, "Test User");
            user.setPasswordHash("oldHash");

            PasswordResetToken token = createValidToken(userId, email, tokenHash);

            when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(newPassword, "oldHash")).thenReturn(false);
            when(passwordEncoder.encode(newPassword)).thenReturn("newHash");

            ResetPasswordRequest request = new ResetPasswordRequest(rawToken, newPassword);
            service.resetPassword(request, ip, userAgent);

            ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(refreshTokenRepository).revokeAllUserTokens(eq(userId), instantCaptor.capture());

            assertThat(instantCaptor.getValue()).isNotNull().isBeforeOrEqualTo(Instant.now());
        }

        @Test
        @DisplayName("should unlock account if locked")
        void shouldUnlockAccountIfLocked() {
            UUID userId = UUID.randomUUID();
            String email = "locked@example.com";
            String rawToken = TokenUtils.generateRawToken();
            String tokenHash = TokenUtils.hashToken(rawToken);
            String newPassword = "NewSecure@Pass123";
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            User user = createLockedUser(userId, email, "Locked User");
            user.setPasswordHash("oldHash");

            PasswordResetToken token = createValidToken(userId, email, tokenHash);

            when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(newPassword, "oldHash")).thenReturn(false);
            when(passwordEncoder.encode(newPassword)).thenReturn("newHash");

            ResetPasswordRequest request = new ResetPasswordRequest(rawToken, newPassword);
            service.resetPassword(request, ip, userAgent);

            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            verify(loginAttemptService).recordSuccess(email);
            verify(auditLogService)
                    .logSuccess(
                            userId,
                            AuditAction.PASSWORD_RESET_COMPLETED,
                            ResourceType.USER,
                            userId.toString());
        }

        @Test
        @DisplayName("should prevent TOCTOU attack - only first concurrent request succeeds")
        void shouldPreventTOCTOUAttack() {
            UUID userId = UUID.randomUUID();
            String email = "user@example.com";
            String rawToken = TokenUtils.generateRawToken();
            String tokenHash = TokenUtils.hashToken(rawToken);
            String newPassword = "NewSecure@Pass123";
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            User user = createActiveUser(userId, email, "Test User");
            user.setPasswordHash("oldHash");

            PasswordResetToken token = createValidToken(userId, email, tokenHash);

            when(tokenRepository.findByTokenHash(tokenHash))
                    .thenReturn(Optional.of(token))
                    .thenReturn(Optional.of(token));

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(newPassword, "oldHash")).thenReturn(false);
            when(passwordEncoder.encode(newPassword)).thenReturn("newHash");

            ResetPasswordRequest request1 = new ResetPasswordRequest(rawToken, newPassword);
            service.resetPassword(request1, ip, userAgent);
            assertThat(token.getConsumedAt()).isNotNull();

            ResetPasswordRequest request2 = new ResetPasswordRequest(rawToken, newPassword);
            assertThatThrownBy(() -> service.resetPassword(request2, ip, userAgent))
                    .isInstanceOf(UnauthorizedException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUTH_003);
        }

        private PasswordResetToken createValidToken(UUID userId, String email, String tokenHash) {
            PasswordResetToken token = new PasswordResetToken();
            token.setUserId(userId);
            token.setEmail(email);
            token.setTokenHash(tokenHash);
            token.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
            return token;
        }

        private PasswordResetToken createExpiredToken(UUID userId, String email, String tokenHash) {
            PasswordResetToken token = new PasswordResetToken();
            token.setUserId(userId);
            token.setEmail(email);
            token.setTokenHash(tokenHash);
            token.setExpiresAt(Instant.now().minus(Duration.ofHours(1)));
            return token;
        }

        private PasswordResetToken createConsumedToken(
                UUID userId, String email, String tokenHash) {
            PasswordResetToken token = new PasswordResetToken();
            token.setUserId(userId);
            token.setEmail(email);
            token.setTokenHash(tokenHash);
            token.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
            token.setConsumedAt(Instant.now().minus(Duration.ofMinutes(10)));
            return token;
        }

        private PasswordResetToken createRevokedToken(UUID userId, String email, String tokenHash) {
            PasswordResetToken token = new PasswordResetToken();
            token.setUserId(userId);
            token.setEmail(email);
            token.setTokenHash(tokenHash);
            token.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
            token.setRevokedAt(Instant.now().minus(Duration.ofMinutes(10)));
            return token;
        }

        private User createLockedUser(UUID userId, String email, String displayName) {
            User user = new User();
            org.springframework.test.util.ReflectionTestUtils.setField(user, "id", userId);
            user.setEmail(email);
            user.setDisplayName(displayName);
            org.springframework.test.util.ReflectionTestUtils.setField(
                    user, "status", UserStatus.LOCKED);
            return user;
        }
    }
}
