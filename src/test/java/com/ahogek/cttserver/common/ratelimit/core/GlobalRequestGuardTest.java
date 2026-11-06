package com.ahogek.cttserver.common.ratelimit.core;

import com.ahogek.cttserver.common.config.properties.SecurityProperties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalRequestGuard")
class GlobalRequestGuardTest {

    private static SecurityProperties securityProperties(int perSecond) {
        return new SecurityProperties(
                null,
                null,
                new SecurityProperties.RateLimitProperties(true, perSecond),
                null,
                null,
                null,
                null,
                null);
    }

    private static GlobalRequestGuard guard(int perSecond, Clock clock) {
        return new GlobalRequestGuard(securityProperties(perSecond), clock);
    }

    /** A clock whose second can be advanced, so one guard instance can cross a window. */
    private static final class MutableClock extends Clock {
        private final AtomicLong epochSecond;

        MutableClock(long epochSecond) {
            this.epochSecond = new AtomicLong(epochSecond);
        }

        void advanceTo(long epochSecond) {
            this.epochSecond.set(epochSecond);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochSecond(epochSecond.get());
        }
    }

    @Test
    @DisplayName("Should allow requests up to the configured per-second ceiling")
    void shouldAllowUpToTheCeiling_withinOneWindow() {
        GlobalRequestGuard guard = guard(3, new MutableClock(1_000_000L));

        assertThat(guard.tryAcquire()).isTrue();
        assertThat(guard.tryAcquire()).isTrue();
        assertThat(guard.tryAcquire()).isTrue();
        assertThat(guard.tryAcquire()).isFalse();
        assertThat(guard.tryAcquire()).isFalse();
    }

    @Test
    @DisplayName("Should reset the counter when the wall-clock second changes")
    void shouldResetTheCounter_whenTheWindowRollsOver() {
        MutableClock clock = new MutableClock(1_000_000L);
        GlobalRequestGuard guard = guard(1, clock);

        assertThat(guard.tryAcquire()).isTrue();
        assertThat(guard.tryAcquire()).isFalse();

        clock.advanceTo(1_000_001L);
        assertThat(guard.tryAcquire()).isTrue();
        assertThat(guard.tryAcquire()).isFalse();
    }

    @Test
    @DisplayName("Should report the configured ceiling")
    void shouldReportTheConfiguredCeiling() {
        assertThat(guard(250, new MutableClock(0L)).limitPerSecond()).isEqualTo(250);
    }
}
