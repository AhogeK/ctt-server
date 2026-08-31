package com.ahogek.cttserver.leaderboard.service;

import com.ahogek.cttserver.common.exception.ValidationException;
import com.ahogek.cttserver.common.lock.RedisLockService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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
        redisLock = mock(RedisLockService.class);
        when(redisLock.tryAcquire(anyString(), any(Duration.class))).thenReturn(true);
        zsetOps = mock(ZSetOperations.class);
        valueOps = mock(ValueOperations.class);
        codingSessionRepository = mock(CodingSessionRepository.class);
        userRepository = mock(UserRepository.class);
        when(redisTemplate.opsForZSet()).thenReturn(zsetOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        service =
                new LeaderboardService(
                        redisTemplate,
                        redisLock,
                        codingSessionRepository,
                        userRepository,
                        FIXED_CLOCK);
    }

    private CodingSession session(Instant start, Instant end) {
        CodingSession session = new CodingSession();
        session.setStartTime(start);
        session.setEndTime(end);
        session.setProjectName("p");
        session.setLanguage("l");
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
            verify(zsetOps, times(8)).add(keyCaptor.capture(), anyString(), anyDouble());
            assertThat(keyCaptor.getAllValues())
                    .containsExactlyInAnyOrder(
                            "leaderboard:total",
                            "leaderboard:total:week:2026-08-31",
                            "leaderboard:total:month:2026-08-01",
                            "leaderboard:total:year:2026-01-01",
                            "leaderboard:streak",
                            "leaderboard:night_owl",
                            "leaderboard:early_bird",
                            "leaderboard:growth:week:2026-08-31");
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

            // week x2 (total-week, growth-week), month, year
            verify(redisTemplate, times(4)).expire(anyString(), any(Duration.class));
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
            when(zsetOps.reverseRank("leaderboard:total", userId.toString())).thenReturn(0L);

            LeaderboardResponse response =
                    service.getLeaderboard(
                            LeaderboardDimension.TOTAL, LeaderboardPeriod.ALL, 20, 0, userId);

            assertThat(response.entries()).hasSize(2);
            assertThat(response.entries().getFirst().userId()).isEqualTo(userId);
            assertThat(response.entries().getFirst().displayName()).isEqualTo("Alice");
            assertThat(response.entries().getFirst().score()).isEqualTo(10800);
            assertThat(response.entries().getFirst().rank()).isEqualTo(1);
            assertThat(response.entries().get(1).userId()).isEqualTo(otherUserId);
            assertThat(response.entries().get(1).rank()).isEqualTo(2);
            assertThat(response.currentUserRank()).isEqualTo(1);
        }

        @Test
        @DisplayName("should read the period-bucketed key for a period ranking")
        void shouldReadPeriodKey_whenPeriodRequested() {
            when(zsetOps.reverseRangeWithScores("leaderboard:total:week:2026-08-31", 0, 19))
                    .thenReturn(Set.of());
            when(zsetOps.reverseRank("leaderboard:total:week:2026-08-31", userId.toString()))
                    .thenReturn(null);

            service.getLeaderboard(
                    LeaderboardDimension.TOTAL, LeaderboardPeriod.WEEK, 20, 0, userId);

            verify(zsetOps).reverseRangeWithScores("leaderboard:total:week:2026-08-31", 0, 19);
        }

        @Test
        @DisplayName("should report null current rank when the user is not ranked")
        void shouldReturnNullRank_whenUserNotRanked() {
            when(zsetOps.reverseRangeWithScores("leaderboard:total", 0, 19)).thenReturn(Set.of());
            when(zsetOps.reverseRank("leaderboard:total", userId.toString())).thenReturn(null);

            LeaderboardResponse response =
                    service.getLeaderboard(
                            LeaderboardDimension.TOTAL, LeaderboardPeriod.ALL, 20, 0, userId);

            assertThat(response.entries()).isEmpty();
            assertThat(response.currentUserRank()).isNull();
        }

        @Test
        @DisplayName("should tolerate a missing user account")
        void shouldTolerateMissingUser() {
            ZSetOperations.TypedTuple<String> first =
                    ZSetOperations.TypedTuple.of(userId.toString(), 100.0);
            when(zsetOps.reverseRangeWithScores("leaderboard:streak", 5, 14))
                    .thenReturn(Set.of(first));
            when(userRepository.findAllById(List.of(userId))).thenReturn(List.of());
            when(zsetOps.reverseRank("leaderboard:streak", userId.toString())).thenReturn(6L);

            LeaderboardResponse response =
                    service.getLeaderboard(
                            LeaderboardDimension.STREAK, LeaderboardPeriod.ALL, 10, 5, userId);

            assertThat(response.entries()).hasSize(1);
            assertThat(response.entries().getFirst().displayName()).isNull();
            assertThat(response.entries().getFirst().rank()).isEqualTo(6);
            assertThat(response.currentUserRank()).isEqualTo(7);
        }

        @Test
        @DisplayName("should pass the offset and limit through to the ZSet range")
        void shouldApplyPaging() {
            when(zsetOps.reverseRangeWithScores("leaderboard:total", 20, 29)).thenReturn(Set.of());
            when(zsetOps.reverseRank("leaderboard:total", userId.toString())).thenReturn(null);

            service.getLeaderboard(
                    LeaderboardDimension.TOTAL, LeaderboardPeriod.ALL, 10, 20, userId);

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
                                            20,
                                            0,
                                            userId))
                    .isInstanceOf(ValidationException.class);
            assertThatThrownBy(
                            () ->
                                    service.getLeaderboard(
                                            LeaderboardDimension.GROWTH,
                                            LeaderboardPeriod.ALL,
                                            20,
                                            0,
                                            userId))
                    .isInstanceOf(ValidationException.class);
        }
    }
}
