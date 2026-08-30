package com.ahogek.cttserver.leaderboard.service;

import com.ahogek.cttserver.leaderboard.dto.LeaderboardResponse;
import com.ahogek.cttserver.leaderboard.enums.LeaderboardDimension;
import com.ahogek.cttserver.sync.entity.CodingSession;
import com.ahogek.cttserver.sync.repository.CodingSessionRepository;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("LeaderboardService")
class LeaderboardServiceTest {

    private StringRedisTemplate redisTemplate;
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
        zsetOps = mock(ZSetOperations.class);
        valueOps = mock(ValueOperations.class);
        codingSessionRepository = mock(CodingSessionRepository.class);
        userRepository = mock(UserRepository.class);
        when(redisTemplate.opsForZSet()).thenReturn(zsetOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        service = new LeaderboardService(redisTemplate, codingSessionRepository, userRepository);
    }

    private CodingSession session(Instant start, Instant end) {
        CodingSession session = new CodingSession();
        session.setStartTime(start);
        session.setEndTime(end);
        session.setProjectName("p");
        session.setLanguage("l");
        return session;
    }

    @Nested
    @DisplayName("updateUserScore")
    class UpdateUserScoreTests {

        @Test
        @DisplayName("should write merged total seconds into the total ZSet")
        void shouldWriteTotal_whenDimensionTotal() {
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    Instant.parse("2026-08-30T10:00:00Z"),
                                    Instant.parse("2026-08-30T12:00:00Z")),
                            session(
                                    Instant.parse("2026-08-30T11:00:00Z"),
                                    Instant.parse("2026-08-30T13:00:00Z")));
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(sessions);

            service.updateUserScore(userId, LeaderboardDimension.TOTAL);

            // merged 10:00-13:00 = 3h = 10800s (overlap collapsed, not 4h)
            verify(zsetOps).add("leaderboard:total", userId.toString(), 10800.0);
        }

        @Test
        @DisplayName("should write the longest streak into the streak ZSet")
        void shouldWriteMaxStreak_whenDimensionStreak() {
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    Instant.parse("2026-08-28T10:00:00Z"),
                                    Instant.parse("2026-08-28T11:00:00Z")),
                            session(
                                    Instant.parse("2026-08-29T10:00:00Z"),
                                    Instant.parse("2026-08-29T11:00:00Z")),
                            session(
                                    Instant.parse("2026-08-30T10:00:00Z"),
                                    Instant.parse("2026-08-30T11:00:00Z")));
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(sessions);

            service.updateUserScore(userId, LeaderboardDimension.STREAK);

            verify(zsetOps).add("leaderboard:streak", userId.toString(), 3.0);
        }

        @Test
        @DisplayName("should swallow Redis failures so the push is not affected")
        void shouldSwallowRedisFailure_whenZAddThrows() {
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(List.of());
            doThrow(new RuntimeException("redis down"))
                    .when(zsetOps)
                    .add(anyString(), anyString(), anyDouble());

            assertThatCode(() -> service.updateUserScore(userId, LeaderboardDimension.TOTAL))
                    .doesNotThrowAnyException();
            // the per-user lock is released even when the write failed
            verify(redisTemplate).delete("leaderboard:lock:" + userId);
        }

        @Test
        @DisplayName("should not write when the user has no sessions")
        void shouldWriteZero_whenNoSessions() {
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(List.of());

            service.updateUserScore(userId, LeaderboardDimension.TOTAL);

            verify(zsetOps).add("leaderboard:total", userId.toString(), 0.0);
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
                    service.getLeaderboard(LeaderboardDimension.TOTAL, 20, 0, userId);

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
        @DisplayName("should report null current rank when the user is not ranked")
        void shouldReturnNullRank_whenUserNotRanked() {
            when(zsetOps.reverseRangeWithScores("leaderboard:total", 0, 19)).thenReturn(Set.of());
            when(zsetOps.reverseRank("leaderboard:total", userId.toString())).thenReturn(null);

            LeaderboardResponse response =
                    service.getLeaderboard(LeaderboardDimension.TOTAL, 20, 0, userId);

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
                    service.getLeaderboard(LeaderboardDimension.STREAK, 10, 5, userId);

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

            service.getLeaderboard(LeaderboardDimension.TOTAL, 10, 20, userId);

            verify(zsetOps).reverseRangeWithScores("leaderboard:total", 20, 29);
        }
    }
}
