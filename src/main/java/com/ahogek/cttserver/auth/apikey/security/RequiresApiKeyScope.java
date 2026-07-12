package com.ahogek.cttserver.auth.apikey.security;

import com.ahogek.cttserver.auth.apikey.enums.ApiKeyScope;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method-level authorization annotation for API key scope enforcement.
 *
 * <p>When placed on a controller method, the request is only permitted if the authenticated
 * principal is an {@link com.ahogek.cttserver.auth.apikey.model.ApiKeyPrincipal} whose granted
 * scopes include the specified {@link ApiKeyScope}. JWT-authenticated users (where the principal is
 * a {@link com.ahogek.cttserver.auth.model.CurrentUser}) bypass this check entirely — they are
 * already authenticated via their web session and are not subject to API key scope restrictions.
 *
 * <p>Authorization is enforced by {@link ApiKeyScopeAspect} which intercepts methods annotated with
 * {@code @RequiresApiKeyScope} and validates the scope before method execution.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * @RequiresApiKeyScope(ApiKeyScope.SYNC)
 * @PostMapping("/pull")
 * public ResponseEntity<SyncPullResponse> pull(@RequestBody SyncPullRequest request) {
 *     // Only API keys with SYNC scope, or JWT users, can access this endpoint
 * }
 * }</pre>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-12
 * @see ApiKeyScopeAspect
 * @see ApiKeyScope
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresApiKeyScope {

    /**
     * The required API key scope for accessing the annotated method.
     *
     * @return the required scope
     */
    ApiKeyScope value();
}
