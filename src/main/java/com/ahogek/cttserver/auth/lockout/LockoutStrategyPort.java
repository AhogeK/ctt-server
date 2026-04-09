package com.ahogek.cttserver.auth.lockout;

import com.ahogek.cttserver.user.enums.UserStatus;

import java.time.Duration;

/**
 * Port interface for account lockout tracking strategies.
 *
 * <p>Defines the contract for implementing different lockout tracking mechanisms (e.g., Redis-based
 * distributed tracking, database-backed persistent tracking, or in-memory local tracking).
 *
 * <p>All methods accept primitive/hash parameters — no dependency on the {@code User} entity.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-08
 */
public interface LockoutStrategyPort {

    /**
     * Records a failed authentication attempt.
     *
     * @param emailHash SHA-256 hash of the email address (lowercased)
     * @param ipHash SHA-256 hash of the IP address
     * @param maxAttempts maximum allowed failures before lockout
     * @param lockDuration duration of the lockout period
     * @param windowSeconds sliding window size in seconds for counting failures
     */
    void recordFailure(
            String emailHash,
            String ipHash,
            int maxAttempts,
            Duration lockDuration,
            int windowSeconds);

    /**
     * Records a successful authentication — clears failure state.
     *
     * @param emailHash SHA-256 hash of the email address (lowercased)
     */
    void recordSuccess(String emailHash);

    /**
     * Checks whether the account should be automatically unlocked.
     *
     * @param emailHash SHA-256 hash of the email address (lowercased)
     * @param status current user status
     * @param lockDuration duration of the lockout period
     * @param windowSeconds sliding window size in seconds
     * @return true if the account should be auto-unlocked, false otherwise
     */
    boolean shouldAutoUnlock(
            String emailHash, UserStatus status, Duration lockDuration, int windowSeconds);
}
