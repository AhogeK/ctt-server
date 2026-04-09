package com.ahogek.cttserver.auth.lockout;

import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.config.properties.SecurityProperties.PasswordProperties;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.common.exception.NotFoundException;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private LockoutStrategyPort lockoutStrategy;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordProperties passwordProps;

    @Mock
    private SecurityProperties securityProperties;

    private static final int TEST_MAX_ATTEMPTS = 5;
    private static final Duration TEST_LOCK_DURATION = Duration.ofMinutes(30);
    private static final int TEST_WINDOW_SECONDS = 900;

    private LoginAttemptService loginAttemptService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        given(securityProperties.password()).willReturn(passwordProps);
        loginAttemptService = new LoginAttemptService(lockoutStrategy, userRepository, securityProperties);
        activeUser = createActiveUser();
    }

    private User createActiveUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
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
        for (int i = 0; i < TEST_MAX_ATTEMPTS; i++) {
            user.recordFailedLogin(TEST_MAX_ATTEMPTS, TEST_LOCK_DURATION, TEST_WINDOW_SECONDS);
        }
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        return user;
    }

    @Nested
    @DisplayName("checkLockStatus")
    class CheckLockStatus {

        @Test
        @DisplayName("should return normally when user is active")
        void shouldReturnNormally_whenUserIsActive() {
            // Given
            given(userRepository.findByEmailIgnoreCase("test@example.com"))
                    .willReturn(Optional.of(activeUser));
            given(lockoutStrategy.shouldAutoUnlock(activeUser)).willReturn(false);
            given(passwordProps.maxFailedAttempts()).willReturn(TEST_MAX_ATTEMPTS);

            // When
            loginAttemptService.checkLockStatus("test@example.com");

            // Then - no exception thrown
            assertThat(activeUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("should auto-unlock and return when lock expired")
        void shouldAutoUnlockAndReturn_whenLockExpired() {
            // Given
            User lockedUser = createLockedUser();

            given(userRepository.findByEmailIgnoreCase("locked@example.com"))
                    .willReturn(Optional.of(lockedUser));
            given(lockoutStrategy.shouldAutoUnlock(lockedUser)).willReturn(true);

            // When
            loginAttemptService.checkLockStatus("locked@example.com");

            // Then
            verify(lockoutStrategy).recordSuccess(lockedUser);
            verify(userRepository).save(lockedUser);
        }

        @Test
        @DisplayName("should throw ForbiddenException when account is locked")
        void shouldThrowForbiddenException_whenAccountIsLocked() {
            // Given
            User lockedUser = createLockedUser();

            given(userRepository.findByEmailIgnoreCase("locked@example.com"))
                    .willReturn(Optional.of(lockedUser));
            given(lockoutStrategy.shouldAutoUnlock(lockedUser)).willReturn(false);

            // When / Then
            assertThatThrownBy(
                            () ->
                                    loginAttemptService.checkLockStatus(
                                            "locked@example.com"))
                    .isInstanceOf(ForbiddenException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_004);
        }

        @Test
        @DisplayName("should throw ForbiddenException when attempts exceeded")
        void shouldThrowForbiddenException_whenAttemptsExceeded() {
            // Given
            User user = createActiveUser();
            user.setFailedLoginAttempts(5);

            given(userRepository.findByEmailIgnoreCase("test@example.com"))
                    .willReturn(Optional.of(user));
            given(lockoutStrategy.shouldAutoUnlock(user)).willReturn(false);
            given(passwordProps.maxFailedAttempts()).willReturn(TEST_MAX_ATTEMPTS);

            // When / Then
            assertThatThrownBy(
                            () -> loginAttemptService.checkLockStatus("test@example.com"))
                    .isInstanceOf(ForbiddenException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_004);
        }

        @Test
        @DisplayName("should throw NotFoundException when user not found")
        void shouldThrowNotFoundException_whenUserNotFound() {
            // Given
            given(userRepository.findByEmailIgnoreCase("nonexistent@example.com"))
                    .willReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(
                            () ->
                                    loginAttemptService.checkLockStatus(
                                            "nonexistent@example.com"))
                    .isInstanceOf(NotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_001);
        }

        @Test
        @DisplayName("should find user with case-insensitive email")
        void shouldFindUser_whenEmailCaseDiffers() {
            // Given
            given(userRepository.findByEmailIgnoreCase("TEST@EXAMPLE.COM"))
                    .willReturn(Optional.of(activeUser));
            given(lockoutStrategy.shouldAutoUnlock(activeUser)).willReturn(false);
            given(passwordProps.maxFailedAttempts()).willReturn(TEST_MAX_ATTEMPTS);

            // When
            loginAttemptService.checkLockStatus("TEST@EXAMPLE.COM");

            // Then
            verify(userRepository).findByEmailIgnoreCase("TEST@EXAMPLE.COM");
        }
    }

    @Nested
    @DisplayName("recordFailure")
    class RecordFailure {

        @Test
        @DisplayName("should increment failure count")
        void shouldIncrementFailureCount() {
            // Given
            given(passwordProps.maxFailedAttempts()).willReturn(TEST_MAX_ATTEMPTS);
            given(passwordProps.lockDuration()).willReturn(TEST_LOCK_DURATION);
            given(passwordProps.failureWindowSeconds()).willReturn(TEST_WINDOW_SECONDS);

            // When
            loginAttemptService.recordFailure(activeUser, "192.168.1.1");

            // Then
            verify(lockoutStrategy)
                    .recordFailure(
                            activeUser,
                            TEST_MAX_ATTEMPTS,
                            TEST_LOCK_DURATION,
                            TEST_WINDOW_SECONDS);
        }

        @Test
        @DisplayName("should save user after recording failure")
        void shouldSaveUserAfterRecordingFailure() {
            // Given
            given(passwordProps.maxFailedAttempts()).willReturn(TEST_MAX_ATTEMPTS);
            given(passwordProps.lockDuration()).willReturn(TEST_LOCK_DURATION);
            given(passwordProps.failureWindowSeconds()).willReturn(TEST_WINDOW_SECONDS);

            // When
            loginAttemptService.recordFailure(activeUser, "192.168.1.1");

            // Then
            verify(userRepository).save(activeUser);
        }

        @Test
        @DisplayName("should increment user failure count after recording")
        void shouldIncrementUserFailureCount_afterRecording() {
            // Given
            given(passwordProps.maxFailedAttempts()).willReturn(TEST_MAX_ATTEMPTS);
            given(passwordProps.lockDuration()).willReturn(TEST_LOCK_DURATION);
            given(passwordProps.failureWindowSeconds()).willReturn(TEST_WINDOW_SECONDS);
            willAnswer(invocation -> {
                User u = invocation.getArgument(0);
                int maxAttempts = invocation.getArgument(1);
                Duration lockDuration = invocation.getArgument(2);
                int windowSeconds = invocation.getArgument(3);
                u.recordFailedLogin(maxAttempts, lockDuration, windowSeconds);
                return null;
            }).given(lockoutStrategy).recordFailure(any(User.class), anyInt(), any(Duration.class), anyInt());
            assertThat(activeUser.getFailedLoginAttempts()).isZero();

            // When
            loginAttemptService.recordFailure(activeUser, "192.168.1.1");

            // Then
            assertThat(activeUser.getFailedLoginAttempts()).isEqualTo(1);
            assertThat(activeUser.getLastFailureTime()).isNotNull();
        }
    }

    @Nested
    @DisplayName("recordSuccess")
    class RecordSuccess {

        @Test
        @DisplayName("should clear failure state")
        void shouldClearFailureState() {
            // Given
            activeUser.recordFailedLogin(TEST_MAX_ATTEMPTS, TEST_LOCK_DURATION, TEST_WINDOW_SECONDS);
            activeUser.recordFailedLogin(TEST_MAX_ATTEMPTS, TEST_LOCK_DURATION, TEST_WINDOW_SECONDS);
            activeUser.recordFailedLogin(TEST_MAX_ATTEMPTS, TEST_LOCK_DURATION, TEST_WINDOW_SECONDS);
            assertThat(activeUser.getFailedLoginAttempts()).isEqualTo(3);
            willAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.recordSuccessfulLogin();
                return null;
            }).given(lockoutStrategy).recordSuccess(activeUser);

            // When
            loginAttemptService.recordSuccess(activeUser);

            // Then
            verify(lockoutStrategy).recordSuccess(activeUser);
            assertThat(activeUser.getFailedLoginAttempts()).isZero();
            assertThat(activeUser.getLastFailureTime()).isNull();
        }

        @Test
        @DisplayName("should save user after success")
        void shouldSaveUserAfterSuccess() {
            // When
            loginAttemptService.recordSuccess(activeUser);

            // Then
            verify(userRepository).save(activeUser);
        }
    }

    @Nested
    @DisplayName("isLocked")
    class IsLocked {

        @Test
        @DisplayName("should return true when locked and not expired")
        void shouldReturnTrue_whenLockedAndNotExpired() {
            // Given
            User lockedUser = createActiveUser();
            for (int i = 0; i < TEST_MAX_ATTEMPTS; i++) {
                lockedUser.recordFailedLogin(TEST_MAX_ATTEMPTS, TEST_LOCK_DURATION, TEST_WINDOW_SECONDS);
            }
            assertThat(lockedUser.getStatus()).isEqualTo(UserStatus.LOCKED);
            given(lockoutStrategy.shouldAutoUnlock(lockedUser)).willReturn(false);

            // When
            boolean result = loginAttemptService.isLocked(lockedUser);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when not locked")
        void shouldReturnFalse_whenNotLocked() {
            // When
            boolean result = loginAttemptService.isLocked(activeUser);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when lock expired")
        void shouldReturnFalse_whenLockExpired() {
            // Given
            User lockedUser = createActiveUser();
            for (int i = 0; i < TEST_MAX_ATTEMPTS; i++) {
                lockedUser.recordFailedLogin(TEST_MAX_ATTEMPTS, TEST_LOCK_DURATION, TEST_WINDOW_SECONDS);
            }
            assertThat(lockedUser.getStatus()).isEqualTo(UserStatus.LOCKED);
            given(lockoutStrategy.shouldAutoUnlock(lockedUser)).willReturn(true);

            // When
            boolean result = loginAttemptService.isLocked(lockedUser);

            // Then
            assertThat(result).isFalse();
        }
    }
}
