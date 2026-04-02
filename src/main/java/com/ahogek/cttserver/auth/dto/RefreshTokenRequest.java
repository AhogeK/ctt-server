package com.ahogek.cttserver.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Refresh token request DTO. Uses record for immutability. */
public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token cannot be blank") String refreshToken) {}
