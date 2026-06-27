package com.ahogek.cttserver.auth.oauth.dto;

import com.ahogek.cttserver.auth.oauth.entity.UserOAuthAccount;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * OAuth account binding details for a single third-party provider.
 *
 * @param provider the OAuth provider identifier (e.g., "github")
 * @param providerLogin the user's login handle on the provider (nullable)
 * @param providerEmail the user's email on the provider (nullable)
 * @param createdAt the binding creation timestamp
 * @param updatedAt the last refresh or update timestamp
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-06-28
 */
@Schema(description = "OAuth account binding details for a single third-party provider")
public record OAuthAccountBinding(
        @Schema(description = "OAuth provider identifier", example = "github") String provider,
        @Schema(
                        description = "User's login handle on the provider",
                        example = "octocat",
                        nullable = true)
                String providerLogin,
        @Schema(
                        description = "User's email on the provider",
                        example = "octocat@example.com",
                        nullable = true)
                String providerEmail,
        @Schema(description = "Binding creation timestamp") OffsetDateTime createdAt,
        @Schema(description = "Last refresh or update timestamp") OffsetDateTime updatedAt) {

    /**
     * Creates a binding DTO from a persistence entity.
     *
     * <p>Intentionally excludes sensitive fields (access token, refresh token, provider user ID).
     *
     * @param entity the UserOAuthAccount entity
     * @return the DTO representation
     */
    public static OAuthAccountBinding fromEntity(UserOAuthAccount entity) {
        return new OAuthAccountBinding(
                entity.getProvider().getValue(),
                entity.getProviderLogin(),
                entity.getProviderEmail(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}