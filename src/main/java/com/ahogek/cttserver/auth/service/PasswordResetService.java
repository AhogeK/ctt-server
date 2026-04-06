package com.ahogek.cttserver.auth.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.entity.PasswordResetToken;
import com.ahogek.cttserver.auth.repository.PasswordResetTokenRepository;
import com.ahogek.cttserver.common.utils.TokenUtils;
import com.ahogek.cttserver.mail.service.MailOutboxService;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;
import com.ahogek.cttserver.user.repository.UserRepository;

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

    private static final Duration TOKEN_TTL = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final MailOutboxService mailOutboxService;
    private final AuditLogService auditLogService;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            MailOutboxService mailOutboxService,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.mailOutboxService = mailOutboxService;
        this.auditLogService = auditLogService;
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
     */
    @Transactional
    public void requestReset(String email, String ip, String userAgent) {
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);

        if (userOpt.isEmpty() || userOpt.get().getStatus() != UserStatus.ACTIVE) {
            auditLogService.logFailure(
                    null,
                    AuditAction.PASSWORD_RESET_EMAIL_NOT_FOUND,
                    ResourceType.UNKNOWN,
                    email,
                    "Email not found or user not active");
            return;
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

        mailOutboxService.enqueuePasswordResetEmail(
                user.getId(), user.getDisplayName(), user.getEmail(), rawToken);

        auditLogService.logSuccess(
                user.getId(),
                AuditAction.PASSWORD_RESET_REQUESTED,
                ResourceType.USER,
                user.getId().toString());
    }
}
