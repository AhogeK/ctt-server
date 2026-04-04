package com.ahogek.cttserver.auth;

import com.ahogek.cttserver.auth.dto.ResendVerificationRequest;
import com.ahogek.cttserver.auth.service.EmailVerificationService;
import com.ahogek.cttserver.common.ratelimit.RateLimit;
import com.ahogek.cttserver.common.ratelimit.RateLimitType;
import com.ahogek.cttserver.common.response.EmptyResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;
import com.ahogek.cttserver.common.security.annotation.PublicApi;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Email verification REST controller.
 *
 * <p>Thin controller responsible only for:
 *
 * <ul>
 *   <li>Protocol conversion (HTTP to Java objects)
 *   <li>DTO syntax validation via {@code @Valid}
 *   <li>Delegating to application service layer
 * </ul>
 *
 * <p>All business logic and domain rules are handled by {@link EmailVerificationService}.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-22
 */
@Tag(
        name = "Email Verification Controller",
        description =
                "User email verification endpoints. Handles email verification token validation, "
                        + "user status activation, and verification email resending. "
                        + "All endpoints are public (no authentication required) with rate limiting on resend operations.")
@RestController
@RequestMapping("/api/v1/auth")
public class EmailVerificationController {

    private final EmailVerificationService verificationService;

    public EmailVerificationController(EmailVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    /**
     * Email verification endpoint.
     *
     * <p>Verifies user's email address using the provided token. Token is validated for:
     *
     * <ul>
     *   <li>Existence in database
     *   <li>Not expired (within 24 hours)
     *   <li>Not consumed (one-time use)
     *   <li>Not revoked (admin action or security event)
     * </ul>
     *
     * <p>On success: user status changes to ACTIVE, token is consumed, all other tokens for user
     * are revoked.
     *
     * @param token the verification token from email link
     * @return success response
     */
    @Operation(
            summary = "Verify email address",
            description =
                    "Verifies user's email address using the provided token. "
                            + "Token validation: existence, not expired (24h), not consumed, not revoked. "
                            + "On success: user status → ACTIVE, token consumed, all other tokens revoked.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "Email verified successfully"),
                @ApiResponse(
                        responseCode = "400",
                        description =
                                "Invalid token - AUTH_004: Token not found, expired, consumed, or revoked"),
                @ApiResponse(
                        responseCode = "409",
                        description =
                                "User already verified - USER_002: User status is not PENDING_VERIFICATION")
            })
    @PublicApi(reason = "Email verification endpoint - Tier 1 public API")
    @GetMapping("/verify-email")
    public ResponseEntity<RestApiResponse<EmptyResponse>> verifyEmail(
            @Parameter(description = "Verification token from email link", required = true)
                    @RequestParam("token")
                    String token) {

        verificationService.verify(token);

        return ResponseEntity.ok(
                RestApiResponse.ok(EmptyResponse.ok("Email verified successfully")));
    }

    /**
     * Resend verification email endpoint.
     *
     * <p>Generates a new verification token and sends email to user. Requirements:
     *
     * <ul>
     *   <li>User must exist with provided email
     *   <li>User status must be PENDING_VERIFICATION (not already verified)
     *   <li>Rate limited: 3 requests per 1 minute per email
     * </ul>
     *
     * <p>Old valid tokens are revoked before generating new one.
     *
     * @param request the resend request with email address
     * @return success response
     */
    @Operation(
            summary = "Resend verification email",
            description =
                    "Generates a new verification token and sends email. "
                            + "Requirements: user exists, status PENDING_VERIFICATION. "
                            + "Rate limited: 3 requests/minute per email. Old valid tokens are revoked.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Verification email sent successfully"),
                @ApiResponse(
                        responseCode = "400",
                        description =
                                "Validation error - COMMON_003: Invalid email format or blank email"),
                @ApiResponse(
                        responseCode = "404",
                        description = "User not found - USER_003: No user with provided email"),
                @ApiResponse(
                        responseCode = "409",
                        description =
                                "User already verified - USER_002: User status is not PENDING_VERIFICATION"),
                @ApiResponse(
                        responseCode = "429",
                        description =
                                "Rate limit exceeded - COMMON_002: Too many resend requests (3/minute per email)")
            })
    @PublicApi(reason = "Resend verification email - Tier 1 public API")
    @RateLimit(
            type = RateLimitType.EMAIL,
            keyExpression = "#request.email",
            limit = 3,
            windowSeconds = 60)
    @PostMapping("/resend-verification")
    public ResponseEntity<RestApiResponse<EmptyResponse>> resendVerification(
            @Parameter(description = "Resend request with email address", required = true)
                    @Valid
                    @RequestBody
                    ResendVerificationRequest request) {

        verificationService.resendVerificationEmail(request.email());

        return ResponseEntity.ok(RestApiResponse.ok(EmptyResponse.ok("Verification email sent")));
    }
}
