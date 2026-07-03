package com.ahogek.cttserver.user.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.entity.EmailVerificationToken;
import com.ahogek.cttserver.auth.enums.TokenStatus;
import com.ahogek.cttserver.auth.repository.EmailVerificationTokenRepository;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.common.response.EmptyResponse;
import com.ahogek.cttserver.common.utils.TokenUtils;
import com.ahogek.cttserver.mail.service.MailOutboxService;
import com.ahogek.cttserver.user.dto.EmailStatusResponse;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;
import com.ahogek.cttserver.user.validator.UserValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Email change service handling the email change workflow.
 *
 * <p>This service manages email change requests with verification tokens. Users must verify their
 * new email address before the change takes effect.
 *
 * <p>Security features:
 *
 * <ul>
 *   <li>Password verification before allowing email change (if user has password)
 *   <li>Token-based verification with SHA-256 hashing
 *   <li>Brute-force protection (max 5 attempts per token)
 *   <li>Audit logging for all email change operations
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-03
 */
@Service
public class EmailChangeService {

    private static final Logger log = LoggerFactory.getLogger(EmailChangeService.class);
    private static final Duration TOKEN_TTL = Duration.ofHours(1);
    private static final int MAX_ATTEMPTS = 5;
    private static final String USER_NOT_FOUND = "User not found";

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final MailOutboxService mailOutboxService;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;
    private final UserValidator userValidator;

