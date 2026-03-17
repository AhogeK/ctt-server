package com.ahogek.cttserver.auth;

import com.ahogek.cttserver.auth.model.CurrentUser;

import java.util.Optional;

/**
 * Contract for providing current user identity.
 *
 * <p>Anti-corruption layer that decouples business logic from security framework implementation
 * details. Implementations handle extraction of identity from various authentication mechanisms
 * (JWT, API Key, OAuth2).
 *
 * <p>This interface enables O(1) mocking in unit tests without heavy Spring Security
 * infrastructure.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
public interface CurrentUserProvider {

    /**
     * Retrieves current authenticated user.
     *
     * <p>Returns empty if no authentication present or user is anonymous.
     *
     * @return Optional containing current user, or empty if not authenticated
     */
    Optional<CurrentUser> getCurrentUser();

    /**
     * Retrieves current authenticated user, throwing if not present.
     *
     * @return current authenticated user
     * @throws com.ahogek.cttserver.common.exception.UnauthorizedException if not authenticated
     */
    CurrentUser getCurrentUserRequired();

    /**
     * Retrieves current active user, throwing if not present or inactive.
     *
     * <p>Enforces account status checks (ACTIVE only). Suitable for business operations that
     * require fully activated accounts.
     *
     * @return current active user
     * @throws com.ahogek.cttserver.common.exception.UnauthorizedException if not authenticated
     * @throws com.ahogek.cttserver.common.exception.ForbiddenException if account not active
     */
    CurrentUser getActiveUserRequired();
}
