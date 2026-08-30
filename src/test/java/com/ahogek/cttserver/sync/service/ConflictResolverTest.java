package com.ahogek.cttserver.sync.service;

import com.ahogek.cttserver.sync.entity.CodingSession;
import com.ahogek.cttserver.sync.service.ConflictResolver.Decision;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConflictResolver LWW Decision Tests")
class ConflictResolverTest {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");

    private static CodingSession session(
            boolean deleted, long serverVersion, int clientVersion, Instant clientModifiedAt) {
        return sessionWithContent(
                deleted,
                serverVersion,
                clientVersion,
                clientModifiedAt,
                "ctt-server",
                "Java",
                NOW,
                NOW.plusSeconds(3600));
    }

    private static CodingSession sessionWithContent(
            boolean deleted,
            long serverVersion,
            int clientVersion,
            Instant clientModifiedAt,
            String projectName,
            String language,
            Instant startTime,
            Instant endTime) {
        CodingSession session = new CodingSession();
        session.setDeleted(deleted);
        session.setServerVersion(serverVersion);
        session.setClientVersion(clientVersion);
        session.setClientModifiedAt(clientModifiedAt);
        session.setProjectName(projectName);
        session.setLanguage(language);
        session.setStartTime(startTime);
        session.setEndTime(endTime);
        return session;
    }

    @Nested
    @DisplayName("different versions")
    class DifferentVersionsTests {

        @Test
        @DisplayName("shouldKeepExisting_whenExistingServerVersionHigher")
        void shouldKeepExisting_whenExistingServerVersionHigher() {
            CodingSession existing = session(false, 5, 3, NOW);
            CodingSession incoming = session(false, 4, 9, NOW.plusSeconds(60));

            Decision decision = ConflictResolver.resolve(existing, incoming);

            assertThat(decision).isEqualTo(Decision.KEEP_EXISTING);
        }

        @ParameterizedTest
        @MethodSource("incomingWinsScenarios")
        @DisplayName("shouldApplyIncoming_whenIncomingWinsLww")
        void shouldApplyIncoming_whenIncomingWins(
                long existingServerVersion,
                int existingClientVersion,
                Instant existingModifiedAt,
                long incomingServerVersion,
                int incomingClientVersion,
                Instant incomingModifiedAt) {
            CodingSession existing =
                    session(
                            false,
                            existingServerVersion,
                            existingClientVersion,
                            existingModifiedAt);
            CodingSession incoming =
                    sessionWithContent(
                            false,
                            incomingServerVersion,
                            incomingClientVersion,
                            incomingModifiedAt,
                            "other",
                            "Kotlin",
                            NOW,
                            NOW.plusSeconds(3600));

            Decision decision = ConflictResolver.resolve(existing, incoming);

            assertThat(decision).isEqualTo(Decision.APPLY_INCOMING);
        }

        private static Stream<Arguments> incomingWinsScenarios() {
            return Stream.of(
                    // incoming server version higher
                    Arguments.of(4L, 9, NOW.plusSeconds(60), 5L, 3, NOW),
                    // client version higher, server versions equal
                    Arguments.of(5L, 3, NOW, 5L, 4, NOW.plusSeconds(60)),
                    // client modified-at later, versions equal
                    Arguments.of(5L, 3, NOW, 5L, 3, NOW.plusSeconds(60)));
        }

        @Test
        @DisplayName("shouldKeepExisting_whenServerVersionsEqual_andClientVersionLower")
        void shouldKeepExisting_whenServerVersionsEqual_andClientVersionLower() {
            CodingSession existing = session(false, 5, 4, NOW);
            CodingSession incoming = session(false, 5, 3, NOW.plusSeconds(60));

            Decision decision = ConflictResolver.resolve(existing, incoming);

            assertThat(decision).isEqualTo(Decision.KEEP_EXISTING);
        }

        @Test
        @DisplayName("shouldKeepExisting_whenVersionsEqual_andClientModifiedAtEarlier")
        void shouldKeepExisting_whenVersionsEqual_andClientModifiedAtEarlier() {
            CodingSession existing = session(false, 5, 3, NOW.plusSeconds(60));
            CodingSession incoming = session(false, 5, 3, NOW);

            Decision decision = ConflictResolver.resolve(existing, incoming);

            assertThat(decision).isEqualTo(Decision.KEEP_EXISTING);
        }

        @Test
        @DisplayName("shouldCompareClientVersion_whenIncomingHasNoServerVersion_freshSubmission")
        void shouldCompareClientVersion_whenIncomingHasNoServerVersion_freshSubmission() {
            CodingSession existing = session(false, 5, 3, NOW);
            CodingSession incoming =
                    sessionWithContent(
                            false,
                            0,
                            4,
                            NOW.plusSeconds(60),
                            "other",
                            "Kotlin",
                            NOW,
                            NOW.plusSeconds(3600));

            Decision decision = ConflictResolver.resolve(existing, incoming);

            assertThat(decision).isEqualTo(Decision.APPLY_INCOMING);
        }

        @Test
        @DisplayName("shouldCompareClientVersion_whenExistingHasNoServerVersion")
        void shouldCompareClientVersion_whenExistingHasNoServerVersion() {
            CodingSession existing = session(false, 0, 4, NOW);
            CodingSession incoming = session(false, 5, 3, NOW.plusSeconds(60));

            Decision decision = ConflictResolver.resolve(existing, incoming);

            assertThat(decision).isEqualTo(Decision.KEEP_EXISTING);
        }
    }

    @Nested
    @DisplayName("identical states")
    class IdenticalStatesTests {

