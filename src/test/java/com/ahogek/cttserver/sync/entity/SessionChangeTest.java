package com.ahogek.cttserver.sync.entity;

import com.ahogek.cttserver.sync.enums.ChangeOp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SessionChange Entity Behavior Tests")
class SessionChangeTest {

    @Nested
    @DisplayName("construction")
    class ConstructionTests {

        @Test
        @DisplayName("shouldRoundTripAllMutableFields")
        void shouldRoundTripAllMutableFields() {
            UUID userId = UUID.randomUUID();
            UUID deviceId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();

            SessionChange change = new SessionChange();
            change.setUserId(userId);
            change.setDeviceId(deviceId);
            change.setSessionId(sessionId);
            change.setOp(ChangeOp.UPSERT);
            change.setServerVersion(7);

            assertThat(change.getUserId()).isEqualTo(userId);
            assertThat(change.getDeviceId()).isEqualTo(deviceId);
            assertThat(change.getSessionId()).isEqualTo(sessionId);
            assertThat(change.getOp()).isEqualTo(ChangeOp.UPSERT);
            assertThat(change.getServerVersion()).isEqualTo(7);
        }

        @Test
        @DisplayName("shouldLeaveChangeIdAndHappenedAtNull_beforePersist")
        void shouldLeaveChangeIdAndHappenedAtNull_beforePersist() {
            SessionChange change = new SessionChange();

            assertThat(change.getChangeId()).isNull();
            assertThat(change.getHappenedAt()).isNull();
        }

        @Test
        @DisplayName("shouldAllowNullDeviceId_forServerOriginatedChanges")
        void shouldAllowNullDeviceId_forServerOriginatedChanges() {
            SessionChange change = new SessionChange();
            change.setUserId(UUID.randomUUID());
            change.setSessionId(UUID.randomUUID());
            change.setOp(ChangeOp.DELETE);
            change.setServerVersion(1);

            assertThat(change.getDeviceId()).isNull();
        }
    }
}
