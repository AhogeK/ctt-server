package com.ahogek.cttserver.auth.oauth.crypto;

/**
 * Contract for encrypting and decrypting OAuth tokens.
 *
 * <p>Implementations must provide authenticated encryption (AEAD) to ensure both confidentiality
 * and integrity of stored tokens.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-12
 */
public interface OAuthTokenEncryptor {

    /**
     * Encrypts a plaintext token into a safe ciphertext.
     *
     * @param plaintext the raw token to encrypt
     * @return Base64-encoded ciphertext, or the original input if null/blank
     */
    String encrypt(String plaintext);

    /**
     * Decrypts a ciphertext back to the original plaintext.
     *
     * <p>Throws an exception if the ciphertext has been tampered with (AEAD tag mismatch).
     *
     * @param ciphertext the Base64-encoded ciphertext to decrypt
     * @return the original plaintext token, or the original input if null/blank
     */
    String decrypt(String ciphertext);
}
