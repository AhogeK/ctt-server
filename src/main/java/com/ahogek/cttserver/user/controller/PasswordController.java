package com.ahogek.cttserver.user.controller;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.common.ratelimit.RateLimit;
import com.ahogek.cttserver.common.ratelimit.RateLimitType;
import com.ahogek.cttserver.common.response.EmptyResponse;
import com.ahogek.cttserver.common.response.ErrorResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;
import com.ahogek.cttserver.user.dto.ChangePasswordRequest;
import com.ahogek.cttserver.user.dto.SetPasswordRequest;
import com.ahogek.cttserver.user.service.PasswordService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for password management operations.
 *
 * <p>Provides endpoints for authenticated users to manage their password: setting one for the first
 * time (OAuth users without a password) and changing an existing one. Users who already have a
 * password set receive a 409 Conflict on {@code /set}; the {@code /change} endpoint requires the
 * current password and rejects a new password identical to it.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-04
 */
@Tag(name = "Password", description = "Password management for authenticated users")
@RestController
@RequestMapping("/api/v1/users/me/password")
public class PasswordController {

    private final PasswordService passwordService;
    private final CurrentUserProvider currentUserProvider;

    public PasswordController(
            PasswordService passwordService, CurrentUserProvider currentUserProvider) {
        this.passwordService = passwordService;
        this.currentUserProvider = currentUserProvider;
    }

    @Operation(
            summary = "Set password for OAuth users",
            description =
                    """
                    Allows OAuth users to set a password for the first time. \
                    Returns 409 if user already has a password.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Password set successfully",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized - missing or invalid JWT",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "unauthorized",
                                                        summary = "Missing or invalid JWT",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "AUTH_001",
                                                                  "message": "Authentication required",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 401,
                                                                  "timestamp": "2026-07-04T10:00:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Password already set",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "passwordAlreadySet",
                                                        summary = "User already has password",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "USER_015",
                                                                  "message": "Password already set",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 409,
                                                                  "timestamp": "2026-07-04T10:00:00Z"
                                                                }
                                                                """)))
            })
    @SecurityRequirement(name = "bearerAuth")
    @RateLimit(type = RateLimitType.USER, limit = 5, windowSeconds = 60)
    @PostMapping("/set")
    public ResponseEntity<RestApiResponse<EmptyResponse>> setPassword(
            @Valid @RequestBody SetPasswordRequest request) {
        UUID userId = currentUserProvider.getCurrentUserRequired().id();
        passwordService.setPassword(userId, request.newPassword());
        return ResponseEntity.ok(RestApiResponse.ok(EmptyResponse.ok()));
    }

    @Operation(
            summary = "Change password",
            description =
                    """
                    Allows authenticated users to change their password. \
                    Requires the current password and rejects the new password if it matches the current one.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Password changed successfully",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "validationError",
                                                        summary = "Invalid request body",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "COMMON_003",
                                                                  "message": "Validation error",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 400,
                                                                  "timestamp": "2026-07-04T10:00:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Current password does not match",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "invalidCurrentPassword",
                                                        summary = "Current password is incorrect",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "USER_014",
                                                                  "message": "Invalid password",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 401,
                                                                  "timestamp": "2026-07-04T10:00:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "409",
                        description = "New password same as current password",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "passwordSameAsOld",
                                                        summary =
                                                                "New password equals current password",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "PASSWORD_SAME_AS_OLD",
                                                                  "message": "New password cannot be the same as the current password",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 409,
                                                                  "timestamp": "2026-07-04T10:00:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "409",
                        description = "User has no password set",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "passwordNotSet",
                                                        summary = "User has no password to change",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "USER_015",
                                                                  "message": "Password already set",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 409,
                                                                  "timestamp": "2026-07-04T10:00:00Z"
                                                                }
                                                                """)))
            })
    @SecurityRequirement(name = "bearerAuth")
    @RateLimit(type = RateLimitType.USER, limit = 5, windowSeconds = 60)
    @PostMapping("/change")
    public ResponseEntity<RestApiResponse<EmptyResponse>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        UUID userId = currentUserProvider.getCurrentUserRequired().id();
        passwordService.changePassword(userId, request.currentPassword(), request.newPassword());
        return ResponseEntity.ok(RestApiResponse.ok(EmptyResponse.ok()));
    }
}
