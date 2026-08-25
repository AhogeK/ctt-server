package com.ahogek.cttserver.sync.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SyncCursorId Composite Key Tests")
class SyncCursorIdTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DEVICE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Nested
    @DisplayName("equals")
    class EqualsTests {

        @Test
        @DisplayName("shouldBeEqual_whenSameUserAndDevice")
        void shouldBeEqual_whenSameUserAndDevice() {
            SyncCursorId a = new SyncCursorId(USER_ID, DEVICE_ID);
            SyncCursorId b = new SyncCursorId(USER_ID, DEVICE_ID);

            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("shouldNotBeEqual_whenDeviceDiffers")
        void shouldNotBeEqual_whenDeviceDiffers() {
            SyncCursorId a = new SyncCursorId(USER_ID, DEVICE_ID);
            SyncCursorId b = new SyncCursorId(USER_ID, UUID.randomUUID());

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("shouldNotBeEqual_whenUserDiffers")
        void shouldNotBeEqual_whenUserDiffers() {
            SyncCursorId a = new SyncCursorId(USER_ID, DEVICE_ID);
            SyncCursorId b = new SyncCursorId(UUID.randomUUID(), DEVICE_ID);

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("shouldNotBeEqual_whenComparedToNullOrOtherType")
        void shouldNotBeEqual_whenComparedToNullOrOtherType() {
            SyncCursorId a = new SyncCursorId(USER_ID, DEVICE_ID);

            assertThat(a).isNotEqualTo(null);
            assertThat(a).isNotEqualTo("not-a-cursor-id");
        }
    }

    @Nested
    @DisplayName("hashCode")
    class HashCodeTests {

        @Test
        @DisplayName("shouldBeConsistentForEqualInstances")
        void shouldBeConsistentForEqualInstances() {
            SyncCursorId a = new SyncCursorId(USER_ID, DEVICE_ID);
            SyncCursorId b = new SyncCursorId(USER_ID, DEVICE_ID);

            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("shouldBeStableAcrossCalls")
        void shouldBeStableAcrossCalls() {
            SyncCursorId a = new SyncCursorId(USER_ID, DEVICE_ID);

            assertThat(a.hashCode()).isEqualTo(a.hashCode());
        }
    }

    @Nested
    @DisplayName("default constructor")
    class DefaultConstructorTests {

        @Test
        @DisplayName("shouldSupportNoArgConstruction_forJpa")
        void shouldSupportNoArgConstruction_forJpa() {
            SyncCursorId id = new SyncCursorId();

            assertThat(id.getUserId()).isNull();
            assertThat(id.getDeviceId()).isNull();
        }
    }
}
