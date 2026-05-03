package com.ahogek.cttserver.auth.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.audit.model.AuditDetails;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.entity.EmailVerificationToken;
import com.ahogek.cttserver.auth.enums.TokenStatus;
import com.ahogek.cttserver.auth.repository.EmailVerificationTokenRepository;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.common.response.EmptyResponse;
import com.ahogek.cttserver.common.utils.TokenUtils;
import com.ahogek.cttserver.common.utils.TokenUtils.EmailVerificationTokenPair;
import com.ahogek.cttserver.mail.service.MailOutboxService;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;
import com.ahogek.cttserver.user.validator.UserValidator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Email verification service handling user email verification workflow.
 *
 * <p>This service manages verification token generation, validation, and email verification
 * completion. It ensures only valid, unexpired tokens can verify user email addresses.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-22
 */
@Service
public class EmailVerificationService {

    private static final Duration TOKEN_TTL = Duration.ofHours(24);

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final MailOutboxService mailOutboxService;
    private final AuditLogService auditLog;
    private final UserValidator userValidator;

    public EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            UserRepository userRepository,
            MailOutboxService mailOutboxService,
            AuditLogService auditLog,
            UserValidator userValidator) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.mailOutboxService = mailOutboxService;
        this.auditLog = auditLog;
        this.userValidator = userValidator;
    }

    @Transactional
    public void verify(String rawToken) {
        String tokenHash = TokenUtils.hashToken(rawToken);

        EmailVerificationToken token =
                tokenRepository
                        .findByTokenHash(tokenHash)
                        .orElseThrow(
                                () ->
                                        new UnauthorizedException(
                                                ErrorCode.MAIL_006,
                                                "Verification token not found"));

        TokenStatus status = token.determineStatus();
        if (status == TokenStatus.EXPIRED) {
            throw new UnauthorizedException(ErrorCode.MAIL_005, "Verification token has expired");
        }
        if (status == TokenStatus.CONSUMED) {
            throw new UnauthorizedException(
                    ErrorCode.MAIL_006, "Verification token has already been used");
        }
        if (status == TokenStatus.REVOKED) {
            throw new UnauthorizedException(
                    ErrorCode.MAIL_006, "Verification token has been revoked");
        }
        if (status == TokenStatus.UNAVAILABLE) {
            throw new UnauthorizedException(
                    ErrorCode.MAIL_006, "Verification token is unavailable");
        }

        User user =
                userRepository
                        .findById(token.getUserId())
                        .orElseThrow(
                                () -> new NotFoundException(ErrorCode.USER_004, "User not found"));

        token.consume();
        user.verifyEmail();

        revokeOtherTokens(token);

        // JPA Dirty Checking auto-persists state changes on transaction commit
        // No explicit save() needed for token and user

        auditLog.log(
                user.getId(),
                AuditAction.EMAIL_VERIFICATION_SUCCESS,
                ResourceType.EMAIL_VERIFICATION,
                token.getId().toString(),
                SecuritySeverity.INFO,
                AuditDetails.empty());
    }

    @Transactional
    public EmptyResponse resendVerificationEmail(String email) {
        User user =
                userRepository
                        .findByEmailIgnoreCase(email)
                        .orElseThrow(
                                () -> new NotFoundException(ErrorCode.USER_004, "User not found"));

        userValidator.assertCanVerifyEmail(user);

        revokeExistingValidTokens(user.getId());

        EmailVerificationTokenPair tokenPair =
                TokenUtils.createVerificationToken(
                        user.getId(), user.getEmail(), TOKEN_TTL, tokenRepository);

        EmptyResponse response =
                mailOutboxService.enqueueVerificationEmail(
                        user.getId(), user.getDisplayName(), user.getEmail(), tokenPair.rawToken());

        auditLog.log(
                user.getId(),
                AuditAction.EMAIL_VERIFICATION_SENT,
                ResourceType.EMAIL_VERIFICATION,
                user.getId().toString(),
                SecuritySeverity.INFO,
                AuditDetails.empty());

        return response;
    }

    private void revokeOtherTokens(EmailVerificationToken consumedToken) {
        tokenRepository.findValidTokensByUserId(consumedToken.getUserId(), Instant.now()).stream()
                .filter(t -> !t.getId().equals(consumedToken.getId()))
                .forEach(
                        t -> {
                            t.revoke();
                            tokenRepository.save(t);
                        });
    }

    private void revokeExistingValidTokens(UUID userId) {
        tokenRepository
                .findValidTokensByUserId(userId, Instant.now())
                .forEach(
                        t -> {
                            t.revoke();
                            tokenRepository.save(t);
                        });
    }
}
