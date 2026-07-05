package com.ahogek.cttserver.user.dto;

import com.ahogek.cttserver.user.entity.User;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response payload containing the current user's profile information.
 *
 * <p>Derived from {@link User} entity with sensitive fields intentionally excluded. The {@code
 * emailVerified} field is computed from {@code emailVerifiedAt != null} rather than the raw {@code
 * User.emailVerified} Boolean to avoid exposing the raw Instant field and ensure consistency.
 *
 * @param id the unique user identifier
 * @param email the user's email address
 * @param displayName the user's display name
 * @param emailVerified whether the user's email has been verified
 * @param emailChangePending whether an email change request is pending verification
 * @param hasPassword whether the user has set a password (false for OAuth-only users)
 * @param createdAt the account creation timestamp
 * @param lastLoginAt the last login timestamp (null for newly registered OAuth users who have not
 *     performed a traditional login, or users who registered but never logged in)
 * @param termsVersion the version of terms the user accepted
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-01
 */
@Schema(description = "Current user's profile information")
public record UserProfileResponse(
        @Schema(
                        description = "Unique user identifier",
                        example = "550e8400-e29b-41d4-a716-446655440000")
                UUID id,
        @Schema(description = "User's email address", example = "user@example.com") String email,
        @Schema(description = "User's display name", example = "John Doe") String displayName,
        @Schema(description = "Whether the user's email has been verified", example = "true")
                boolean emailVerified,
        @Schema(description = "Whether an email change request is pending", example = "false")
                boolean emailChangePending,
        @Schema(description = "Whether the user has set a password", example = "true")
                boolean hasPassword,
        @Schema(description = "Account creation timestamp", example = "2026-01-15T10:30:00Z")
                Instant createdAt,
        @Schema(
                        description = "Last login timestamp",
                        example = "2026-07-01T09:15:00Z",
                        nullable = true)
                @JsonInclude(JsonInclude.Include.ALWAYS)
                Instant lastLoginAt,
        @Schema(description = "Version of terms the user accepted", example = "1.0.0")
                String termsVersion) {

    /**
     * Creates a profile DTO from a persistence entity.
     *
     * <p>Intentionally excludes sensitive fields (passwordHash, lastLoginIp, version,
     * emailVerifiedAt, termsAcceptedAt, updatedAt).
     *
     * @param user the User entity
     * @param emailChangePending whether an email change request is currently pending verification
     * @return the DTO representation
     */
    public static UserProfileResponse fromEntity(User user, boolean emailChangePending) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getEmailVerifiedAt() != null,
                emailChangePending,
                user.getPasswordHash() != null,
                user.getCreatedAt(),
                user.getLastLoginAt(),
                user.getTermsVersion());
    }
}
