package com.ahogek.cttserver.auth.lockout;

import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;

import java.time.Duration;
import java.time.Instant;

/**
 * Database-backed lockout strategy using User entity fields.
 *
 * <p>Stores lockout tracking data in PostgreSQL via User entity fields:
 *
 * <ul>
 *   <li>{@code failedLoginAttempts}: counter for consecutive failures
 *   <li>{@code lastFailureTime}: timestamp of most recent failure (for sliding window)
 *   <li>{@code lockedUntil}: lockout expiration timestamp
 * </ul>
 *
 * <p><strong>Deployment:</strong> Suitable for single-instance deployments. For distributed systems, use
 * {@link RedisLockoutStrategy}.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-08
 */
public class DbLockoutStrategy implements LockoutStrategyPort {

    @Override
    public void recordFailure(User user, int maxAttempts, Duration lockDuration, int windowSeconds) {
        user.recordFailedLogin(maxAttempts, lockDuration, windowSeconds);
    }

    @Override
    public void recordSuccess(User user) {
        user.recordSuccessfulLogin();
    }

    @Override
    public boolean shouldAutoUnlock(User user) {
        if (user.getStatus() != UserStatus.LOCKED) {
            return false;
        }

        if (user.getLockedUntil() == null) {
            return false;
        }

        return Instant.now().isAfter(user.getLockedUntil());
    }
}