package com.ahogek.cttserver.auth.lockout;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.repository.LoginAttemptRepository;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.config.properties.SecurityProperties.PasswordProperties;
import com.ahogek.cttserver.common.exception.AccountLockedException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.utils.TokenUtils;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock private LockoutStrategyPort lockoutStrategy;

    @Mock private LoginAttemptRepository loginAttemptRepository;

    @Mock private UserRepository userRepository;

    @Mock private PasswordProperties passwordProps;

    @Mock private SecurityProperties securityProperties;

    @Mock private AuditLogService auditLogService;

    private LoginAttemptService loginAttemptService;

    private User activeUser;

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_EMAIL_HASH = TokenUtils.hashToken(TEST_EMAIL);
    private static final String TEST_IP_HASH = TokenUtils.hashToken("192.168.1.1");
    private static final int TEST_MAX_ATTEMPTS = 5;
    private static final Duration TEST_LOCK_DURATION = Duration.ofMinutes(30);
    private static final int TEST_WINDOW_SECONDS = 900;

    @BeforeEach
    void setUp() {
        given(securityProperties.password()).willReturn(passwordProps);
        loginAttemptService =
                new LoginAttemptService(
                        lockoutStrategy,
                        loginAttemptRepository,
                        userRepository,
                        securityProperties,
                        auditLogService);
        activeUser = createActiveUser();
    }

    private User createActiveUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(TEST_EMAIL);
        user.setDisplayName("Test User");
        user.setPasswordHash("hashedPassword");
        user.verifyEmail();
        return user;
    }

    private User createLockedUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("locked@example.com");
        user.setDisplayName("Locked User");
        user.setPasswordHash("hashedPassword");
        user.verifyEmail();
        user.lockAccount();
        return user;
    }

    @Nested
    @DisplayName("checkLockStatus")
    class CheckLockStatus {

        @BeforeEach
        void setUpCheckLockStatus() {
            lenient().when(passwordProps.lockDuration()).thenReturn(TEST_LOCK_DURATION);
            lenient().when(passwordProps.failureWindowSeconds()).thenReturn(TEST_WINDOW_SECONDS);
            lenient().when(passwordProps.maxFailedAttempts()).thenReturn(TEST_MAX_ATTEMPTS);
        }

        @Test
        @DisplayName("should return normally when user is active")
        void shouldReturnNormally_whenUserIsActive() {
            given(
                            lockoutStrategy.shouldAutoUnlock(
                                    TEST_EMAIL_HASH,
                                    UserStatus.ACTIVE,
                                    TEST_LOCK_DURATION,
                                    TEST_WINDOW_SECONDS))
                    .willReturn(false);
            given(
                            loginAttemptRepository.countAttemptsInWindow(
                                    eq(TEST_EMAIL_HASH), any(Instant.class)))
                    .willReturn(0L);

            loginAttemptService.checkLockStatus(activeUser);
        }

        @Test
        @DisplayName("should auto-unlock and return when lock expired")
        void shouldAutoUnlockAndReturn_whenLockExpired() {
            User lockedUser = createLockedUser();

            given(
                            lockoutStrategy.shouldAutoUnlock(
                                    anyString(),
                                    eq(UserStatus.LOCKED),
                                    eq(TEST_LOCK_DURATION),
                                    eq(TEST_WINDOW_SECONDS)))
                    .willReturn(true);

            loginAttemptService.checkLockStatus(lockedUser);

            verify(lockoutStrategy).recordSuccess(anyString());
            verify(userRepository).save(lockedUser);
            verify(auditLogService)
                    .logSuccess(
                            lockedUser.getId(),
                            AuditAction.ACCOUNT_UNLOCKED,
                            ResourceType.USER,
                            lockedUser.getId().toString());
        }

        @Test
        @DisplayName("should throw AccountLockedException when account is locked")
        void shouldThrowAccountLockedException_whenAccountIsLocked() {
            User lockedUser = createLockedUser();
            Instant expectedRetryAfter = Instant.now().plus(TEST_LOCK_DURATION);

            given(
                            lockoutStrategy.shouldAutoUnlock(
                                    anyString(),
                                    eq(UserStatus.LOCKED),
                                    eq(TEST_LOCK_DURATION),
                                    eq(TEST_WINDOW_SECONDS)))
                    .willReturn(false);
            given(
                            lockoutStrategy.getRetryAfter(
                                    anyString(), eq(TEST_LOCK_DURATION), eq(TEST_WINDOW_SECONDS)))
                    .willReturn(expectedRetryAfter);

            assertThatThrownBy(() -> loginAttemptService.checkLockStatus(lockedUser))
                    .isInstanceOf(AccountLockedException.class)
                    .satisfies(
                            thrown -> {
                                AccountLockedException ex = (AccountLockedException) thrown;
                                assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_004);
                                assertThat(ex.retryAfter())
                                        .isCloseTo(
                                                expectedRetryAfter, within(1, ChronoUnit.SECONDS));
                            });
        }

        @Test
        @DisplayName("should throw AccountLockedException when attempts exceeded in DB")
        void shouldThrowAccountLockedException_whenAttemptsExceeded() {
            given(
                            lockoutStrategy.shouldAutoUnlock(
                                    TEST_EMAIL_HASH,
                                    UserStatus.ACTIVE,
                                    TEST_LOCK_DURATION,
                                    TEST_WINDOW_SECONDS))
                    .willReturn(false);
            given(
                            loginAttemptRepository.countAttemptsInWindow(
                                    eq(TEST_EMAIL_HASH), any(Instant.class)))
                    .willReturn((long) TEST_MAX_ATTEMPTS);

            assertThatThrownBy(() -> loginAttemptService.checkLockStatus(activeUser))
                    .isInstanceOf(AccountLockedException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_004);
        }

        @Test
        @DisplayName("should find user with case-insensitive email")
        void shouldFindUser_whenEmailCaseDiffers() {
            User upperCaseUser = createActiveUser();
            upperCaseUser.setEmail("TEST@EXAMPLE.COM");

            given(
                            lockoutStrategy.shouldAutoUnlock(
                                    anyString(),
                                    eq(UserStatus.ACTIVE),
                                    eq(TEST_LOCK_DURATION),
                                    eq(TEST_WINDOW_SECONDS)))
                    .willReturn(false);
            given(loginAttemptRepository.countAttemptsInWindow(anyString(), any(Instant.class)))
                    .willReturn(0L);

            loginAttemptService.checkLockStatus(upperCaseUser);

            assertThat(upperCaseUser.getEmail()).isEqualTo("TEST@EXAMPLE.COM");
        }

        @Test
        @DisplayName(
                "should use fallback retryAfter when getRetryAfter returns null for locked user")
        void shouldUseFallbackRetryAfter_whenGetRetryAfterReturnsNull() {
            User lockedUser = createLockedUser();

            given(
                            lockoutStrategy.shouldAutoUnlock(
                                    anyString(),
                                    eq(UserStatus.LOCKED),
                                    eq(TEST_LOCK_DURATION),
                                    eq(TEST_WINDOW_SECONDS)))
                    .willReturn(false);
            given(
                            lockoutStrategy.getRetryAfter(
                                    anyString(), eq(TEST_LOCK_DURATION), eq(TEST_WINDOW_SECONDS)))
                    .willReturn(null);

            assertThatThrownBy(() -> loginAttemptService.checkLockStatus(lockedUser))
                    .isInstanceOf(AccountLockedException.class)
                    .satisfies(
                            thrown -> {
                                AccountLockedException ex = (AccountLockedException) thrown;
                                assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_004);
                                assertThat(ex.retryAfter()).isAfterOrEqualTo(Instant.now());
                            });
        }
    }

    @Nested
    @DisplayName("recordFailure")
    class RecordFailure {

        @Test
        @DisplayName("should delegate to lockout strategy")
        void shouldDelegateToLockoutStrategy() {
            given(passwordProps.maxFailedAttempts()).willReturn(TEST_MAX_ATTEMPTS);
            given(passwordProps.lockDuration()).willReturn(TEST_LOCK_DURATION);
            given(passwordProps.failureWindowSeconds()).willReturn(TEST_WINDOW_SECONDS);
            given(loginAttemptRepository.countAttemptsInWindow(anyString(), any(Instant.class)))
                    .willReturn(1L);
            given(userRepository.findByEmailIgnoreCase(TEST_EMAIL))
                    .willReturn(Optional.of(activeUser));

            loginAttemptService.recordFailure(TEST_EMAIL, "192.168.1.1");

            verify(lockoutStrategy)
                    .recordFailure(
                            TEST_EMAIL_HASH,
                            TEST_IP_HASH,
                            TEST_MAX_ATTEMPTS,
                            TEST_LOCK_DURATION,
                            TEST_WINDOW_SECONDS);
        }

        @Test
        @DisplayName("should save user after recording failure")
        void shouldSaveUserAfterRecordingFailure() {
            given(passwordProps.maxFailedAttempts()).willReturn(TEST_MAX_ATTEMPTS);
            given(passwordProps.lockDuration()).willReturn(TEST_LOCK_DURATION);
            given(passwordProps.failureWindowSeconds()).willReturn(TEST_WINDOW_SECONDS);
            given(loginAttemptRepository.countAttemptsInWindow(anyString(), any(Instant.class)))
                    .willReturn(1L);
            given(userRepository.findByEmailIgnoreCase(TEST_EMAIL))
                    .willReturn(Optional.of(activeUser));

            loginAttemptService.recordFailure(TEST_EMAIL, "192.168.1.1");

            verify(userRepository).save(activeUser);
        }

        @Test
        @DisplayName("should not throw when user not found")
        void shouldNotThrow_whenUserNotFound() {
            given(passwordProps.maxFailedAttempts()).willReturn(TEST_MAX_ATTEMPTS);
            given(passwordProps.lockDuration()).willReturn(TEST_LOCK_DURATION);
            given(passwordProps.failureWindowSeconds()).willReturn(TEST_WINDOW_SECONDS);
            given(loginAttemptRepository.countAttemptsInWindow(anyString(), any(Instant.class)))
                    .willReturn(1L);
            given(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).willReturn(Optional.empty());

            loginAttemptService.recordFailure(TEST_EMAIL, "192.168.1.1");

            // Strategy is still called (it saves the attempt record)
            verify(lockoutStrategy)
                    .recordFailure(
                            TEST_EMAIL_HASH,
                            TEST_IP_HASH,
                            TEST_MAX_ATTEMPTS,
                            TEST_LOCK_DURATION,
                            TEST_WINDOW_SECONDS);
            // But user is not saved
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should log audit when account gets locked")
        void shouldLogAudit_whenAccountGetsLocked() {
            given(passwordProps.maxFailedAttempts()).willReturn(TEST_MAX_ATTEMPTS);
            given(passwordProps.lockDuration()).willReturn(TEST_LOCK_DURATION);
            given(passwordProps.failureWindowSeconds()).willReturn(TEST_WINDOW_SECONDS);
            given(loginAttemptRepository.countAttemptsInWindow(anyString(), any(Instant.class)))
                    .willReturn((long) TEST_MAX_ATTEMPTS);
            given(userRepository.findByEmailIgnoreCase(TEST_EMAIL))
                    .willReturn(Optional.of(activeUser));

            loginAttemptService.recordFailure(TEST_EMAIL, "192.168.1.1");

            assertThat(activeUser.getStatus()).isEqualTo(UserStatus.LOCKED);
            verify(auditLogService)
                    .logFailure(
                            activeUser.getId(),
                            AuditAction.ACCOUNT_LOCKED,
                            ResourceType.USER,
                            activeUser.getId().toString(),
                            "Brute-force threshold reached: %d failed attempts within %ds"
                                    .formatted(TEST_MAX_ATTEMPTS, TEST_WINDOW_SECONDS));
        }
    }

    @Nested
    @DisplayName("recordSuccess")
    class RecordSuccess {

        @Test
        @DisplayName("should delegate to lockout strategy")
        void shouldDelegateToLockoutStrategy() {
            loginAttemptService.recordSuccess(TEST_EMAIL);

            verify(lockoutStrategy).recordSuccess(TEST_EMAIL_HASH);
        }

        @Test
        @DisplayName("should not query user repository")
        void shouldNotQueryUserRepository() {
            loginAttemptService.recordSuccess(TEST_EMAIL);

            verify(userRepository, never()).findByEmailIgnoreCase(anyString());
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("isLocked")
    class IsLocked {

        @Test
        @DisplayName("should return true when locked and not expired")
        void shouldReturnTrue_whenLockedAndNotExpired() {
            User lockedUser = createLockedUser();
            given(userRepository.findByEmailIgnoreCase("locked@example.com"))
                    .willReturn(Optional.of(lockedUser));
            given(passwordProps.lockDuration()).willReturn(TEST_LOCK_DURATION);
            given(passwordProps.failureWindowSeconds()).willReturn(TEST_WINDOW_SECONDS);
            given(
                            lockoutStrategy.shouldAutoUnlock(
                                    anyString(),
                                    eq(UserStatus.LOCKED),
                                    eq(TEST_LOCK_DURATION),
                                    eq(TEST_WINDOW_SECONDS)))
                    .willReturn(false);

            boolean result = loginAttemptService.isLocked("locked@example.com");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when not locked")
        void shouldReturnFalse_whenNotLocked() {
            given(userRepository.findByEmailIgnoreCase(TEST_EMAIL))
                    .willReturn(Optional.of(activeUser));

            boolean result = loginAttemptService.isLocked(TEST_EMAIL);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when lock expired")
        void shouldReturnFalse_whenLockExpired() {
            User lockedUser = createLockedUser();
            given(userRepository.findByEmailIgnoreCase("locked@example.com"))
                    .willReturn(Optional.of(lockedUser));
            given(passwordProps.lockDuration()).willReturn(TEST_LOCK_DURATION);
            given(passwordProps.failureWindowSeconds()).willReturn(TEST_WINDOW_SECONDS);
            given(
                            lockoutStrategy.shouldAutoUnlock(
                                    anyString(),
                                    eq(UserStatus.LOCKED),
                                    eq(TEST_LOCK_DURATION),
                                    eq(TEST_WINDOW_SECONDS)))
                    .willReturn(true);

            boolean result = loginAttemptService.isLocked("locked@example.com");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when user not found")
        void shouldReturnFalse_whenUserNotFound() {
            given(userRepository.findByEmailIgnoreCase("nonexistent@example.com"))
                    .willReturn(Optional.empty());

            boolean result = loginAttemptService.isLocked("nonexistent@example.com");

            assertThat(result).isFalse();
        }
    }
}
