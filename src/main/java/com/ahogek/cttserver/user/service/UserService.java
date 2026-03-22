package com.ahogek.cttserver.user.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.dto.UserRegisterRequest;
import com.ahogek.cttserver.auth.entity.EmailVerificationToken;
import com.ahogek.cttserver.auth.repository.EmailVerificationTokenRepository;
import com.ahogek.cttserver.common.utils.TokenUtils;
import com.ahogek.cttserver.mail.service.MailOutboxService;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;
import com.ahogek.cttserver.user.validator.UserValidator;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * User application service.
 *
 * <p>Coordinates domain logic for user-related operations. Responsible for:
 *
 * <ul>
 *   <li>Validating domain rules via UserValidator
 *   <li>Building and persisting domain entities
 *   <li>Publishing audit events
 *   <li>Orchestrating transactional boundaries
 * </ul>
 *
 * <p>This is a thin orchestration layer - all business rules are delegated to the domain validator.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
@Service
public class UserService {

    private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final UserValidator userValidator;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final EmailVerificationTokenRepository tokenRepository;
    private final MailOutboxService mailOutboxService;

    public UserService(
            UserRepository userRepository,
            UserValidator userValidator,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService,
            EmailVerificationTokenRepository tokenRepository,
            MailOutboxService mailOutboxService) {
        this.userRepository = userRepository;
        this.userValidator = userValidator;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.tokenRepository = tokenRepository;
        this.mailOutboxService = mailOutboxService;
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

    /**
     * Registers a new user.
     *
     * <p>Process:
     *
     * <ol>
     *   <li>Domain validation (email uniqueness)
     *   <li>Build user entity with encoded password
     *   <li>Persist user
     *   <li>Generate verification token
     *   <li>Enqueue verification email
     *   <li>Audit log registration event
     * </ol>
     *
     * <p>All steps execute in a single transaction - if email enqueue fails, user and token
     * persistence are rolled back.
     *
     * @param request the registration request
     */
    @Transactional
    public void registerUser(UserRegisterRequest request) {
        // 1. Domain rule validation (Fail-Fast)
        userValidator.assertEmailUnique(request.email());

        // 2. Build domain entity
        User newUser = new User();
        newUser.setEmail(request.email());
        newUser.setDisplayName(request.displayName());
        newUser.setPasswordHash(passwordEncoder.encode(request.password()));

        // 3. Persist to database
        User savedUser = userRepository.save(newUser);

        // 4. Generate verification token
        TokenPair tokenPair = createVerificationToken(savedUser.getId(), VERIFICATION_TOKEN_TTL);

        // 5. Enqueue verification email
        mailOutboxService.enqueueVerificationEmail(
                savedUser.getId(),
                savedUser.getDisplayName(),
                savedUser.getEmail(),
                tokenPair.rawToken());

        // 6. Publish audit event
        auditLogService.logSuccess(
                savedUser.getId(),
                AuditAction.REGISTER_REQUESTED,
                ResourceType.USER,
                savedUser.getId().toString());
    }
}
