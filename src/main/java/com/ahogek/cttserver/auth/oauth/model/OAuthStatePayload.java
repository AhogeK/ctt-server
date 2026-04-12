package com.ahogek.cttserver.auth.oauth.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * OAuth state payload carrying context for the authorization callback.
 *
 * @param action operation type: LOGIN (unauthenticated OAuth) or BIND (authenticated linking)
 * @param redirectUri frontend callback URI to prevent redirect tampering
 * @param userId target user ID when action is BIND; null for LOGIN
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OAuthStatePayload(Action action, String redirectUri, String userId) {
    public enum Action {
        LOGIN,
        BIND
    }
}
