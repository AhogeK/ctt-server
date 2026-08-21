package com.ahogek.cttserver.common.ratelimit.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.List;

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
    void shouldReturnAllowed_whenUnderLimit() {
        when(mockRedisTemplate.execute(
                        any(RedisScript.class),
                        eq(Collections.singletonList("test:key")),
                        eq("10"),
                        eq("60")))
                .thenReturn(List.of(1L, 0L));

        RateLimitResult result = rateLimiter.checkLimit("test:key", 10, 60);

        assertThat(result.allowed()).isTrue();
        assertThat(result.remainingSeconds()).isZero();
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldReturnRejectedWithTtl_whenOverLimit() {
        when(mockRedisTemplate.execute(
                        any(RedisScript.class),
                        eq(Collections.singletonList("test:key")),
                        eq("5"),
                        eq("300")))
                .thenReturn(List.of(0L, 42L));

        RateLimitResult result = rateLimiter.checkLimit("test:key", 5, 300);

        assertThat(result.allowed()).isFalse();
        assertThat(result.remainingSeconds()).isEqualTo(42L);
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldReturnRejectedWithZeroTtl_whenRedisReturnsNull() {
        when(mockRedisTemplate.execute(
                        any(RedisScript.class),
                        eq(Collections.singletonList("test:key")),
                        eq("10"),
                        eq("60")))
                .thenReturn(null);

        RateLimitResult result = rateLimiter.checkLimit("test:key", 10, 60);

        assertThat(result.allowed()).isFalse();
        assertThat(result.remainingSeconds()).isZero();
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldReturnRejectedWithNegativeTtl_whenRedisReportsNoExpiry() {
        when(mockRedisTemplate.execute(
                        any(RedisScript.class),
                        eq(Collections.singletonList("test:key")),
                        eq("10"),
                        eq("60")))
                .thenReturn(List.of(0L, -1L));

        RateLimitResult result = rateLimiter.checkLimit("test:key", 10, 60);

        assertThat(result.allowed()).isFalse();
        assertThat(result.remainingSeconds()).isEqualTo(-1L);
    }
}
