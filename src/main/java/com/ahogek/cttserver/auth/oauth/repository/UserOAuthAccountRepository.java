package com.ahogek.cttserver.auth.oauth.repository;

import com.ahogek.cttserver.auth.oauth.entity.UserOAuthAccount;
import com.ahogek.cttserver.auth.oauth.enums.OAuthProvider;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for UserOAuthAccount entity.
 *
 * <p>UserOAuthAccount is an aggregate root representing third-party OAuth account bindings.
 * Supports OAuth login flow, account binding/unbinding operations, and user profile display.
 *
 * <p>Query methods leverage underlying PostgreSQL indexes:
 *
 * <ul>
 *   <li>uk_user_oauth_provider_uid - unique index on (provider, provider_user_id)
 *   <li>uk_user_oauth_user_provider - unique index on (user_id, provider)
 * </ul>
 *
 * @author AhogeK
 * @since 2026-04-12
 */
@Repository
public interface UserOAuthAccountRepository extends JpaRepository<UserOAuthAccount, UUID> {

    /**
     * Finds an OAuth account binding by provider and provider's user ID.
     *
     * <p>Used in login flow: after user completes third-party authorization, lookup the binding
     * record in our system.
     *
     * <p>Uses unique index uk_user_oauth_provider_uid for O(log N) lookup.
     *
     * @param provider the OAuth provider (GITHUB, GOOGLE, etc.)
     * @param providerUserId the unique user ID from the third-party provider
     * @return Optional containing the OAuth account binding if found
     */
    Optional<UserOAuthAccount> findByProviderAndProviderUserId(
            OAuthProvider provider, String providerUserId);

    /**
     * Finds an OAuth account binding for a specific user and provider.
     *
     * <p>Used in binding/unbinding flow and single-point queries: check if a user has bound a
     * specific third-party account.
     *
     * <p>Uses unique index uk_user_oauth_user_provider for O(log N) lookup.
     *
     * @param userId the core user ID
     * @param provider the OAuth provider
     * @return Optional containing the OAuth account binding if found
     */
    Optional<UserOAuthAccount> findByUserIdAndProvider(UUID userId, OAuthProvider provider);

    /**
     * Finds all OAuth account bindings for a user.
     *
     * <p>Used in user profile display: list all third-party accounts bound to the current user.
     *
     * @param userId the core user ID
     * @return list of all OAuth account bindings for the user
     */
    List<UserOAuthAccount> findAllByUserId(UUID userId);

    /**
     * Checks if a user has bound a specific OAuth provider.
     *
     * <p>Used for high-frequency state checks: determine binding status without fetching full
     * entity data.
     *
     * @param userId the core user ID
     * @param provider the OAuth provider
     * @return true if binding exists, false otherwise
     */
    boolean existsByUserIdAndProvider(UUID userId, OAuthProvider provider);
}
