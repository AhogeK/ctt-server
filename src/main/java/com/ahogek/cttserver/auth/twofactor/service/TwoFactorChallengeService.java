package com.ahogek.cttserver.auth.twofactor.service;

import com.ahogek.cttserver.common.config.properties.TwoFactorProperties;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.TooManyRequestsException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Issues and consumes one-time login challenges.
 *
 * <p>A challenge is claimed with {@code GETDEL}, so only one request can attempt it at a time. A
 * failed attempt is re-inserted with an incremented counter; the fifth failure ends the challenge.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-30
 */
@Service
public class TwoFactorChallengeService {

    private static final String KEY_PREFIX = "2fa:challenge:";
    private static final int TOKEN_BYTES = 32;
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(5);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TwoFactorProperties properties;

    public TwoFactorChallengeService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            TwoFactorProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ChallengeTicket create(UUID userId, String deviceId) {
        byte[] raw = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        ChallengePayload payload = new ChallengePayload(userId, deviceId, 0, Instant.now());
        redisTemplate.opsForValue().set(key(token), json(payload), properties.challengeTtl());
        return new ChallengeTicket(token, properties.challengeTtl().toSeconds());
    }

    public ChallengePayload consume(String token) {
        String json = redisTemplate.opsForValue().getAndDelete(key(token));
        if (json == null) {
            throw invalidChallenge();
        }
        return parse(json);
    }

    /**
     * Reads a challenge without consuming it.
     *
     * <p>The passkey second-factor options endpoint must validate the token without spending it:
     * the user may still choose the TOTP/recovery-code path after declining the passkey.
     *
     * @param token the one-time login challenge token
     * @return the stored challenge payload
     * @throws UnauthorizedException with {@code AUTH_026} when the token is missing or expired
     */
    public ChallengePayload peek(String token) {
        String json = redisTemplate.opsForValue().get(key(token));
        if (json == null) {
            throw invalidChallenge();
        }
        return parse(json);
    }

    private ChallengePayload parse(String json) {
        try {
            return objectMapper.readValue(json, ChallengePayload.class);
        } catch (JsonProcessingException e) {
            throw invalidChallenge();
        }
    }

    private static UnauthorizedException invalidChallenge() {
        return new UnauthorizedException(
                ErrorCode.AUTH_026, "Two-factor challenge is invalid or expired");
    }

    public void recordFailure(String token, ChallengePayload payload) {
        int attempts = payload.attempts() + 1;
        Instant now = Instant.now();
        if (attempts >= MAX_ATTEMPTS) {
            throw new TooManyRequestsException(
                    ErrorCode.RATE_LIMIT_001,
                    "Too many two-factor attempts",
                    now.plus(FAILURE_WINDOW));
        }
        Duration remaining =
                Duration.between(now, payload.createdAt().plus(properties.challengeTtl()));
        if (remaining.isNegative() || remaining.isZero()) {
            throw new UnauthorizedException(
                    ErrorCode.AUTH_026, "Two-factor challenge is invalid or expired");
        }
        ChallengePayload next =
                new ChallengePayload(
                        payload.userId(), payload.deviceId(), attempts, payload.createdAt());
        redisTemplate.opsForValue().set(key(token), json(next), remaining);
    }

    private String json(ChallengePayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize a two-factor challenge", e);
        }
    }

    private static String key(String token) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(token.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public record ChallengeTicket(String token, long expiresIn) {}

    public record ChallengePayload(UUID userId, String deviceId, int attempts, Instant createdAt) {}
}
