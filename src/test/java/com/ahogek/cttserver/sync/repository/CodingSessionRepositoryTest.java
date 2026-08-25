package com.ahogek.cttserver.sync.repository;

import com.ahogek.cttserver.common.BaseRepositoryTest;
import com.ahogek.cttserver.device.entity.Device;
import com.ahogek.cttserver.fixtures.UserFixtures;
import com.ahogek.cttserver.sync.entity.CodingSession;
import com.ahogek.cttserver.user.entity.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@BaseRepositoryTest
@DisplayName("CodingSessionRepository")
class CodingSessionRepositoryTest {

    @Autowired TestEntityManager em;

    @Autowired CodingSessionRepository repository;

    private UUID userId;

    private UUID deviceId;

    @BeforeEach
    void setUp() {
        User user = em.persistFlushFind(UserFixtures.regularUser().build());
        userId = user.getId();
        deviceId = persistDevice(userId).getId();
    }

    private Device persistDevice(UUID ownerId) {
        Device device = new Device();
        device.setUserId(ownerId);
        device.setDeviceName("test-device");
        device.setLastSeenAt(Instant.now());
        return em.persistFlushFind(device);
    }

    private CodingSession liveSession(UUID sessionUuid) {
        CodingSession session = new CodingSession();
        session.setUserId(userId);
        session.setSessionUuid(sessionUuid);
        session.setProjectName("ctt-server");
        session.setLanguage("java");
        session.setStartTime(Instant.parse("2026-08-25T08:00:00Z"));
        session.setEndTime(Instant.parse("2026-08-25T09:00:00Z"));
        session.setClientModifiedAt(Instant.parse("2026-08-25T09:00:00Z"));
        return session;
    }

    private CodingSession persistLive(UUID sessionUuid) {
        return em.persistFlushFind(liveSession(sessionUuid));
    }

    private CodingSession persistDeleted(UUID sessionUuid) {
        CodingSession session = liveSession(sessionUuid);
        session.softDelete(Instant.parse("2026-08-25T10:00:00Z"));
        return em.persistFlushFind(session);
    }

    @Nested
    @DisplayName("findAllByUserIdAndIsDeletedFalse")
    class FindAllByUserIdAndIsDeletedFalse {

        @Test
        @DisplayName("shouldReturnOnlyLiveSessions_excludingSoftDeleted")
        void shouldReturnOnlyLiveSessions_excludingSoftDeleted() {
            persistLive(UUID.randomUUID());
            persistLive(UUID.randomUUID());
            persistDeleted(UUID.randomUUID());

            List<CodingSession> result = repository.findAllByUserIdAndIsDeletedFalse(userId);

            assertThat(result).hasSize(2).allMatch(s -> !s.isDeleted());
        }

