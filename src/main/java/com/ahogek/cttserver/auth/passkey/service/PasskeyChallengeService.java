package com.ahogek.cttserver.auth.passkey.service;

import com.ahogek.cttserver.common.config.properties.PasskeyProperties;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.UnauthorizedException;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yubico.webauthn.data.ByteArray;

/**
 * Stores one-time WebAuthn ceremony challenges (registration, login, second factor) in Redis.
 *
 * <p>The challenge itself is the lookup key; no extra client token is needed. A challenge is
 * claimed with {@code GETDEL}, so a replay cannot complete twice. The request JSON is retained so
 * the finish step can reconstruct the exact options that were sent to the browser.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-03
 */
@Service
public class PasskeyChallengeService {

    private static final String KEY_PREFIX = "passkey:challenge:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final PasskeyProperties properties;

    public PasskeyChallengeService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            PasskeyProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Stores a registration or assertion challenge and its request JSON.
     *
     * @param operation ceremony kind
     * @param userId the initiating user, or {@code null} for a discoverable assertion
     * @param challenge the WebAuthn challenge bytes
     * @param requestJson the exact options JSON sent to the browser
     * @return the configured challenge lifetime in seconds
     */
    public long store(Operation operation, UUID userId, ByteArray challenge, String requestJson) {
        return store(operation, userId, challenge, requestJson, null);
    }

    /**
     * Stores a ceremony challenge with an optional binding to another one-time token.
     *
     * @param operation ceremony kind
     * @param userId the initiating user, or {@code null} for a discoverable assertion
     * @param challenge the WebAuthn challenge bytes
     * @param requestJson the exact options JSON sent to the browser
     * @param bindingHash hash of the login challenge token, or {@code null}
     * @return the configured challenge lifetime in seconds
     */
    public long store(
            Operation operation,
            UUID userId,
            ByteArray challenge,
            String requestJson,
            String bindingHash) {
        ChallengePayload payload =
                new ChallengePayload(operation, userId, requestJson, bindingHash);
        redisTemplate.opsForValue().set(key(challenge), json(payload), properties.challengeTtl());
        return properties.challengeTtl().toSeconds();
    }

    /**
     * Atomically claims a challenge and returns its stored request JSON.
     *
     * @param expected the ceremony kind expected by the caller
     * @param expectedUserId the initiating account for a registration or second-factor ceremony, or
     *     {@code null} for a discoverable assertion
     * @param expectedBindingHash hash of the bound login token, or {@code null} when unbound
     * @param challenge the challenge from the browser response
     * @return the claimed challenge
     * @throws UnauthorizedException with {@code AUTH_032} when missing, expired, for another kind,
     *     or bound to another account/token
     */
    public ChallengePayload consume(
            Operation expected,
            UUID expectedUserId,
            String expectedBindingHash,
            ByteArray challenge) {
        if (challenge == null || challenge.isEmpty()) {
            throw invalidChallenge();
        }
        String json = redisTemplate.opsForValue().getAndDelete(key(challenge));
        if (json == null) {
            throw invalidChallenge();
        }
        try {
            ChallengePayload payload = objectMapper.readValue(json, ChallengePayload.class);
            if (payload.operation() != expected
                    || payload.requestJson() == null
                    || !Objects.equals(expectedUserId, payload.userId())
                    || !Objects.equals(expectedBindingHash, payload.bindingHash())) {
                throw invalidChallenge();
            }
            return payload;
        } catch (JsonProcessingException e) {
            throw invalidChallenge();
        }
    }

    private String json(ChallengePayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize a passkey challenge", e);
        }
    }

    private static String key(ByteArray challenge) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(challenge.getBase64Url().getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static UnauthorizedException invalidChallenge() {
        return new UnauthorizedException(
                ErrorCode.AUTH_032, "Passkey challenge is invalid or expired");
    }

    /** Ceremony kind bound to a challenge. */
    public enum Operation {
        REGISTRATION,
        ASSERTION,
        SECOND_FACTOR
    }

    public record ChallengePayload(
            Operation operation, UUID userId, String requestJson, String bindingHash) {}
}
