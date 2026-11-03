package com.ahogek.cttserver.auth.passkey.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Browser response from a passkey assertion.
 *
 * @param credential the PublicKeyCredential JSON returned by the browser
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-03
 */
@Schema(description = "Complete a passkey assertion")
public record PasskeyAssertionRequest(
        @Schema(description = "PublicKeyCredential JSON") @NotNull
                Map<String, Object> credential) {}
