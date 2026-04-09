package com.ahogek.cttserver.auth.lockout;

import com.ahogek.cttserver.auth.entity.LoginAttempt;
import com.ahogek.cttserver.auth.repository.LoginAttemptRepository;
import com.ahogek.cttserver.user.enums.UserStatus;

import java.time.Duration;
import java.time.Instant;

/**
 * Database-based lockout strategy.
 *
 * <p>Registered via {@code LockoutConfig} with conditional property support. Do NOT add
 * {@code @Repository} or {@code @Component} - it would conflict with the {@code @Bean} definition
 * in {@code LockoutConfig}.
 */
public class DbLockoutStrategy implements LockoutStrategyPort {

    private final LoginAttemptRepository loginAttemptRepository;

    public DbLockoutStrategy(LoginAttemptRepository loginAttemptRepository) {
        this.loginAttemptRepository = loginAttemptRepository;
    }

    @Override
    public void recordFailure(
            String emailHash,
            String ipHash,
            int maxAttempts,
            Duration lockDuration,
            int windowSeconds) {
        loginAttemptRepository.save(new LoginAttempt(emailHash, ipHash));
    }

    @Override
    public void recordSuccess(String emailHash) {
        loginAttemptRepository.deleteByEmailHash(emailHash);
    }

    @Override
    public boolean shouldAutoUnlock(
            String emailHash, UserStatus status, Duration lockDuration, int windowSeconds) {
        if (status != UserStatus.LOCKED) {
            return false;
        }
        Instant windowStart = Instant.now().minusSeconds(windowSeconds);
        return loginAttemptRepository
                .findEarliestAttemptInWindow(emailHash, windowStart)
                .map(earliest -> Instant.now().isAfter(earliest.plus(lockDuration)))
                .orElse(true);
    }

    @Override
    public Instant getRetryAfter(String emailHash, Duration lockDuration, int windowSeconds) {
        Instant windowStart = Instant.now().minusSeconds(windowSeconds);
        return loginAttemptRepository
                .findEarliestAttemptInWindow(emailHash, windowStart)
                .map(earliest -> earliest.plus(lockDuration))
                .orElse(null);
    }
}
