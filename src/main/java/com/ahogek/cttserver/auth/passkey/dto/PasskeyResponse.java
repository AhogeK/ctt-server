package com.ahogek.cttserver.auth.passkey.dto;

import com.ahogek.cttserver.auth.passkey.entity.UserPasskey;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Metadata shown on the security settings page. Credential material is never returned.
 *
 * @param id the credential row id
 * @param name human-readable label
 * @param createdAt registration time
 * @param lastUsedAt last successful assertion, or null
 * @param transports authenticator transports reported at registration
 * @param backupEligible WebAuthn BE flag, for display only
 * @param backupState WebAuthn BS flag, for display only
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-03
 */
@Schema(description = "Registered passkey metadata")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PasskeyResponse(
        @Schema(description = "Credential row id") UUID id,
        @Schema(description = "Human-readable label", example = "MacBook Pro") String name,
        @Schema(description = "Registration time") Instant createdAt,
        @Schema(description = "Last successful use, or null", nullable = true) Instant lastUsedAt,
        @Schema(description = "Authenticator transports") List<String> transports,
        @Schema(description = "WebAuthn BE flag") boolean backupEligible,
        @Schema(description = "WebAuthn BS flag") boolean backupState) {

    /**
     * Maps a persisted credential to its safe response projection.
     *
     * @param passkey the persisted credential
     * @return the response projection
     */
    public static PasskeyResponse fromEntity(UserPasskey passkey) {
        return new PasskeyResponse(
                passkey.getId(),
                passkey.getName(),
                passkey.getCreatedAt(),
                passkey.getLastUsedAt(),
                passkey.getTransports(),
                passkey.isBackupEligible(),
                passkey.isBackupState());
    }
}
