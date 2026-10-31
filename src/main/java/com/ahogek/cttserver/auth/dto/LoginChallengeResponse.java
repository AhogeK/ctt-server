package com.ahogek.cttserver.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Second-factor challenge returned by a password login.
 *
 * @param twoFactorRequired always true
 * @param challengeToken one-time challenge token
 * @param expiresIn challenge lifetime in seconds
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-30
 */
@Schema(description = "Second-factor login challenge")
public record LoginChallengeResponse(
        @Schema(description = "Whether a second factor is required", example = "true")
                boolean twoFactorRequired,
        @Schema(description = "One-time challenge token") String challengeToken,
        @Schema(description = "Challenge lifetime in seconds", example = "300") long expiresIn) {}
