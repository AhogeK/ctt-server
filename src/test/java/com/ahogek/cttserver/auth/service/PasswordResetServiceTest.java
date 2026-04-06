package com.ahogek.cttserver.auth.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.entity.PasswordResetToken;
import com.ahogek.cttserver.auth.repository.PasswordResetTokenRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service =
                new PasswordResetService(
                        userRepository, tokenRepository, mailOutboxService, auditLogService);
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
}
