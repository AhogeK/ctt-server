package com.ahogek.cttserver.sync.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CodingSession Entity Behavior Tests")
class CodingSessionTest {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");

    @Nested
    @DisplayName("softDelete")
    class SoftDeleteTests {

        @Test
        @DisplayName("shouldSetDeletedFlagAndTimestamp_whenLive")
        void shouldSetDeletedFlagAndTimestamp_whenLive() {
            CodingSession session = new CodingSession();

            session.softDelete(NOW);

            assertThat(session.isDeleted()).isTrue();
            assertThat(session.getDeletedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("shouldPreserveOriginalDeletedAt_whenAlreadyDeleted_idempotent")
        void shouldPreserveOriginalDeletedAt_whenAlreadyDeleted_idempotent() {
            Instant firstDelete = NOW.minusSeconds(3600);
            CodingSession session = new CodingSession();
            session.softDelete(firstDelete);

            session.softDelete(NOW);

            assertThat(session.isDeleted()).isTrue();
            assertThat(session.getDeletedAt()).isEqualTo(firstDelete);
        }
    }

    @Nested
    @DisplayName("restore")
    class RestoreTests {

        @Test
        @DisplayName("shouldClearDeletedFlagAndTimestamp_whenDeleted")
        void shouldClearDeletedFlagAndTimestamp_whenDeleted() {
            CodingSession session = new CodingSession();
            session.softDelete(NOW);

            session.restore();

            assertThat(session.isDeleted()).isFalse();
            assertThat(session.getDeletedAt()).isNull();
        }

        @Test
        @DisplayName("shouldRemainLive_whenCalledOnLiveSession")
        void shouldRemainLive_whenCalledOnLiveSession() {
            CodingSession session = new CodingSession();

            session.restore();

            assertThat(session.isDeleted()).isFalse();
            assertThat(session.getDeletedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("bumpServerVersion")
    class BumpServerVersionTests {

        @Test
        @DisplayName("shouldIncrementServerVersionByOne")
        void shouldIncrementServerVersionByOne() {
            CodingSession session = new CodingSession();
            session.setServerVersion(3);

            session.bumpServerVersion();

            assertThat(session.getServerVersion()).isEqualTo(4);
        }

        @Test
        @DisplayName("shouldIncrementFromDefaultZero")
        void shouldIncrementFromDefaultZero() {
            CodingSession session = new CodingSession();

            session.bumpServerVersion();

            assertThat(session.getServerVersion()).isEqualTo(1);
        }
    }
}
