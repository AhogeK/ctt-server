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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
            when(loginAttemptRepository.findEarliestAttemptInWindow(
                            eq(TEST_EMAIL_HASH), any(Instant.class)))
                    .thenReturn(Optional.empty());

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
            when(loginAttemptRepository.findEarliestAttemptInWindow(
                            eq(TEST_EMAIL_HASH), any(Instant.class)))
                    .thenReturn(Optional.of(firstAttempt));

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
            when(loginAttemptRepository.findEarliestAttemptInWindow(
                            eq(TEST_EMAIL_HASH), any(Instant.class)))
                    .thenReturn(Optional.of(firstAttempt));

            assertThat(
                            strategy.shouldAutoUnlock(
                                    TEST_EMAIL_HASH,
                                    UserStatus.LOCKED,
                                    Duration.ofMinutes(30),
                                    900))
                    .isTrue();
        }
    }
}
