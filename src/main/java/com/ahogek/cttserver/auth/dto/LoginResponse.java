package com.ahogek.cttserver.auth.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Login success response DTO.
 *
 * @param userId the user's ID
 * @param accessToken the JWT access token
 * @param refreshToken the refresh token
 * @param expiresIn access token expiration time in seconds
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-23
 */
@Schema(description = "Login success response with JWT tokens")
public record LoginResponse(
        @Schema(
                        description = "User unique identifier",
                        example = "550e8400-e29b-41d4-a716-446655440000")
                UUID userId,
        @Schema(
                        description = "JWT access token for API authentication",
                        example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
                String accessToken,
        @Schema(
                        description = "Refresh token for obtaining new access tokens",
                        example = "d4f5e6a7b8c9d0e1f2a3b4c5d6e7f8a9")
                String refreshToken,
        @Schema(description = "Access token expiration time in seconds", example = "3600")
                long expiresIn) {}
