package com.ahogek.cttserver.auth.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One login session of the current account.
 *
 * <p>A session is one login and everything rotation produced from it, so the fields describe the
 * chain rather than a single refresh token: {@code createdAt} is when the login happened, {@code
 * lastUsedAt} the most recent refresh, and {@code expiresAt} the latest expiry any of its tokens
 * reached.
 *
 * <p>{@code deviceName} and {@code ideName} are only present for device-bound (plugin) sessions —
 * they come from the device registry, and a browser session has no entry there. {@code userAgent}
 * is the client string exactly as it arrived; the server does not parse it.
 *
 * <p>Nullable keys are serialized even when null ({@link JsonInclude.Include#ALWAYS}): a client
 * must be able to tell "this browser session has no device" from "this client is too old to know
 * the field".
 *
 * @param id the session identifier
 * @param kind the audience the session was issued with ({@code WEB} or {@code PLUGIN})
 * @param deviceName the registered device name, null for a browser session
 * @param ideName the registered IDE name, null for a browser session
 * @param userAgent the client User-Agent as sent, null when the client sent none
 * @param createdAt when the session started
 * @param lastUsedAt when the session last refreshed, null when it never has
 * @param expiresAt when the session's newest token expires
 * @param current whether this is the session the calling access token belongs to
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-19
 */
@Schema(description = "One login session of the current user")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record LoginSessionResponse(
        @Schema(
                        description = "Session identifier",
                        example = "9f1c8f7a-2b4e-4d5a-9c3e-1a2b3c4d5e6f")
                UUID id,
        @Schema(
                        description = "Audience the session was issued with",
                        example = "PLUGIN",
                        allowableValues = {"WEB", "PLUGIN"})
                String kind,
        @Schema(
                        description = "Registered device name, null for a browser session",
                        example = "MacBook Pro")
                String deviceName,
        @Schema(
                        description = "Registered IDE name, null for a browser session",
                        example = "IntelliJ IDEA")
                String ideName,
        @Schema(
                        description = "Client User-Agent exactly as sent, null when none was sent",
                        example = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)")
                String userAgent,
        @Schema(description = "When the session started", example = "2026-10-19T09:12:31Z")
                Instant createdAt,
        @Schema(
                        description = "When the session last refreshed, null when it never has",
                        example = "2026-10-19T10:04:07Z")
                Instant lastUsedAt,
        @Schema(
                        description = "When the session's newest token expires",
                        example = "2026-11-18T09:12:31Z")
                Instant expiresAt,
        @Schema(description = "Whether this is the session the request was made with")
                boolean current) {}
