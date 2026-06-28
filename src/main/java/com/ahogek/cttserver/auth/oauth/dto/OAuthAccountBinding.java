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
     * <p>The {@code providerLogin} fallback chain guarantees a non-blank display handle for the
     * UI:
     *
     * <ol>
     *   <li>Stored {@code providerLogin} (the GitHub handle at last sync)
     *   <li>Local-part of {@code providerEmail} (e.g. {@code "octocat"} from {@code
     *       "octocat@example.com"})
     *   <li>{@code providerUserId} (last-resort; guaranteed non-null per DB NOT NULL)
     * </ol>
     *
     * @param entity the UserOAuthAccount entity
     * @return the DTO representation
     */
    public static OAuthAccountBinding fromEntity(UserOAuthAccount entity) {
        String login = entity.getProviderLogin();
        String email = entity.getProviderEmail();

        String displayName = (login != null && !login.isBlank()) ? login.trim() : null;
        if (displayName == null) {
            if (email != null && email.contains("@")) {
                String localPart = email.substring(0, email.indexOf('@')).trim();
                displayName = localPart.isEmpty() ? entity.getProviderUserId() : localPart;
            } else {
                displayName = entity.getProviderUserId();
            }
        }
        return new OAuthAccountBinding(
                entity.getProvider().getValue(),
                displayName,
                entity.getProviderEmail(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}