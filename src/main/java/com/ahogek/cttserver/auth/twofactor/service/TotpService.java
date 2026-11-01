package com.ahogek.cttserver.auth.twofactor.service;

import com.ahogek.cttserver.common.config.properties.TwoFactorProperties;

import org.apache.commons.codec.binary.Base32;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;

import com.bastiaanjansen.otp.HMACAlgorithm;
import com.bastiaanjansen.otp.TOTPGenerator;

/**
 * Generates and verifies RFC 6238 TOTP codes.
 *
 * <p>Verification returns the matching step so the caller can persist it and reject a replay of the
 * same code. The comparison is constant-time.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-30
 */
@Service
public class TotpService {

    private static final int SECRET_BYTES = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TwoFactorProperties properties;
    private final Clock clock;
    private final Base32 base32 = new Base32();

    /**
     * Spring entry point. Two constructors exist, so the one Spring uses is marked explicitly
     * instead of leaving the choice to constructor-resolution heuristics.
     *
     * @param properties the two-factor configuration
     */
    @Autowired
    public TotpService(TwoFactorProperties properties) {
        this(properties, Clock.systemUTC());
    }

    TotpService(TwoFactorProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Generates a new 160-bit secret encoded as Base32.
     *
     * <p>Twenty random bytes are encoded once. The otp-java {@code SecretGenerator.generate(int)}
     * helper already returns Base32 bytes, so feeding its output to a second Base32 encoder would
     * produce a 52-character payload plus {@code ====} padding that iOS Google Authenticator
     * rejects.
     *
     * @return the Base32 secret
     */
    public String generateSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        RANDOM.nextBytes(secret);
        return base32.encodeAsString(secret);
    }

    /**
     * Returns the code for a specific time step.
     *
     * @param secret the Base32 secret
     * @param step the TOTP step
     * @return the six-digit code
     */
    public String codeAt(String secret, long step) {
        return generator(secret).at(step * properties.periodSeconds());
    }

    /**
     * Finds the time step matching a submitted code within the configured window.
     *
     * @param secret the Base32 secret
     * @param code the submitted code
     * @param at the verification instant
     * @return the matching step, or empty when the code is invalid
     */
    public OptionalLong matchingStep(String secret, String code, Instant at) {
        if (code == null || code.isBlank()) {
            return OptionalLong.empty();
        }
        // One generator per verification: the window can span three steps, and rebuilding the
        // HMAC key for each of them is pure overhead on a path an attacker can drive.
        TOTPGenerator generator = generator(secret);
        byte[] submitted = code.trim().getBytes(StandardCharsets.UTF_8);
        long currentStep = at.getEpochSecond() / properties.periodSeconds();
        for (long step = currentStep - properties.windowSteps();
                step <= currentStep + properties.windowSteps();
                step++) {
            // otp-java rejects a timestamp of zero; step zero is outside its supported range.
            if (step <= 0) {
                continue;
            }
            String candidate = generator.at(step * properties.periodSeconds());
            if (MessageDigest.isEqual(candidate.getBytes(StandardCharsets.UTF_8), submitted)) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    /**
     * Builds the otpauth URI consumed by authenticator applications.
     *
     * @param secret the Base32 secret
     * @param account the account label
     * @return the otpauth URI
     */
    public String otpauthUri(String secret, String account) {
        try {
            return generator(secret).getURI(properties.issuer(), account).toString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Unable to build the otpauth URI", e);
        }
    }

    /**
     * Returns the current step from the configured clock.
     *
     * @return the current TOTP step
     */
    public long currentStep() {
        return clock.instant().getEpochSecond() / properties.periodSeconds();
    }

    /**
     * Builds a generator for one secret.
     *
     * <p>SHA-1 is not a weakness here: RFC 6238 fixes it as the default, and every authenticator
     * application interoperates on it. The library marks the constant deprecated for reasons
     * unrelated to TOTP's use of HMAC, so the warning is suppressed rather than silently ignored.
     *
     * @param secret the Base32 secret
     * @return a generator bound to this secret and the configured period
     */
    @SuppressWarnings("deprecation")
    private TOTPGenerator generator(String secret) {
        return new TOTPGenerator.Builder(secret)
                .withPeriod(Duration.ofSeconds(properties.periodSeconds()))
                .withHOTPGenerator(
                        builder ->
                                builder.withPasswordLength(properties.digits())
                                        .withAlgorithm(HMACAlgorithm.SHA1))
                .build();
    }
}
