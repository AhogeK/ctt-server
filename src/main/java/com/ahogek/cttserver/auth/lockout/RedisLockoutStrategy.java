package com.ahogek.cttserver.auth.lockout;

import com.ahogek.cttserver.user.entity.User;

import java.time.Duration;

/**
 * Redis-based account lockout tracking strategy (future enhancement).
 *
 * <p>This is a placeholder implementation for future Redis-based distributed lockout tracking.
 * Currently not implemented. Use {@code storage: DB} in configuration for database-backed
 * lockout tracking.
 *
 * <p><strong>Future Implementation Notes:</strong>
 *
 * <ul>
 *   <li>Will use Redis for distributed lockout state management
 *   <li>Better suited for multi-instance deployments
 *   <li>Will provide faster lockout checks compared to database queries
 *   <li>Will require Redis connection configuration
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-08
 */
public class RedisLockoutStrategy implements LockoutStrategyPort {

    @Override
    public void recordFailure(User user, int maxAttempts, Duration lockDuration, int windowSeconds) {
        throw new UnsupportedOperationException(
            "RedisLockoutStrategy not yet implemented. Use storage: DB in configuration.");
    }

    @Override
    public void recordSuccess(User user) {
        throw new UnsupportedOperationException(
            "RedisLockoutStrategy not yet implemented. Use storage: DB in configuration.");
    }

    @Override
    public boolean shouldAutoUnlock(User user) {
        throw new UnsupportedOperationException(
            "RedisLockoutStrategy not yet implemented. Use storage: DB in configuration.");
    }
}