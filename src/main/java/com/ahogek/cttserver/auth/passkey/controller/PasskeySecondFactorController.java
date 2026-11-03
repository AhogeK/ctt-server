package com.ahogek.cttserver.auth.passkey.controller;

import com.ahogek.cttserver.auth.dto.LoginResponse;
import com.ahogek.cttserver.auth.passkey.dto.PasskeySecondFactorAssertRequest;
import com.ahogek.cttserver.auth.passkey.dto.PasskeySecondFactorOptionsRequest;
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
 * Anonymous passkey completion for an existing two-factor login challenge.
 *
 * <p>This is deliberately separate from the discoverable passkey login: the account is known from
 * the challenge, and the assertion options must carry that account's {@code allowCredentials}.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-03
 */
@Tag(name = "Passkey second factor", description = "Complete a 2FA login with a passkey")
@RestController
@RequestMapping("/api/v1/auth/2fa/passkey")
public class PasskeySecondFactorController {

    private final PasskeyAuthenticationService authenticationService;
    private final SecurityProperties securityProperties;

    public PasskeySecondFactorController(
            PasskeyAuthenticationService authenticationService,
            SecurityProperties securityProperties) {
        this.authenticationService = authenticationService;
        this.securityProperties = securityProperties;
    }

    @Operation(
            summary = "Create passkey options for a second-factor challenge",
            description = "Returns account-bound assertion options for the challenge's account.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "WebAuthn request options",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description =
                                "Challenge invalid or expired, or no passkey registered - AUTH_026/AUTH_033",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PublicApi(reason = "Passkey second-factor options")
    @RateLimit(type = RateLimitType.IP, limit = 20, windowSeconds = 300)
    @PostMapping("/options")
    public ResponseEntity<RestApiResponse<Map<String, Object>>> options(
            @Valid @RequestBody PasskeySecondFactorOptionsRequest request) {
        return ResponseEntity.ok(
                RestApiResponse.ok(
                        authenticationService.startSecondFactor(request.challengeToken())));
    }

    @Operation(
            summary = "Complete a second-factor login with a passkey",
            description =
                    "Verifies the account-bound assertion and issues the normal login response.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Login successful",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Challenge or assertion failed - AUTH_026/AUTH_032/AUTH_033",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "429",
                        description = "Too many attempts - RATE_LIMIT_001",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PublicApi(reason = "Passkey second-factor completion")
    @RateLimit(type = RateLimitType.IP, limit = 10, windowSeconds = 300)
    @PostMapping("/assert")
    public ResponseEntity<RestApiResponse<LoginResponse>> assertPasskey(
            @Valid @RequestBody PasskeySecondFactorAssertRequest request,
            HttpServletResponse httpResponse) {
        LoginResponse response =
                authenticationService.finishSecondFactor(
                        request.challengeToken(), request.credential());
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