        @Test
        @DisplayName("shouldReturnEmpty_whenUserHasNoSessions")
        void shouldReturnEmpty_whenUserHasNoSessions() {
            assertThat(repository.findAllByUserIdAndIsDeletedFalse(UUID.randomUUID())).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByUserIdAndSessionUuidAndIsDeletedFalse")
    class FindByUserIdAndSessionUuidAndIsDeletedFalse {

        @Test
        @DisplayName("shouldFindLiveSession_byUserAndSessionUuid")
        void shouldFindLiveSession_byUserAndSessionUuid() {
            UUID sessionUuid = UUID.randomUUID();
            persistLive(sessionUuid);

            assertThat(repository.findByUserIdAndSessionUuidAndIsDeletedFalse(userId, sessionUuid))
                    .isPresent()
                    .hasValueSatisfying(s -> assertThat(s.getSessionUuid()).isEqualTo(sessionUuid));
        }

        @Test
        @DisplayName("shouldReturnEmpty_whenSessionIsSoftDeleted")
        void shouldReturnEmpty_whenSessionIsSoftDeleted() {
            UUID sessionUuid = UUID.randomUUID();
            persistDeleted(sessionUuid);

            assertThat(repository.findByUserIdAndSessionUuidAndIsDeletedFalse(userId, sessionUuid))
                    .isEmpty();
        }

        @Test
        @DisplayName("shouldReturnEmpty_whenSessionBelongsToAnotherUser")
        void shouldReturnEmpty_whenSessionBelongsToAnotherUser() {
            UUID sessionUuid = UUID.randomUUID();
            persistLive(sessionUuid);

            assertThat(
                            repository.findByUserIdAndSessionUuidAndIsDeletedFalse(
                                    UUID.randomUUID(), sessionUuid))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("findAllByUserIdAndUpdatedByDeviceIdAndIsDeletedFalse")
    class FindAllByUserIdAndUpdatedByDeviceIdAndIsDeletedFalse {

        @Test
        @DisplayName("shouldReturnOnlyLiveSessionsUpdatedByDevice")
        void shouldReturnOnlyLiveSessionsUpdatedByDevice() {
            CodingSession byDevice = persistLive(UUID.randomUUID());
            byDevice.setUpdatedByDeviceId(deviceId);
            em.persistAndFlush(byDevice);

            CodingSession byOtherDevice = persistLive(UUID.randomUUID());
            byOtherDevice.setUpdatedByDeviceId(persistDevice(userId).getId());
            em.persistAndFlush(byOtherDevice);

            CodingSession deletedByDevice = persistDeleted(UUID.randomUUID());
            deletedByDevice.setUpdatedByDeviceId(deviceId);
            em.persistAndFlush(deletedByDevice);

            List<CodingSession> result =
                    repository.findAllByUserIdAndUpdatedByDeviceIdAndIsDeletedFalse(
                            userId, deviceId);

            assertThat(result)
                    .hasSize(1)
                    .first()
                    .satisfies(s -> assertThat(s.getId()).isEqualTo(byDevice.getId()));
        }
    }

    @Nested
    @DisplayName("countByUserIdAndIsDeletedFalse")
    class CountByUserIdAndIsDeletedFalse {

        @Test
        @DisplayName("shouldCountOnlyLiveSessions")
        void shouldCountOnlyLiveSessions() {
            persistLive(UUID.randomUUID());
            persistLive(UUID.randomUUID());
            persistDeleted(UUID.randomUUID());

            assertThat(repository.countByUserIdAndIsDeletedFalse(userId)).isEqualTo(2);
        }

        @Test
        @DisplayName("shouldReturnZero_whenUserHasNoLiveSessions")
        void shouldReturnZero_whenUserHasNoLiveSessions() {
            assertThat(repository.countByUserIdAndIsDeletedFalse(UUID.randomUUID())).isZero();
        }
    }

    @Nested
    @DisplayName("findAllByIdInAndIsDeletedFalse")
    class FindAllByIdInAndIsDeletedFalse {

        @Test
        @DisplayName("shouldBatchFetchLiveSessions_excludingDeleted")
        void shouldBatchFetchLiveSessions_excludingDeleted() {
            CodingSession live1 = persistLive(UUID.randomUUID());
            CodingSession live2 = persistLive(UUID.randomUUID());
            CodingSession deleted = persistDeleted(UUID.randomUUID());

            List<CodingSession> result =
                    repository.findAllByIdInAndIsDeletedFalse(
                            List.of(live1.getId(), live2.getId(), deleted.getId()));

            assertThat(result)
                    .extracting(CodingSession::getId)
                    .containsExactlyInAnyOrder(live1.getId(), live2.getId());
        }

        @Test
        @DisplayName("shouldReturnEmpty_whenNoIdsMatch")
        void shouldReturnEmpty_whenNoIdsMatch() {
            assertThat(
                            repository.findAllByIdInAndIsDeletedFalse(
                                    List.of(UUID.randomUUID(), UUID.randomUUID())))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("findAllByUserIdAndServerVersionGreaterThanAndIsDeletedFalse")
    class FindAllByUserIdAndServerVersionGreaterThanAndIsDeletedFalse {

        @Test
        @DisplayName("shouldReturnLiveSessionsAboveWatermark_excludingDeleted")
        void shouldReturnLiveSessionsAboveWatermark_excludingDeleted() {
            CodingSession above = persistLive(UUID.randomUUID());
            above.setServerVersion(5);
            em.persistAndFlush(above);

            CodingSession atWatermark = persistLive(UUID.randomUUID());
            atWatermark.setServerVersion(3);
            em.persistAndFlush(atWatermark);

            CodingSession deletedAbove = persistDeleted(UUID.randomUUID());
            deletedAbove.setServerVersion(9);
            em.persistAndFlush(deletedAbove);

            List<CodingSession> result =
                    repository.findAllByUserIdAndServerVersionGreaterThanAndIsDeletedFalse(
                            userId, 3);

            assertThat(result).extracting(CodingSession::getId).containsExactly(above.getId());
        }

        @Test
        @DisplayName("shouldReturnEmpty_whenNothingAboveWatermark")
        void shouldReturnEmpty_whenNothingAboveWatermark() {
            CodingSession atWatermark = persistLive(UUID.randomUUID());
            atWatermark.setServerVersion(3);
            em.persistAndFlush(atWatermark);

            assertThat(
                            repository.findAllByUserIdAndServerVersionGreaterThanAndIsDeletedFalse(
                                    userId, 3))
                    .isEmpty();
        }
    }
}
