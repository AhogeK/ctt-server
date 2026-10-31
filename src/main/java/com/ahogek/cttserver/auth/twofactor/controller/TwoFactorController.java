package com.ahogek.cttserver.auth.twofactor.controller;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.auth.WebSessionGuard;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.auth.twofactor.dto.ConfirmTotpRequest;
import com.ahogek.cttserver.auth.twofactor.dto.DisableTwoFactorRequest;
import com.ahogek.cttserver.auth.twofactor.dto.RecoveryCodesResponse;
import com.ahogek.cttserver.auth.twofactor.dto.RegenerateRecoveryCodesRequest;
import com.ahogek.cttserver.auth.twofactor.dto.StartTotpRequest;
import com.ahogek.cttserver.auth.twofactor.dto.StartTotpResponse;
import com.ahogek.cttserver.auth.twofactor.dto.TwoFactorStatusResponse;
import com.ahogek.cttserver.auth.twofactor.service.TwoFactorService;
import com.ahogek.cttserver.common.ratelimit.RateLimit;
import com.ahogek.cttserver.common.ratelimit.RateLimitType;
import com.ahogek.cttserver.common.response.ErrorResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Two-factor authentication endpoints for the authenticated browser session.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-30
 */
@Tag(name = "Two-factor authentication", description = "TOTP setup and recovery codes")
@RestController
@RequestMapping("/api/v1/users/me/2fa")
public class TwoFactorController {

    private final CurrentUserProvider currentUserProvider;
    private final TwoFactorService twoFactorService;

    public TwoFactorController(
            CurrentUserProvider currentUserProvider, TwoFactorService twoFactorService) {
        this.currentUserProvider = currentUserProvider;
        this.twoFactorService = twoFactorService;
    }

    @Operation(summary = "Get two-factor status")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Current two-factor state",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "API key caller - AUTH_025",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ResponseEntity<RestApiResponse<TwoFactorStatusResponse>> status() {
        CurrentUser current =
                WebSessionGuard.requireWebSession(currentUserProvider.getActiveUserRequired());
        return ResponseEntity.ok(
                RestApiResponse.ok(twoFactorService.status(current.id(), current.sessionId())));
    }

    @Operation(summary = "Start TOTP setup")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Unconfirmed secret and otpauth URI",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Password verification failed - USER_013/USER_014",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "Recent authentication required - AUTH_031",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Two-factor is already enabled - AUTH_028",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @SecurityRequirement(name = "bearerAuth")
    @RateLimit(type = RateLimitType.USER, limit = 5, windowSeconds = 3600)
    @PostMapping("/totp/start")
    public ResponseEntity<RestApiResponse<StartTotpResponse>> start(
            @Valid @RequestBody StartTotpRequest request) {
        CurrentUser current =
                WebSessionGuard.requireWebSession(currentUserProvider.getActiveUserRequired());
        return ResponseEntity.ok(
                RestApiResponse.ok(
                        twoFactorService.start(
                                current.id(), request.currentPassword(), current.sessionId())));
    }

    @Operation(summary = "Confirm TOTP setup")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Recovery codes, returned once",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Invalid code or expired setup - AUTH_026/AUTH_027",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Two-factor is already enabled - AUTH_028",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @SecurityRequirement(name = "bearerAuth")
    @RateLimit(type = RateLimitType.USER, limit = 5, windowSeconds = 300)
    @PostMapping("/totp/confirm")
    public ResponseEntity<RestApiResponse<RecoveryCodesResponse>> confirm(
            @Valid @RequestBody ConfirmTotpRequest request) {
        CurrentUser current =
                WebSessionGuard.requireWebSession(currentUserProvider.getActiveUserRequired());
        return ResponseEntity.ok(
                RestApiResponse.ok(
                        twoFactorService.confirm(
                                current.id(), request.code(), current.sessionId())));
    }

    @Operation(summary = "Disable two-factor authentication")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "204", description = "Two-factor disabled"),
                @ApiResponse(
                        responseCode = "401",
                        description = "Invalid two-factor credential - AUTH_027",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Two-factor is not enabled - AUTH_029",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @SecurityRequirement(name = "bearerAuth")
    @RateLimit(type = RateLimitType.USER, limit = 5, windowSeconds = 3600)
    @PostMapping("/disable")
    public ResponseEntity<Void> disable(@Valid @RequestBody DisableTwoFactorRequest request) {
        CurrentUser current =
                WebSessionGuard.requireWebSession(currentUserProvider.getActiveUserRequired());
        twoFactorService.disable(
                current.id(), request.code(), request.recoveryCode(), current.sessionId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Regenerate recovery codes")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "New recovery codes, returned once",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Invalid two-factor credential - AUTH_027",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Two-factor is not enabled - AUTH_029",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @SecurityRequirement(name = "bearerAuth")
    @RateLimit(type = RateLimitType.USER, limit = 3, windowSeconds = 3600)
    @PostMapping("/recovery-codes/regenerate")
    public ResponseEntity<RestApiResponse<RecoveryCodesResponse>> regenerate(
            @Valid @RequestBody RegenerateRecoveryCodesRequest request) {
        CurrentUser current =
                WebSessionGuard.requireWebSession(currentUserProvider.getActiveUserRequired());
        return ResponseEntity.ok(
                RestApiResponse.ok(
                        twoFactorService.regenerate(
                                current.id(), request.code(), request.recoveryCode())));
    }
}
