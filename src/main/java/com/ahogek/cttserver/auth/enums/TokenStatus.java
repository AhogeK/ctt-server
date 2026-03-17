package com.ahogek.cttserver.auth.enums;

/**
 * Token lifecycle status enumeration.
 *
 * <p>Applicable to both one-time tokens (e.g., verification codes) and long-lived tokens (e.g.,
 * Refresh Token).
 *
 * <p>Design principle: <strong>Pessimistic Assertion</strong> - reject if not fully valid.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
public enum TokenStatus {

    /** Valid and ready for consumption. */
    VALID,

    /** Expired: exceeds the set expiration time. */
    EXPIRED,

    /** Consumed: already successfully used (prevents replay attacks). */
    CONSUMED,

    /** Revoked: actively revoked by system or user for security reasons. */
    REVOKED,

    /** Unavailable: associated user or device status is abnormal. */
    UNAVAILABLE;

    /**
     * Checks if token is valid for business flow continuation.
     *
     * @return true only if status is VALID
     */
    public boolean isValid() {
        return this == VALID;
    }

    /**
     * Gets human-readable description of the status.
     *
     * @return description string
     */
    public String description() {
        return switch (this) {
            case VALID -> "Token is valid";
            case EXPIRED -> "Token has expired";
            case CONSUMED -> "Token has been consumed";
            case REVOKED -> "Token has been revoked";
            case UNAVAILABLE -> "Token is unavailable";
        };
    }
}
