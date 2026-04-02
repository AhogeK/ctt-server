package com.ahogek.cttserver.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Logout request DTO. */
public record LogoutRequest(
        @NotBlank(message = "Refresh token cannot be blank") String refreshToken) {}
