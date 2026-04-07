package com.ahogek.cttserver.auth;

import com.ahogek.cttserver.auth.dto.ForgotPasswordRequest;
import com.ahogek.cttserver.auth.dto.LoginRequest;
import com.ahogek.cttserver.auth.dto.LoginResponse;
import com.ahogek.cttserver.auth.dto.PasswordResetRequest;
import com.ahogek.cttserver.auth.dto.RefreshTokenRequest;
import com.ahogek.cttserver.auth.dto.ResetPasswordRequest;
import com.ahogek.cttserver.auth.dto.UserRegisterRequest;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.auth.service.LogoutService;
import com.ahogek.cttserver.auth.service.PasswordResetService;
import com.ahogek.cttserver.auth.service.TokenRefreshService;
import com.ahogek.cttserver.auth.service.UserLoginService;
import com.ahogek.cttserver.common.ratelimit.RateLimit;
import com.ahogek.cttserver.common.ratelimit.RateLimitType;
import com.ahogek.cttserver.common.response.EmptyResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;
import com.ahogek.cttserver.common.security.annotation.PublicApi;
import com.ahogek.cttserver.common.utils.IpUtils;
import com.ahogek.cttserver.user.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
    private final LogoutService logoutService;
    private final PasswordResetService passwordResetService;

    public AuthController(
            UserService userService,
            UserLoginService userLoginService,
            TokenRefreshService tokenRefreshService,
            LogoutService logoutService,
            PasswordResetService passwordResetService) {
        this.userService = userService;
        this.userLoginService = userLoginService;
        this.tokenRefreshService = tokenRefreshService;
        this.logoutService = logoutService;
        this.passwordResetService = passwordResetService;
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
    @RateLimit(limit = 60, windowSeconds = 3600)
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
    @RateLimit(limit = 30, windowSeconds = 3600)
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
    @RateLimit(limit = 120, windowSeconds = 3600)
    @PostMapping("/refresh")
    public ResponseEntity<RestApiResponse<LoginResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {

        String ip = IpUtils.getRealIp(httpRequest);
        String userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT);

        LoginResponse response = tokenRefreshService.refresh(request.refreshToken(), ip, userAgent);

        return ResponseEntity.ok(RestApiResponse.ok(response));
    }

    /**
     * Global logout (Kill Switch). Revokes all active sessions for current user.
     *
     * <p>Protected endpoint: requires valid JWT. Uses USER_ID rate limiting to prevent abuse.
     *
     * @param currentUser current authenticated user (auto-injected by Spring Security)
     * @return success response
     */
    @Operation(
            summary = "Global logout (Kill Switch)",
            description =
                    "Revokes all active sessions for the current authenticated user. "
                            + "Protected endpoint requiring valid JWT authentication. "
                            + "Uses USER_ID rate limiting (5/minute) to prevent abuse.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "All sessions revoked successfully"),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized - AUTH_002: Invalid or expired JWT token"),
                @ApiResponse(
                        responseCode = "429",
                        description = "Rate limit exceeded - COMMON_002: Too many logout requests")
            })
    @SecurityRequirement(name = "bearerAuth")
    @RateLimit(type = RateLimitType.USER, limit = 5, windowSeconds = 60)
    @PostMapping("/logout-all")
    public ResponseEntity<RestApiResponse<EmptyResponse>> logoutAll(
            @AuthenticationPrincipal CurrentUser currentUser) {

        logoutService.logoutAll(currentUser.id());

        return ResponseEntity.ok(RestApiResponse.ok());
    }

    /**
     * Password reset request endpoint.
     *
     * <p>Anti-enumeration protection: Always returns 200 OK regardless of whether the email exists
     * or the user is active. This prevents attackers from determining which emails are registered.
     *
     * <p>Security mechanisms:
     *
     * <ul>
     *   <li>Rate limiting: 3 requests per 10 minutes per email address
     *   <li>Input validation: {@code @Valid} triggers JSR-380 validation on DTO
     *   <li>Audit logging: All requests logged (PASSWORD_RESET_REQUESTED or
     *       PASSWORD_RESET_EMAIL_NOT_FOUND)
     *   <li>IP/User-Agent extraction: Automatically handled by RequestContext
     * </ul>
     *
     * @param request the password reset request containing email (validated)
     * @param httpRequest the HTTP request for IP/User-Agent extraction
     * @return success response (always 200 OK for anti-enumeration)
     */
    @Operation(
            summary = "Request password reset",
            description =
                    "Password reset request endpoint with anti-enumeration protection. "
                            + "Always returns 200 OK regardless of email existence to prevent email enumeration attacks. "
                            + "Security mechanisms include rate limiting (3/10min per email), input validation, audit logging, "
                            + "and automatic IP/User-Agent extraction")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description =
                                "Password reset request processed - if email exists and user is active, a reset link will be sent"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error - COMMON_003: Invalid email format")
            })
    @PublicApi(reason = "Password reset request endpoint - Tier 1 public API")
    @RateLimit(
            type = RateLimitType.EMAIL,
            keyExpression = "#request.email",
            limit = 3,
            windowSeconds = 600)
    @PostMapping("/password-reset/request")
    public ResponseEntity<RestApiResponse<EmptyResponse>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request, HttpServletRequest httpRequest) {

        String ip = IpUtils.getRealIp(httpRequest);
        String userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT);

        passwordResetService.requestReset(request.email(), ip, userAgent);

        return ResponseEntity.ok(
                RestApiResponse.ok(
                        EmptyResponse.ok(
                                "If your email address exists in our database, you will receive a password recovery link at your email address in a few minutes.")));
    }

    /**
     * Forgot password request endpoint.
     *
     * <p>Security features:
     *
     * <ul>
     *   <li>Anti-enumeration: Same response for existing/non-existing emails
     *   <li>IP-based rate limiting: 30 requests per hour per IP
     * </ul>
     *
     * @param request the forgot password request containing email (validated)
     * @param httpRequest the HTTP request for IP/User-Agent extraction
     * @return success response (always 200 OK for anti-enumeration)
     */
    @Operation(
            summary = "Forgot password",
            description =
                    "Forgot password request endpoint with anti-enumeration protection. "
                            + "Always returns 200 OK regardless of email existence to prevent email enumeration attacks. "
                            + "Security mechanisms include IP-based rate limiting (30/hour per IP), input validation, audit logging, "
                            + "and automatic IP/User-Agent extraction")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description =
                                "Forgot password request processed - if email exists and user is active, a reset link will be sent"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error - COMMON_003: Invalid email format"),
                @ApiResponse(
                        responseCode = "429",
                        description =
                                "Rate limit exceeded - COMMON_002: Too many requests from this IP (30/hour)")
            })
    @PublicApi(reason = "Forgot password request endpoint - Tier 1 public API")
    @RateLimit(type = RateLimitType.IP, limit = 30, windowSeconds = 3600)
    @PostMapping("/forgot-password")
    public ResponseEntity<RestApiResponse<EmptyResponse>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {

        String ip = IpUtils.getRealIp(httpRequest);
        String userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT);

        passwordResetService.requestReset(request.email(), ip, userAgent);

        return ResponseEntity.ok(
                RestApiResponse.ok(
                        EmptyResponse.ok(
                                "If your email address exists in our database, you will receive a password recovery link at your email address in a few minutes.")));
    }

    /**
     * Confirm and execute password reset.
     *
     * <p>Security: IP-based rate limiting to prevent CPU exhaustion attacks (BCrypt is expensive).
     *
     * @param request the reset password request containing token and new password (validated)
     * @param httpRequest the HTTP request for IP/User-Agent extraction
     * @return success response
     */
    @Operation(
            summary = "Confirm password reset",
            description =
                    "Password reset confirmation endpoint that validates token and updates password. "
                            + "Security mechanisms include IP-based rate limiting (15/10min per IP), input validation, "
                            + "token validation, password strength validation, and automatic account unlock.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description =
                                "Password reset successful - all existing sessions have been terminated"),
                @ApiResponse(
                        responseCode = "400",
                        description =
                                "Validation error - COMMON_003: Invalid token or weak password"),
                @ApiResponse(
                        responseCode = "401",
                        description =
                                "Invalid or expired token - AUTH_003: Token validation failed"),
                @ApiResponse(
                        responseCode = "409",
                        description =
                                "Password conflict - PASSWORD_SAME_AS_OLD: New password cannot be the same as current password"),
                @ApiResponse(
                        responseCode = "429",
                        description =
                                "Rate limit exceeded - RATE_LIMIT_001: Too many requests from this IP (15/10min)")
            })
    @PublicApi(reason = "Password reset confirmation endpoint - Tier 1 public API")
    @RateLimit(type = RateLimitType.IP, limit = 15, windowSeconds = 600)
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<RestApiResponse<EmptyResponse>> confirmPasswordReset(
            @Valid @RequestBody ResetPasswordRequest request, HttpServletRequest httpRequest) {

        String ip = IpUtils.getRealIp(httpRequest);
        String userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT);

        passwordResetService.resetPassword(request, ip, userAgent);

        return ResponseEntity.ok(
                RestApiResponse.ok(
                        EmptyResponse.ok(
                                "Password has been reset successfully. All existing sessions have been terminated.")));
    }
}
