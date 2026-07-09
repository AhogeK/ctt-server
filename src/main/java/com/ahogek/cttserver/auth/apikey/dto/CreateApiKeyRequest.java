package com.ahogek.cttserver.auth.apikey.dto;

import com.ahogek.cttserver.auth.apikey.enums.ApiKeyScope;
import com.ahogek.cttserver.common.validation.ValidationConstants;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request payload for creating a new API key.
 *
 * <p>The raw key value is returned exactly once in {@link CreateApiKeyResponse} and cannot be
 * retrieved afterwards. Callers must store it securely at creation time.
 *
 * @param name human-readable label for the key (1-100 characters)
 * @param scopes non-empty set of {@link ApiKeyScope} permissions granted to the key
 * @param expiresAt optional expiration timestamp; when {@code null} the key never expires
 * @author AhogeK [ahogek@gmail.com]
 */
@Schema(description = "Request payload to create a new API key")
public record CreateApiKeyRequest(
        @Schema(
                        description = "Human-readable label for the key",
                        example = "MacBook Pro — IntelliJ IDEA")
                @NotBlank(message = ValidationConstants.MSG_NOT_BLANK)
                @Size(max = 100, message = "Name must not exceed 100 characters")
                String name,
        @Schema(
                        description =
                                "Permission scopes granted to the key (at least one required)",
                        example = "[\"READ\", \"SYNC\"]")
                @NotEmpty(message = "At least one scope is required")
                Set<ApiKeyScope> scopes,
        @Schema(
                        description =
                                "Optional expiration timestamp; null means the key never expires",
                        example = "2027-01-01T00:00:00+09:00",
                        nullable = true)
                @Future(message = "Expiration must be in the future")
                OffsetDateTime expiresAt) {}
