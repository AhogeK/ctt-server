package com.ahogek.cttserver.auth.oauth.model;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * OAuth state payload carrying context for the authorization callback.
 *
 * <p>Stored in Redis under {@code oauth:state:<uuid>} with a 10-minute TTL and consumed atomically
 * (GETDEL) on callback. The payload survives the GitHub round-trip so the callback knows whether it
 * is performing a LOGIN or a BIND operation, and which user (if any) initiated the request.
 *
 * <p><b>Session invariant</b> (enforced by the callback controller): the BIND flow must never issue
 * a new CTT access/refresh token — the user's browser tokens remain byte-identical before and after
 * a successful bind. The LOGIN flow, by contrast, always issues fresh tokens.
 *
 * @param action the operation type: LOGIN (unauthenticated) or BIND (authenticated linking)
 * @param currentUserId the user performing the bind (required for {@link Action#BIND}, must be
 *     {@code null} for {@link Action#LOGIN})
 * @param redirectUrl the frontend route to redirect to after a successful bind (optional, defaults
 *     to {@code null}; BIND callers typically pass {@code "/settings/profile"})
 * @param clientIp the client IP address captured at authorize time (IPv4/IPv6, nullable); used to
 *     set {@code lastLoginAt} on the user entity at callback time. For OAuth flows the callback
 *     request originates from the provider's servers, so the real client IP must be captured at
 *     authorize and forwarded through the state payload.
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-06-28
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OAuthStatePayload(
        Action action, UUID currentUserId, String redirectUrl, String clientIp) {
    public enum Action {
        LOGIN,
        BIND
    }

    /**
     * Canonical constructor with payload invariant validation.
     *
     * @throws IllegalArgumentException if {@link Action#BIND} is supplied without a {@code
     *     currentUserId}; a bind without a target user is meaningless and indicates a caller bug
     */
    public OAuthStatePayload {
        if (action == Action.BIND && currentUserId == null) {
            throw new IllegalArgumentException("BIND action requires currentUserId in payload");
        }
        if (action == Action.LOGIN && currentUserId != null) {
            throw new IllegalArgumentException(
                    "LOGIN action must not have currentUserId in payload");
        }
    }
}
