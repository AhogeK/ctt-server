package com.ahogek.cttserver.auth.lockout;

import com.ahogek.cttserver.user.enums.UserStatus;

import java.time.Duration;

/**
 * Redis-based account lockout tracking strategy (future enhancement).
 *
 * <p>Placeholder for future Redis-based distributed lockout tracking. Currently not implemented.
 * Use {@code storage: DB} in configuration.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-08
 */
public class RedisLockoutStrategy implements LockoutStrategyPort {

    @Override
    public void recordFailure(
            String emailHash,
            String ipHash,
            int maxAttempts,
            Duration lockDuration,
            int windowSeconds) {
        throw new UnsupportedOperationException(
                "RedisLockoutStrategy not yet implemented. Use storage: DB in configuration.");
    }

    @Override
    public void recordSuccess(String emailHash) {
        throw new UnsupportedOperationException(
                "RedisLockoutStrategy not yet implemented. Use storage: DB in configuration.");
    }

    @Override
    public boolean shouldAutoUnlock(
            String emailHash, UserStatus status, Duration lockDuration, int windowSeconds) {
        throw new UnsupportedOperationException(
                "RedisLockoutStrategy not yet implemented. Use storage: DB in configuration.");
    }
}
