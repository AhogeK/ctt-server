package com.ahogek.cttserver.auth.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.audit.model.AuditDetails;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.entity.EmailVerificationToken;
import com.ahogek.cttserver.auth.repository.EmailVerificationTokenRepository;
import com.ahogek.cttserver.common.exception.BusinessException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.mail.service.MailOutboxService;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;
import com.ahogek.cttserver.user.validator.UserValidator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailVerificationServiceTest {

    @Mock private EmailVerificationTokenRepository tokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private MailOutboxService mailOutboxService;
    @Mock private AuditLogService auditLog;
    @Mock private UserValidator userValidator;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service =
                new EmailVerificationService(
                        tokenRepository,
                        userRepository,
                        mailOutboxService,
                        auditLog,
                        userValidator);
    }

    private EmailVerificationToken createValidToken(UUID tokenId, UUID userId, String tokenHash) {
        EmailVerificationToken token = new EmailVerificationToken();
        org.springframework.test.util.ReflectionTestUtils.setField(token, "id", tokenId);
        token.setUserId(userId);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        return token;
    }

    private EmailVerificationToken createExpiredToken(UUID tokenId, UUID userId, String tokenHash) {
        EmailVerificationToken token = new EmailVerificationToken();
        org.springframework.test.util.ReflectionTestUtils.setField(token, "id", tokenId);
        token.setUserId(userId);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        return token;
    }

    private EmailVerificationToken createConsumedToken(
            UUID tokenId, UUID userId, String tokenHash) {
        EmailVerificationToken token = new EmailVerificationToken();
        org.springframework.test.util.ReflectionTestUtils.setField(token, "id", tokenId);
        token.setUserId(userId);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        token.setConsumedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        return token;
    }

    private EmailVerificationToken createRevokedToken(UUID tokenId, UUID userId, String tokenHash) {
        EmailVerificationToken token = new EmailVerificationToken();
        org.springframework.test.util.ReflectionTestUtils.setField(token, "id", tokenId);
        token.setUserId(userId);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        token.setRevokedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        return token;
    }

    private User createUser(UUID userId, String email, boolean emailVerified) {
        User user = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", userId);
        user.setEmail(email);
        user.setDisplayName("Test User");
        user.setEmailVerified(emailVerified);
        return user;
    }

    private String hashTokenSha256(String rawToken) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("verify()")
    class VerifyTests {

        @Test
        @DisplayName("should verify email successfully when valid token")
        void shouldVerifyEmailSuccessfully_whenValidToken() {
            // Given
            String rawToken = "valid-raw-token-123";
            String tokenHash = hashTokenSha256(rawToken);
            UUID userId = UUID.randomUUID();
            UUID tokenId = UUID.randomUUID();

            EmailVerificationToken token = createValidToken(tokenId, userId, tokenHash);
            User user = createUser(userId, "test@example.com", false);

            when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(tokenRepository.findValidTokensByUserId(eq(userId), any(Instant.class)))
                    .thenReturn(Collections.emptyList());

            // When
            service.verify(rawToken);

            // Then - Verify state changes (JPA Dirty Checking persists on commit)
            assertThat(token.getConsumedAt()).isNotNull();
            assertThat(user.getEmailVerified()).isTrue();

            verify(auditLog)
                    .log(
                            eq(userId),
                            eq(AuditAction.EMAIL_VERIFICATION_SUCCESS),
                            eq(ResourceType.EMAIL_VERIFICATION),
                            eq(tokenId.toString()),
                            eq(SecuritySeverity.INFO),
                            any(AuditDetails.class));
        }

        @Test
        @DisplayName("should throw NotFoundException when token not found")
        void shouldThrowNotFoundException_whenTokenNotFound() {
            // Given
            String rawToken = "non-existent-token";
            String tokenHash = hashTokenSha256(rawToken);

            when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> service.verify(rawToken))
                    .isInstanceOf(NotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MAIL_006)
                    .hasMessageContaining("Verification token not found");

            verify(userRepository, never()).save(any());
            verify(auditLog, never()).log(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should throw BusinessException when token expired")
        void shouldThrowBusinessException_whenTokenExpired() {
            // Given
            String rawToken = "expired-token";
            String tokenHash = hashTokenSha256(rawToken);
            UUID userId = UUID.randomUUID();

            EmailVerificationToken token = createExpiredToken(UUID.randomUUID(), userId, tokenHash);

            when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

            // When & Then
            assertThatThrownBy(() -> service.verify(rawToken))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MAIL_005)
                    .hasMessageContaining("Verification token has expired");

            verify(tokenRepository, never()).save(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw BusinessException when token consumed")
        void shouldThrowBusinessException_whenTokenConsumed() {
            // Given
            String rawToken = "consumed-token";
            String tokenHash = hashTokenSha256(rawToken);
            UUID userId = UUID.randomUUID();

            EmailVerificationToken token =
                    createConsumedToken(UUID.randomUUID(), userId, tokenHash);

            when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

            // When & Then
            assertThatThrownBy(() -> service.verify(rawToken))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MAIL_006)
                    .hasMessageContaining("Verification token has already been used");

            verify(tokenRepository, never()).save(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw BusinessException when token revoked")
        void shouldThrowBusinessException_whenTokenRevoked() {
            // Given
            String rawToken = "revoked-token";
            String tokenHash = hashTokenSha256(rawToken);
            UUID userId = UUID.randomUUID();

            EmailVerificationToken token = createRevokedToken(UUID.randomUUID(), userId, tokenHash);

            when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

            // When & Then
            assertThatThrownBy(() -> service.verify(rawToken))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MAIL_006)
                    .hasMessageContaining("Verification token has been revoked");

            verify(tokenRepository, never()).save(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should revoke all other tokens after verification")
        void shouldRevokeAllOtherTokensAfterVerification() {
            // Given
            String rawToken = "valid-token";
            String tokenHash = hashTokenSha256(rawToken);
            UUID userId = UUID.randomUUID();
            UUID tokenId = UUID.randomUUID();
            UUID otherTokenId = UUID.randomUUID();

            EmailVerificationToken token = createValidToken(tokenId, userId, tokenHash);
            EmailVerificationToken otherToken =
                    createValidToken(otherTokenId, userId, "other-hash");
            User user = createUser(userId, "test@example.com", false);

            when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(tokenRepository.findValidTokensByUserId(eq(userId), any(Instant.class)))
                    .thenReturn(List.of(token, otherToken));

            // When
            service.verify(rawToken);

            // Then - Verify state changes (JPA Dirty Checking persists on commit)
            assertThat(token.getConsumedAt()).isNotNull();
            assertThat(otherToken.getRevokedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("resendVerificationEmail()")
    class ResendVerificationEmailTests {

        @Test
        @DisplayName("should throw NotFoundException when user not found")
        void shouldThrowNotFoundException_whenUserNotFound() {
            // Given
            String email = "nonexistent@example.com";

            when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> service.resendVerificationEmail(email))
                    .isInstanceOf(NotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_004)
                    .hasMessageContaining("User not found");

            verify(tokenRepository, never()).save(any());
            verify(mailOutboxService, never()).enqueueVerificationEmail(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should resend verification email successfully")
        void shouldResendVerificationEmailSuccessfully() {
            // Given
            String email = "test@example.com";
            UUID userId = UUID.randomUUID();
            User user = createUser(userId, email, false);

            when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));
            when(tokenRepository.findValidTokensByUserId(eq(userId), any(Instant.class)))
                    .thenReturn(Collections.emptyList());

            // When
            service.resendVerificationEmail(email);

            // Then
            verify(tokenRepository).save(any(EmailVerificationToken.class));
            verify(mailOutboxService)
                    .enqueueVerificationEmail(
                            eq(userId), eq("Test User"), eq(email), any(String.class));
        }

        @Test
        @DisplayName("should throw ConflictException when user status is not PENDING_VERIFICATION")
        void shouldThrowConflictException_whenUserStatusIsNotPendingVerification() {
            // Given
            String email = "verified@example.com";
            UUID userId = UUID.randomUUID();
            User user = createUser(userId, email, true);

            when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));
            doThrow(
                            new com.ahogek.cttserver.common.exception.ConflictException(
                                    ErrorCode.COMMON_003,
                                    "User is not in pending verification state"))
                    .when(userValidator)
                    .assertCanVerifyEmail(user);

            // When & Then
            assertThatThrownBy(() -> service.resendVerificationEmail(email))
                    .isInstanceOf(com.ahogek.cttserver.common.exception.ConflictException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMON_003)
                    .hasMessageContaining("not in pending verification state");

            verify(tokenRepository, never()).save(any());
            verify(mailOutboxService, never()).enqueueVerificationEmail(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should revoke existing tokens when resending")
        void shouldRevokeExistingTokens_whenResending() {
            // Given
            String email = "test@example.com";
            UUID userId = UUID.randomUUID();
            User user = createUser(userId, email, false);
            EmailVerificationToken existingToken =
                    createValidToken(UUID.randomUUID(), userId, "existing-hash");

            when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));
            when(tokenRepository.findValidTokensByUserId(eq(userId), any(Instant.class)))
                    .thenReturn(List.of(existingToken));

            // When
            service.resendVerificationEmail(email);

            // Then - verify the existing token was revoked
            assertThat(existingToken.getRevokedAt()).isNotNull();
            // Then - verify save was called twice (revoke + new token)
            verify(tokenRepository, org.mockito.Mockito.times(2))
                    .save(any(EmailVerificationToken.class));
        }
    }
}
