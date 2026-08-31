package com.ahogek.cttserver.stats.service;

import com.ahogek.cttserver.stats.dto.HeatmapResponse;
import com.ahogek.cttserver.stats.dto.StatsSummaryResponse;
import com.ahogek.cttserver.stats.dto.StreakStatsResponse;
import com.ahogek.cttserver.stats.materialization.entity.DailyStats;
import com.ahogek.cttserver.stats.materialization.repository.DailyStatsRepository;
import com.ahogek.cttserver.stats.materialization.service.DailyStatsMaterializer;
import com.ahogek.cttserver.sync.entity.CodingSession;
import com.ahogek.cttserver.sync.repository.CodingSessionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("StatsService")
class StatsServiceTest {

    private CodingSessionRepository codingSessionRepository;
    private DailyStatsRepository dailyStatsRepository;
    private DailyStatsMaterializer dailyStatsMaterializer;
    private StatsService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        codingSessionRepository = mock(CodingSessionRepository.class);
        dailyStatsRepository = mock(DailyStatsRepository.class);
        dailyStatsMaterializer = mock(DailyStatsMaterializer.class);
        service =
                new StatsService(
                        codingSessionRepository, dailyStatsRepository, dailyStatsMaterializer);
    }

    private static DailyStats day(String date, long seconds) {
        return new DailyStats(UUID.randomUUID(), LocalDate.parse(date), seconds, true);
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
    @DisplayName("UTC materialized path")
    class UtcMaterializedTests {

        @Test
        @DisplayName("summary shouldReadMaterializedDays_whenUtcAndBootstrapped")
        void summaryShouldReadMaterializedDays_whenUtcAndBootstrapped() {
            // rows are anchored to the real "today" so the week/month boundary logic holds
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            when(dailyStatsMaterializer.bootstrapIfNeeded(userId)).thenReturn(true);
            when(dailyStatsRepository.findByUserIdOrderByUtcDateAsc(userId))
                    .thenReturn(
                            List.of(
                                    day(today.minusDays(2).toString(), 3600),
                                    day(today.minusDays(1).toString(), 7200),
                                    day(today.toString(), 1800)));

            StatsSummaryResponse summary = service.summary(userId, ZoneOffset.UTC);

            assertThat(summary.total()).isEqualTo(12600);
            // today is the ISO week start (Monday), so only today's row falls in this week
            assertThat(summary.thisWeek()).isEqualTo(1800);
            assertThat(summary.thisMonth()).isEqualTo(12600);
            assertThat(summary.today()).isEqualTo(1800);
            // total / (first day .. today inclusive) = 12600 / 3
            assertThat(summary.dailyAverage()).isEqualTo(4200);
            verify(dailyStatsRepository).findByUserIdOrderByUtcDateAsc(userId);
            verify(codingSessionRepository, never()).findAllByUserIdAndIsDeletedFalse(any());
        }

        @Test
        @DisplayName("summaryShouldFallBackToLive_whenNonUtcZone")
        void summaryShouldFallBackToLive_whenNonUtcZone() {
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(List.of(session("2026-08-30T10:00:00", "2026-08-30T11:00:00")));

            StatsSummaryResponse summary = service.summary(userId, ZoneOffset.ofHours(8));

            assertThat(summary.total()).isEqualTo(3600);
            verify(dailyStatsRepository, never()).findByUserIdOrderByUtcDateAsc(any());
        }

        @Test
        @DisplayName("heatmapShouldPadZeroDays_whenUtcAndBootstrapped")
        void heatmapShouldPadZeroDays_whenUtcAndBootstrapped() {
            when(dailyStatsMaterializer.bootstrapIfNeeded(userId)).thenReturn(true);
            when(dailyStatsRepository.findByUserIdAndUtcDateBetweenOrderByUtcDateAsc(
                            any(), any(), any()))
                    .thenReturn(List.of(day("2026-08-30", 3600)));

            HeatmapResponse heatmap =
                    service.heatmap(
                            userId,
                            ZoneOffset.UTC,
                            LocalDate.of(2026, 8, 30),
                            LocalDate.of(2026, 8, 31));

            // dense output: zero day included
            assertThat(heatmap.points()).hasSize(2);
            assertThat(heatmap.points().get(0).seconds()).isEqualTo(3600);
            assertThat(heatmap.points().get(1).seconds()).isZero();
        }

        @Test
        @DisplayName("streaksShouldReadActiveDays_whenUtcAndBootstrapped")
        void streaksShouldReadActiveDays_whenUtcAndBootstrapped() {
            when(dailyStatsMaterializer.bootstrapIfNeeded(userId)).thenReturn(true);
            when(dailyStatsRepository.findByUserIdOrderByUtcDateAsc(userId))
                    .thenReturn(
                            List.of(
                                    day("2026-08-29", 3600),
                                    day("2026-08-30", 3600),
                                    day("2026-08-31", 3600)));

            StreakStatsResponse streaks = service.streaks(userId, ZoneOffset.UTC);

            assertThat(streaks.max()).isEqualTo(3);
            assertThat(streaks.current()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("bootstrap not available")
    class BootstrapUnavailableTests {

        @Test
        @DisplayName("summaryShouldFallBackToLive_whenBootstrapUnavailable")
        void summaryShouldFallBackToLive_whenBootstrapUnavailable() {
            when(dailyStatsMaterializer.bootstrapIfNeeded(userId)).thenReturn(false);
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(List.of(session("2026-08-30T10:00:00", "2026-08-30T11:00:00")));

            StatsSummaryResponse summary = service.summary(userId, ZoneOffset.UTC);

            assertThat(summary.total()).isEqualTo(3600);
        }
    }
}
