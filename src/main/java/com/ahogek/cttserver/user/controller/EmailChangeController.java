package com.ahogek.cttserver.user.controller;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.common.response.EmptyResponse;
import com.ahogek.cttserver.common.response.ErrorResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;
import com.ahogek.cttserver.common.utils.IpUtils;
import com.ahogek.cttserver.user.dto.EmailChangeConfirmRequest;
import com.ahogek.cttserver.user.dto.EmailChangeRequest;
import com.ahogek.cttserver.user.dto.EmailStatusResponse;
import com.ahogek.cttserver.user.service.EmailChangeService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
 * REST controller for email change operations.
 *
 * <p>Provides endpoints for requesting, confirming, cancelling email changes, and checking the
 * current email status. The change flow is two-step: {@code POST /change-request} sends a
 * verification token to the new address; {@code POST /change-confirm} applies the change once the
 * user clicks the link.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-03
 */
@Tag(name = "Email Change", description = "Email address change management")
@RestController
@RequestMapping("/api/v1/users/me/email")
public class EmailChangeController {

    private final EmailChangeService emailChangeService;
    private final CurrentUserProvider currentUserProvider;

    public EmailChangeController(
            EmailChangeService emailChangeService, CurrentUserProvider currentUserProvider) {
        this.emailChangeService = emailChangeService;
        this.currentUserProvider = currentUserProvider;
    }

    @Operation(
            summary = "Request email change",
            description =
                    """
                    Initiates an email change by sending a verification email to the new address. \
                    Users with a password set must include it in the request body for verification; \
                    OAuth-only users may omit it. Any previously pending CHANGE_EMAIL token for the \
                    same user is cancelled before a new one is issued.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Email change request accepted",
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
                                                                  "timestamp": "2026-07-03T10:00:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "403",
                        description =
                                "Password verification required - USER_013 when the user has a password"
                                        + " but the request body omitted it",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "passwordRequired",
                                                        summary = "Password required for change",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "USER_013",
                                                                  "message": "Password verification required",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 403,
                                                                  "timestamp": "2026-07-03T10:00:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "404",
                        description = "User not found",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "userNotFound",
                                                        summary = "User no longer exists",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "USER_004",
                                                                  "message": "User not found",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 404,
                                                                  "timestamp": "2026-07-03T10:00:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Email already registered or email change already pending",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples = {
                                            @ExampleObject(
                                                    name = "emailExists",
                                                    summary = "Email already registered",
                                                    value =
                                                            """
                                                            {
                                                              "code": "USER_001",
                                                              "message": "Email is already registered",
                                                              "details": [],
                                                              "traceId": "abc-123",
                                                              "httpStatus": 409,
                                                              "timestamp": "2026-07-03T10:00:00Z"
                                                            }
                                                            """),
                                            @ExampleObject(
                                                    name = "alreadyPending",
                                                    summary = "Email change already pending",
                                                    value =
                                                            """
                                                            {
                                                              "code": "USER_009",
                                                              "message": "Email change already pending",
                                                              "details": [],
                                                              "traceId": "abc-123",
                                                              "httpStatus": 409,
                                                              "timestamp": "2026-07-03T10:00:00Z"
                                                            }
                                                            """)
                                        }))
            })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/change-request")
    public ResponseEntity<RestApiResponse<EmptyResponse>> requestEmailChange(
            @Valid @RequestBody EmailChangeRequest request, HttpServletRequest httpRequest) {
        UUID userId = currentUserProvider.getCurrentUserRequired().id();
        String ip = IpUtils.getRealIp(httpRequest);
        String userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT);
        EmptyResponse response =
                emailChangeService.requestEmailChange(
                        userId, request.newEmail(), request.password(), ip, userAgent);
        return ResponseEntity.ok(RestApiResponse.ok(response));
    }

    @Operation(
            summary = "Confirm email change",
            description =
                    """
                    Confirms an email change using the raw verification token from the email link. \
                    Tokens are SHA-256 hashed at rest and valid for 1 hour. After 5 failed \
                    verification attempts the token is permanently locked. \
                    On success, the user's email is updated, emailVerified is reset to false, \
                    and any other pending CHANGE_EMAIL tokens are cancelled.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Email changed successfully",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description =
                                "Invalid token - USER_011 covers missing, wrong-purpose, or"
                                        + " no-longer-valid tokens",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "invalidToken",
                                                        summary = "Invalid or wrong-purpose token",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "USER_011",
                                                                  "message": "Invalid email change token",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 400,
                                                                  "timestamp": "2026-07-03T10:00:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "401",
                        description =
                                "Invalid password supplied during request (USER_014) - returned only"
                                        + " if the token itself is valid but a related path required"
                                        + " password re-verification",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "invalidPassword",
                                                        summary = "Wrong password",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "USER_014",
                                                                  "message": "Invalid password",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 401,
                                                                  "timestamp": "2026-07-03T10:00:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "404",
                        description = "User not found for the supplied token",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "userNotFound",
                                                        summary = "User no longer exists",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "USER_004",
                                                                  "message": "User not found",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 404,
                                                                  "timestamp": "2026-07-03T10:00:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "409",
                        description = "New email already taken by another user at confirm time",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "emailTaken",
                                                        summary = "Email already registered",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "USER_001",
                                                                  "message": "Email is already registered",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 409,
                                                                  "timestamp": "2026-07-03T10:00:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "410",
                        description = "Token expired",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "expired",
                                                        summary = "Token expired",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "USER_010",
                                                                  "message": "Email change token expired",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 410,
                                                                  "timestamp": "2026-07-03T10:00:00Z"
                                                                }
                                                                """)))
            })
    @PostMapping("/change-confirm")
    public ResponseEntity<RestApiResponse<EmptyResponse>> confirmEmailChange(
            @Valid @RequestBody EmailChangeConfirmRequest request, HttpServletRequest httpRequest) {
        String ip = IpUtils.getRealIp(httpRequest);
        String userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT);
        emailChangeService.confirmEmailChange(request.token(), ip, userAgent);
        return ResponseEntity.ok(RestApiResponse.ok(EmptyResponse.ok()));
    }

    @Operation(
            summary = "Cancel pending email change",
            description =
                    """
                    Cancels any pending CHANGE_EMAIL token for the authenticated user. \
                    Idempotent — cancelling when no token is pending is a successful no-op and \
                    emits no audit event.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Email change cancelled (no-op when nothing was pending)",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized - missing or invalid JWT",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/change-request")
    public ResponseEntity<RestApiResponse<EmptyResponse>> cancelEmailChange() {
        UUID userId = currentUserProvider.getCurrentUserRequired().id();
        emailChangeService.cancelEmailChange(userId);
        return ResponseEntity.ok(RestApiResponse.ok(EmptyResponse.ok()));
    }

    @Operation(
            summary = "Get email status",
            description =
                    """
                    Returns the authenticated user's current email address, its verification status, \
                    and the details of any pending CHANGE_EMAIL request (when present).
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Email status retrieved successfully",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized - missing or invalid JWT",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "User not found",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/status")
    public ResponseEntity<RestApiResponse<EmailStatusResponse>> getEmailStatus() {
        UUID userId = currentUserProvider.getCurrentUserRequired().id();
        EmailStatusResponse status = emailChangeService.getEmailStatus(userId);
        return ResponseEntity.ok(RestApiResponse.ok(status));
    }
}
