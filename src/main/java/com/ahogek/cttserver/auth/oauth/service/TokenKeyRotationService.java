package com.ahogek.cttserver.auth.oauth.service;

import com.ahogek.cttserver.auth.oauth.crypto.AesGcmTokenEncryptor;
import com.ahogek.cttserver.auth.oauth.entity.UserOAuthAccount;
import com.ahogek.cttserver.auth.oauth.repository.UserOAuthAccountRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for rotating OAuth token encryption keys.
 *
 * <p>When the encryption key needs to be rotated (e.g., key compromise, periodic rotation), this
 * service re-encrypts all stored OAuth tokens from the old key to the new key.
 *
 * <p><b>Process</b>:
 *
 * <ol>
 *   <li>Read all OAuth accounts from database
 *   <li>For each account: decrypt access_token and refresh_token with old key
 *   <li>Re-encrypt with new key
 *   <li>Save updated account
 * </ol>
 *
 * <p><b>Important</b>: This operation is NOT reversible. After rotation, the old key cannot decrypt
 * the tokens. Ensure the new key is properly backed up before running this service.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-05
 */
@Service
public class TokenKeyRotationService {

    private static final Logger log = LoggerFactory.getLogger(TokenKeyRotationService.class);

    private final UserOAuthAccountRepository oauthAccountRepository;

    public TokenKeyRotationService(UserOAuthAccountRepository oauthAccountRepository) {
        this.oauthAccountRepository = oauthAccountRepository;
    }

    /**
     * Rotates all OAuth token encryption keys from old to new.
     *
     * @param oldEncryptor the encryptor configured with the old key
     * @param newEncryptor the encryptor configured with the new key
     * @return the number of accounts rotated
     * @throws IllegalArgumentException if either encryptor is null
     */
    @Transactional
    public int rotateKeys(AesGcmTokenEncryptor oldEncryptor, AesGcmTokenEncryptor newEncryptor) {
        if (oldEncryptor == null || newEncryptor == null) {
            throw new IllegalArgumentException("Both old and new encryptors must be provided");
        }

        List<UserOAuthAccount> accounts = oauthAccountRepository.findAll();
        int rotated = 0;

        for (UserOAuthAccount account : accounts) {
            try {
                rotateAccountTokens(account, oldEncryptor, newEncryptor);
                rotated++;
            } catch (Exception e) {
                log.error(
                        "Failed to rotate tokens for account {} (user: {}): {}",
                        account.getId(),
                        account.getUser().getId(),
                        e.getMessage());
                throw new RuntimeException("Key rotation failed for account " + account.getId(), e);
            }
        }

        log.info("Key rotation completed: {} accounts rotated", rotated);
        return rotated;
    }

    /**
     * Rotates tokens for a single account.
     *
     * <p>Decrypts with old key, explicitly encrypts with new key. We cannot rely on JPA converter
     * because it uses the Spring-managed encryptor (which still has the old key).
     */
    private void rotateAccountTokens(
            UserOAuthAccount account,
            AesGcmTokenEncryptor oldEncryptor,
            AesGcmTokenEncryptor newEncryptor) {
        if (account.getAccessToken() != null && !account.getAccessToken().isBlank()) {
            String plaintext = oldEncryptor.decrypt(account.getAccessToken());
            account.setAccessToken(newEncryptor.encrypt(plaintext));
        }

        if (account.getRefreshToken() != null && !account.getRefreshToken().isBlank()) {
            String plaintext = oldEncryptor.decrypt(account.getRefreshToken());
            account.setRefreshToken(newEncryptor.encrypt(plaintext));
        }

        oauthAccountRepository.save(account);
    }
}
