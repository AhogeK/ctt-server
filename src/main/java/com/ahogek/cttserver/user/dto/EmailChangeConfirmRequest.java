package com.ahogek.cttserver.user.dto;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to confirm email change via verification link")
public record EmailChangeConfirmRequest(
        @Schema(description = "Verification token from email link", example = "abc123def456...")
                @NotBlank(message = "Token is required")
                String token) {}
