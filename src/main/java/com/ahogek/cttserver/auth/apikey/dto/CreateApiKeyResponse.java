package com.ahogek.cttserver.auth.apikey.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response payload returned exactly once when an API key is created.
 *
 * <p>The raw key value is the only opportunity for the caller to obtain the secret. The server
 * stores only the SHA-256 hash; the raw value cannot be retrieved, rotated, or recovered after this
 * response is delivered.
 *
 * @param rawKey the full API key value (e.g. {@code cttak_<prefix>_<secret>}); shown only at
 *     creation
 * @param apiKey the persisted key metadata snapshot, identical to what is returned by subsequent
 *     list/get endpoints
 * @author AhogeK [ahogek@gmail.com]
 */
@Schema(description = "Response returned at API key creation, containing the raw key exactly once")
public record CreateApiKeyResponse(
        @Schema(
                        description =
                                "API key value — shown only at creation. Store securely; it cannot be"
                                        + " retrieved later.",
                        example =
                                "cttak_a1b2c3d4_z9y8x7w6v5u4t3s2r1q0p9o8n7m6l5k4j3i2h1g0f9e8d7c6b5a4")
                String rawKey,
        @Schema(description = "Persisted API key metadata snapshot") ApiKeyResponse apiKey) {}
