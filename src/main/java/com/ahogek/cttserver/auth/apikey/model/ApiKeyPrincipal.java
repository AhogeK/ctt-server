package com.ahogek.cttserver.auth.apikey.model;

import com.ahogek.cttserver.auth.apikey.enums.ApiKeyScope;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.user.entity.User;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Security principal for API key authentication.
 *
 * <p>Carries both the API-key-specific identity (keyId, scopes) and a populated {@link CurrentUser}
 * built from the owning user entity. Embedding {@code CurrentUser} lets {@code
 * SpringSecurityCurrentUserProvider} serve both the JWT path and the API key path without an
 * additional database lookup — the user details were already loaded by {@code
 * ApiKeyAuthenticationFilter} via {@link ApiKeyService#validateAndTouch(String)}.
 *
 * <p>The embedded {@link CurrentUser} propagates the user's persisted status. Callers are expected
 * to construct this record only after {@code ApiKeyService.validateAndTouch} has run, which rejects
 * inactive users (locking, suspended, deleted, pending verification) and guarantees the populated
 * status is {@code ACTIVE}.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-10
 */
public record ApiKeyPrincipal(CurrentUser user, UUID keyId, Set<ApiKeyScope> scopes) {

    public ApiKeyPrincipal {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(keyId, "keyId");
        Objects.requireNonNull(scopes, "scopes");
    }

    /** Returns the user UUID. */
    public UUID userId() {
        return user.id();
    }

    /**
     * Builds an {@link ApiKeyPrincipal} from a {@link User} entity. The resulting {@code
     * CurrentUser} carries whatever status the user entity holds; the active-status invariant is
     * enforced upstream by {@code ApiKeyService.validateAndTouch}.
     */
    public static ApiKeyPrincipal from(User user, UUID keyId, Set<ApiKeyScope> scopes) {
        Set<String> authorities =
                scopes.stream()
                        .map(ApiKeyScope::getAuthority)
                        .collect(Collectors.toUnmodifiableSet());
        CurrentUser currentUser =
                new CurrentUser(
                        user.getId(),
                        user.getEmail(),
                        user.getStatus(),
                        authorities,
                        CurrentUser.AuthenticationType.API_KEY);
        return new ApiKeyPrincipal(currentUser, keyId, scopes);
    }
}
