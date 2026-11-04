package com.ahogek.cttserver.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Refresh token request DTO.
 *
 * <p>The token may be supplied in the request body or in the httpOnly refresh-token cookie. When
 * the cookie carries a token, the body and this field may both be omitted.
 *
 * @param refreshToken the refresh token for token rotation; optional when the cookie is present
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-02
 */
@Schema(description = "Refresh token request for token rotation")
public record RefreshTokenRequest(
        @Schema(
                        description =
                                "Refresh token for obtaining new access tokens; optional when the "
                                        + "httpOnly refresh-token cookie is present",
                        example = "d4f5e6a7b8c9d0e1f2a3b4c5d6e7f8a9")
                String refreshToken) {}
