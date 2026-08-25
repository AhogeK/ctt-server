package com.ahogek.cttserver.sync.repository;

import com.ahogek.cttserver.common.BaseRepositoryTest;
import com.ahogek.cttserver.device.entity.Device;
import com.ahogek.cttserver.fixtures.UserFixtures;
import com.ahogek.cttserver.sync.entity.CodingSession;
import com.ahogek.cttserver.sync.entity.SessionChange;
import com.ahogek.cttserver.sync.enums.ChangeOp;
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
@DisplayName("SessionChangeRepository")
class SessionChangeRepositoryTest {

    @Autowired TestEntityManager em;

    @Autowired SessionChangeRepository repository;

    private UUID userId;

    private UUID sessionId;

    @BeforeEach
    void setUp() {
        User user = em.persistFlushFind(UserFixtures.regularUser().build());
        userId = user.getId();

        Device device = new Device();
        device.setUserId(userId);
        device.setDeviceName("test-device");
        device.setLastSeenAt(Instant.now());
        em.persistFlushFind(device);

        CodingSession session = new CodingSession();
        session.setUserId(userId);
        session.setSessionUuid(UUID.randomUUID());
        session.setProjectName("ctt-server");
        session.setLanguage("java");
        session.setStartTime(Instant.parse("2026-08-25T08:00:00Z"));
        session.setEndTime(Instant.parse("2026-08-25T09:00:00Z"));
        session.setClientModifiedAt(Instant.parse("2026-08-25T09:00:00Z"));
        sessionId = em.persistFlushFind(session).getId();
    }

    private SessionChange change(ChangeOp op, long serverVersion) {
        SessionChange change = new SessionChange();
        change.setUserId(userId);
        change.setSessionId(sessionId);
        change.setOp(op);
        change.setServerVersion(serverVersion);
        return change;
    }

    private SessionChange persistChange(ChangeOp op, long serverVersion) {
        return em.persistFlushFind(change(op, serverVersion));
    }

    @Nested
    @DisplayName("findAllByChangeIdGreaterThanAndUserIdOrderByChangeIdAsc")
    class FindAllByChangeIdGreaterThanAndUserIdOrderByChangeIdAsc {

        @Test
        @DisplayName("shouldReturnChangesAfterCursor_inAscendingOrder")
        void shouldReturnChangesAfterCursor_inAscendingOrder() {
            SessionChange first = persistChange(ChangeOp.UPSERT, 1);
            SessionChange second = persistChange(ChangeOp.UPSERT, 2);
            SessionChange third = persistChange(ChangeOp.DELETE, 3);

            List<SessionChange> result =
                    repository.findAllByChangeIdGreaterThanAndUserIdOrderByChangeIdAsc(
                            first.getChangeId(), userId);

            assertThat(result)
                    .extracting(SessionChange::getChangeId)
                    .containsExactly(second.getChangeId(), third.getChangeId());
            assertThat(result)
                    .extracting(SessionChange::getOp)
                    .containsExactly(ChangeOp.UPSERT, ChangeOp.DELETE);
        }

        @Test
        @DisplayName("shouldReturnEmpty_whenCursorAtOrBeyondLatest")
        void shouldReturnEmpty_whenCursorAtOrBeyondLatest() {
            SessionChange latest = persistChange(ChangeOp.UPSERT, 1);

            assertThat(
                            repository.findAllByChangeIdGreaterThanAndUserIdOrderByChangeIdAsc(
                                    latest.getChangeId(), userId))
                    .isEmpty();
        }

        @Test
        @DisplayName("shouldIsolateByUser")
        void shouldIsolateByUser() {
            SessionChange first = persistChange(ChangeOp.UPSERT, 1);
            persistChange(ChangeOp.UPSERT, 2);

            List<SessionChange> result =
                    repository.findAllByChangeIdGreaterThanAndUserIdOrderByChangeIdAsc(
                            first.getChangeId(), UUID.randomUUID());

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAllByUserIdAndSessionIdOrderByChangeIdAsc")
    class FindAllByUserIdAndSessionIdOrderByChangeIdAsc {

        @Test
        @DisplayName("shouldReturnSessionHistory_inAscendingOrder")
        void shouldReturnSessionHistory_inAscendingOrder() {
            SessionChange first = persistChange(ChangeOp.UPSERT, 1);
            SessionChange second = persistChange(ChangeOp.DELETE, 2);

            List<SessionChange> result =
                    repository.findAllByUserIdAndSessionIdOrderByChangeIdAsc(userId, sessionId);

            assertThat(result)
                    .extracting(SessionChange::getChangeId)
                    .containsExactly(first.getChangeId(), second.getChangeId());
        }

        @Test
        @DisplayName("shouldReturnEmpty_whenSessionHasNoChanges")
        void shouldReturnEmpty_whenSessionHasNoChanges() {
            assertThat(
                            repository.findAllByUserIdAndSessionIdOrderByChangeIdAsc(
                                    userId, UUID.randomUUID()))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("countByChangeIdGreaterThanAndUserId")
    class CountByChangeIdGreaterThanAndUserId {

        @Test
        @DisplayName("shouldCountPendingChangesAfterCursor")
        void shouldCountPendingChangesAfterCursor() {
            SessionChange first = persistChange(ChangeOp.UPSERT, 1);
            persistChange(ChangeOp.UPSERT, 2);
            persistChange(ChangeOp.DELETE, 3);

            assertThat(repository.countByChangeIdGreaterThanAndUserId(first.getChangeId(), userId))
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("shouldReturnZero_whenNoPendingChanges")
        void shouldReturnZero_whenNoPendingChanges() {
            SessionChange latest = persistChange(ChangeOp.UPSERT, 1);

            assertThat(repository.countByChangeIdGreaterThanAndUserId(latest.getChangeId(), userId))
                    .isZero();
        }
    }

    @Nested
    @DisplayName("findMaxChangeIdForUser")
    class FindMaxChangeIdForUser {

        @Test
        @DisplayName("shouldReturnHighestChangeIdForUser")
        void shouldReturnHighestChangeIdForUser() {
            persistChange(ChangeOp.UPSERT, 1);
            SessionChange latest = persistChange(ChangeOp.DELETE, 2);

            assertThat(repository.findMaxChangeIdForUser(userId)).isEqualTo(latest.getChangeId());
        }

        @Test
        @DisplayName("shouldReturnZero_whenUserHasNoChanges")
        void shouldReturnZero_whenUserHasNoChanges() {
            assertThat(repository.findMaxChangeIdForUser(UUID.randomUUID())).isZero();
        }
    }
}
