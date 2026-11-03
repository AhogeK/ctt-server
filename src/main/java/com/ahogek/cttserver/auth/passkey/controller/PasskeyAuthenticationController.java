package com.ahogek.cttserver.auth.passkey.controller;

import com.ahogek.cttserver.auth.dto.LoginResponse;
import com.ahogek.cttserver.auth.passkey.dto.PasskeyAssertionRequest;
import com.ahogek.cttserver.auth.passkey.service.PasskeyAuthenticationService;
import com.ahogek.cttserver.auth.util.CookieHelper;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.ratelimit.RateLimit;
import com.ahogek.cttserver.common.ratelimit.RateLimitType;
import com.ahogek.cttserver.common.response.ErrorResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;
import com.ahogek.cttserver.common.security.annotation.PublicApi;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Anonymous discoverable-passkey authentication endpoints.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-03
 */
@Tag(name = "Passkey authentication", description = "Discoverable passkey sign-in")
@RestController
@RequestMapping("/api/v1/auth/passkey")
public class PasskeyAuthenticationController {

    private final PasskeyAuthenticationService authenticationService;
    private final SecurityProperties securityProperties;

    public PasskeyAuthenticationController(
            PasskeyAuthenticationService authenticationService,
            SecurityProperties securityProperties) {
        this.authenticationService = authenticationService;
        this.securityProperties = securityProperties;
    }

    @Operation(
            summary = "Create passkey assertion options",
            description = "Anonymous, discoverable assertion options without allowCredentials.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "WebAuthn request options",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class)))
            })
    @PublicApi(reason = "Discoverable passkey sign-in options")
    @RateLimit(type = RateLimitType.IP, limit = 30, windowSeconds = 300)
    @PostMapping("/options")
    public ResponseEntity<RestApiResponse<Map<String, Object>>> options() {
        return ResponseEntity.ok(RestApiResponse.ok(authenticationService.startAssertion()));
    }

    @Operation(
            summary = "Complete passkey sign-in",
            description = "Verifies the assertion and issues the normal login response.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Login successful",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Assertion failed - AUTH_032/AUTH_033",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "429",
                        description = "Too many attempts - RATE_LIMIT_001",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PublicApi(reason = "Discoverable passkey sign-in completion")
    @RateLimit(type = RateLimitType.IP, limit = 20, windowSeconds = 300)
    @PostMapping("/assert")
    public ResponseEntity<RestApiResponse<LoginResponse>> assertPasskey(
            @Valid @RequestBody PasskeyAssertionRequest request, HttpServletResponse httpResponse) {
        LoginResponse response = authenticationService.finishAssertion(request.credential());
        CookieHelper.addCookiesToResponse(
                httpResponse,
                response.accessToken(),
                response.refreshToken(),
                securityProperties.cookie().refreshTokenPath());
        return ResponseEntity.ok()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + response.accessToken())
                .body(RestApiResponse.ok(response));
    }
}
