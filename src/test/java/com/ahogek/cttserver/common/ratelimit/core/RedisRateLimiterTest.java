package com.ahogek.cttserver.common.ratelimit.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisRateLimiterTest {

    private StringRedisTemplate mockRedisTemplate;
    private RedisRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        mockRedisTemplate = mock(StringRedisTemplate.class);
        rateLimiter = new RedisRateLimiter(mockRedisTemplate);
    }

    @SuppressWarnings("unchecked")
    @Test
    void isAllowed_whenUnderLimit_returnsTrue() {
        when(mockRedisTemplate.execute(
                        any(RedisScript.class),
                        eq(Collections.singletonList("test:key")),
                        eq("10"),
                        eq("60")))
                .thenReturn(1L);

        boolean allowed = rateLimiter.isAllowed("test:key", 10, 60);

        assertThat(allowed).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void isAllowed_whenOverLimit_returnsFalse() {
        when(mockRedisTemplate.execute(
                        any(RedisScript.class),
                        eq(Collections.singletonList("test:key")),
                        eq("5"),
                        eq("300")))
                .thenReturn(0L);

        boolean allowed = rateLimiter.isAllowed("test:key", 5, 300);

        assertThat(allowed).isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    void isAllowed_whenRedisReturnsNull_returnsFalse() {
        when(mockRedisTemplate.execute(
                        any(RedisScript.class),
                        eq(Collections.singletonList("test:key")),
                        eq("10"),
                        eq("60")))
                .thenReturn(null);

        boolean allowed = rateLimiter.isAllowed("test:key", 10, 60);

        assertThat(allowed).isFalse();
    }
}
