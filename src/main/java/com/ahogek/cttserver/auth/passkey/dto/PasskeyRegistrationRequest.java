package com.ahogek.cttserver.auth.passkey.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Browser response from {@code navigator.credentials.create()}.
 *
 * @param credential the PublicKeyCredential JSON returned by the browser
 * @param name optional label; defaults to {@code Passkey}
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-03
 */
@Schema(description = "Complete a passkey registration")
public record PasskeyRegistrationRequest(
        @Schema(description = "PublicKeyCredential JSON") @NotNull Map<String, Object> credential,
        @Schema(description = "Optional label", nullable = true) @Size(max = 64) String name) {}