    public EmailChangeService(
            UserRepository userRepository,
            EmailVerificationTokenRepository tokenRepository,
            MailOutboxService mailOutboxService,
            AuditLogService auditLogService,
            PasswordEncoder passwordEncoder,
            UserValidator userValidator) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.mailOutboxService = mailOutboxService;
        this.auditLogService = auditLogService;
        this.passwordEncoder = passwordEncoder;
        this.userValidator = userValidator;
    }

    /**
     * Requests an email change for the authenticated user.
     *
     * <p>Flow:
     *
     * <ol>
     *   <li>Find user by ID
     *   <li>Validate new email is unique
     *   <li>If user has password, verify provided password matches
     *   <li>Cancel any existing pending CHANGE_EMAIL tokens
     *   <li>Generate new token and create EmailVerificationToken with purpose=CHANGE_EMAIL
     *   <li>Enqueue change email verification via MailOutboxService
     *   <li>Audit log EMAIL_CHANGE_REQUESTED
     * </ol>
     *
     * @param userId the authenticated user's ID
     * @param newEmail the new email address to change to
     * @param password the current password for verification (may be null for OAuth users)
     * @param ip the client IP address for audit logging
     * @param userAgent the client User-Agent for audit logging
     * @return EmptyResponse indicating success
     * @throws NotFoundException if user not found
     * @throws ConflictException if new email is already registered
     * @throws UnauthorizedException if password verification fails
     */
    @Transactional
    public EmptyResponse requestEmailChange(
            UUID userId, String newEmail, String password, String ip, String userAgent) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> new NotFoundException(ErrorCode.USER_004, USER_NOT_FOUND));

        userValidator.assertEmailUnique(newEmail);

        if (user.getPasswordHash() != null) {
            if (password == null || password.isBlank()) {
                throw new UnauthorizedException(
                        ErrorCode.USER_013, "Password verification required");
            }
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new UnauthorizedException(ErrorCode.USER_014, "Invalid password");
            }
        }

        Instant now = Instant.now();
        tokenRepository.cancelPendingChangeEmailTokensByUserId(userId, now);

        String rawToken = TokenUtils.generateRawToken();
        String tokenHash = TokenUtils.hashToken(rawToken);

        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserId(userId);
        token.setEmail(newEmail);
        token.setOldEmail(user.getEmail());
        token.setPurpose(EmailVerificationToken.PURPOSE_CHANGE_EMAIL);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(now.plus(TOKEN_TTL));
        token.setRequestIp(ip);
        token.setUserAgent(userAgent);

        tokenRepository.save(token);

        EmptyResponse mailResponse =
                mailOutboxService.enqueueChangeEmailVerification(
                        userId, user.getDisplayName(), newEmail, rawToken);

        auditLogService.logSuccess(
                userId, AuditAction.EMAIL_CHANGE_REQUESTED, ResourceType.USER, userId.toString());

        log.info("Email change requested for user {}, new email: {}", userId, maskEmail(newEmail));

        return mailResponse;
    }

    /**
     * Confirms an email change via verification token.
     *
     * <p>Flow:
     *
     * <ol>
     *   <li>Hash token and find by tokenHash
     *   <li>Check brute-force (max 5 attempts)
     *   <li>Validate token state (not cancelled/completed/expired)
     *   <li>Validate purpose is CHANGE_EMAIL
     *   <li>Find user, double-check email still unique
     *   <li>Update user.email, set emailVerified=false, emailVerifiedAt=null
     *   <li>Mark token as completed
     *   <li>Cancel other pending tokens
     *   <li>Audit log EMAIL_CHANGE_COMPLETED
     * </ol>
     *
     * @param rawToken the raw verification token from email link
     * @param ip the client IP address for audit logging
     * @param userAgent the client User-Agent for audit logging
     * @throws UnauthorizedException if token is invalid, expired, or attempts exceeded
     * @throws ConflictException if new email is already registered
     */
    @Transactional
    public void confirmEmailChange(String rawToken, String ip, String userAgent) {
        String tokenHash = TokenUtils.hashToken(rawToken);
        EmailVerificationToken token =
                tokenRepository
                        .findByTokenHash(tokenHash)
                        .orElseThrow(
                                () ->
                                        new UnauthorizedException(
                                                ErrorCode.USER_011, "Invalid email change token"));

        if (token.getAttempts() >= MAX_ATTEMPTS) {
            token.incrementAttempts();
            tokenRepository.save(token);
            throw new UnauthorizedException(ErrorCode.USER_011, "Too many verification attempts");
        }

        token.incrementAttempts();

        TokenStatus status = token.determineStatus();
        if (status == TokenStatus.EXPIRED) {
            tokenRepository.save(token);
            throw new UnauthorizedException(ErrorCode.USER_010, "Email change token has expired");
        }
        if (status != TokenStatus.VALID) {
            tokenRepository.save(token);
            throw new UnauthorizedException(ErrorCode.USER_011, "Token is no longer valid");
        }

        if (!EmailVerificationToken.PURPOSE_CHANGE_EMAIL.equals(token.getPurpose())) {
            tokenRepository.save(token);
            throw new UnauthorizedException(ErrorCode.USER_011, "Invalid token purpose");
        }

        User user =
                userRepository
                        .findById(token.getUserId())
                        .orElseThrow(
                                () -> new NotFoundException(ErrorCode.USER_004, USER_NOT_FOUND));

        String newEmail = token.getEmail();
        userValidator.assertEmailUnique(newEmail);

        user.setEmail(newEmail);
        user.setEmailVerified(false);
        user.setEmailVerifiedAt(null);
        userRepository.save(user);

        token.complete();
        tokenRepository.save(token);

        Instant now = Instant.now();
        tokenRepository.cancelPendingChangeEmailTokensByUserId(user.getId(), now);

        auditLogService.logSuccess(
                user.getId(),
                AuditAction.EMAIL_CHANGE_COMPLETED,
                ResourceType.USER,
                user.getId().toString());

        log.info("Email change completed for user {}", user.getId());
    }

    /**
     * Cancels a pending email change request for the authenticated user.
     *
     * @param userId the authenticated user's ID
     */
    @Transactional
    public void cancelEmailChange(UUID userId) {
        Instant now = Instant.now();
        int cancelled = tokenRepository.cancelPendingChangeEmailTokensByUserId(userId, now);

        if (cancelled > 0) {
            auditLogService.logSuccess(
                    userId,
                    AuditAction.EMAIL_CHANGE_CANCELLED,
                    ResourceType.USER,
                    userId.toString());
            log.info("Email change cancelled for user {}, {} tokens cancelled", userId, cancelled);
        }
    }

    /**
     * Re-sends the verification email for a pending email change request.
     *
     * <p>Rotates the existing pending CHANGE_EMAIL token (new raw token, new hash) so the previous
     * link in any earlier email becomes invalid, then re-enqueues the verification email to the
     * pending new address. Does not create a new token record.
     *
     * @param userId the authenticated user's ID
     * @param ip the client IP address for audit logging
     * @param userAgent the client User-Agent for audit logging
     * @return EmptyResponse indicating success
     * @throws ConflictException if no pending CHANGE_EMAIL token exists for the user (USER_009)
     */
    @Transactional
    public EmptyResponse resendEmailChangeVerification(UUID userId, String ip, String userAgent) {
        Instant now = Instant.now();
        EmailVerificationToken pendingToken =
                tokenRepository
                        .findPendingChangeEmailTokenByUserId(userId, now)
                        .orElseThrow(
                                () ->
                                        new ConflictException(
                                                ErrorCode.USER_009,
                                                "No pending email change request"));

        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> new NotFoundException(ErrorCode.USER_004, USER_NOT_FOUND));

        String newRawToken = TokenUtils.generateRawToken();
        String newTokenHash = TokenUtils.hashToken(newRawToken);

        pendingToken.setTokenHash(newTokenHash);
        pendingToken.setSentAt(now);
        pendingToken.setRequestIp(ip);
        pendingToken.setUserAgent(userAgent);
        tokenRepository.save(pendingToken);

        EmptyResponse mailResponse =
                mailOutboxService.enqueueChangeEmailVerification(
                        userId, user.getDisplayName(), pendingToken.getEmail(), newRawToken);

        auditLogService.logSuccess(
                userId, AuditAction.EMAIL_CHANGE_RESENT, ResourceType.USER, userId.toString());

        log.info(
                "Email change verification resent for user {}, target email: {}",
                userId,
                maskEmail(pendingToken.getEmail()));

        return mailResponse;
    }

    /**
     * Gets the current email status for the authenticated user.
     *
     * @param userId the authenticated user's ID
     * @return EmailStatusResponse containing email status information
     * @throws NotFoundException if user not found
     */
    @Transactional(readOnly = true)
    public EmailStatusResponse getEmailStatus(UUID userId) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> new NotFoundException(ErrorCode.USER_004, USER_NOT_FOUND));

        Instant now = Instant.now();
        Optional<EmailVerificationToken> pendingToken =
                tokenRepository.findPendingChangeEmailTokenByUserId(userId, now);

        return new EmailStatusResponse(
                user.getEmail(),
                Boolean.TRUE.equals(user.getEmailVerified()),
                pendingToken.isPresent(),
                pendingToken.map(EmailVerificationToken::getEmail).orElse(null));
    }

    /**
     * Masks email address for audit logging.
     *
     * <p>Example: "user@example.com" → "us***@example.com"
     *
     * @param email the email address to mask
     * @return masked email address
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@");
        String localPart = parts[0];
        String domain = parts[1];

        if (localPart.length() <= 2) {
            return "***@" + domain;
        }

        return localPart.substring(0, 2) + "***@" + domain;
    }
}
