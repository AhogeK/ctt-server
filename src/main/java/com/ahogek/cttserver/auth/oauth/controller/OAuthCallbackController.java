package com.ahogek.cttserver.auth.oauth.controller;

import com.ahogek.cttserver.auth.dto.LoginResponse;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.auth.oauth.client.GitHubOAuthClient;
import com.ahogek.cttserver.auth.oauth.dto.AuthorizeResponse;
import com.ahogek.cttserver.auth.oauth.enums.OAuthProvider;
import com.ahogek.cttserver.auth.oauth.model.GitHubTokenResponse;
import com.ahogek.cttserver.auth.oauth.model.GitHubUserInfo;
import com.ahogek.cttserver.auth.oauth.model.OAuthStatePayload;
import com.ahogek.cttserver.auth.oauth.service.OAuthLoginOrRegisterService;
import com.ahogek.cttserver.auth.oauth.service.OAuthStateService;
import com.ahogek.cttserver.auth.util.CookieHelper;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.context.RequestContext;
import com.ahogek.cttserver.common.context.RequestInfo;
import com.ahogek.cttserver.common.exception.BusinessException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.common.exception.ValidationException;
import com.ahogek.cttserver.common.ratelimit.RateLimit;
import com.ahogek.cttserver.common.ratelimit.RateLimitType;
import com.ahogek.cttserver.common.response.ErrorResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;
import com.ahogek.cttserver.common.security.annotation.PublicApi;

