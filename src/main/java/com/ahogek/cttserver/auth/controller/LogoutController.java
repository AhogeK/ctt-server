package com.ahogek.cttserver.auth.controller;

import com.ahogek.cttserver.auth.dto.LogoutRequest;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.auth.service.LogoutService;
import com.ahogek.cttserver.auth.util.CookieHelper;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.response.ErrorResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Logout controller for session termination. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "User authentication and session management")
public class LogoutController {

    private final LogoutService logoutService;
    private final SecurityProperties securityProperties;

    public LogoutController(LogoutService logoutService, SecurityProperties securityProperties) {
        this.logoutService = logoutService;
        this.securityProperties = securityProperties;
    }

    private String refreshTokenPath() {
        return securityProperties.cookie().refreshTokenPath();
    }

    /**
     * Logout user and revoke refresh token.
     *
     * @param currentUser current authenticated user (auto-injected by Spring Security)
     * @param request Logout request containing refresh token
     * @param httpResponse HTTP response used to clear authentication cookies
     */
    @Operation(
            summary = "Logout user",
            description = "Revoke refresh token and terminate session (requires authentication)")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Logout successful",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid request - COMMON_003",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "validation-error",
                                                        summary = "Invalid request body",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "COMMON_003",
                                                                  "message": "Invalid request parameters",
                                                                  "details": [
                                                                    {
                                                                      "field": "refreshToken",
                                                                      "message": "Refresh token must not be blank"
                                                                    }
                                                                  ],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 400,
                                                                  "timestamp": "2026-04-10T03:23:12Z"
                                                                }"""))),
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
                                                                  "code": "AUTH_002",
                                                                  "message": "Invalid or expired JWT token",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 401,
                                                                  "timestamp": "2026-04-10T03:23:12Z"
                                                                }""")))
            })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public ResponseEntity<RestApiResponse<Void>> logout(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody LogoutRequest request,
            HttpServletResponse httpResponse) {

        logoutService.logout(currentUser.id(), request.refreshToken());
        CookieHelper.clearCookiesFromResponse(httpResponse, refreshTokenPath());

        return ResponseEntity.ok(RestApiResponse.ok());
    }
}
