package com.ahogek.cttserver.auth.model;

import com.ahogek.cttserver.user.enums.UserStatus;

import java.util.Set;
import java.util.UUID;

/**
 * Unified identity model for security context.
 *
 * <p>Anti-corruption layer that shields business logic from JWT, API Key, and other authentication
 * mechanism details. Provides a consistent view regardless of how the user authenticated.
 *
 * @param id the unique user identifier
 * @param email the user's email address
 * @param status the user's account status
 * @param authorities the granted authorities/roles
 * @param authType the authentication mechanism used
 * @param sessionId the login session the presented access token belongs to; null for API-key
 *     principals and for tokens minted outside a session
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
public record CurrentUser(
        UUID id,
        String email,
        UserStatus status,
        Set<String> authorities,
        AuthenticationType authType,
        UUID sessionId) {

    /**
     * Builds an identity without a session, which is the case for every non-web authentication.
     *
     * @param id the unique user identifier
     * @param email the user's email address
     * @param status the user's account status
     * @param authorities the granted authorities/roles
     * @param authType the authentication mechanism used
     */
    public CurrentUser(
            UUID id,
            String email,
            UserStatus status,
            Set<String> authorities,
            AuthenticationType authType) {
        this(id, email, status, authorities, authType, null);
    }

    /**
     * Checks if the user account is in ACTIVE state.
     *
     * @return true if status is ACTIVE
     */
    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    /**
     * Authentication mechanism types.
     *
     * <p>Distinguishes between different client types for audit and rate limiting purposes.
     */
    public enum AuthenticationType {
        /** Browser-based JWT session */
        WEB_SESSION,
        /** IDE plugin or API client */
        API_KEY,
        /** OAuth2 provider authentication */
        OAUTH2
    }
}
