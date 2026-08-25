package com.ahogek.cttserver.sync.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SyncCursor} domain behavior.
 *
 * <p>Focuses on the monotonic watermark guard — the LWW invariant that a pull can never rewind a
 * device — which is enforced both here at the entity level and by the repository bulk update.
 */
class SyncCursorTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID deviceId = UUID.randomUUID();

    private SyncCursor cursor() {
        SyncCursor c = new SyncCursor();
        c.setUserId(userId);
        c.setDeviceId(deviceId);
        c.setLastPulledChangeId(0);
        c.setLastPushAt(Instant.parse("2026-08-25T00:00:00Z"));
        return c;
    }

    @Nested
    @DisplayName("advancePullWatermark")
    class AdvancePullWatermarkTests {

        @Test
        @DisplayName("should advance when new watermark is higher")
        void shouldAdvance_whenNewWatermarkIsHigher() {
            SyncCursor cursor = cursor();

            cursor.advancePullWatermark(42);

            assertThat(cursor.getLastPulledChangeId()).isEqualTo(42L);
        }

        @Test
        @DisplayName("should be no-op when new watermark is lower")
        void shouldBeNoOp_whenNewWatermarkIsLower() {
            SyncCursor cursor = cursor();
            cursor.advancePullWatermark(10);

            cursor.advancePullWatermark(5);

            assertThat(cursor.getLastPulledChangeId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("should be no-op when new watermark equals current")
        void shouldBeNoOp_whenNewWatermarkEqualsCurrent() {
            SyncCursor cursor = cursor();
            cursor.advancePullWatermark(7);

            cursor.advancePullWatermark(7);

            assertThat(cursor.getLastPulledChangeId()).isEqualTo(7L);
        }
    }
}
