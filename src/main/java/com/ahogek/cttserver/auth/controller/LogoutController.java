package com.ahogek.cttserver.auth.controller;

import com.ahogek.cttserver.auth.dto.LogoutRequest;
import com.ahogek.cttserver.auth.service.LogoutService;
import com.ahogek.cttserver.common.response.RestApiResponse;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
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

    public LogoutController(LogoutService logoutService) {
        this.logoutService = logoutService;
    }

    /**
     * Logout user and revoke refresh token.
     *
     * @param jwt Current authenticated user's JWT token
     * @param request Logout request containing refresh token
     */
    @Operation(
            summary = "Logout user",
            description = "Revoke refresh token and terminate session (requires authentication)")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "Logout successful"),
                @ApiResponse(responseCode = "400", description = "Invalid request - AUTH_003"),
                @ApiResponse(responseCode = "401", description = "Unauthorized - AUTH_001")
            })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public ResponseEntity<RestApiResponse<Void>> logout(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LogoutRequest request) {

        UUID userId = UUID.fromString(jwt.getSubject());
        logoutService.logout(userId, request.refreshToken());

        return ResponseEntity.ok(RestApiResponse.ok());
    }
}
