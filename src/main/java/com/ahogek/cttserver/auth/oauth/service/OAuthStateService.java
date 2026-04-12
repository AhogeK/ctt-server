package com.ahogek.cttserver.auth.oauth.service;

import com.ahogek.cttserver.auth.oauth.model.OAuthStatePayload;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.InternalServerErrorException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * OAuth state management using Redis for CSRF protection.
 *
 * <p>States are short-lived (10 min TTL) and consumed once via atomic GETDEL to prevent replay
 * attacks.
 */
@Service
public class OAuthStateService {

    private static final String STATE_KEY_PREFIX = "oauth:state:";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public OAuthStateService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Generates a unique state ID and stores the associated payload in Redis with TTL.
     *
     * @param payload the context payload to carry through the OAuth flow
     * @return the generated state UUID
     */
    public String generateAndSaveState(OAuthStatePayload payload) {
        String stateId = UUID.randomUUID().toString();
        String key = STATE_KEY_PREFIX + stateId;

        try {
            String jsonValue = objectMapper.writeValueAsString(payload);
            redisTemplate.opsForValue().set(key, jsonValue, STATE_TTL);
            return stateId;
        } catch (JsonProcessingException e) {
            throw new InternalServerErrorException("Failed to serialize OAuth state payload", e);
        }
    }

    /**
     * Validates and consumes the state in a single atomic operation (GETDEL).
     *
     * <p>After consumption the key is deleted, preventing replay attacks.
     *
     * @param stateId the state UUID received from the OAuth provider callback
     * @return the stored payload
     * @throws UnauthorizedException if the state is missing or expired
     */
    public OAuthStatePayload consumeState(String stateId) {
        String key = STATE_KEY_PREFIX + stateId;
        String jsonValue = redisTemplate.opsForValue().getAndDelete(key);

        if (jsonValue == null) {
            throw new UnauthorizedException(ErrorCode.AUTH_013);
        }

        try {
            return objectMapper.readValue(jsonValue, OAuthStatePayload.class);
        } catch (JsonProcessingException e) {
            throw new InternalServerErrorException("Failed to deserialize OAuth state payload", e);
        }
    }
}
