package com.ahogek.cttserver.sync.repository;

import com.ahogek.cttserver.common.BaseRepositoryTest;
import com.ahogek.cttserver.device.entity.Device;
import com.ahogek.cttserver.fixtures.UserFixtures;
import com.ahogek.cttserver.sync.entity.SyncCursor;
import com.ahogek.cttserver.user.entity.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@BaseRepositoryTest
@DisplayName("SyncCursorRepository")
class SyncCursorRepositoryTest {

    @Autowired TestEntityManager em;

    @Autowired SyncCursorRepository repository;

    private UUID userId;

    private UUID deviceId;

    @BeforeEach
    void setUp() {
        User user = em.persistFlushFind(UserFixtures.regularUser().build());
        userId = user.getId();

        Device device = new Device();
        device.setUserId(userId);
        device.setDeviceName("test-device");
        device.setLastSeenAt(Instant.now());
        deviceId = em.persistFlushFind(device).getId();
    }

    private SyncCursor cursor(long watermark) {
        SyncCursor cursor = new SyncCursor();
        cursor.setUserId(userId);
        cursor.setDeviceId(deviceId);
        cursor.setLastPulledChangeId(watermark);
        return cursor;
    }

    @Nested
    @DisplayName("findByUserIdAndDeviceId")
    class FindByUserIdAndDeviceId {

        @Test
        @DisplayName("shouldFindCursorByCompositeKey")
        void shouldFindCursorByCompositeKey() {
            em.persistFlushFind(cursor(7));

            assertThat(repository.findByUserIdAndDeviceId(userId, deviceId))
                    .isPresent()
                    .hasValueSatisfying(c -> assertThat(c.getLastPulledChangeId()).isEqualTo(7));
        }

        @Test
        @DisplayName("shouldReturnEmpty_whenCursorDoesNotExist")
        void shouldReturnEmpty_whenCursorDoesNotExist() {
            assertThat(repository.findByUserIdAndDeviceId(userId, deviceId)).isEmpty();
        }

        @Test
        @DisplayName("shouldIsolateByDevice")
        void shouldIsolateByDevice() {
            em.persistFlushFind(cursor(7));

            assertThat(repository.findByUserIdAndDeviceId(userId, UUID.randomUUID())).isEmpty();
        }
    }

    @Nested
    @DisplayName("advancePullWatermark")
    class AdvancePullWatermark {

        @Test
        @DisplayName("shouldAdvanceWatermark_whenNewWatermarkIsHigher")
        void shouldAdvanceWatermark_whenNewWatermarkIsHigher() {
            em.persistFlushFind(cursor(0));

            int updated = repository.advancePullWatermark(userId, deviceId, 5);

            assertThat(updated).isEqualTo(1);
            em.clear();
            assertThat(repository.findByUserIdAndDeviceId(userId, deviceId))
                    .isPresent()
                    .hasValueSatisfying(c -> assertThat(c.getLastPulledChangeId()).isEqualTo(5));
        }

        @Test
        @DisplayName("shouldNotRewindWatermark_whenNewWatermarkIsLower_monotonicGuard")
        void shouldNotRewindWatermark_whenNewWatermarkIsLower_monotonicGuard() {
            em.persistFlushFind(cursor(5));

            int updated = repository.advancePullWatermark(userId, deviceId, 3);

            assertThat(updated).isEqualTo(1);
            em.clear();
            assertThat(repository.findByUserIdAndDeviceId(userId, deviceId))
                    .isPresent()
                    .hasValueSatisfying(c -> assertThat(c.getLastPulledChangeId()).isEqualTo(5));
        }

        @Test
        @DisplayName("shouldNotRewindWatermark_whenNewWatermarkEqualsCurrent")
        void shouldNotRewindWatermark_whenNewWatermarkEqualsCurrent() {
            em.persistFlushFind(cursor(5));

            int updated = repository.advancePullWatermark(userId, deviceId, 5);

            assertThat(updated).isEqualTo(1);
            em.clear();
            assertThat(repository.findByUserIdAndDeviceId(userId, deviceId))
                    .isPresent()
                    .hasValueSatisfying(c -> assertThat(c.getLastPulledChangeId()).isEqualTo(5));
        }

        @Test
        @DisplayName("shouldCreateCursor_whenCursorDoesNotExist")
        void shouldCreateCursor_whenCursorDoesNotExist() {
            int updated = repository.advancePullWatermark(userId, deviceId, 5);

            assertThat(updated).isEqualTo(1);
            assertThat(repository.findByUserIdAndDeviceId(userId, deviceId))
                    .isPresent()
                    .hasValueSatisfying(c -> assertThat(c.getLastPulledChangeId()).isEqualTo(5));
        }
    }
}