import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.UUID;

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

    private static final String BIND_REDIRECT_PATH = "/settings/profile";
    private static final String LOGIN_REDIRECT_PATH = "/oauth/callback";
    private static final String ERROR_REDIRECT_PATH = "/oauth/error";

    private static final java.util.regex.Pattern STATE_UUID_PATTERN =
            java.util.regex.Pattern.compile(
                    "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

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
                    The client should redirect the user to this URL to begin the OAuth flow. \
                    Use `?action=login` (default, public) for unauthenticated login/registration, \
                    or `?action=bind` (requires JWT) to attach a GitHub account to the \
                    authenticated user without issuing new tokens.
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
                                                                """))),
                @ApiResponse(
                        responseCode = "401",
                        description =
                                "Authentication required for action=bind - AUTH_001: Missing or invalid JWT",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "bind-unauthenticated",
                                                        summary = "Bind requires JWT",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "AUTH_001",
                                                                  "message": "Authentication required for bind action",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 401,
                                                                  "timestamp": "2026-06-28T10:00:00Z"
                                                                }
                                                                """)))
            })
    @PublicApi(reason = "OAuth authorization initiation - redirects user to provider")
    @RateLimit(type = RateLimitType.IP, limit = 30, windowSeconds = 3600)
    @GetMapping("/{provider}/authorize")
    public ResponseEntity<RestApiResponse<AuthorizeResponse>> authorize(
            // Parameter kept for future multi-provider support (currently only GitHub is
            // implemented)
            @SuppressWarnings("unused") @PathVariable OAuthProvider provider,
            @RequestParam(name = "action", defaultValue = "login") String action,
            Authentication authentication) {

        OAuthStatePayload.Action oauthAction = parseAction(action);
        String clientIp = RequestContext.current().map(RequestInfo::clientIp).orElse(null);

        OAuthStatePayload payload = getOAuthStatePayload(authentication, oauthAction, clientIp);
        String stateId = stateService.generateAndSaveState(payload);

        String authUrl =
                UriComponentsBuilder.fromUriString("https://github.com/login/oauth/authorize")
                        .queryParam("client_id", securityProps.oauth().github().clientId())
                        .queryParam("scope", securityProps.oauth().github().scope())
                        .queryParam("state", stateId)
                        .toUriString();

        return ResponseEntity.ok(RestApiResponse.ok(new AuthorizeResponse(authUrl)));
    }

    private static @NonNull OAuthStatePayload getOAuthStatePayload(
            Authentication authentication, OAuthStatePayload.Action oauthAction, String clientIp) {
        UUID currentUserId = null;
        String redirectUrl = null;

        if (oauthAction == OAuthStatePayload.Action.BIND) {
            if (authentication == null
                    || !(authentication.getPrincipal() instanceof CurrentUser cu)) {
                throw new UnauthorizedException(
                        ErrorCode.AUTH_001, "Authentication required for bind action");
            }
            currentUserId = cu.id();
            redirectUrl = BIND_REDIRECT_PATH;
        }

        return new OAuthStatePayload(oauthAction, currentUserId, redirectUrl, clientIp);
    }

    @Operation(
            summary = "Handle OAuth callback",
            description =
                    """
                    Receives the OAuth provider callback, validates state, exchanges code for token, \
                    fetches user info, and performs login, registration, or BIND. Redirects to frontend \
                    with access/refresh tokens on LOGIN success, to the bind redirect URL on BIND \
                    success, or to an error page on failure. The BIND path never issues new tokens; \
                    the caller's browser tokens remain byte-identical.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "302",
                        description =
                                "Redirect to frontend with tokens on LOGIN success, to bind"
                                        + " redirect URL on BIND success, or to error page on failure",
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

        if (!STATE_UUID_PATTERN.matcher(state).matches()) {
            log.warn("OAuth callback state has invalid UUID format: {}", state);
            redirectError(response, "MISSING_OAUTH_PARAMS");
            return;
        }
        OAuthStatePayload payload = stateService.consumeState(state);
        if (payload.action() != OAuthStatePayload.Action.LOGIN
                && payload.action() != OAuthStatePayload.Action.BIND) {
            log.warn("OAuth state action invalid: {}", payload.action());
            redirectError(response, "INVALID_STATE_ACTION");
            return;
        }

        GitHubTokenResponse tokenResponse = githubClient.exchangeCodeForToken(code, state);
        GitHubUserInfo userInfo = githubClient.getUserInfo(tokenResponse.accessToken());

        if (payload.action() == OAuthStatePayload.Action.LOGIN) {
            LoginResponse loginResponse =
                    loginOrRegisterService.process(
                            provider, tokenResponse.accessToken(), userInfo, payload.clientIp());

            CookieHelper.addCookiesToResponse(
                    response,
                    loginResponse.accessToken(),
                    loginResponse.refreshToken(),
                    securityProps.cookie().refreshTokenPath());

            String redirectUrl =
                    UriComponentsBuilder.fromUriString(securityProps.oauth().frontendUrl())
                            .path(LOGIN_REDIRECT_PATH)
                            .queryParam("accessToken", loginResponse.accessToken())
                            .queryParam("refreshToken", loginResponse.refreshToken())
                            .queryParam("termsExpired", loginResponse.termsExpired())
                            .toUriString();

            response.sendRedirect(redirectUrl);
        } else {
            if (payload.currentUserId() == null) {
                log.error("BIND state payload has null currentUserId despite canonical validation");
                throw new ForbiddenException(ErrorCode.AUTH_013);
            }
            try {
                loginOrRegisterService.attachToExistingUser(
                        payload.currentUserId(), provider, tokenResponse.accessToken(), userInfo);

                String redirectUrl = buildBindSuccessRedirect(payload.redirectUrl(), provider);
                response.sendRedirect(redirectUrl);
            } catch (BusinessException e) {
                log.warn("OAuth BIND callback business error: code={}", e.errorCode().name());
                String errorRedirect =
                        buildBindErrorRedirect(
                                payload.redirectUrl(), provider, e.errorCode().name());
                response.sendRedirect(errorRedirect);
            }
        }
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleOAuthBusinessException(
            BusinessException exception, HandlerMethod handlerMethod, HttpServletResponse response)
            throws IOException {
        // authorize endpoint is a JSON API — return JSON error response
        // callback endpoint is browser-facing — redirect to error page
        if (!"callback".equals(handlerMethod.getMethod().getName())) {
            log.warn(
                    "OAuth authorize business error: code={}, message={}",
                    exception.errorCode().name(),
                    exception.getMessage());
            ErrorResponse errorBody =
                    ErrorResponse.of(exception.errorCode(), exception.getMessage());
            return ResponseEntity.status(exception.errorCode().httpStatus()).body(errorBody);
        }
        log.warn("OAuth callback business error: code={}", exception.errorCode().name());
        redirectError(response, exception.errorCode().name());
        return null;
    }

    @ExceptionHandler(Exception.class)
    public void handleOAuthCallbackUnexpectedError(
            Exception exception, HttpServletResponse response) throws IOException {
        log.error("OAuth callback unexpected error", exception);
        redirectError(response, "OAUTH_INTERNAL_ERROR");
    }

    private OAuthStatePayload.Action parseAction(String action) {
        try {
            return OAuthStatePayload.Action.valueOf(action.toUpperCase());
        } catch (IllegalArgumentException _) {
            throw new ValidationException(ErrorCode.COMMON_001, "Unsupported action: " + action);
        }
    }

    private String buildBindSuccessRedirect(String redirectUrl, OAuthProvider provider) {
        String base =
                (redirectUrl != null && !redirectUrl.isBlank()) ? redirectUrl : BIND_REDIRECT_PATH;
        return UriComponentsBuilder.fromUriString(securityProps.oauth().frontendUrl())
                .path(base)
                .queryParam("linked", provider.getValue())
                .toUriString();
    }

    private String buildBindErrorRedirect(
            String redirectUrl, OAuthProvider provider, String errorCode) {
        String base =
                (redirectUrl != null && !redirectUrl.isBlank()) ? redirectUrl : BIND_REDIRECT_PATH;
        return UriComponentsBuilder.fromUriString(securityProps.oauth().frontendUrl())
                .path(base)
                .queryParam("linked", provider.getValue())
                .queryParam("error", errorCode)
                .toUriString();
    }

    private void redirectError(HttpServletResponse response, String errorCode) throws IOException {
        String redirectUrl =
                UriComponentsBuilder.fromUriString(securityProps.oauth().frontendUrl())
                        .path(ERROR_REDIRECT_PATH)
                        .queryParam("code", errorCode)
                        .toUriString();

        response.sendRedirect(redirectUrl);
    }
}
