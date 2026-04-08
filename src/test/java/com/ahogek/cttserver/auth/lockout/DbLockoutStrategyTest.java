package com.ahogek.cttserver.auth.lockout;

import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DbLockoutStrategyTest {

    private DbLockoutStrategy strategy;
    private User user;

    @BeforeEach
    void setUp() {
        strategy = new DbLockoutStrategy();
        user = createActiveUser();
    }

    private User createActiveUser() {
        User newUser = new User();
        newUser.setId(UUID.randomUUID());
        newUser.setEmail("test@example.com");
        newUser.setDisplayName("Test User");
        newUser.setPasswordHash("hashedPassword");
        newUser.verifyEmail();
        return newUser;
    }

    @Test
    void shouldIncrementFailureCount_whenRecordFailureCalled() {
        strategy.recordFailure(user, 5, Duration.ofMinutes(30), 900);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getLastFailureTime()).isNotNull();
    }

    @Test
    void shouldAccumulateFailures_whenMultipleFailuresWithinWindow() {
        strategy.recordFailure(user, 5, Duration.ofMinutes(30), 900);
        strategy.recordFailure(user, 5, Duration.ofMinutes(30), 900);
        strategy.recordFailure(user, 5, Duration.ofMinutes(30), 900);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void shouldLockAccount_whenFailureCountReachesThreshold() {
        int maxAttempts = 3;

        strategy.recordFailure(user, maxAttempts, Duration.ofMinutes(30), 900);
        strategy.recordFailure(user, maxAttempts, Duration.ofMinutes(30), 900);
        strategy.recordFailure(user, maxAttempts, Duration.ofMinutes(30), 900);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(user.getLockedUntil()).isNotNull();
    }

    @Test
    void shouldLockAccount_whenFailureCountExactlyAtThreshold() {
        int maxAttempts = 5;

        for (int i = 0; i < maxAttempts; i++) {
            strategy.recordFailure(user, maxAttempts, Duration.ofMinutes(30), 900);
        }

        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
    }

    @Test
    void shouldNotLockAccount_whenFailureCountBelowThreshold() {
        int maxAttempts = 5;

        strategy.recordFailure(user, maxAttempts, Duration.ofMinutes(30), 900);
        strategy.recordFailure(user, maxAttempts, Duration.ofMinutes(30), 900);
        strategy.recordFailure(user, maxAttempts, Duration.ofMinutes(30), 900);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void shouldNotChangeDeletedUser_whenRecordFailureCalled() {
        user.markAsDeleted();

        strategy.recordFailure(user, 5, Duration.ofMinutes(30), 900);

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
    }

    @Test
    void shouldResetFailureCount_whenRecordSuccessCalled() {
        strategy.recordFailure(user, 5, Duration.ofMinutes(30), 900);
        strategy.recordFailure(user, 5, Duration.ofMinutes(30), 900);

        strategy.recordSuccess(user);

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLastFailureTime()).isNull();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void shouldUnlockAccount_whenRecordSuccessCalledOnLockedUser() {
        strategy.recordFailure(user, 3, Duration.ofMinutes(30), 900);
        strategy.recordFailure(user, 3, Duration.ofMinutes(30), 900);
        strategy.recordFailure(user, 3, Duration.ofMinutes(30), 900);
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);

        strategy.recordSuccess(user);

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void shouldNotChangeActiveUser_whenRecordSuccessCalled() {
        strategy.recordSuccess(user);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    void shouldReturnFalse_whenUserNotLocked() {
        assertThat(strategy.shouldAutoUnlock(user)).isFalse();
    }

    @Test
    void shouldReturnFalse_whenLockPeriodNotExpired() {
        strategy.recordFailure(user, 3, Duration.ofMinutes(30), 900);
        strategy.recordFailure(user, 3, Duration.ofMinutes(30), 900);
        strategy.recordFailure(user, 3, Duration.ofMinutes(30), 900);

        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(user.getLockedUntil()).isAfter(Instant.now());

        assertThat(strategy.shouldAutoUnlock(user)).isFalse();
    }

    @Test
    void shouldReturnTrue_whenLockPeriodExpired() {
        Duration shortLockDuration = Duration.ofMillis(1);

        strategy.recordFailure(user, 3, shortLockDuration, 900);
        strategy.recordFailure(user, 3, shortLockDuration, 900);
        strategy.recordFailure(user, 3, shortLockDuration, 900);

        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);

        assertThat(user.getLockedUntil())
                .isNotNull()
                .isBeforeOrEqualTo(Instant.now().plusMillis(5));
    }

    @Test
    void shouldReturnFalse_whenUserSuspended() {
        user.suspend();

        assertThat(strategy.shouldAutoUnlock(user)).isFalse();
    }

    @Test
    void shouldReturnFalse_whenUserDeleted() {
        user.markAsDeleted();

        assertThat(strategy.shouldAutoUnlock(user)).isFalse();
    }

    @Test
    void shouldHandleZeroWindowSeconds_whenRecordFailureCalled() {
        strategy.recordFailure(user, 5, Duration.ofMinutes(30), 0);
        strategy.recordFailure(user, 5, Duration.ofMinutes(30), 0);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(2);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void shouldContinueCounting_whenFailuresBeyondThreshold() {
        int maxAttempts = 3;

        strategy.recordFailure(user, maxAttempts, Duration.ofMinutes(30), 900);
        strategy.recordFailure(user, maxAttempts, Duration.ofMinutes(30), 900);
        strategy.recordFailure(user, maxAttempts, Duration.ofMinutes(30), 900);
        strategy.recordFailure(user, maxAttempts, Duration.ofMinutes(30), 900);
        strategy.recordFailure(user, maxAttempts, Duration.ofMinutes(30), 900);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
    }
}