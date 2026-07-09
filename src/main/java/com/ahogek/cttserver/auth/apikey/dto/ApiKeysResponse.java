package com.ahogek.cttserver.auth.apikey.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response payload wrapping a list of {@link ApiKeyResponse} entries.
 *
 * @param keys the user's API keys, ordered by creation time (most recent first by convention)
 * @author AhogeK [ahogek@gmail.com]
 */
@Schema(description = "List of API keys belonging to the current user")
public record ApiKeysResponse(
        @Schema(description = "API key metadata entries") List<ApiKeyResponse> keys) {}
