package com.ahogek.cttserver.auth.dto;

import com.ahogek.cttserver.common.validation.ValidationConstants;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Logout request DTO.
 *
 * @param refreshToken the refresh token to revoke
 */
@Schema(description = "Logout request containing the refresh token to revoke")
public record LogoutRequest(
        @Schema(description = "Refresh token to revoke", example = "abc123xyz")
                @NotBlank(message = ValidationConstants.MSG_NOT_BLANK)
                String refreshToken) {}
