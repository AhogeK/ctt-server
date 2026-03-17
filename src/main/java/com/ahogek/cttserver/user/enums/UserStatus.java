package com.ahogek.cttserver.user.enums;

/**
 * User state machine enum with transition contracts.
 *
 * <p>Defines valid state transitions according to the state machine topology:
 *
 * <pre>
 * PENDING_VERIFICATION → ACTIVE → LOCKED/SUSPENDED/DELETED
 *                     ↘ SUSPENDED ↘
 *                      ↘ DELETED ←┘
 * </pre>
 *
 * <p>State transition rules:
 *
 * <ul>
 *   <li>PENDING_VERIFICATION: Initial state after registration
 *   <li>ACTIVE: Normal operational state after email verification
 *   <li>LOCKED: Temporary restriction due to failed login attempts (reversible)
 *   <li>SUSPENDED: Administrative ban due to violations (reversible by admin)
 *   <li>DELETED: Terminal state, account soft-deleted (irreversible)
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
public enum UserStatus {

    /** Pending verification: newly registered, core features disabled. */
    PENDING_VERIFICATION,

    /** Active: normal user with full access. */
    ACTIVE,

    /** Locked: temporary restriction due to failed login attempts. */
    LOCKED,

    /** Suspended: administrative ban due to violations. */
    SUSPENDED,

    /** Deleted: terminal state, account soft-deleted. */
    DELETED;

    /**
     * Validates if transition to next state is allowed.
     *
     * <p>Time complexity: O(1) using modern switch expression.
     *
     * @param nextState the target state
     * @return true if transition is valid
     */
    public boolean canTransitionTo(UserStatus nextState) {
        if (nextState == null || this == nextState) {
            return false;
        }
        return switch (this) {
            case PENDING_VERIFICATION, LOCKED ->
                    nextState == ACTIVE || nextState == SUSPENDED || nextState == DELETED;
            case ACTIVE -> nextState == LOCKED || nextState == SUSPENDED || nextState == DELETED;
            case SUSPENDED -> nextState == ACTIVE || nextState == DELETED;
            case DELETED -> false;
        };
    }
}
