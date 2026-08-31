package com.ahogek.cttserver.stats.materialization.service;

import com.ahogek.cttserver.common.lock.RedisLockService;
import com.ahogek.cttserver.stats.materialization.repository.DailyStatsRepository;
import com.ahogek.cttserver.sync.entity.CodingSession;
import com.ahogek.cttserver.sync.repository.CodingSessionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DailyStatsMaterializer")
class DailyStatsMaterializerTest {

    private CodingSessionRepository codingSessionRepository;
    private DailyStatsRepository dailyStatsRepository;
    private StringRedisTemplate redisTemplate;
    private RedisLockService redisLock;
    private DailyStatsMaterializer materializer;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        codingSessionRepository = mock(CodingSessionRepository.class);
        dailyStatsRepository = mock(DailyStatsRepository.class);
        redisTemplate = mock(StringRedisTemplate.class);
        redisLock = mock(RedisLockService.class);
        when(redisLock.tryAcquire(anyString(), any(Duration.class))).thenReturn(true);
        materializer =
                new DailyStatsMaterializer(
                        codingSessionRepository, dailyStatsRepository, redisTemplate, redisLock);
    }

    private static CodingSession session(String start, String end) {
        CodingSession session = new CodingSession();
        session.setStartTime(Instant.parse(start + "Z"));
        session.setEndTime(Instant.parse(end + "Z"));
        session.setProjectName("p");
        session.setLanguage("Java");
        return session;
    }

    @Nested
    @DisplayName("recomputeDays")
    class RecomputeDaysTests {

        @Test
        @DisplayName("shouldUpsertTouchedDays_whenSessionsOverlapRange")
        void shouldUpsertTouchedDays_whenSessionsOverlapRange() {
            when(codingSessionRepository.findLiveInUtcDayRange(eq(userId), any(), any()))
                    .thenReturn(
                            List.of(
                                    session("2026-08-30T23:00:00", "2026-08-31T02:00:00"),
                                    session("2026-08-30T10:00:00", "2026-08-30T11:00:00")));

            materializer.recomputeDays(userId, List.of(LocalDate.of(2026, 8, 30)));

            // only the touched UTC day is upserted, with the bootstrap flag false
            ArgumentCaptor<LocalDate> dayCaptor = ArgumentCaptor.forClass(LocalDate.class);
            verify(dailyStatsRepository)
                    .upsertDay(eq(userId), dayCaptor.capture(), eq(7200L), eq(false));
            assertThat(dayCaptor.getValue()).isEqualTo(LocalDate.of(2026, 8, 30));
            // 23:00-24:00 = 1h on 08-30; 00:00-02:00 belongs to 08-31 (not touched here)
        }

        @Test
        @DisplayName("shouldWriteZeroRow_whenDayNoLongerHasCoding")
        void shouldWriteZeroRow_whenDayNoLongerHasCoding() {
            when(codingSessionRepository.findLiveInUtcDayRange(eq(userId), any(), any()))
                    .thenReturn(List.of());

            materializer.recomputeDays(userId, List.of(LocalDate.of(2026, 8, 30)));

            verify(dailyStatsRepository).upsertDay(eq(userId), any(), eq(0L), eq(false));
        }

        @Test
        @DisplayName("shouldSkip_whenNoTouchedDates")
        void shouldSkip_whenNoTouchedDates() {
            materializer.recomputeDays(userId, List.of());
            verify(codingSessionRepository, never()).findLiveInUtcDayRange(any(), any(), any());
            verify(dailyStatsRepository, never()).upsertDay(any(), any(), anyLong(), anyBoolean());
        }

        @Test
        @DisplayName("shouldSwallowFailure_andReleaseLock")
        void shouldSwallowFailure_whenUpsertThrows() {
            when(codingSessionRepository.findLiveInUtcDayRange(eq(userId), any(), any()))
                    .thenReturn(List.of(session("2026-08-30T10:00:00", "2026-08-30T11:00:00")));
            doThrow(new RuntimeException("db down"))
                    .when(dailyStatsRepository)
                    .upsertDay(any(), any(), anyLong(), anyBoolean());

            assertThatCode(
                            () ->
                                    materializer.recomputeDays(
                                            userId, List.of(LocalDate.of(2026, 8, 30))))
                    .doesNotThrowAnyException();
            verify(redisLock).release("daily_stats:lock:" + userId);
            // release delegates to redisTemplate.delete, asserted via the shared-lock unit contract
        }
    }

    @Nested
    @DisplayName("bootstrapIfNeeded")
    class BootstrapTests {

        @Test
        @DisplayName("shouldRebuildAll_whenNotBootstrapped")
        void shouldRebuildAll_whenNotBootstrapped() {
            when(dailyStatsRepository.existsBootstrapped(userId)).thenReturn(false);
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(
                            List.of(
                                    session("2026-08-29T10:00:00", "2026-08-29T11:00:00"),
                                    session("2026-08-30T10:00:00", "2026-08-30T11:00:00")));

            boolean available = materializer.bootstrapIfNeeded(userId);

            assertThat(available).isTrue();
            verify(dailyStatsRepository).deleteByUserId(userId);
            verify(dailyStatsRepository, times(2))
                    .upsertDay(eq(userId), any(), eq(3600L), eq(true));
        }

        @Test
        @DisplayName("shouldSkipRebuild_whenAlreadyBootstrapped")
        void shouldSkipRebuild_whenAlreadyBootstrapped() {
            when(dailyStatsRepository.existsBootstrapped(userId)).thenReturn(true);

            boolean available = materializer.bootstrapIfNeeded(userId);

            assertThat(available).isTrue();
            verify(dailyStatsRepository, never()).deleteByUserId(any());
            verify(dailyStatsRepository, never()).upsertDay(any(), any(), anyLong(), anyBoolean());
        }

        @Test
        @DisplayName("shouldReturnFalse_whenLockContentionPreventsBootstrap")
        void shouldReturnFalse_whenLockContention() {
            when(dailyStatsRepository.existsBootstrapped(userId)).thenReturn(false);
            when(redisLock.tryAcquire(anyString(), any(Duration.class))).thenReturn(false);

            boolean available = materializer.bootstrapIfNeeded(userId);

            assertThat(available).isFalse();
            verify(dailyStatsRepository, never()).deleteByUserId(any());
        }
    }
}
