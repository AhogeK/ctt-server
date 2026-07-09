package com.ahogek.cttserver.auth.apikey.enums;

import com.ahogek.cttserver.auth.apikey.entity.ApiKey;

import java.time.Instant;

/**
 * Lifecycle status of an {@link ApiKey}.
 *
 * <p>Status is derived from the {@code revokedAt} and {@code expiresAt} columns rather than stored
 * as a separate field — this avoids the risk of drift between the source of truth and the computed
 * value.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-09
 */
public enum ApiKeyStatus {

    /** Key is in normal use; not revoked and not expired. */
    ACTIVE,

    /** Key has been explicitly revoked via {@code ApiKey.revoke()}. */
    REVOKED,

    /** Key has passed its {@code expiresAt} timestamp. */
    EXPIRED;

    /**
     * Derives the status for the given entity at the supplied instant.
     *
     * <p>Resolution order mirrors operator intuition: an explicit revocation outranks a passive
     * expiration so revoked keys are reported as {@link #REVOKED} even if they also happen to be
     * expired.
     *
     * @param apiKey the entity to evaluate; must not be {@code null}
     * @param now the reference instant; typically {@link Instant#now()}
     * @return the derived status; {@link #ACTIVE} when neither revoked nor expired
     */
    public static ApiKeyStatus derive(ApiKey apiKey, Instant now) {
        if (apiKey.getRevokedAt() != null) {
            return REVOKED;
        }
        if (apiKey.getExpiresAt() != null && !apiKey.getExpiresAt().isAfter(now)) {
            return EXPIRED;
        }
        return ACTIVE;
    }
}
