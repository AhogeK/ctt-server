package com.ahogek.cttserver.user.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.entity.EmailVerificationToken;
import com.ahogek.cttserver.auth.repository.EmailVerificationTokenRepository;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.common.response.EmptyResponse;
import com.ahogek.cttserver.common.utils.TokenUtils;
import com.ahogek.cttserver.mail.service.MailOutboxService;
import com.ahogek.cttserver.user.dto.EmailStatusResponse;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;
import com.ahogek.cttserver.user.validator.UserValidator;

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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailChangeServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private EmailVerificationTokenRepository tokenRepository;
    @Mock private MailOutboxService mailOutboxService;
    @Mock private AuditLogService auditLogService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserValidator userValidator;

    private EmailChangeService service;

    @BeforeEach
    void setUp() {
        service =
                new EmailChangeService(
                        userRepository,
                        tokenRepository,
                        mailOutboxService,
                        auditLogService,
                        passwordEncoder,
                        userValidator);
    }

    private User createUserWithPassword(UUID userId, String email, String displayName) {
        User user = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", userId);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setPasswordHash("encodedPassword");
        return user;
    }

    private User createOAuthUser(UUID userId, String email, String displayName) {
        User user = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", userId);
        user.setEmail(email);
        user.setDisplayName(displayName);
        // OAuth users have no password hash
        return user;
    }

    private EmailVerificationToken createValidChangeEmailToken(
            UUID userId, String newEmail, String tokenHash) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserId(userId);
        token.setEmail(newEmail);
        token.setPurpose(EmailVerificationToken.PURPOSE_CHANGE_EMAIL);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        return token;
    }

    private EmailVerificationToken createExpiredChangeEmailToken(
            UUID userId, String newEmail, String tokenHash) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserId(userId);
        token.setEmail(newEmail);
        token.setPurpose(EmailVerificationToken.PURPOSE_CHANGE_EMAIL);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(Instant.now().minus(Duration.ofHours(1)));
        return token;
    }

    private EmailVerificationToken createCancelledChangeEmailToken(
            UUID userId, String newEmail, String tokenHash) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserId(userId);
        token.setEmail(newEmail);
        token.setPurpose(EmailVerificationToken.PURPOSE_CHANGE_EMAIL);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        token.cancel();
        return token;
    }

    private EmailVerificationToken createCompletedChangeEmailToken(
            UUID userId, String newEmail, String tokenHash) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserId(userId);
        token.setEmail(newEmail);
        token.setPurpose(EmailVerificationToken.PURPOSE_CHANGE_EMAIL);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        token.complete();
        return token;
    }

    private EmailVerificationToken createTokenWithMaxAttempts(
            UUID userId, String newEmail, String tokenHash) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserId(userId);
        token.setEmail(newEmail);
        token.setPurpose(EmailVerificationToken.PURPOSE_CHANGE_EMAIL);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        token.setAttempts(5);
        return token;
    }

    @Nested
    @DisplayName("requestEmailChange")
    class RequestEmailChange {

        @Test
        @DisplayName("should send verification email when valid request")
        void shouldSendVerificationEmail_whenValidRequest() {
            UUID userId = UUID.randomUUID();
            String newEmail = "new@example.com";
            String password = "currentPassword";
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            User user = createUserWithPassword(userId, "old@example.com", "Test User");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(password, "encodedPassword")).thenReturn(true);
            when(tokenRepository.cancelPendingChangeEmailTokensByUserId(
                            eq(userId), any(Instant.class)))
                    .thenReturn(0);
            when(mailOutboxService.enqueueChangeEmailVerification(
                            eq(userId), eq("Test User"), eq(newEmail), any(String.class)))
                    .thenReturn(EmptyResponse.ok(false));

            EmptyResponse response =
                    service.requestEmailChange(userId, newEmail, password, ip, userAgent);

            verify(userValidator).assertEmailUnique(newEmail);
            verify(tokenRepository)
                    .cancelPendingChangeEmailTokensByUserId(eq(userId), any(Instant.class));

            ArgumentCaptor<EmailVerificationToken> tokenCaptor =
                    ArgumentCaptor.forClass(EmailVerificationToken.class);
            verify(tokenRepository).save(tokenCaptor.capture());

            EmailVerificationToken savedToken = tokenCaptor.getValue();
            assertThat(savedToken.getUserId()).isEqualTo(userId);
            assertThat(savedToken.getEmail()).isEqualTo(newEmail);
            assertThat(savedToken.getOldEmail()).isEqualTo("old@example.com");
            assertThat(savedToken.getPurpose())
                    .isEqualTo(EmailVerificationToken.PURPOSE_CHANGE_EMAIL);
            assertThat(savedToken.getTokenHash()).isNotBlank();
            assertThat(savedToken.getExpiresAt()).isAfter(Instant.now());
            assertThat(savedToken.getRequestIp()).isEqualTo(ip);
            assertThat(savedToken.getUserAgent()).isEqualTo(userAgent);

            verify(mailOutboxService)
                    .enqueueChangeEmailVerification(
                            eq(userId), eq("Test User"), eq(newEmail), any(String.class));

            verify(auditLogService)
                    .logSuccess(
                            userId,
                            AuditAction.EMAIL_CHANGE_REQUESTED,
                            ResourceType.USER,
                            userId.toString());

            assertThat(response.success()).isTrue();
            assertThat(response.idempotentSkip()).isFalse();
        }

        @Test
        @DisplayName("should throw ConflictException when email already registered")
        void shouldThrowConflictException_whenEmailAlreadyRegistered() {
            UUID userId = UUID.randomUUID();
            String newEmail = "taken@example.com";
            String password = "currentPassword";
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            User user = createUserWithPassword(userId, "old@example.com", "Test User");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            doThrow(new ConflictException(ErrorCode.USER_001, "Email already registered"))
                    .when(userValidator)
                    .assertEmailUnique(newEmail);

            assertThatThrownBy(
                            () ->
                                    service.requestEmailChange(
                                            userId, newEmail, password, ip, userAgent))
                    .isInstanceOf(ConflictException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_001)
                    .hasMessageContaining("Email already registered");

            verify(tokenRepository, never()).save(any());
            verify(mailOutboxService, never())
                    .enqueueChangeEmailVerification(any(), any(), any(), any());
            verify(auditLogService, never()).logSuccess(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should throw UnauthorizedException when password required but blank")
        void shouldThrowValidationException_whenPasswordRequiredButBlank() {
            UUID userId = UUID.randomUUID();
            String newEmail = "new@example.com";
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            User user = createUserWithPassword(userId, "old@example.com", "Test User");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThatThrownBy(
                            () -> service.requestEmailChange(userId, newEmail, null, ip, userAgent))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_013)
                    .hasMessageContaining("Password verification required");

            assertThatThrownBy(
                            () ->
                                    service.requestEmailChange(
                                            userId, newEmail, "   ", ip, userAgent))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_013)
                    .hasMessageContaining("Password verification required");

            verify(tokenRepository, never()).save(any());
            verify(mailOutboxService, never())
                    .enqueueChangeEmailVerification(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should throw UnauthorizedException when password incorrect")
        void shouldThrowUnauthorizedException_whenPasswordIncorrect() {
            UUID userId = UUID.randomUUID();
            String newEmail = "new@example.com";
            String wrongPassword = "wrongPassword";
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            User user = createUserWithPassword(userId, "old@example.com", "Test User");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(wrongPassword, "encodedPassword")).thenReturn(false);

            assertThatThrownBy(
                            () ->
                                    service.requestEmailChange(
                                            userId, newEmail, wrongPassword, ip, userAgent))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_014)
                    .hasMessageContaining("Invalid password");

            verify(tokenRepository, never()).save(any());
            verify(mailOutboxService, never())
                    .enqueueChangeEmailVerification(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should cancel existing pending tokens when requesting new change")
        void shouldCancelExistingPendingTokens_whenRequestingNewChange() {
            UUID userId = UUID.randomUUID();
            String newEmail = "new@example.com";
            String password = "currentPassword";
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            User user = createUserWithPassword(userId, "old@example.com", "Test User");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(password, "encodedPassword")).thenReturn(true);
            when(tokenRepository.cancelPendingChangeEmailTokensByUserId(
                            eq(userId), any(Instant.class)))
                    .thenReturn(2);
            when(mailOutboxService.enqueueChangeEmailVerification(
                            eq(userId), eq("Test User"), eq(newEmail), any(String.class)))
                    .thenReturn(EmptyResponse.ok(false));

            service.requestEmailChange(userId, newEmail, password, ip, userAgent);

            verify(tokenRepository)
                    .cancelPendingChangeEmailTokensByUserId(eq(userId), any(Instant.class));
        }

        @Test
        @DisplayName("should skip password verification for OAuth users")
        void shouldSkipPasswordVerification_forOAuthUsers() {
            UUID userId = UUID.randomUUID();
            String newEmail = "new@example.com";
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            User user = createOAuthUser(userId, "old@example.com", "OAuth User");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(tokenRepository.cancelPendingChangeEmailTokensByUserId(
                            eq(userId), any(Instant.class)))
                    .thenReturn(0);
            when(mailOutboxService.enqueueChangeEmailVerification(
                            eq(userId), eq("OAuth User"), eq(newEmail), any(String.class)))
                    .thenReturn(EmptyResponse.ok(false));

            EmptyResponse response =
                    service.requestEmailChange(userId, newEmail, null, ip, userAgent);

            verify(passwordEncoder, never()).matches(any(), any());
            assertThat(response.success()).isTrue();
        }

        @Test
        @DisplayName("should throw NotFoundException when user not found")
        void shouldThrowNotFoundException_whenUserNotFound() {
            UUID userId = UUID.randomUUID();
            String newEmail = "new@example.com";
            String password = "currentPassword";
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    service.requestEmailChange(
                                            userId, newEmail, password, ip, userAgent))
                    .isInstanceOf(NotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_004)
                    .hasMessageContaining("User not found");

            verify(userValidator, never()).assertEmailUnique(any());
            verify(tokenRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("confirmEmailChange")
    class ConfirmEmailChange {

        @Test
        @DisplayName("should change email when valid token")
        void shouldChangeEmail_whenValidToken() {
            UUID userId = UUID.randomUUID();
            String newEmail = "new@example.com";
            String rawToken = TokenUtils.generateRawToken();
            String tokenHash = TokenUtils.hashToken(rawToken);
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            User user = createUserWithPassword(userId, "old@example.com", "Test User");
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(Instant.now().minus(Duration.ofDays(30)));

            EmailVerificationToken token = createValidChangeEmailToken(userId, newEmail, tokenHash);

            when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(tokenRepository.cancelPendingChangeEmailTokensByUserId(
                            eq(userId), any(Instant.class)))
                    .thenReturn(0);

            service.confirmEmailChange(rawToken, ip, userAgent);

            assertThat(user.getEmail()).isEqualTo(newEmail);
            assertThat(user.getEmailVerified()).isFalse();
            assertThat(user.getEmailVerifiedAt()).isNull();
            assertThat(token.isCompleted()).isTrue();

            verify(userRepository).save(user);
            verify(tokenRepository, atLeastOnce()).save(token);
            verify(tokenRepository)
                    .cancelPendingChangeEmailTokensByUserId(eq(userId), any(Instant.class));
            verify(auditLogService)
                    .logSuccess(
                            userId,
                            AuditAction.EMAIL_CHANGE_COMPLETED,
                            ResourceType.USER,
                            userId.toString());
        }

        @Test
        @DisplayName("should reset email verification status after change")
        void shouldResetEmailVerificationStatus_afterChange() {
            UUID userId = UUID.randomUUID();
            String newEmail = "new@example.com";
            String rawToken = TokenUtils.generateRawToken();
            String tokenHash = TokenUtils.hashToken(rawToken);
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            User user = createUserWithPassword(userId, "old@example.com", "Test User");
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(Instant.now().minus(Duration.ofDays(30)));

            EmailVerificationToken token = createValidChangeEmailToken(userId, newEmail, tokenHash);

            when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(tokenRepository.cancelPendingChangeEmailTokensByUserId(
                            eq(userId), any(Instant.class)))
                    .thenReturn(0);

            service.confirmEmailChange(rawToken, ip, userAgent);

            assertThat(user.getEmailVerified()).isFalse();
            assertThat(user.getEmailVerifiedAt()).isNull();
        }

        @Test
        @DisplayName("should throw UnauthorizedException when token invalid")
        void shouldThrowUnauthorizedException_whenTokenInvalid() {
            String rawToken = "non-existent-token";
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            when(tokenRepository.findByTokenHash(any(String.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.confirmEmailChange(rawToken, ip, userAgent))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_011)
                    .hasMessageContaining("Invalid email change token");

            verify(userRepository, never()).findById(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw UnauthorizedException when max attempts exceeded")
        void shouldThrowUnauthorizedException_whenMaxAttemptsExceeded() {
            UUID userId = UUID.randomUUID();
            String newEmail = "new@example.com";
            String rawToken = TokenUtils.generateRawToken();
            String tokenHash = TokenUtils.hashToken(rawToken);
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            EmailVerificationToken token = createTokenWithMaxAttempts(userId, newEmail, tokenHash);

            when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

            assertThatThrownBy(() -> service.confirmEmailChange(rawToken, ip, userAgent))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_011)
                    .hasMessageContaining("Too many verification attempts");

            assertThat(token.getAttempts()).isEqualTo(6);
            verify(tokenRepository).save(token);
            verify(userRepository, never()).findById(any());
        }

        @Test
        @DisplayName("should throw UnauthorizedException when token cancelled")
        void shouldThrowUnauthorizedException_whenTokenCancelled() {
            UUID userId = UUID.randomUUID();
            String newEmail = "new@example.com";
            String rawToken = TokenUtils.generateRawToken();
            String tokenHash = TokenUtils.hashToken(rawToken);
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            EmailVerificationToken token =
                    createCancelledChangeEmailToken(userId, newEmail, tokenHash);

            when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

            assertThatThrownBy(() -> service.confirmEmailChange(rawToken, ip, userAgent))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_011)
                    .hasMessageContaining("Token is no longer valid");

            verify(tokenRepository).save(token);
            verify(userRepository, never()).findById(any());
        }

        @Test
        @DisplayName("should throw UnauthorizedException when token already completed")
        void shouldThrowConflictException_whenTokenAlreadyCompleted() {
            UUID userId = UUID.randomUUID();
            String newEmail = "new@example.com";
            String rawToken = TokenUtils.generateRawToken();
            String tokenHash = TokenUtils.hashToken(rawToken);
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            EmailVerificationToken token =
                    createCompletedChangeEmailToken(userId, newEmail, tokenHash);

            when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

            assertThatThrownBy(() -> service.confirmEmailChange(rawToken, ip, userAgent))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_011)
                    .hasMessageContaining("Token is no longer valid");

            verify(tokenRepository).save(token);
            verify(userRepository, never()).findById(any());
        }

        @Test
        @DisplayName("should throw UnauthorizedException when token expired")
        void shouldThrowGoneException_whenTokenExpired() {
            UUID userId = UUID.randomUUID();
            String newEmail = "new@example.com";
            String rawToken = TokenUtils.generateRawToken();
            String tokenHash = TokenUtils.hashToken(rawToken);
            String ip = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            EmailVerificationToken token =
                    createExpiredChangeEmailToken(userId, newEmail, tokenHash);

            when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

            assertThatThrownBy(() -> service.confirmEmailChange(rawToken, ip, userAgent))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_010)
                    .hasMessageContaining("Email change token has expired");

            verify(tokenRepository).save(token);
            verify(userRepository, never()).findById(any());
        }
    }

    @Nested
    @DisplayName("cancelEmailChange")
    class CancelEmailChange {

        @Test
        @DisplayName("should cancel pending tokens when tokens exist")
        void shouldCancelPendingTokens_whenTokensExist() {
            UUID userId = UUID.randomUUID();

            when(tokenRepository.cancelPendingChangeEmailTokensByUserId(
                            eq(userId), any(Instant.class)))
                    .thenReturn(2);

            service.cancelEmailChange(userId);

            verify(tokenRepository)
                    .cancelPendingChangeEmailTokensByUserId(eq(userId), any(Instant.class));
            verify(auditLogService)
                    .logSuccess(
                            userId,
                            AuditAction.EMAIL_CHANGE_CANCELLED,
                            ResourceType.USER,
                            userId.toString());
        }

        @Test
        @DisplayName("should do nothing when no pending tokens")
        void shouldDoNothing_whenNoPendingTokens() {
            UUID userId = UUID.randomUUID();

            when(tokenRepository.cancelPendingChangeEmailTokensByUserId(
                            eq(userId), any(Instant.class)))
                    .thenReturn(0);

            service.cancelEmailChange(userId);

            verify(tokenRepository)
                    .cancelPendingChangeEmailTokensByUserId(eq(userId), any(Instant.class));
            verify(auditLogService, never()).logSuccess(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("getEmailStatus")
    class GetEmailStatus {

        @Test
        @DisplayName("should return status when no pending change")
        void shouldReturnStatus_whenNoPendingChange() {
            UUID userId = UUID.randomUUID();

            User user = createUserWithPassword(userId, "user@example.com", "Test User");
            user.setEmailVerified(true);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(tokenRepository.findPendingChangeEmailTokenByUserId(
                            eq(userId), any(Instant.class)))
                    .thenReturn(Optional.empty());

            EmailStatusResponse response = service.getEmailStatus(userId);

            assertThat(response.email()).isEqualTo("user@example.com");
            assertThat(response.emailVerified()).isTrue();
            assertThat(response.emailChangePending()).isFalse();
            assertThat(response.pendingNewEmail()).isNull();
        }

        @Test
        @DisplayName("should return status with pending change")
        void shouldReturnStatus_withPendingChange() {
            UUID userId = UUID.randomUUID();

            User user = createUserWithPassword(userId, "old@example.com", "Test User");
            user.setEmailVerified(true);

            EmailVerificationToken pendingToken = new EmailVerificationToken();
            pendingToken.setEmail("new@example.com");
            pendingToken.setPurpose(EmailVerificationToken.PURPOSE_CHANGE_EMAIL);
            pendingToken.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(tokenRepository.findPendingChangeEmailTokenByUserId(
                            eq(userId), any(Instant.class)))
                    .thenReturn(Optional.of(pendingToken));

            EmailStatusResponse response = service.getEmailStatus(userId);

            assertThat(response.email()).isEqualTo("old@example.com");
            assertThat(response.emailVerified()).isTrue();
            assertThat(response.emailChangePending()).isTrue();
            assertThat(response.pendingNewEmail()).isEqualTo("new@example.com");
        }

        @Test
        @DisplayName("should throw NotFoundException when user not found")
        void shouldThrowNotFoundException_whenUserNotFound() {
            UUID userId = UUID.randomUUID();

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getEmailStatus(userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_004)
                    .hasMessageContaining("User not found");

            verify(tokenRepository, never()).findPendingChangeEmailTokenByUserId(any(), any());
        }
    }
}
