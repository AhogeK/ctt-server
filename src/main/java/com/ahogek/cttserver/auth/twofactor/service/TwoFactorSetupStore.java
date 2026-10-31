package com.ahogek.cttserver.auth.twofactor.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores an encrypted, unconfirmed TOTP secret until the user proves possession.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-30
 */
@Component
public class TwoFactorSetupStore {

    private static final String KEY_PREFIX = "2fa:setup:";

    private final StringRedisTemplate redisTemplate;

    public TwoFactorSetupStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void put(UUID userId, String encryptedSecret, Duration ttl) {
        redisTemplate.opsForValue().set(key(userId), encryptedSecret, ttl);
    }

    public Optional<String> get(UUID userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(userId)));
    }

    public void delete(UUID userId) {
        redisTemplate.delete(key(userId));
    }

    private static String key(UUID userId) {
        return KEY_PREFIX + userId;
    }
}
