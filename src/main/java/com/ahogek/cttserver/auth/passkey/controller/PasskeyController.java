package com.ahogek.cttserver.auth.passkey.controller;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.auth.WebSessionGuard;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.auth.passkey.dto.PasskeyRegistrationRequest;
import com.ahogek.cttserver.auth.passkey.dto.PasskeyRenameRequest;
import com.ahogek.cttserver.auth.passkey.dto.PasskeyResponse;
import com.ahogek.cttserver.auth.passkey.service.PasskeyManagementService;
import com.ahogek.cttserver.auth.passkey.service.PasskeyRegistrationService;
import com.ahogek.cttserver.common.ratelimit.RateLimit;
import com.ahogek.cttserver.common.ratelimit.RateLimitType;
import com.ahogek.cttserver.common.response.ErrorResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Passkey management endpoints for the authenticated browser session.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-03
 */
@Tag(name = "Passkeys", description = "Register and manage WebAuthn passkeys")
@RestController
@RequestMapping("/api/v1/users/me/passkey")
public class PasskeyController {

    private final CurrentUserProvider currentUserProvider;
    private final PasskeyRegistrationService registrationService;
    private final PasskeyManagementService managementService;

    public PasskeyController(
            CurrentUserProvider currentUserProvider,
            PasskeyRegistrationService registrationService,
            PasskeyManagementService managementService) {
        this.currentUserProvider = currentUserProvider;
        this.registrationService = registrationService;
        this.managementService = managementService;
    }

    @Operation(summary = "Start passkey registration")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "WebAuthn creation options",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "403",
                        description =
                                "API key or recent authentication required - AUTH_025/AUTH_031",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "429",
                        description = "Too many requests - RATE_LIMIT_001",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @SecurityRequirement(name = "bearerAuth")
    @RateLimit(type = RateLimitType.USER, limit = 10, windowSeconds = 3600)
    @PostMapping("/options")
    public ResponseEntity<RestApiResponse<Map<String, Object>>> options() {
        CurrentUser current = requireWebSession();
        return ResponseEntity.ok(
                RestApiResponse.ok(
                        registrationService.startRegistration(current.id(), current.sessionId())));
    }

    @Operation(summary = "Complete passkey registration")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Passkey registered",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Registration verification failed - AUTH_033",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "Recent authentication required - AUTH_031",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Credential already registered - AUTH_034",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "429",
                        description = "Too many requests - RATE_LIMIT_001",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @SecurityRequirement(name = "bearerAuth")
    @RateLimit(type = RateLimitType.USER, limit = 10, windowSeconds = 3600)
    @PostMapping
    public ResponseEntity<RestApiResponse<PasskeyResponse>> register(
            @Valid @RequestBody PasskeyRegistrationRequest request) {
        CurrentUser current = requireWebSession();
        PasskeyResponse response =
                registrationService.finishRegistration(current.id(), current.sessionId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(RestApiResponse.ok(response));
    }

    @Operation(summary = "List registered passkeys")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Registered passkeys",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "API key callers cannot manage passkeys - AUTH_025",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ResponseEntity<RestApiResponse<List<PasskeyResponse>>> list() {
        CurrentUser current = requireWebSession();
        return ResponseEntity.ok(RestApiResponse.ok(managementService.list(current.id())));
    }

    @Operation(summary = "Rename a passkey")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Passkey renamed",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Passkey not found - AUTH_035",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "429",
                        description = "Too many requests - RATE_LIMIT_001",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @SecurityRequirement(name = "bearerAuth")
    @RateLimit(type = RateLimitType.USER, limit = 20, windowSeconds = 3600)
    @PatchMapping("/{id}")
    public ResponseEntity<RestApiResponse<PasskeyResponse>> rename(
            @PathVariable UUID id, @Valid @RequestBody PasskeyRenameRequest request) {
        CurrentUser current = requireWebSession();
        return ResponseEntity.ok(
                RestApiResponse.ok(managementService.rename(current.id(), id, request.name())));
    }

    @Operation(summary = "Delete a passkey")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "204", description = "Passkey deleted"),
                @ApiResponse(
                        responseCode = "403",
                        description = "Recent authentication required - AUTH_031",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Passkey not found - AUTH_035",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Deleting the last login credential - AUTH_018",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "429",
                        description = "Too many requests - RATE_LIMIT_001",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @SecurityRequirement(name = "bearerAuth")
    @RateLimit(type = RateLimitType.USER, limit = 10, windowSeconds = 3600)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        CurrentUser current = requireWebSession();
        managementService.delete(current.id(), id, current.sessionId());
        return ResponseEntity.noContent().build();
    }

    private CurrentUser requireWebSession() {
        return WebSessionGuard.requireWebSession(currentUserProvider.getActiveUserRequired());
    }
}
