package com.ahogek.cttserver.auth.lockout;

import com.ahogek.cttserver.auth.entity.LoginAttempt;
import com.ahogek.cttserver.auth.repository.LoginAttemptRepository;
import com.ahogek.cttserver.common.utils.TokenUtils;
import com.ahogek.cttserver.user.enums.UserStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DbLockoutStrategyTest {

    @Mock private LoginAttemptRepository loginAttemptRepository;

    private DbLockoutStrategy strategy;

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_EMAIL_HASH = TokenUtils.hashToken(TEST_EMAIL);
    private static final String TEST_IP_HASH = TokenUtils.hashToken("192.168.1.1");

    @BeforeEach
    void setUp() {
        strategy = new DbLockoutStrategy(loginAttemptRepository);
    }

    @Nested
    @DisplayName("recordFailure")
    class RecordFailure {

        @Test
        @DisplayName("should save login attempt with correct hashes")
        void shouldSaveLoginAttemptWithHashes() {
            strategy.recordFailure(TEST_EMAIL_HASH, TEST_IP_HASH, 5, Duration.ofMinutes(30), 900);

            ArgumentCaptor<LoginAttempt> captor = ArgumentCaptor.forClass(LoginAttempt.class);
            verify(loginAttemptRepository).save(captor.capture());
            assertThat(captor.getValue().getEmailHash()).isEqualTo(TEST_EMAIL_HASH);
            assertThat(captor.getValue().getIpHash()).isEqualTo(TEST_IP_HASH);
        }
    }

    @Nested
    @DisplayName("recordSuccess")
    class RecordSuccess {

        @Test
        @DisplayName("should delete login attempts by email hash")
        void shouldDeleteAttemptsByEmailHash() {
            strategy.recordSuccess(TEST_EMAIL_HASH);

            verify(loginAttemptRepository).deleteByEmailHash(TEST_EMAIL_HASH);
        }
    }

    @Nested
    @DisplayName("shouldAutoUnlock")
    class ShouldAutoUnlock {

        @Test
        @DisplayName("should return false when user not locked")
        void shouldReturnFalse_whenNotLocked() {
            assertThat(
                            strategy.shouldAutoUnlock(
                                    TEST_EMAIL_HASH,
                                    UserStatus.ACTIVE,
                                    Duration.ofMinutes(30),
                                    900))
                    .isFalse();
        }

        @Test
        @DisplayName("should return false when suspended")
        void shouldReturnFalse_whenSuspended() {
            assertThat(
                            strategy.shouldAutoUnlock(
                                    TEST_EMAIL_HASH,
                                    UserStatus.SUSPENDED,
                                    Duration.ofMinutes(30),
                                    900))
                    .isFalse();
        }

        @Test
        @DisplayName("should return true when no attempts in window (lockout expired)")
        void shouldReturnTrue_whenNoAttempts() {
            given(
                            loginAttemptRepository.findEarliestAttemptInWindow(
                                    eq(TEST_EMAIL_HASH), any(Instant.class)))
                    .willReturn(Optional.empty());

            assertThat(
                            strategy.shouldAutoUnlock(
                                    TEST_EMAIL_HASH,
                                    UserStatus.LOCKED,
                                    Duration.ofMinutes(30),
                                    900))
                    .isTrue();
        }

        @Test
        @DisplayName("should return false when lock not expired")
        void shouldReturnFalse_whenLockNotExpired() {
            Instant firstAttempt = Instant.now().minus(Duration.ofMinutes(10));
            given(
                            loginAttemptRepository.findEarliestAttemptInWindow(
                                    eq(TEST_EMAIL_HASH), any(Instant.class)))
                    .willReturn(Optional.of(firstAttempt));

            assertThat(
                            strategy.shouldAutoUnlock(
                                    TEST_EMAIL_HASH,
                                    UserStatus.LOCKED,
                                    Duration.ofMinutes(30),
                                    900))
                    .isFalse();
        }

        @Test
        @DisplayName("should return true when lock expired")
        void shouldReturnTrue_whenLockExpired() {
            Instant firstAttempt = Instant.now().minus(Duration.ofMinutes(31));
            given(
                            loginAttemptRepository.findEarliestAttemptInWindow(
                                    eq(TEST_EMAIL_HASH), any(Instant.class)))
                    .willReturn(Optional.of(firstAttempt));

            assertThat(
                            strategy.shouldAutoUnlock(
                                    TEST_EMAIL_HASH,
                                    UserStatus.LOCKED,
                                    Duration.ofMinutes(30),
                                    900))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("getRetryAfter")
    class GetRetryAfter {

        @Test
        @DisplayName("should return earliest attempt plus lock duration")
        void shouldReturnRetryAfter_whenAttemptsExist() {
            String emailHash = "test-hash";
            Instant earliestAttempt = Instant.now().minusSeconds(600);
            Duration lockDuration = Duration.ofMinutes(30);
            given(loginAttemptRepository.findEarliestAttemptInWindow(eq(emailHash), any()))
                    .willReturn(Optional.of(earliestAttempt));

            Instant retryAfter = strategy.getRetryAfter(emailHash, lockDuration, 900);

            assertThat(retryAfter)
                    .isCloseTo(
                            earliestAttempt.plus(lockDuration),
                            within(1, java.time.temporal.ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("should return null when no attempts in window")
        void shouldReturnNull_whenNoAttemptsInWindow() {
            String emailHash = "test-hash";
            Duration lockDuration = Duration.ofMinutes(30);
            given(loginAttemptRepository.findEarliestAttemptInWindow(eq(emailHash), any()))
                    .willReturn(Optional.empty());

            Instant retryAfter = strategy.getRetryAfter(emailHash, lockDuration, 900);

            assertThat(retryAfter).isNull();
        }
    }
}
