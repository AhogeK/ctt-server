package com.ahogek.cttserver.auth.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.dto.ResetPasswordRequest;
import com.ahogek.cttserver.auth.entity.PasswordResetToken;
import com.ahogek.cttserver.auth.enums.TokenStatus;
import com.ahogek.cttserver.auth.lockout.LoginAttemptService;
import com.ahogek.cttserver.auth.repository.PasswordResetTokenRepository;
import com.ahogek.cttserver.auth.repository.RefreshTokenRepository;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.common.response.EmptyResponse;
import com.ahogek.cttserver.common.utils.TokenUtils;
import com.ahogek.cttserver.mail.service.MailOutboxService;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Password reset service handling password reset request workflow.
 *
 * <p>This service manages password reset token generation with anti-enumeration protection.
 * Regardless of whether the email exists or the user is active, the response is always identical to
 * prevent email enumeration attacks.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-06
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration TOKEN_TTL = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final MailOutboxService mailOutboxService;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptService loginAttemptService;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            MailOutboxService mailOutboxService,
            AuditLogService auditLogService,
            PasswordEncoder passwordEncoder,
            RefreshTokenRepository refreshTokenRepository,
            LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.mailOutboxService = mailOutboxService;
        this.auditLogService = auditLogService;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginAttemptService = loginAttemptService;
    }

    /**
     * Requests a password reset for the given email address.
     *
     * <p>Anti-enumeration protection: This method always returns successfully, regardless of
     * whether the email exists or the user is active. This prevents attackers from determining
     * which emails are registered in the system.
     *
     * <p>Flow:
     *
     * <ol>
     *   <li>Check if user exists and is ACTIVE (silent failure if not)
     *   <li>Revoke all previous active tokens for this user
     *   <li>Generate new token (64 bytes raw, SHA-256 hashed, 1 hour TTL)
     *   <li>Enqueue password reset email via MailOutboxService
     *   <li>Log audit event (PASSWORD_RESET_REQUESTED or PASSWORD_RESET_EMAIL_NOT_FOUND)
     * </ol>
     *
     * @param email the email address to send reset link to
     * @param ip the client IP address for audit logging
     * @param userAgent the client User-Agent for audit logging
     * @return EmptyResponse indicating success, with idempotentSkip flag if request was
     *     deduplicated
     */
    @Transactional
    public EmptyResponse requestReset(String email, String ip, String userAgent) {
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);

        if (userOpt.isEmpty() || userOpt.get().getStatus() != UserStatus.ACTIVE) {
            auditLogService.logFailure(
                    null,
                    AuditAction.PASSWORD_RESET_EMAIL_NOT_FOUND,
                    ResourceType.UNKNOWN,
                    email,
                    "Email not found or user not active");
            return EmptyResponse.ok(
                    "If your email address exists in our database, you will receive a password recovery link at your email address in a few minutes.",
                    false);
        }

        User user = userOpt.get();
        Instant now = Instant.now();

        tokenRepository.revokeActiveTokensByUserId(user.getId(), now);

        String rawToken = TokenUtils.generateRawToken();
        String tokenHash = TokenUtils.hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setEmail(user.getEmail());
        token.setTokenHash(tokenHash);
        token.setExpiresAt(now.plus(TOKEN_TTL));
        token.setRequestIp(ip);
        token.setUserAgent(userAgent);

        tokenRepository.save(token);

        EmptyResponse mailResponse =
                mailOutboxService.enqueuePasswordResetEmail(
                        user.getId(), user.getDisplayName(), user.getEmail(), rawToken);

        auditLogService.logSuccess(
                user.getId(),
                AuditAction.PASSWORD_RESET_REQUESTED,
                ResourceType.USER,
                user.getId().toString());

        return mailResponse;
    }

    /**
     * Validates token and executes password reset.
     *
     * <p>Security features:
     *
     * <ul>
     *   <li>Optimistic locking prevents concurrent token consumption (TOCTOU attack)
     *   <li>New password cannot match current password
     *   <li>Revokes all active refresh tokens (force re-login)
     *   <li>Auto-unlocks account if locked due to brute-force
     * </ul>
     *
     * @param request reset password request
     * @param ip client IP address
     * @param userAgent client User-Agent
     * @throws UnauthorizedException if token is invalid, expired, or already consumed
     * @throws ConflictException if new password is same as current password
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request, String ip, String userAgent) {
        String tokenHash = TokenUtils.hashToken(request.token());
        PasswordResetToken token =
                tokenRepository
                        .findByTokenHash(tokenHash)
                        .orElseThrow(
                                () ->
                                        new UnauthorizedException(
                                                ErrorCode.AUTH_003,
                                                "Invalid or expired reset token"));

        TokenStatus status = token.determineStatus();
        if (status == TokenStatus.EXPIRED) {
            throw new UnauthorizedException(ErrorCode.AUTH_002, "Reset token has expired");
        }
        if (status != TokenStatus.VALID) {
            throw new UnauthorizedException(ErrorCode.AUTH_003, "Token is no longer valid");
        }

        User user =
                userRepository
                        .findById(token.getUserId())
                        .orElseThrow(
                                () ->
                                        new UnauthorizedException(
                                                ErrorCode.AUTH_003, "User not found"));

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ConflictException(
                    ErrorCode.PASSWORD_SAME_AS_OLD,
                    "New password cannot be the same as the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        if (user.getStatus() == UserStatus.LOCKED) {
            loginAttemptService.recordSuccess(user.getEmail());
            user.reactivate();
            auditLogService.logSuccess(
                    user.getId(),
                    AuditAction.ACCOUNT_UNLOCKED,
                    ResourceType.USER,
                    user.getId().toString());
        }

        token.setConsumedAt(Instant.now());
        tokenRepository.save(token);

        refreshTokenRepository.revokeAllUserTokens(user.getId(), Instant.now());

        auditLogService.logSuccess(
                user.getId(),
                AuditAction.PASSWORD_RESET_COMPLETED,
                ResourceType.USER,
                user.getId().toString());
        log.info("User {} successfully reset their password via email token.", user.getId());
    }
}