        @Test
        @DisplayName("shouldKeepExisting_whenLiveStatesIdentical")
        void shouldKeepExisting_whenLiveStatesIdentical() {
            CodingSession existing = session(false, 5, 3, NOW);
            CodingSession incoming = session(false, 5, 3, NOW);

            Decision decision = ConflictResolver.resolve(existing, incoming);

            assertThat(decision).isEqualTo(Decision.KEEP_EXISTING);
        }

        @Test
        @DisplayName("shouldKeepExisting_whenDeletedStatesIdentical")
        void shouldKeepExisting_whenDeletedStatesIdentical() {
            CodingSession existing = session(true, 5, 3, NOW);
            CodingSession incoming = session(true, 5, 3, NOW);

            Decision decision = ConflictResolver.resolve(existing, incoming);

            assertThat(decision).isEqualTo(Decision.KEEP_EXISTING);
        }
    }

    @Nested
    @DisplayName("content identity")
    class ContentIdentityTests {

        @Test
        @DisplayName("shouldKeepExisting_whenContentSame_butClientModifiedAtLater")
        void shouldKeepExisting_whenContentSame_butClientModifiedAtLater() {
            CodingSession existing = session(false, 5, 3, NOW);
            CodingSession incoming = session(false, 5, 3, NOW.plusSeconds(60));

            Decision decision = ConflictResolver.resolve(existing, incoming);

            assertThat(decision).isEqualTo(Decision.KEEP_EXISTING);
        }

        @Test
        @DisplayName("shouldKeepExisting_whenContentSame_butClientVersionHigher")
        void shouldKeepExisting_whenContentSame_butClientVersionHigher() {
            CodingSession existing = session(false, 5, 3, NOW);
            CodingSession incoming = session(false, 5, 4, NOW.plusSeconds(60));

            Decision decision = ConflictResolver.resolve(existing, incoming);

            assertThat(decision).isEqualTo(Decision.KEEP_EXISTING);
        }

        @Test
        @DisplayName("shouldKeepExisting_whenContentSame_butServerVersionHigher")
        void shouldKeepExisting_whenContentSame_butServerVersionHigher() {
            CodingSession existing = session(false, 4, 9, NOW.plusSeconds(60));
            CodingSession incoming = session(false, 5, 3, NOW);

            Decision decision = ConflictResolver.resolve(existing, incoming);

            assertThat(decision).isEqualTo(Decision.KEEP_EXISTING);
        }
    }

    @Nested
    @DisplayName("delete competition")
    class DeleteCompetitionTests {

        @Test
        @DisplayName("shouldApplyDelete_whenIncomingDeleted_andExistingLive")
        void shouldApplyDelete_whenIncomingDeleted_andExistingLive() {
            CodingSession existing = session(false, 5, 3, NOW);
            CodingSession incoming = session(true, 5, 3, NOW);

            Decision decision = ConflictResolver.resolve(existing, incoming);

            assertThat(decision).isEqualTo(Decision.APPLY_DELETE);
        }

        @Test
        @DisplayName("shouldKeepExisting_whenExistingDeleted_andIncomingLive")
        void shouldKeepExisting_whenExistingDeleted_andIncomingLive() {
            CodingSession existing = session(true, 5, 3, NOW);
            CodingSession incoming = session(false, 5, 3, NOW);

            Decision decision = ConflictResolver.resolve(existing, incoming);

            assertThat(decision).isEqualTo(Decision.KEEP_EXISTING);
        }

        @Test
        @DisplayName("shouldApplyDelete_whenBothDeleted_andIncomingServerVersionHigher")
        void shouldApplyDelete_whenBothDeleted_andIncomingServerVersionHigher() {
            CodingSession existing = session(true, 5, 3, NOW);
            CodingSession incoming = session(true, 6, 3, NOW);

            Decision decision = ConflictResolver.resolve(existing, incoming);

            assertThat(decision).isEqualTo(Decision.APPLY_DELETE);
        }

        @Test
        @DisplayName("shouldApplyDelete_whenBothDeleted_andIncomingClientVersionHigher")
        void shouldApplyDelete_whenBothDeleted_andIncomingClientVersionHigher() {
            CodingSession existing = session(true, 5, 3, NOW);
            CodingSession incoming = session(true, 5, 4, NOW.plusSeconds(60));

            Decision decision = ConflictResolver.resolve(existing, incoming);

            assertThat(decision).isEqualTo(Decision.APPLY_DELETE);
        }

        @Test
        @DisplayName("shouldKeepExisting_whenBothDeleted_andExistingServerVersionHigher")
        void shouldKeepExisting_whenBothDeleted_andExistingServerVersionHigher() {
            CodingSession existing = session(true, 6, 3, NOW);
            CodingSession incoming = session(true, 5, 4, NOW.plusSeconds(60));

            Decision decision = ConflictResolver.resolve(existing, incoming);

            assertThat(decision).isEqualTo(Decision.KEEP_EXISTING);
        }
    }

    @Nested
    @DisplayName("pure function")
    class PureFunctionTests {

        @Test
        @DisplayName("shouldNotMutateInputs_whenResolving")
        void shouldNotMutateInputs_whenResolving() {
            CodingSession existing = session(false, 5, 3, NOW);
            CodingSession incoming = session(true, 5, 4, NOW.plusSeconds(60));

            ConflictResolver.resolve(existing, incoming);

            assertThat(existing.isDeleted()).isFalse();
            assertThat(existing.getServerVersion()).isEqualTo(5);
            assertThat(existing.getClientVersion()).isEqualTo(3);
            assertThat(existing.getClientModifiedAt()).isEqualTo(NOW);
            assertThat(incoming.isDeleted()).isTrue();
            assertThat(incoming.getServerVersion()).isEqualTo(5);
            assertThat(incoming.getClientVersion()).isEqualTo(4);
            assertThat(incoming.getClientModifiedAt()).isEqualTo(NOW.plusSeconds(60));
        }
    }
}
