package com.ahogek.cttserver.auth.apikey.dto;

import com.ahogek.cttserver.auth.apikey.entity.ApiKey;
import com.ahogek.cttserver.auth.apikey.enums.ApiKeyScope;
import com.ahogek.cttserver.auth.apikey.enums.ApiKeyStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * API key metadata snapshot exposed by list/get endpoints.
 *
 * <p>Intentionally excludes the raw key value and the SHA-256 hash. The {@code keyPrefix} is the
 * short, non-secret visible portion (e.g. {@code cttak_a1b2c3d4}) that callers use to identify a
 * key in audit logs.
 *
 * @param id the key's unique identifier
 * @param name the human-readable label
 * @param keyPrefix the visible (non-secret) prefix used for identification in audit logs
 * @param scopes the permission scopes granted to the key
 * @param lastUsedAt timestamp of the most recent successful authentication, or {@code null} if
 *     never used
 * @param expiresAt expiration timestamp, or {@code null} when the key never expires
 * @param revokedAt revocation timestamp, or {@code null} when the key is still active
 * @param createdAt creation timestamp
 * @param status derived status: {@code ACTIVE}, {@code REVOKED}, or {@code EXPIRED}
 * @author AhogeK [ahogek@gmail.com]
 */
@Schema(description = "API key metadata snapshot (no secret material exposed)")
public record ApiKeyResponse(
        @Schema(
                        description = "API key unique identifier",
                        example = "550e8400-e29b-41d4-a716-446655440000")
                UUID id,
        @Schema(
                        description = "Human-readable label for the key",
                        example = "MacBook Pro — IntelliJ")
                String name,
        @Schema(
                        description =
                                "Visible (non-secret) prefix used to identify the key in audit logs",
                        example = "cttak_a1b2c3d4")
                String keyPrefix,
        @Schema(
                        description = "Permission scopes granted to the key",
                        example = "[\"READ\", \"SYNC\"]")
                Set<ApiKeyScope> scopes,
        @Schema(
                        description =
                                "Timestamp of the most recent successful authentication; null if never"
                                        + " used",
                        example = "2026-07-09T10:30:00Z",
                        nullable = true)
                Instant lastUsedAt,
        @Schema(
                        description = "Expiration timestamp; null means the key never expires",
                        example = "2027-01-01T00:00:00Z",
                        nullable = true)
                Instant expiresAt,
        @Schema(
                        description = "Revocation timestamp; null means the key is not revoked",
                        example = "2026-08-01T12:00:00Z",
                        nullable = true)
                Instant revokedAt,
        @Schema(description = "Creation timestamp", example = "2026-07-09T10:30:00Z")
                Instant createdAt,
        @Schema(description = "Derived key status", example = "ACTIVE") ApiKeyStatus status) {

    /**
     * Builds an {@link ApiKeyResponse} from a persisted entity, deriving the current status.
     *
     * <p>Deliberately omits {@code keyHash} and the raw key value. The {@code status} is derived
     * via {@link ApiKeyStatus#derive(ApiKey, Instant)} using the call-time clock.
     *
     * @param entity the persisted {@link ApiKey}
     * @return a metadata-only DTO safe for API exposure
     */
    public static ApiKeyResponse fromEntity(ApiKey entity) {
        ApiKeyStatus status = ApiKeyStatus.derive(entity, Instant.now());
        return new ApiKeyResponse(
                entity.getId(),
                entity.getName(),
                entity.getKeyPrefix(),
                entity.getScopes(),
                entity.getLastUsedAt(),
                entity.getExpiresAt(),
                entity.getRevokedAt(),
                entity.getCreatedAt(),
                status);
    }
}
