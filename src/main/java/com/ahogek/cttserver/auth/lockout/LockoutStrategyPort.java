package com.ahogek.cttserver.auth.lockout;

import com.ahogek.cttserver.user.entity.User;

import java.time.Duration;

/**
 * Port interface for account lockout tracking strategies.
 *
 * <p>Defines the contract for implementing different lockout tracking mechanisms (e.g., Redis-based
 * distributed tracking, database-backed persistent tracking, or in-memory local tracking).
 *
 * <p><strong>Strategy Pattern:</strong>
 *
 * <ul>
 *   <li>Allows swapping lockout implementations without changing business logic
 *   <li>Supports different deployment scenarios (single-node vs distributed)
 *   <li>Enables testing with mock strategies
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-08
 */
public interface LockoutStrategyPort {

    /**
     * Records a failed authentication attempt for the specified user.
     *
     * <p>Implementations should:
     *
     * <ul>
     *   <li>Increment failure count within the sliding window
     *   <li>Lock the account if max attempts exceeded
     *   <li>Set lock expiration based on lockDuration
     * </ul>
     *
     * @param user the user entity whose account may be locked
     * @param maxAttempts maximum allowed failures before lockout
     * @param lockDuration duration of the lockout period
     * @param windowSeconds sliding window size in seconds for counting failures
     */
    void recordFailure(User user, int maxAttempts, Duration lockDuration, int windowSeconds);

    /**
     * Records a successful authentication for the specified user.
     *
     * <p>Implementations should clear the failure count and any active lockout state.
     *
     * @param user the user entity whose authentication succeeded
     */
    void recordSuccess(User user);

    /**
     * Checks whether the user's account should be automatically unlocked.
     *
     * <p>Implementations should check if the lockout period has expired and the account can be
     * unlocked without manual intervention.
     *
     * @param user the user entity to check
     * @return true if the account should be auto-unlocked, false otherwise
     */
    boolean shouldAutoUnlock(User user);
}