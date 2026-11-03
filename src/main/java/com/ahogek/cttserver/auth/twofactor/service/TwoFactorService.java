package com.ahogek.cttserver.auth.twofactor.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.repository.RefreshTokenRepository;
import com.ahogek.cttserver.auth.service.RecentAuthenticationService;
import com.ahogek.cttserver.auth.twofactor.dto.RecoveryCodesResponse;
import com.ahogek.cttserver.auth.twofactor.dto.StartTotpResponse;
import com.ahogek.cttserver.auth.twofactor.dto.TwoFactorStatusResponse;
import com.ahogek.cttserver.common.config.properties.TwoFactorProperties;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.common.exception.ValidationException;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Orchestrates TOTP setup, confirmation, disabling and recovery-code rotation.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-30
 */
@Service
public class TwoFactorService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    private final TotpSecretCipher cipher;
    private final TwoFactorSetupStore setupStore;
    private final RecoveryCodeService recoveryCodeService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TwoFactorProperties properties;
    private final AuditLogService auditLogService;
    private final SecondFactorVerifier verifier;
    private final RecentAuthenticationService recentAuthentication;
    private final Clock clock;

    @Autowired
    public TwoFactorService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TotpService totpService,
            TotpSecretCipher cipher,
            TwoFactorSetupStore setupStore,
            RecoveryCodeService recoveryCodeService,
            RefreshTokenRepository refreshTokenRepository,
            TwoFactorProperties properties,
            AuditLogService auditLogService,
            SecondFactorVerifier verifier,
            RecentAuthenticationService recentAuthentication) {
        this(
                userRepository,
                passwordEncoder,
                totpService,
                cipher,
                setupStore,
                recoveryCodeService,
                refreshTokenRepository,
                properties,
                auditLogService,
                verifier,
                recentAuthentication,
                Clock.systemUTC());
    }

    TwoFactorService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TotpService totpService,
            TotpSecretCipher cipher,
            TwoFactorSetupStore setupStore,
            RecoveryCodeService recoveryCodeService,
            RefreshTokenRepository refreshTokenRepository,
            TwoFactorProperties properties,
            AuditLogService auditLogService,
            SecondFactorVerifier verifier,
            RecentAuthenticationService recentAuthentication,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.totpService = totpService;
        this.cipher = cipher;
        this.setupStore = setupStore;
        this.recoveryCodeService = recoveryCodeService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.properties = properties;
        this.auditLogService = auditLogService;
        this.verifier = verifier;
        this.recentAuthentication = recentAuthentication;
        this.clock = clock;
    }

    public TwoFactorStatusResponse status(UUID userId, UUID sessionId) {
        User user = requireUser(userId);
        boolean recent = recentAuthentication.isRecent(userId, sessionId);
        return new TwoFactorStatusResponse(
                user.getTotpConfirmedAt() != null,
                user.getTotpConfirmedAt(),
                recoveryCodeService.countRemaining(userId),
                user.getPasswordHash() != null,
                recent);
    }

    public StartTotpResponse start(UUID userId, String currentPassword, UUID sessionId) {
        User user = requireUser(userId);
        if (user.getTotpConfirmedAt() != null) {
            throw new ConflictException(ErrorCode.AUTH_028);
        }
        requireReauthentication(user, currentPassword, sessionId);
        String secret = totpService.generateSecret();
        String encrypted = cipher.encrypt(secret);
        setupStore.put(userId, encrypted, properties.pendingSetupTtl());
        return new StartTotpResponse(
                secret,
                totpService.otpauthUri(secret, user.getEmail()),
                properties.digits(),
                properties.periodSeconds(),
                properties.issuer(),
                clock.instant().plus(properties.pendingSetupTtl()));
    }

    /**
     * Confirms a pending secret and issues the first batch of recovery codes.
     *
     * <p>The submitted code is checked against the pending secret directly rather than through
     * {@link SecondFactorVerifier}: that verifier reads the persisted secret, which by definition
     * does not exist yet at this point.
     *
     * @param userId the account
     * @param code the code generated by the authenticator application
     * @param sessionId the session enabling the factor, kept when other sessions are revoked
     * @return the recovery codes, returned only once
     */
    @Transactional
    public RecoveryCodesResponse confirm(UUID userId, String code, UUID sessionId) {
        User user = requireUser(userId);
        if (user.getTotpConfirmedAt() != null) {
            throw new ConflictException(ErrorCode.AUTH_028);
        }
        String encrypted =
                setupStore
                        .get(userId)
                        .orElseThrow(
                                () ->
                                        new UnauthorizedException(
                                                ErrorCode.AUTH_026,
                                                "Two-factor setup is invalid or expired"));
        String secret = cipher.decrypt(encrypted);
        OptionalLong pendingStep = totpService.matchingStep(secret, code, clock.instant());
        if (pendingStep.isEmpty()) {
            throw new UnauthorizedException(ErrorCode.AUTH_027, "Invalid two-factor code");
        }

        long step = pendingStep.getAsLong();
        Instant confirmedAt = clock.instant();
        user.setTotpSecretCiphertext(encrypted);
        user.setTotpConfirmedAt(confirmedAt);
        user.setTotpLastUsedStep(step);
        userRepository.save(user);

        List<String> codes = recoveryCodeService.issue(userId);
        setupStore.delete(userId);
        auditLogService.logSuccess(
                userId, AuditAction.TOTP_ENABLED, ResourceType.USER, userId.toString());
        revokeOtherSessions(userId, sessionId);
        return new RecoveryCodesResponse(codes);
    }

    @Transactional
    public void disable(UUID userId, String code, String recoveryCode, UUID sessionId) {
        User user = requireUser(userId);
        if (user.getTotpConfirmedAt() == null) {
            throw new ConflictException(ErrorCode.AUTH_029);
        }
        verifyFactor(user, code, recoveryCode);
        user.setTotpSecretCiphertext(null);
        user.setTotpConfirmedAt(null);
        user.setTotpLastUsedStep(null);
        userRepository.save(user);
        recoveryCodeService.deleteAll(userId);
        setupStore.delete(userId);
        auditLogService.logSuccess(
                userId, AuditAction.TOTP_DISABLED, ResourceType.USER, userId.toString());
        revokeOtherSessions(userId, sessionId);
    }

    @Transactional
    public RecoveryCodesResponse regenerate(UUID userId, String code, String recoveryCode) {
        User user = requireUser(userId);
        if (user.getTotpConfirmedAt() == null) {
            throw new ConflictException(ErrorCode.AUTH_029);
        }
        verifyFactor(user, code, recoveryCode);
        List<String> codes = recoveryCodeService.issue(userId);
        auditLogService.logSuccess(
                userId,
                AuditAction.TOTP_RECOVERY_CODES_REGENERATED,
                ResourceType.USER,
                userId.toString());
        return new RecoveryCodesResponse(codes);
    }

    /**
     * Requires a second factor when the account has 2FA enabled. Used by high-risk actions such as
     * API-key creation.
     *
     * @param userId the account
     * @param code a TOTP code or an unused recovery code
     */
    public void requireCodeForSensitiveAction(UUID userId, String code) {
        User user = requireUser(userId);
        if (user.getTotpConfirmedAt() == null) {
            return;
        }
        if (code == null || code.isBlank()) {
            throw new ForbiddenException(ErrorCode.AUTH_030);
        }
        if (!verifier.verify(user, code).accepted()) {
            throw new UnauthorizedException(ErrorCode.AUTH_027, "Invalid two-factor code");
        }
    }

    private void verifyFactor(User user, String code, String recoveryCode) {
        boolean hasCode = code != null && !code.isBlank();
        boolean hasRecovery = recoveryCode != null && !recoveryCode.isBlank();
        if (hasCode == hasRecovery) {
            throw new ValidationException(
                    ErrorCode.COMMON_003, "Provide exactly one two-factor credential");
        }
        SecondFactorVerifier.Verified verified =
                hasCode
                        ? verifier.verifyTotp(user, code)
                        : verifier.verifyRecoveryCode(user.getId(), recoveryCode);
        if (!verified.accepted()) {
            throw new UnauthorizedException(ErrorCode.AUTH_027, "Invalid two-factor code");
        }
    }

    private void requireReauthentication(User user, String currentPassword, UUID sessionId) {
        if (user.getPasswordHash() != null) {
            if (currentPassword == null || currentPassword.isBlank()) {
                throw new UnauthorizedException(
                        ErrorCode.USER_013, "Password verification required");
            }
            if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
                throw new UnauthorizedException(ErrorCode.USER_014, "Invalid password");
            }
            return;
        }
        recentAuthentication.requireRecent(user.getId(), sessionId);
    }

    private void revokeOtherSessions(UUID userId, UUID sessionId) {
        Instant now = clock.instant();
        if (sessionId == null) {
            refreshTokenRepository.revokeAllUserTokens(userId, now);
        } else {
            refreshTokenRepository.revokeAllUserTokensExceptSession(userId, sessionId, now);
        }
    }

    private User requireUser(UUID userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_004));
    }
}
