package com.ahogek.cttserver.auth.twofactor.service;

import com.ahogek.cttserver.auth.twofactor.entity.UserRecoveryCode;
import com.ahogek.cttserver.auth.twofactor.repository.UserRecoveryCodeRepository;
import com.ahogek.cttserver.common.config.properties.TwoFactorProperties;

import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Issues and consumes single-use recovery codes.
 *
 * <p>A code is ten random bytes shown as four Base32 groups. Only its SHA-256 hash is stored, and
 * consumption is an atomic conditional update so two concurrent submissions cannot both win.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-30
 */
@Service
public class RecoveryCodeService {

    private static final int CODE_BYTES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base32 BASE32 = new Base32();

    private final UserRecoveryCodeRepository repository;
    private final TwoFactorProperties properties;

    public RecoveryCodeService(
            UserRecoveryCodeRepository repository, TwoFactorProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * Replaces the account's recovery codes with a new batch.
     *
     * @param userId the account
     * @return the raw codes, returned only once
     */
    @Transactional
    public List<String> issue(UUID userId) {
        repository.deleteByUserId(userId);
        UUID batchId = UUID.randomUUID();
        List<String> rawCodes = new ArrayList<>(properties.recoveryCodeCount());
        List<UserRecoveryCode> entities = new ArrayList<>(properties.recoveryCodeCount());
        for (int i = 0; i < properties.recoveryCodeCount(); i++) {
            String raw = generateRawCode();
            rawCodes.add(raw);
            UserRecoveryCode entity = new UserRecoveryCode();
            entity.setUserId(userId);
            entity.setCodeHash(hash(normalize(raw)));
            entity.setBatchId(batchId);
            entities.add(entity);
        }
        repository.saveAll(entities);
        return rawCodes;
    }

    /**
     * Consumes a recovery code exactly once.
     *
     * @param userId the account
     * @param rawCode the submitted code
     * @return true when this call consumed it
     */
    @Transactional
    public boolean consume(UUID userId, String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return false;
        }
        return repository.consume(userId, hash(normalize(rawCode)), Instant.now()) == 1;
    }

    /**
     * Counts unused recovery codes.
     *
     * @param userId the account
     * @return the number of unused codes
     */
    public long countRemaining(UUID userId) {
        return repository.countByUserIdAndUsedAtIsNull(userId);
    }

    /**
     * Deletes every recovery code for an account.
     *
     * @param userId the account
     */
    @Transactional
    public void deleteAll(UUID userId) {
        repository.deleteByUserId(userId);
    }

    static String normalize(String code) {
        return code.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private static String generateRawCode() {
        byte[] bytes = new byte[CODE_BYTES];
        RANDOM.nextBytes(bytes);
        String encoded = BASE32.encodeAsString(bytes);
        return encoded.substring(0, 4)
                + "-"
                + encoded.substring(4, 8)
                + "-"
                + encoded.substring(8, 12)
                + "-"
                + encoded.substring(12, 16);
    }

    private static String hash(String normalized) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
