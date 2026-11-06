package com.ahogek.cttserver.device.repository;

import com.ahogek.cttserver.common.BaseRepositoryTest;
import com.ahogek.cttserver.device.entity.Device;
import com.ahogek.cttserver.fixtures.UserFixtures;
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
@DisplayName("DeviceRepository")
class DeviceRepositoryTest {

    /** Second precision keeps the stored value and the cutoff byte-identical. */
    private static final Instant SEEN_AT = Instant.parse("2026-11-06T12:00:00Z");

    @Autowired TestEntityManager em;

    @Autowired DeviceRepository repository;

    private UUID userId;

    @BeforeEach
    void setUp() {
        User user = em.persistFlushFind(UserFixtures.regularUser().build());
        userId = user.getId();
    }

    @Nested
    @DisplayName("touchLastSeen")
    class TouchLastSeen {

        @Test
        @DisplayName("shouldSkipTheWrite_whenStoredHeartbeatIsFresh")
        void shouldSkipTheWrite_whenStoredHeartbeatIsFresh() {
            Device device = persistDevice(userId, SEEN_AT.minusSeconds(60), null);

            int updated = repository.touchLastSeen(userId, device.getId(), SEEN_AT);

            assertThat(updated).isZero();
            assertThat(reload(device).getLastSeenAt()).isEqualTo(SEEN_AT.minusSeconds(60));
        }

        @Test
        @DisplayName("shouldSkipTheWrite_whenStoredHeartbeatIsExactlyAtTheBoundary")
        void shouldSkipTheWrite_whenStoredHeartbeatIsExactlyAtTheBoundary() {
            // The predicate is strict: a heartbeat exactly LAST_SEEN_WRITE_INTERVAL old is current.
            Device device = persistDevice(userId, SEEN_AT.minusSeconds(900), null);

            int updated = repository.touchLastSeen(userId, device.getId(), SEEN_AT);

            assertThat(updated).isZero();
            assertThat(reload(device).getLastSeenAt()).isEqualTo(SEEN_AT.minusSeconds(900));
        }

        @Test
        @DisplayName("shouldAdvanceTheHeartbeat_whenStoredOneIsStale")
        void shouldAdvanceTheHeartbeat_whenStoredOneIsStale() {
            Device device = persistDevice(userId, SEEN_AT.minusSeconds(1800), null);

            int updated = repository.touchLastSeen(userId, device.getId(), SEEN_AT);

            assertThat(updated).isEqualTo(1);
            assertThat(reload(device).getLastSeenAt()).isEqualTo(SEEN_AT);
        }

        @Test
        @DisplayName("shouldFreezeTheHeartbeat_whenDeviceRevoked")
        void shouldFreezeTheHeartbeat_whenDeviceRevoked() {
            Device device =
                    persistDevice(userId, SEEN_AT.minusSeconds(7200), SEEN_AT.minusSeconds(3600));

            int updated = repository.touchLastSeen(userId, device.getId(), SEEN_AT);

            assertThat(updated).isZero();
            assertThat(reload(device).getLastSeenAt()).isEqualTo(SEEN_AT.minusSeconds(7200));
        }

        @Test
        @DisplayName("shouldNotTouchADevice_whenOwnedByAnotherAccount")
        void shouldNotTouchADevice_whenOwnedByAnotherAccount() {
            Device device = persistDevice(userId, SEEN_AT.minusSeconds(7200), null);

            int updated = repository.touchLastSeen(UUID.randomUUID(), device.getId(), SEEN_AT);

            assertThat(updated).isZero();
            assertThat(reload(device).getLastSeenAt()).isEqualTo(SEEN_AT.minusSeconds(7200));
        }
    }

    private Device persistDevice(UUID ownerId, Instant lastSeenAt, Instant revokedAt) {
        Device device = new Device();
        device.setId(UUID.randomUUID());
        device.setUserId(ownerId);
        device.setDeviceName("test-device");
        device.setLastSeenAt(lastSeenAt);
        device.setRevokedAt(revokedAt);
        return em.persistFlushFind(device);
    }

    private Device reload(Device device) {
        return em.find(Device.class, device.getId());
    }
}
