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
import com.ahogek.cttserver.common.exception.ValidationException;
import com.ahogek.cttserver.common.utils.TokenUtils;
import com.ahogek.cttserver.mail.service.MailOutboxService;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class EmailVerificationService {

    private static final Duration TOKEN_TTL = Duration.ofHours(24);

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final MailOutboxService mailOutboxService;
    private final AuditLogService auditLog;

    public EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            UserRepository userRepository,
            MailOutboxService mailOutboxService,
            AuditLogService auditLog) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.mailOutboxService = mailOutboxService;
        this.auditLog = auditLog;
    }

    /**
     * Holds a verification token entity and its raw (unhashed) value.
     *
     * @param token the persisted token entity
     * @param rawToken the raw token string (transient, not stored)
     */
    public record TokenPair(EmailVerificationToken token, String rawToken) {}

    /**
     * Creates and persists a verification token for a user.
     *
     * @param userId the user ID
     * @param ttl time-to-live for the token
     * @return TokenPair containing the persisted token and raw token
     */
    public TokenPair createVerificationToken(UUID userId, Duration ttl) {
        String rawToken = TokenUtils.generateRawToken();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserId(userId);
        token.setTokenHash(TokenUtils.hashToken(rawToken));
        token.setExpiresAt(Instant.now().plus(ttl));
        tokenRepository.save(token);
        return new TokenPair(token, rawToken);
    }

    @Transactional
    public void verify(String rawToken) {
        String tokenHash = TokenUtils.hashToken(rawToken);

        EmailVerificationToken token =
                tokenRepository
                        .findByTokenHash(tokenHash)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.MAIL_006,
                                                "Verification token not found"));

        TokenStatus status = token.determineStatus();
        if (status == TokenStatus.EXPIRED) {
            throw new ValidationException(ErrorCode.MAIL_005, "Verification token has expired");
        }
        if (status == TokenStatus.CONSUMED) {
            throw new ValidationException(
                    ErrorCode.MAIL_006, "Verification token has already been used");
        }
        if (status == TokenStatus.REVOKED) {
            throw new ValidationException(
                    ErrorCode.MAIL_006, "Verification token has been revoked");
        }
        if (status == TokenStatus.UNAVAILABLE) {
            throw new ValidationException(ErrorCode.MAIL_006, "Verification token is unavailable");
        }

        User user =
                userRepository
                        .findById(token.getUserId())
                        .orElseThrow(
                                () -> new NotFoundException(ErrorCode.USER_004, "User not found"));

        token.consume();
        user.verifyEmail();

        revokeOtherTokens(token);

        tokenRepository.save(token);
        userRepository.save(user);

        auditLog.log(
                user.getId(),
                AuditAction.EMAIL_VERIFICATION_SUCCESS,
                ResourceType.EMAIL_VERIFICATION,
                token.getId().toString(),
                SecuritySeverity.INFO,
                AuditDetails.empty());
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        User user =
                userRepository
                        .findByEmailIgnoreCase(email)
                        .orElseThrow(
                                () -> new NotFoundException(ErrorCode.USER_004, "User not found"));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new ValidationException(ErrorCode.USER_001, "Email is already verified");
        }

        revokeExistingValidTokens(user.getId());

        TokenPair tokenPair = createVerificationToken(user.getId(), TOKEN_TTL);

        mailOutboxService.enqueueVerificationEmail(
                user.getId(), user.getDisplayName(), user.getEmail(), tokenPair.rawToken());
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
