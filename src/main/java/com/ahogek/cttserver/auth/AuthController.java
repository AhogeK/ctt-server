package com.ahogek.cttserver.auth;

import com.ahogek.cttserver.auth.dto.LoginRequest;
import com.ahogek.cttserver.auth.dto.LoginResponse;
import com.ahogek.cttserver.auth.dto.RefreshTokenRequest;
import com.ahogek.cttserver.auth.dto.UserRegisterRequest;
import com.ahogek.cttserver.auth.service.TokenRefreshService;
import com.ahogek.cttserver.auth.service.UserLoginService;
import com.ahogek.cttserver.common.utils.IpUtils;

import jakarta.servlet.http.HttpServletRequest;
import com.ahogek.cttserver.common.ratelimit.RateLimit;
import com.ahogek.cttserver.common.ratelimit.RateLimitType;
import com.ahogek.cttserver.common.response.EmptyResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;
import com.ahogek.cttserver.common.security.annotation.PublicApi;
import com.ahogek.cttserver.user.service.UserService;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Authentication REST controller.
 *
 * <p>Thin controller responsible only for:
 *
 * <ul>
 *   <li>Protocol conversion (HTTP to Java objects)
 *   <li>DTO syntax validation via {@code @Valid}
 *   <li>Delegating to application service layer
 * </ul>
 *
 * <p>All business logic and domain rules are handled by application services.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
@Tag(
        name = "Authentication",
        description = "User authentication endpoints for registration and login")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final UserLoginService userLoginService;
    private final TokenRefreshService tokenRefreshService;

    public AuthController(
            UserService userService,
            UserLoginService userLoginService,
            TokenRefreshService tokenRefreshService) {
        this.userService = userService;
        this.userLoginService = userLoginService;
        this.tokenRefreshService = tokenRefreshService;
    }

    /**
     * User registration endpoint.
     *
     * <p>Two-layer validation:
     *
     * <ol>
     *   <li>Syntax validation: {@code @Valid} triggers JSR-380 validation on DTO
     *   <li>Semantic validation: {@link UserService} validates domain rules
     * </ol>
     *
     * @param request the registration request (validated)
     * @return success response
     */
    @Operation(
            summary = "Register new user",
            description =
                    "User registration endpoint with two-layer validation: "
                            + "syntax validation via @Valid on DTO, and semantic validation by UserService for domain rules")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "User registered successfully"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error - COMMON_003: Invalid request parameters"),
                @ApiResponse(
                        responseCode = "409",
                        description = "Email already exists - USER_001: Email already registered")
            })
    @PublicApi(reason = "User registration endpoint - Tier 1 public API")
    @RateLimit(type = RateLimitType.IP, limit = 60, windowSeconds = 3600)
    @PostMapping("/register")
    public ResponseEntity<RestApiResponse<EmptyResponse>> register(
            @Valid @RequestBody UserRegisterRequest request) {

        userService.registerUser(request);

        return ResponseEntity.ok(
                RestApiResponse.ok(EmptyResponse.ok("User registered successfully")));
    }

    /**
     * User login endpoint.
     *
     * <p>Authenticates user credentials and returns JWT tokens for session management.
     *
     * <p>Security mechanisms:
     *
     * <ul>
     *   <li>Rate limiting: 30 requests per hour per IP address
     *   <li>Input validation: {@code @Valid} triggers JSR-380 validation on DTO
     *   <li>Audit logging: Login attempts automatically logged via AuditLogService
     *   <li>IP/User-Agent extraction: Automatically handled by RequestContext
     * </ul>
     *
     * @param request the login request containing email and password (validated)
     * @return response containing JWT access token and refresh token
     */
    @Operation(
            summary = "User login",
            description =
                    "User login endpoint that authenticates credentials and returns JWT tokens for session management. "
                            + "Security mechanisms include rate limiting (30/hour per IP), input validation, audit logging, "
                            + "and automatic IP/User-Agent extraction")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description =
                                "Login successful - returns JWT access token and refresh token"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error - COMMON_003: Invalid request parameters"),
                @ApiResponse(
                        responseCode = "401",
                        description =
                                "Invalid credentials - AUTH_001: Authentication failed due to wrong email/password")
            })
    @PublicApi(reason = "User login endpoint - Tier 1 public API")
    @RateLimit(type = RateLimitType.IP, limit = 30, windowSeconds = 3600)
    @PostMapping("/login")
    public ResponseEntity<RestApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        LoginResponse response = userLoginService.login(request);

        return ResponseEntity.ok(RestApiResponse.ok(response));
    }

    /**
     * Token refresh endpoint.
     *
     * <p>Refreshes access token using refresh token with rotation mechanism.
     *
     * <p>Security mechanisms:
     *
     * <ul>
     *   <li>Rate limiting: 120 requests per hour per IP address
     *   <li>Input validation: {@code @Valid} triggers JSR-380 validation on DTO
     *   <li>Token rotation: Old refresh token invalidated, new one issued
     *   <li>IP/User-Agent extraction: Passed to service for audit logging
     * </ul>
     *
     * @param request the refresh token request (validated)
     * @param httpRequest the HTTP request for IP/User-Agent extraction
     * @return response containing new JWT access token and refresh token
     */
    @Operation(
            summary = "Refresh access token",
            description =
                    "Token refresh endpoint that rotates refresh tokens and issues new access tokens. "
                            + "Security mechanisms include rate limiting (120/hour per IP), input validation, "
                            + "token rotation, and automatic IP/User-Agent extraction for audit logging")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description =
                                "Token refresh successful - returns new JWT access token and refresh token"),
                @ApiResponse(
                        responseCode = "400",
                        description =
                                "Syntax validation error - COMMON_003: Blank/null refresh token (checked before business logic)"),
                @ApiResponse(
                        responseCode = "401",
                        description =
                                "Business validation error - AUTH_003: Token invalid/expired/revoked (checked in service layer)")
            })
    @PublicApi(reason = "Token refresh endpoint - Tier 1 public API")
    @RateLimit(type = RateLimitType.IP, limit = 120, windowSeconds = 3600)
    @PostMapping("/refresh")
    public ResponseEntity<RestApiResponse<LoginResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {

        String ip = IpUtils.getRealIp(httpRequest);
        String userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT);

        LoginResponse response = tokenRefreshService.refresh(
                request.refreshToken(),
                ip,
                userAgent
        );

        return ResponseEntity.ok(RestApiResponse.ok(response));
    }
}
