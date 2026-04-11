package com.ahogek.cttserver.auth.dto;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Refresh token request DTO.
 *
 * @param refreshToken the refresh token for token rotation
 * @author AhogeK [ahogek@gmail.com]
 * @since 0.1.0
 */
@Schema(description = "Refresh token request for token rotation")
public record RefreshTokenRequest(
        @Schema(
                        description = "Refresh token for obtaining new access tokens",
                        example = "d4f5e6a7b8c9d0e1f2a3b4c5d6e7f8a9")
                @NotBlank(message = "Refresh token cannot be blank")
                String refreshToken) {}
