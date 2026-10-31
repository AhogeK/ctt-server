package com.ahogek.cttserver.auth.twofactor.service;

import com.ahogek.cttserver.common.config.properties.TwoFactorProperties;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Verifies a submitted second factor against the account's TOTP secret or recovery codes.
 *
 * <p>This is the single place that decides what a valid second factor is, so the login path and the
 * management endpoints cannot drift apart. A TOTP match advances the replay guard atomically, which
 * is what makes one time step unusable twice; a recovery code is consumed with a conditional
 * update, which is what makes it single-use under concurrency.
 *
 * <p>{@link #verify} treats a submission as a TOTP code exactly when it matches the configured
 * digit count and as a recovery code otherwise. The pattern is derived from configuration because a
 * hard-coded six would misroute codes whenever {@code digits} is raised. Endpoints that receive the
 * two credential kinds as separate fields call {@link #verifyTotp} or {@link #verifyRecoveryCode}
 * directly, so a recovery code in the wrong field is still rejected.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-31
 */
@Component
public class SecondFactorVerifier {

    private final UserRepository userRepository;
    private final TotpService totpService;
    private final TotpSecretCipher cipher;
    private final RecoveryCodeService recoveryCodeService;
    private final Pattern totpPattern;

    public SecondFactorVerifier(
            UserRepository userRepository,
            TotpService totpService,
            TotpSecretCipher cipher,
            RecoveryCodeService recoveryCodeService,
            TwoFactorProperties properties) {
        this.userRepository = userRepository;
        this.totpService = totpService;
        this.cipher = cipher;
        this.recoveryCodeService = recoveryCodeService;
        this.totpPattern = Pattern.compile("\\d{" + properties.digits() + "}");
    }

    /**
     * Verifies a credential whose kind the caller does not know, such as a single login form field.
     *
     * @param user the account
     * @param credential a TOTP code or a recovery code
     * @return the outcome, rejected when the credential is missing, malformed or already used
     */
    @Transactional
    public Verified verify(User user, String credential) {
        if (credential == null || credential.isBlank()) {
            return Verified.rejected();
        }
        String submitted = credential.trim();
        // Deliberately not delegating to the other public methods: a self-invocation would bypass
        // the proxy and silently drop their transaction, so the dispatch calls the bodies directly
        // while this method's own transaction covers both branches.
        if (totpPattern.matcher(submitted).matches()) {
            return verifyTotpCode(user, submitted);
        }
        return verifyRecoveryCodeValue(user.getId(), submitted);
    }

    /**
     * Verifies a TOTP code and advances the replay guard.
     *
     * @param user the account
     * @param code the submitted TOTP code
     * @return the outcome, carrying the accepted step when the code is valid and unused
     */
    @Transactional
    public Verified verifyTotp(User user, String code) {
        return verifyTotpCode(user, code);
    }

    /**
     * Consumes a recovery code.
     *
     * @param userId the account
     * @param code the submitted recovery code
     * @return the outcome, accepted when this call consumed an unused code
     */
    @Transactional
    public Verified verifyRecoveryCode(UUID userId, String code) {
        return verifyRecoveryCodeValue(userId, code);
    }

    private Verified verifyTotpCode(User user, String code) {
        if (code == null || code.isBlank() || user.getTotpSecretCiphertext() == null) {
            return Verified.rejected();
        }
        String secret = cipher.decrypt(user.getTotpSecretCiphertext());
        OptionalLong step = totpService.matchingStep(secret, code, Instant.now());
        if (step.isEmpty()) {
            return Verified.rejected();
        }
        long accepted = step.getAsLong();
        if (userRepository.markTotpStepUsed(user.getId(), accepted) != 1) {
            return Verified.rejected();
        }
        return Verified.totp(accepted);
    }

    private Verified verifyRecoveryCodeValue(UUID userId, String code) {
        return recoveryCodeService.consume(userId, code)
                ? Verified.recoveryCode()
                : Verified.rejected();
    }

    /**
     * Outcome of a second-factor submission.
     *
     * @param accepted whether the credential was valid and is now consumed
     * @param totpStep the accepted TOTP step, or {@code null} when a recovery code was consumed
     */
    public record Verified(boolean accepted, Long totpStep) {

        static Verified rejected() {
            return new Verified(false, null);
        }

        static Verified totp(long step) {
            return new Verified(true, step);
        }

        static Verified recoveryCode() {
            return new Verified(true, null);
        }
    }
}
