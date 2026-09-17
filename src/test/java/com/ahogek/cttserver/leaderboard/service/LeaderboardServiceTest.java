package com.ahogek.cttserver.leaderboard.service;

import com.ahogek.cttserver.common.exception.ValidationException;
import com.ahogek.cttserver.common.lock.RedisLockService;
import com.ahogek.cttserver.language.LanguageVocabulary;
import com.ahogek.cttserver.leaderboard.dto.LanguageBoardDto;
import com.ahogek.cttserver.leaderboard.dto.LeaderboardResponse;
import com.ahogek.cttserver.leaderboard.enums.LeaderboardDimension;
import com.ahogek.cttserver.leaderboard.enums.LeaderboardPeriod;
import com.ahogek.cttserver.sync.entity.CodingSession;
import com.ahogek.cttserver.sync.repository.CodingSessionRepository;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("LeaderboardService")
class LeaderboardServiceTest {

    // Fixed reference date: Monday 2026-08-31 12:00 UTC (ISO week starts Monday 08-31).
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC);

    private StringRedisTemplate redisTemplate;
    private RedisLockService redisLock;
    private ZSetOperations<String, String> zsetOps;
    private SetOperations<String, String> setOps;
    private ValueOperations<String, String> valueOps;
    private CodingSessionRepository codingSessionRepository;
    private UserRepository userRepository;
    private LeaderboardService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        setOps = mock(SetOperations.class);
        redisLock = mock(RedisLockService.class);
        when(redisLock.tryAcquire(anyString(), any(Duration.class))).thenReturn(true);
        zsetOps = mock(ZSetOperations.class);
        valueOps = mock(ValueOperations.class);
        codingSessionRepository = mock(CodingSessionRepository.class);
        userRepository = mock(UserRepository.class);
        when(redisTemplate.opsForZSet()).thenReturn(zsetOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        service =
                new LeaderboardService(
                        redisTemplate,
                        redisLock,
                        codingSessionRepository,
                        userRepository,
                        FIXED_CLOCK,
                        VOCABULARY);
    }

    private static final LanguageVocabulary VOCABULARY = new LanguageVocabulary(new ObjectMapper());

    private CodingSession session(Instant start, Instant end) {
        CodingSession session = new CodingSession();
        session.setStartTime(start);
        session.setEndTime(end);
        session.setProjectName("p");
        session.setLanguage("l");
        return session;
    }

    private CodingSession session(Instant start, Instant end, String language) {
        CodingSession session = session(start, end);
        session.setLanguage(language);
        return session;
    }

    private static Instant at(String dateTime) {
        return Instant.parse(dateTime + "Z");
    }

    @Nested
    @DisplayName("updateUserScores")
    class UpdateUserScoresTests {

        @Test
        @DisplayName("should write every supported dimension/period key")
        void shouldWriteAllKeys_whenUserHasSessions() {
            List<CodingSession> sessions =
                    List.of(
                            session(at("2026-08-30T10:00:00"), at("2026-08-30T12:00:00")),
                            session(at("2026-08-30T11:00:00"), at("2026-08-30T13:00:00")));
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(sessions);

            service.updateUserScores(userId);

            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            // 20 legal dimension/period pairs: TOTAL 4, STREAK 1, NIGHT_OWL 4, EARLY_BIRD 4,
            // GROWTH 3 (no ALL), ACTIVE_DAYS 4.
            verify(zsetOps, times(20)).add(keyCaptor.capture(), anyString(), anyDouble());
            assertThat(keyCaptor.getAllValues())
                    .containsExactlyInAnyOrder(
                            "leaderboard:total",
                            "leaderboard:total:week:2026-08-31",
                            "leaderboard:total:month:2026-08-01",
                            "leaderboard:total:year:2026-01-01",
                            "leaderboard:streak",
                            "leaderboard:night_owl",
                            "leaderboard:night_owl:week:2026-08-31",
                            "leaderboard:night_owl:month:2026-08-01",
                            "leaderboard:night_owl:year:2026-01-01",
                            "leaderboard:early_bird",
                            "leaderboard:early_bird:week:2026-08-31",
                            "leaderboard:early_bird:month:2026-08-01",
                            "leaderboard:early_bird:year:2026-01-01",
                            "leaderboard:active_days",
                            "leaderboard:active_days:week:2026-08-31",
                            "leaderboard:active_days:month:2026-08-01",
                            "leaderboard:active_days:year:2026-01-01",
                            "leaderboard:growth:week:2026-08-31",
                            "leaderboard:growth:month:2026-08-01",
                            "leaderboard:growth:year:2026-01-01");
        }

        @Test
        @DisplayName("should write merged total per period key")
        void shouldWriteMergedTotal_whenSessionsSpanPeriods() {
            // 1h in the current week (09-01) and 1h in the previous week (08-25)
            List<CodingSession> sessions =
                    List.of(
                            session(at("2026-09-01T10:00:00"), at("2026-09-01T11:00:00")),
                            session(at("2026-08-25T10:00:00"), at("2026-08-25T11:00:00")));
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(sessions);

            service.updateUserScores(userId);

            verify(zsetOps).add("leaderboard:total", userId.toString(), 7200.0);
            verify(zsetOps).add("leaderboard:total:week:2026-08-31", userId.toString(), 3600.0);
            verify(zsetOps).add("leaderboard:total:month:2026-08-01", userId.toString(), 3600.0);
            verify(zsetOps).add("leaderboard:total:year:2026-01-01", userId.toString(), 7200.0);
        }

        @Test
        @DisplayName("should write one board per language and merge spellings")
        void shouldWriteLanguageBoards_whenSpellingsDiffer() {
            List<CodingSession> sessions =
                    List.of(
                            session(at("2026-08-30T10:00:00"), at("2026-08-30T11:00:00"), "JAVA"),
                            session(at("2026-08-30T11:00:00"), at("2026-08-30T12:00:00"), "java"),
                            session(at("2026-08-30T12:00:00"), at("2026-08-30T13:00:00"), "Kotlin"),
                            session(
                                    at("2026-08-30T13:00:00"),
                                    at("2026-08-30T14:00:00"),
                                    "textmate"));
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(sessions);

            service.updateUserScores(userId);

            // Two spellings of Java are one language, so both sessions land on a single board.
            verify(zsetOps).add("leaderboard:language:Java", userId.toString(), 7200.0);
            verify(zsetOps).add("leaderboard:language:Kotlin", userId.toString(), 3600.0);
            // The IDE internal is not a language and must not create a board.
            verify(zsetOps, never())
                    .add(eq("leaderboard:language:Other"), anyString(), anyDouble());
            // Both real languages are offered in the selector.
            verify(setOps, atLeastOnce()).add("leaderboard:languages", "Java");
            verify(setOps, atLeastOnce()).add("leaderboard:languages", "Kotlin");
            verify(setOps, never()).add("leaderboard:languages", "Other");
        }

        @Test
        @DisplayName("should write the longest streak into the streak ZSet")
        void shouldWriteMaxStreak_whenConsecutiveDays() {
            List<CodingSession> sessions =
                    List.of(
                            session(at("2026-08-28T10:00:00"), at("2026-08-28T11:00:00")),
                            session(at("2026-08-29T10:00:00"), at("2026-08-29T11:00:00")),
                            session(at("2026-08-30T10:00:00"), at("2026-08-30T11:00:00")));
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(sessions);

            service.updateUserScores(userId);

            verify(zsetOps).add("leaderboard:streak", userId.toString(), 3.0);
        }

        @Test
        @DisplayName("should write night-owl window duration")
        void shouldWriteNightOwl_whenSessionInWindow() {
            List<CodingSession> sessions =
                    List.of(
                            // 23:00-24:00 inside the 22:00-05:00 window
                            session(at("2026-08-30T23:00:00"), at("2026-08-31T00:00:00")),
                            // 15:00-16:00 outside the window
                            session(at("2026-08-30T15:00:00"), at("2026-08-30T16:00:00")));
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(sessions);

            service.updateUserScores(userId);

            verify(zsetOps).add("leaderboard:night_owl", userId.toString(), 3600.0);
        }

        @Test
        @DisplayName("should write early-bird window duration")
        void shouldWriteEarlyBird_whenSessionInWindow() {
            List<CodingSession> sessions =
                    List.of(
                            // 07:00-08:00 inside the 06:00-09:00 window
                            session(at("2026-08-30T07:00:00"), at("2026-08-30T08:00:00")));
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(sessions);

            service.updateUserScores(userId);

            verify(zsetOps).add("leaderboard:early_bird", userId.toString(), 3600.0);
        }

        @Test
        @DisplayName("should write week-over-week net growth")
        void shouldWriteGrowthNet_whenWeekDiffers() {
            // 2h this week (09-01) minus 1h last week (08-25) = 1h net growth
            List<CodingSession> sessions =
                    List.of(
                            session(at("2026-09-01T10:00:00"), at("2026-09-01T12:00:00")),
                            session(at("2026-08-25T10:00:00"), at("2026-08-25T11:00:00")));
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(sessions);

            service.updateUserScores(userId);

            verify(zsetOps).add("leaderboard:growth:week:2026-08-31", userId.toString(), 3600.0);
        }

        @Test
        @DisplayName("should set TTL only on period keys")
        void shouldSetTtl_whenWritingPeriodKeys() {
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(List.of());

            service.updateUserScores(userId);

            // 15 of the 20 legal pairs carry a period: TOTAL 3, NIGHT_OWL 3, EARLY_BIRD 3,
            // ACTIVE_DAYS 3, GROWTH 3. The five ALL keys never expire.
            verify(redisTemplate, times(15)).expire(anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("should swallow Redis failures so the push is not affected")
        void shouldSwallowRedisFailure_whenZAddThrows() {
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(List.of());
            doThrow(new RuntimeException("redis down"))
                    .when(zsetOps)
                    .add(anyString(), anyString(), anyDouble());

            assertThatCode(() -> service.updateUserScores(userId)).doesNotThrowAnyException();
            // the per-user lock is released even when the write failed
            verify(redisTemplate).delete("leaderboard:lock:" + userId);
        }

        @Test
        @DisplayName("should remove the user from a language board they no longer code in")
        void shouldRemoveLanguageBoards_whenLanguageIsNoLongerUsed() {
            // Ranked in Kotlin on the previous recompute, and no longer coding in it.
            when(valueOps.get("leaderboard:user:languages:" + userId)).thenReturn("Java\nKotlin");
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(List.of(javaSession()));

            service.updateUserScores(userId);

            verify(zsetOps).remove("leaderboard:language:Kotlin", userId.toString());
            verify(zsetOps)
                    .remove("leaderboard:language:Kotlin:week:2026-08-31", userId.toString());
            verify(zsetOps)
                    .remove("leaderboard:language:Kotlin:month:2026-08-01", userId.toString());
            verify(zsetOps)
                    .remove("leaderboard:language:Kotlin:year:2026-01-01", userId.toString());
            // The board held nobody else, so the language leaves the index with its last member.
            verify(setOps).remove("leaderboard:languages", "Kotlin");
            // Java is still coded in, so it is neither removed from nor dropped out of the index.
            verify(zsetOps, never()).remove("leaderboard:language:Java", userId.toString());
            // The record moves forward, which is what makes the next recompute exact.
            verify(valueOps).set("leaderboard:user:languages:" + userId, "Java");
        }

        @Test
        @DisplayName("should keep a language indexed while someone else is still ranked in it")
        void shouldKeepLanguageIndexed_whenOthersAreStillRanked() {
            when(valueOps.get("leaderboard:user:languages:" + userId)).thenReturn("Kotlin");
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(List.of(javaSession()));
            when(zsetOps.size("leaderboard:language:Kotlin")).thenReturn(3L);

            service.updateUserScores(userId);

            verify(zsetOps).remove("leaderboard:language:Kotlin", userId.toString());
            verify(setOps, never()).remove("leaderboard:languages", "Kotlin");
        }

        private CodingSession javaSession() {
            CodingSession java =
                    session(
                            Instant.parse("2026-08-31T10:00:00Z"),
                            Instant.parse("2026-08-31T11:00:00Z"));
            java.setLanguage("Java");
            return java;
        }
    }

    @Nested
    @DisplayName("languageBoards")
    class LanguageBoardsTests {

        @Test
        @DisplayName("should list only the boards that hold someone")
        void shouldListPopulatedBoards_whenDirectoryRequested() {
            when(setOps.members("leaderboard:languages")).thenReturn(Set.of("Java"));

            List<LanguageBoardDto> boards = service.languageBoards(false);

            // A board nobody is ranked in is a dead end for the selector this list exists for. The
            // vocabulary is large and the set of languages anyone has pushed is small, so offering
            // the vocabulary buries the boards that can be opened under hundreds that cannot.
            assertThat(boards).extracting(LanguageBoardDto::name).containsExactly("Java");
            assertThat(boards).allMatch(LanguageBoardDto::hasMembers);
        }

        @Test
        @DisplayName("should list the whole vocabulary when empty boards are asked for")
        void shouldListVocabulary_whenEmptyBoardsRequested() {
            when(setOps.members("leaderboard:languages")).thenReturn(Set.of("Java"));

            List<LanguageBoardDto> boards = service.languageBoards(true);

            // The neighbouring question — which boards exist at all — stays answerable, including
            // the
            // membership fact per entry for a client that wants to sort or filter the long list.
            assertThat(boards)
                    .extracting(LanguageBoardDto::name)
                    .contains("Java", "Elixir", "Astro", "Kotlin")
                    .hasSize(VOCABULARY.languages().size());
            assertThat(boards)
                    .filteredOn(LanguageBoardDto::hasMembers)
                    .extracting(LanguageBoardDto::name)
                    .containsExactly("Java");
        }

        @Test
        @DisplayName("should list nothing when no board holds anyone")
        void shouldListNothing_whenNoBoardHasMembers() {
            when(setOps.members("leaderboard:languages")).thenReturn(Set.of());

            assertThat(service.languageBoards(false)).isEmpty();
            // Nothing was written yet, so every board is empty — the vocabulary is what remains.
            assertThat(service.languageBoards(true))
                    .isNotEmpty()
                    .noneMatch(LanguageBoardDto::hasMembers);
        }
    }

    @Nested
    @DisplayName("getLeaderboard")
    class GetLeaderboardTests {

        @Test
        @DisplayName("should map ZSet members to users and ranks")
        void shouldReturnEntriesWithUsersAndRanks() {
            ZSetOperations.TypedTuple<String> first =
                    ZSetOperations.TypedTuple.of(userId.toString(), 10800.0);
            ZSetOperations.TypedTuple<String> second =
                    ZSetOperations.TypedTuple.of(otherUserId.toString(), 5400.0);
            Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>();
            tuples.add(first);
            tuples.add(second);
            when(zsetOps.reverseRangeWithScores("leaderboard:total", 0, 19)).thenReturn(tuples);

            User user = new User();
            user.setId(userId);
            user.setDisplayName("Alice");
            when(userRepository.findAllById(List.of(userId, otherUserId)))
                    .thenReturn(List.of(user));
            // Two members: the leader has nobody above them, the second has one.
            when(zsetOps.count("leaderboard:total", Math.nextUp(10800.0), Double.POSITIVE_INFINITY))
                    .thenReturn(0L);
            when(zsetOps.count("leaderboard:total", Math.nextUp(5400.0), Double.POSITIVE_INFINITY))
                    .thenReturn(1L);
            when(zsetOps.score("leaderboard:total", userId.toString())).thenReturn(10800.0);
            when(zsetOps.size("leaderboard:total")).thenReturn(2L);

            LeaderboardResponse response =
                    service.getLeaderboard(
                            LeaderboardDimension.TOTAL, LeaderboardPeriod.ALL, null, 20, 0, userId);

            assertThat(response.entries()).hasSize(2);
            assertThat(response.entries().getFirst().userId()).isEqualTo(userId);
            assertThat(response.entries().getFirst().displayName()).isEqualTo("Alice");
            assertThat(response.entries().getFirst().score()).isEqualTo(10800);
            assertThat(response.entries().getFirst().rank()).isEqualTo(1);
            assertThat(response.entries().get(1).userId()).isEqualTo(otherUserId);
            assertThat(response.entries().get(1).rank()).isEqualTo(2);
            assertThat(response.currentUserRank()).isEqualTo(1);
            assertThat(response.totalParticipants()).isEqualTo(2);
        }

        @Test
        @DisplayName("should share a rank between tied scores and resume after the gap")
        void shouldShareRank_whenScoresTie() {
            // 90 is held by two members, so they share rank 2 and the next distinct score is 4.
            ZSetOperations.TypedTuple<String> first =
                    ZSetOperations.TypedTuple.of(userId.toString(), 100.0);
            ZSetOperations.TypedTuple<String> tiedA =
                    ZSetOperations.TypedTuple.of(otherUserId.toString(), 90.0);
            ZSetOperations.TypedTuple<String> tiedB =
                    ZSetOperations.TypedTuple.of(UUID.randomUUID().toString(), 90.0);
            ZSetOperations.TypedTuple<String> fourth =
                    ZSetOperations.TypedTuple.of(UUID.randomUUID().toString(), 80.0);
            Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>();
            tuples.add(first);
            tuples.add(tiedA);
            tuples.add(tiedB);
            tuples.add(fourth);
            when(zsetOps.reverseRangeWithScores("leaderboard:total", 0, 19)).thenReturn(tuples);
            when(userRepository.findAllById(any())).thenReturn(List.of());
            when(zsetOps.count("leaderboard:total", Math.nextUp(100.0), Double.POSITIVE_INFINITY))
                    .thenReturn(0L);
            when(zsetOps.score("leaderboard:total", userId.toString())).thenReturn(100.0);
            when(zsetOps.size("leaderboard:total")).thenReturn(4L);

            LeaderboardResponse response =
                    service.getLeaderboard(
                            LeaderboardDimension.TOTAL, LeaderboardPeriod.ALL, null, 20, 0, userId);

            assertThat(response.entries()).extracting("rank").containsExactly(1L, 2L, 2L, 4L);
        }

        @Test
        @DisplayName("should report the global rank of the page's first member after paging")
        void shouldReportGlobalRank_whenPageStartsMidRanking() {
            // Page 2 of a ranking whose top two members are tied: the page opens at absolute
            // position 3 with a score of 90, so its members keep the rank 2 they hold globally.
            ZSetOperations.TypedTuple<String> tied =
                    ZSetOperations.TypedTuple.of(otherUserId.toString(), 90.0);
            when(zsetOps.reverseRangeWithScores("leaderboard:total", 2, 3))
                    .thenReturn(Set.of(tied));
            when(userRepository.findAllById(List.of(otherUserId))).thenReturn(List.of());
            when(zsetOps.count("leaderboard:total", Math.nextUp(90.0), Double.POSITIVE_INFINITY))
                    .thenReturn(1L);
            when(zsetOps.score("leaderboard:total", userId.toString())).thenReturn(null);
            when(zsetOps.size("leaderboard:total")).thenReturn(4L);

            LeaderboardResponse response =
                    service.getLeaderboard(
                            LeaderboardDimension.TOTAL, LeaderboardPeriod.ALL, null, 2, 2, userId);

            // offset+1 would have said 3; the correct competition rank is 2.
            assertThat(response.entries()).hasSize(1);
            assertThat(response.entries().getFirst().rank()).isEqualTo(2);
            assertThat(response.totalParticipants()).isEqualTo(4);
        }

        @Test
        @DisplayName("should read the period-bucketed key for a period ranking")
        void shouldReadPeriodKey_whenPeriodRequested() {
            when(zsetOps.reverseRangeWithScores("leaderboard:total:week:2026-08-31", 0, 19))
                    .thenReturn(Set.of());

            service.getLeaderboard(
                    LeaderboardDimension.TOTAL, LeaderboardPeriod.WEEK, null, 20, 0, userId);

            verify(zsetOps).reverseRangeWithScores("leaderboard:total:week:2026-08-31", 0, 19);
        }

        @Test
        @DisplayName("should report null current rank when the user is not ranked")
        void shouldReturnNullRank_whenUserNotRanked() {
            when(zsetOps.reverseRangeWithScores("leaderboard:total", 0, 19)).thenReturn(Set.of());
            // unranked: no score of their own in this ZSet
            when(zsetOps.score("leaderboard:total", userId.toString())).thenReturn(null);

            LeaderboardResponse response =
                    service.getLeaderboard(
                            LeaderboardDimension.TOTAL, LeaderboardPeriod.ALL, null, 20, 0, userId);

            assertThat(response.entries()).isEmpty();
            assertThat(response.currentUserRank()).isNull();
            assertThat(response.totalParticipants()).isZero();
        }

        @Test
        @DisplayName("should tolerate a missing user account")
        void shouldTolerateMissingUser() {
            ZSetOperations.TypedTuple<String> first =
                    ZSetOperations.TypedTuple.of(userId.toString(), 100.0);
            when(zsetOps.reverseRangeWithScores("leaderboard:streak", 5, 14))
                    .thenReturn(Set.of(first));
            when(userRepository.findAllById(List.of(userId))).thenReturn(List.of());
            // The caller sits at position 6 with a score of 100, and exactly 5 members score higher
            // than them — their competition rank is therefore 6, matching their page position.
            when(zsetOps.score("leaderboard:streak", userId.toString())).thenReturn(100.0);
            when(zsetOps.count("leaderboard:streak", 100.0, Double.POSITIVE_INFINITY))
                    .thenReturn(0L);
            when(zsetOps.count("leaderboard:streak", Math.nextUp(100.0), Double.POSITIVE_INFINITY))
                    .thenReturn(5L);
            when(zsetOps.size("leaderboard:streak")).thenReturn(11L);

            LeaderboardResponse response =
                    service.getLeaderboard(
                            LeaderboardDimension.STREAK,
                            LeaderboardPeriod.ALL,
                            null,
                            10,
                            5,
                            userId);

            assertThat(response.entries()).hasSize(1);
            assertThat(response.entries().getFirst().displayName()).isNull();
            assertThat(response.entries().getFirst().rank()).isEqualTo(6);
            assertThat(response.currentUserRank()).isEqualTo(6);
            assertThat(response.totalParticipants()).isEqualTo(11);
        }

        @Test
        @DisplayName("should pass the offset and limit through to the ZSet range")
        void shouldApplyPaging() {
            when(zsetOps.reverseRangeWithScores("leaderboard:total", 20, 29)).thenReturn(Set.of());
            when(zsetOps.reverseRank("leaderboard:total", userId.toString())).thenReturn(null);

            service.getLeaderboard(
                    LeaderboardDimension.TOTAL, LeaderboardPeriod.ALL, null, 10, 20, userId);

            verify(zsetOps).reverseRangeWithScores("leaderboard:total", 20, 29);
        }

        @Test
        @DisplayName("should reject an illegal dimension/period combination")
        void shouldRejectCombination_whenPeriodUnsupported() {
            assertThatThrownBy(
                            () ->
                                    service.getLeaderboard(
                                            LeaderboardDimension.STREAK,
                                            LeaderboardPeriod.WEEK,
                                            null,
                                            20,
                                            0,
                                            userId))
                    .isInstanceOf(ValidationException.class);
            assertThatThrownBy(
                            () ->
                                    service.getLeaderboard(
                                            LeaderboardDimension.GROWTH,
                                            LeaderboardPeriod.ALL,
                                            null,
                                            20,
                                            0,
                                            userId))
                    .isInstanceOf(ValidationException.class);
        }
    }
}
