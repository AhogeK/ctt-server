package com.ahogek.cttserver.common.idempotent.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotentLockerTest {

    private StringRedisTemplate mockRedisTemplate;
    private ValueOperations<String, String> mockValueOps;
    private IdempotentLocker locker;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        mockRedisTemplate = mock(StringRedisTemplate.class);
        mockValueOps = mock(ValueOperations.class);
        when(mockRedisTemplate.opsForValue()).thenReturn(mockValueOps);
        locker = new IdempotentLocker(mockRedisTemplate);
    }

    @Test
    void tryLock_whenKeyNotExists_returnsTrue() {
        when(mockValueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        boolean result = locker.tryLock("test:lock:key", 5);

        assertThat(result).isTrue();
    }

    @Test
    void tryLock_whenKeyExists_returnsFalse() {
        when(mockValueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        boolean result = locker.tryLock("test:lock:key", 5);

        assertThat(result).isFalse();
    }

    @Test
    void tryLock_whenRedisReturnsNull_returnsFalse() {
        when(mockValueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(null);

        boolean result = locker.tryLock("test:lock:key", 5);

        assertThat(result).isFalse();
    }

    @Test
    void unlock_deletesKey() {
        locker.unlock("test:lock:key");

        verify(mockRedisTemplate).delete("test:lock:key");
    }
}
