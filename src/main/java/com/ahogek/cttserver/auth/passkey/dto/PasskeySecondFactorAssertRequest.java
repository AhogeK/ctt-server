package com.ahogek.cttserver.auth.passkey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Completes a second-factor login with a passkey assertion.
 *
 * @param challengeToken the one-time challenge returned by the password or OAuth login
 * @param credential the PublicKeyCredential JSON returned by the browser
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-03
 */
@Schema(description = "Complete a second-factor login with a passkey")
public record PasskeySecondFactorAssertRequest(
        @Schema(description = "One-time second-factor challenge token") @NotBlank @Size(max = 256)
                String challengeToken,
        @Schema(description = "PublicKeyCredential JSON") @NotNull
                Map<String, Object> credential) {}
