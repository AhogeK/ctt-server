package com.ahogek.cttserver.auth.twofactor.service;

import com.ahogek.cttserver.common.config.properties.TwoFactorProperties;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts TOTP secrets at rest with AES-256-GCM.
 *
 * <p>Ciphertext format: {@code v1:<base64 iv>:<base64 ciphertext>}. The version prefix leaves room
 * for a future key rotation without guessing how an old value was produced.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-30
 */
@Component
public class TotpSecretCipher {

    private static final String VERSION = "v1";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public TotpSecretCipher(TwoFactorProperties properties) {
        this.key = new SecretKeySpec(Base64.getDecoder().decode(properties.encryptionKey()), "AES");
    }

    /**
     * Encrypts a plaintext secret.
     *
     * @param plaintext the TOTP secret
     * @return versioned ciphertext
     */
    public String encrypt(String plaintext) {
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return VERSION
                    + ":"
                    + Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().encodeToString(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to encrypt the TOTP secret", e);
        }
    }

    /**
     * Decrypts a versioned ciphertext.
     *
     * @param ciphertext the stored value
     * @return the plaintext secret
     * @throws IllegalArgumentException when the value is malformed, tampered with, or uses an
     *     unsupported version
     */
    public String decrypt(String ciphertext) {
        String[] parts = ciphertext == null ? new String[0] : ciphertext.split(":", 3);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("Unsupported TOTP ciphertext");
        }
        try {
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] encrypted = Base64.getDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid TOTP ciphertext", e);
        }
    }
}
