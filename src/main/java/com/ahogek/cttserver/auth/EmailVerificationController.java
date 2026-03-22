package com.ahogek.cttserver.auth;

import com.ahogek.cttserver.auth.dto.ResendVerificationRequest;
import com.ahogek.cttserver.auth.service.EmailVerificationService;
import com.ahogek.cttserver.common.ratelimit.RateLimit;
import com.ahogek.cttserver.common.ratelimit.RateLimitType;
import com.ahogek.cttserver.common.response.ApiResponse;
import com.ahogek.cttserver.common.response.EmptyResponse;
import com.ahogek.cttserver.common.security.annotation.PublicApi;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    @PublicApi(reason = "Email verification endpoint - Tier 1 public API")
    @GetMapping("/verify-email")
    public ResponseEntity<ApiResponse<EmptyResponse>> verifyEmail(
            @RequestParam("token") String token) {

        verificationService.verify(token);

        return ResponseEntity.ok(ApiResponse.ok(EmptyResponse.ok("Email verified successfully")));
    }

    /**
     * Resend verification email endpoint.
     *
     * <p>Generates a new verification token and sends email to user. Requirements:
     *
     * <ul>
     *   <li>User must exist with provided email
     *   <li>User status must be PENDING_VERIFICATION (not already verified)
     *   <li>Rate limited: 3 requests per 5 minutes per email
     * </ul>
     *
     * <p>Old valid tokens are revoked before generating new one.
     *
     * @param request the resend request with email address
     * @return success response
     */
    @PublicApi(reason = "Resend verification email - Tier 1 public API")
    @RateLimit(
            type = RateLimitType.EMAIL,
            keyExpression = "#request.email",
            limit = 3,
            windowSeconds = 300)
    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<EmptyResponse>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {

        verificationService.resendVerificationEmail(request.email());

        return ResponseEntity.ok(ApiResponse.ok(EmptyResponse.ok("Verification email sent")));
    }
}
