package com.ahogek.cttserver.auth.dto;

import java.util.UUID;

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
public record LoginResponse(UUID userId, String accessToken, String refreshToken, long expiresIn) {}
