package com.ahogek.cttserver.auth.oauth.crypto;

import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.InternalServerErrorException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM implementation of {@link OAuthTokenEncryptor}.
 *
 * <p>Uses authenticated encryption (AEAD) to provide both confidentiality and integrity. Tampered
 * ciphertexts are detected during decryption via the GCM authentication tag.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-12
 */
@Component
public class AesGcmTokenEncryptor implements OAuthTokenEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmTokenEncryptor(SecurityProperties properties) {
        String base64Key = properties.oauth().tokenEncryptionKey();
        if (!StringUtils.hasText(base64Key)) {
            throw new IllegalStateException(
                    "Missing required configuration: ctt.security.oauth.token-encryption-key");
        }

        byte[] decodedKey = Base64.getDecoder().decode(base64Key);
        if (decodedKey.length != 32) {
            throw new IllegalArgumentException(
                    "OAuth token encryption key must be exactly 32 bytes (256 bits) for AES-256");
        }
        this.secretKey = new SecretKeySpec(decodedKey, "AES");
    }

    @Override
    public String encrypt(String plaintext) {
        if (!StringUtils.hasText(plaintext)) {
            return plaintext;
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTE];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv));

            byte[] cipherTextWithTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherTextWithTag.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherTextWithTag);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception _) {
            throw new InternalServerErrorException("Internal encryption error");
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        if (!StringUtils.hasText(ciphertext)) {
            return ciphertext;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext);

            if (decoded.length < IV_LENGTH_BYTE + 16) {
                throw new IllegalArgumentException("Invalid ciphertext format");
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);

            byte[] iv = new byte[IV_LENGTH_BYTE];
            byteBuffer.get(iv);

            byte[] cipherTextWithTag = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherTextWithTag);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv));

            byte[] plainTextBytes = cipher.doFinal(cipherTextWithTag);
            return new String(plainTextBytes, StandardCharsets.UTF_8);
        } catch (Exception _) {
            throw new UnauthorizedException(ErrorCode.AUTH_014);
        }
    }
}
