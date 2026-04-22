package com.ahogek.cttserver.auth.oauth.controller;

import com.ahogek.cttserver.auth.dto.LoginResponse;
import com.ahogek.cttserver.auth.oauth.client.GitHubOAuthClient;
import com.ahogek.cttserver.auth.oauth.dto.AuthorizeResponse;
import com.ahogek.cttserver.auth.oauth.enums.OAuthProvider;
import com.ahogek.cttserver.auth.oauth.model.GitHubTokenResponse;
import com.ahogek.cttserver.auth.oauth.model.GitHubUserInfo;
import com.ahogek.cttserver.auth.oauth.model.OAuthStatePayload;
import com.ahogek.cttserver.auth.oauth.service.OAuthLoginOrRegisterService;
import com.ahogek.cttserver.auth.oauth.service.OAuthStateService;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.exception.BusinessException;
import com.ahogek.cttserver.common.ratelimit.RateLimit;
import com.ahogek.cttserver.common.ratelimit.RateLimitType;
import com.ahogek.cttserver.common.response.ErrorResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;
import com.ahogek.cttserver.common.security.annotation.PublicApi;

import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * OAuth callback REST controller.
 *
 * <p>Handles GitHub OAuth authorization initiation and callback processing.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-22
 */
@Tag(name = "OAuth", description = "GitHub OAuth authentication endpoints")
@RestController
@RequestMapping("/api/v1/auth/oauth")
public class OAuthCallbackController {

    private static final Logger log = LoggerFactory.getLogger(OAuthCallbackController.class);

    private final OAuthStateService stateService;
    private final GitHubOAuthClient githubClient;
    private final OAuthLoginOrRegisterService loginOrRegisterService;
    private final SecurityProperties securityProps;

    public OAuthCallbackController(
            OAuthStateService stateService,
            GitHubOAuthClient githubClient,
            OAuthLoginOrRegisterService loginOrRegisterService,
            SecurityProperties securityProps) {
        this.stateService = stateService;
        this.githubClient = githubClient;
        this.loginOrRegisterService = loginOrRegisterService;
        this.securityProps = securityProps;
    }

    @Operation(
            summary = "Initiate OAuth authorization",
            description =
                    """
                    Generates a CSRF-protected state and returns the GitHub authorization URL. \
                    The client should redirect the user to this URL to begin the OAuth flow.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Authorization URL generated successfully",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid provider - COMMON_001: Unsupported OAuth provider",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "invalid-provider",
                                                        summary = "Unsupported OAuth provider",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "COMMON_001",
                                                                  "message": "Invalid request parameters",
                                                                  "details": [
                                                                    {
                                                                      "field": "provider",
                                                                      "message": "Unsupported OAuth provider: google"
                                                                    }
                                                                  ],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 400,
                                                                  "timestamp": "2026-04-22T10:00:00Z"
                                                                }
                                                                """)))
            })
    @PublicApi(reason = "OAuth authorization initiation - redirects user to provider")
    @RateLimit(type = RateLimitType.IP, limit = 30, windowSeconds = 3600)
    @GetMapping("/{provider}/authorize")
    public ResponseEntity<RestApiResponse<AuthorizeResponse>> authorize(
            // Parameter kept for future multi-provider support (currently only GitHub is
            // implemented)
            @SuppressWarnings("unused") @PathVariable OAuthProvider provider) {

        String stateId =
                stateService.generateAndSaveState(
                        new OAuthStatePayload(OAuthStatePayload.Action.LOGIN, null, null));

        String authUrl =
                UriComponentsBuilder.fromUriString("https://github.com/login/oauth/authorize")
                        .queryParam("client_id", securityProps.oauth().github().clientId())
                        .queryParam("scope", securityProps.oauth().github().scope())
                        .queryParam("state", stateId)
                        .toUriString();

        return ResponseEntity.ok(RestApiResponse.ok(new AuthorizeResponse(authUrl)));
    }

    @Operation(
            summary = "Handle OAuth callback",
            description =
                    """
                    Receives the OAuth provider callback, validates state, exchanges code for token, \
                    fetches user info, and performs login or registration. Redirects to frontend \
                    with access/refresh tokens on success, or to error page on failure.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "302",
                        description =
                                "Redirect to frontend with tokens on success, or error page on failure",
                        content = @Content),
                @ApiResponse(
                        responseCode = "403",
                        description =
                                "OAuth state validation failed - AUTH_013: State expired or invalid",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "state-invalid",
                                                        summary = "OAuth state validation failed",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "AUTH_013",
                                                                  "message": "OAuth state validation failed",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 403,
                                                                  "timestamp": "2026-04-22T10:00:00Z"
                                                                }
                                                                """)))
            })
    @PublicApi(reason = "OAuth callback endpoint - receives provider redirect back")
    @RateLimit(type = RateLimitType.IP, limit = 60, windowSeconds = 3600)
    @GetMapping("/{provider}/callback")
    public void callback(
            @PathVariable OAuthProvider provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String errorDescription,
            HttpServletResponse response)
            throws IOException {

        if (error != null && !error.isBlank()) {
            log.warn(
                    "OAuth provider returned error: provider={}, error={}, description={}",
                    provider,
                    error,
                    errorDescription);
            redirectError(response, "OAUTH_PROVIDER_ERROR");
            return;
        }

        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            log.warn("OAuth callback missing required parameters: code={}, state={}", code, state);
            redirectError(response, "MISSING_OAUTH_PARAMS");
            return;
        }

        // IntelliJ taint analysis false positive:
        // 'state' is validated by OAuthStateService (Redis key prefix + UUID format check).
        // objectMapper.readValue() is JSON deserialization, not file system path traversal.
        OAuthStatePayload payload = stateService.consumeState(state);
        if (payload.action() != OAuthStatePayload.Action.LOGIN) {
            log.warn("OAuth state action mismatch: expected LOGIN, got {}", payload.action());
            redirectError(response, "INVALID_STATE_ACTION");
            return;
        }

        GitHubTokenResponse tokenResponse = githubClient.exchangeCodeForToken(code, state);
        GitHubUserInfo userInfo = githubClient.getUserInfo(tokenResponse.accessToken());

        LoginResponse loginResponse =
                loginOrRegisterService.process(provider, tokenResponse.accessToken(), userInfo);

        String redirectUrl =
                UriComponentsBuilder.fromUriString(securityProps.oauth().frontendUrl())
                        .path("/oauth/callback")
                        .queryParam("accessToken", loginResponse.accessToken())
                        .queryParam("refreshToken", loginResponse.refreshToken())
                        .toUriString();

        response.sendRedirect(redirectUrl);
    }

    @ExceptionHandler(BusinessException.class)
    public void handleOAuthCallbackError(BusinessException exception, HttpServletResponse response)
            throws IOException {
        log.warn("OAuth callback business error: code={}", exception.errorCode().name());
        redirectError(response, exception.errorCode().name());
    }

    @ExceptionHandler(Exception.class)
    public void handleOAuthCallbackUnexpectedError(
            Exception exception, HttpServletResponse response) throws IOException {
        log.error("OAuth callback unexpected error", exception);
        redirectError(response, "OAUTH_INTERNAL_ERROR");
    }

    private void redirectError(HttpServletResponse response, String errorCode) throws IOException {
        String redirectUrl =
                UriComponentsBuilder.fromUriString(securityProps.oauth().frontendUrl())
                        .path("/oauth/error")
                        .queryParam("code", errorCode)
                        .toUriString();

        response.sendRedirect(redirectUrl);
    }
}
