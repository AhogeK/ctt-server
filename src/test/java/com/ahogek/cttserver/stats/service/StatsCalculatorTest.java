package com.ahogek.cttserver.stats.service;

import com.ahogek.cttserver.stats.service.StatsCalculator.DailyPoint;
import com.ahogek.cttserver.stats.service.StatsCalculator.DistributionEntry;
import com.ahogek.cttserver.stats.service.StatsCalculator.HourlyPoint;
import com.ahogek.cttserver.stats.service.StatsCalculator.Streaks;
import com.ahogek.cttserver.stats.service.StatsCalculator.Summary;
import com.ahogek.cttserver.sync.entity.CodingSession;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StatsCalculator")
class StatsCalculatorTest {

    private static final ZoneOffset UTC = ZoneOffset.UTC;
    private static final ZoneOffset UTC_PLUS_8 = ZoneOffset.ofHours(8);

    private static CodingSession session(Instant start, Instant end, String project, String lang) {
        CodingSession session = new CodingSession();
        session.setStartTime(start);
        session.setEndTime(end);
        session.setProjectName(project);
        session.setLanguage(lang);
        return session;
    }

    private static Instant at(String dateTime) {
        return Instant.parse(dateTime + "Z");
    }

    private static OffsetDateTime odt(String dateTime) {
        return Instant.parse(dateTime + "Z").atOffset(UTC);
    }

    @Nested
    @DisplayName("summary")
    class SummaryTests {

        @Test
        @DisplayName("shouldComputeAllPeriods_whenSessionsSpanThem")
        void shouldComputeAllPeriods_whenSessionsSpanThem() {
            LocalDate today = LocalDate.of(2026, 8, 30);
            // Sunday 2026-08-30; this ISO week starts Monday 08-24
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-30T10:00:00"),
                                    at("2026-08-30T11:00:00"),
                                    "a",
                                    "Java"),
                            session(
                                    at("2026-08-28T09:00:00"),
                                    at("2026-08-28T10:00:00"),
                                    "b",
                                    "Kotlin"),
                            session(
                                    at("2026-07-15T08:00:00"),
                                    at("2026-07-15T10:00:00"),
                                    "c",
                                    "Java"),
                            session(
                                    at("2025-12-01T09:00:00"),
                                    at("2025-12-01T10:00:00"),
                                    "d",
                                    "Go"));

            Summary summary = StatsCalculator.summary(sessions, UTC, today);

            assertThat(summary.today()).isEqualTo(3600);
            assertThat(summary.thisWeek()).isEqualTo(7200); // today + 08-28
            assertThat(summary.thisMonth()).isEqualTo(7200); // today + 08-28 (07-15 is July)
            assertThat(summary.thisYear()).isEqualTo(14400); // today + 08-28 + 07-15 (2h)
            assertThat(summary.total()).isEqualTo(18000); // all four sessions
            // dailyAverage = total / days from 2025-12-01 to 2026-08-30 inclusive
            assertThat(summary.dailyAverage()).isGreaterThan(0);
        }

        @Test
        @DisplayName("shouldMergeOverlappingSessions_whenCountingTotal")
        void shouldMergeOverlappingIntervals_whenConcurrentSessions() {
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-30T10:00:00"),
                                    at("2026-08-30T12:00:00"),
                                    "a",
                                    "Java"),
                            session(
                                    at("2026-08-30T11:00:00"),
                                    at("2026-08-30T13:00:00"),
                                    "b",
                                    "Kotlin"));

            Summary summary = StatsCalculator.summary(sessions, UTC, LocalDate.of(2026, 8, 30));

            assertThat(summary.today()).isEqualTo(10800);
            assertThat(summary.total()).isEqualTo(10800);
        }

        @Test
        @DisplayName("shouldIgnoreSessionsWithInvalidInterval_whenAggregating")
        void shouldIgnoreInvalidIntervals_whenAggregating() {
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-30T10:00:00"),
                                    at("2026-08-30T11:00:00"),
                                    "a",
                                    "Java"),
                            // start == end: degenerate interval, must be skipped not fail
                            session(
                                    at("2026-08-30T12:00:00"),
                                    at("2026-08-30T12:00:00"),
                                    "b",
                                    "Java"),
                            session(
                                    at("2026-08-30T13:00:00"),
                                    at("2026-08-30T12:00:00"),
                                    "c",
                                    "Java"));

            Summary summary = StatsCalculator.summary(sessions, UTC, LocalDate.of(2026, 8, 30));

            assertThat(summary.today()).isEqualTo(3600);
            assertThat(summary.total()).isEqualTo(3600);
        }

        @Test
        @DisplayName("shouldReturnZeroSummary_whenNoSessions")
        void shouldReturnZero_whenEmpty() {
            Summary summary = StatsCalculator.summary(List.of(), UTC, LocalDate.of(2026, 8, 30));

            assertThat(summary.today()).isZero();
            assertThat(summary.dailyAverage()).isZero();
            assertThat(summary.total()).isZero();
        }

        @Test
        @DisplayName("shouldBucketTodayByTimezone")
        void shouldBucketByTimezone_whenOffsetDiffers() {
            Instant start = at("2026-08-29T20:00:00");
            Instant end = at("2026-08-29T21:00:00");
            List<CodingSession> sessions = List.of(session(start, end, "a", "Java"));

            Summary utc = StatsCalculator.summary(sessions, UTC, LocalDate.of(2026, 8, 29));
            Summary plus8 =
                    StatsCalculator.summary(sessions, UTC_PLUS_8, LocalDate.of(2026, 8, 30));

            assertThat(utc.today()).isEqualTo(3600);
            assertThat(plus8.today()).isEqualTo(3600);
        }
    }

    @Nested
    @DisplayName("heatmap")
    class HeatmapTests {

        @Test
        @DisplayName("shouldSplitCrossMidnightSessionAcrossDays")
        void shouldSplitSession_whenCrossingMidnight() {
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-29T23:00:00"),
                                    at("2026-08-30T01:00:00"),
                                    "a",
                                    "Java"));

            List<DailyPoint> points =
                    StatsCalculator.heatmap(
                            sessions, UTC, LocalDate.of(2026, 8, 29), LocalDate.of(2026, 8, 30));

            assertThat(points).hasSize(2);
            assertThat(points.get(0).date()).isEqualTo(LocalDate.of(2026, 8, 29));
            assertThat(points.get(0).seconds()).isEqualTo(3600);
            assertThat(points.get(1).date()).isEqualTo(LocalDate.of(2026, 8, 30));
            assertThat(points.get(1).seconds()).isEqualTo(3600);
        }

        @Test
        @DisplayName("shouldMergeOverlapsWithinADay")
        void shouldMergeOverlaps_whenWithinSameDay() {
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-30T10:00:00"),
                                    at("2026-08-30T12:00:00"),
                                    "a",
                                    "Java"),
                            session(
                                    at("2026-08-30T11:00:00"),
                                    at("2026-08-30T13:00:00"),
                                    "b",
                                    "Kotlin"));

            List<DailyPoint> points =
                    StatsCalculator.heatmap(
                            sessions, UTC, LocalDate.of(2026, 8, 30), LocalDate.of(2026, 8, 30));

            assertThat(points).hasSize(1);
            assertThat(points.getFirst().seconds()).isEqualTo(10800);
        }
    }

    @Nested
    @DisplayName("streaks")
    class StreaksTests {

        @Test
        @DisplayName("shouldComputeCurrentAndMaxStreaks")
        void shouldComputeStreaks_whenConsecutiveDays() {
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-28T10:00:00"),
                                    at("2026-08-28T11:00:00"),
                                    "a",
                                    "Java"),
                            session(
                                    at("2026-08-29T10:00:00"),
                                    at("2026-08-29T11:00:00"),
                                    "b",
                                    "Java"),
                            session(
                                    at("2026-08-30T10:00:00"),
                                    at("2026-08-30T11:00:00"),
                                    "c",
                                    "Java"));

            Streaks streaks = StatsCalculator.streaks(sessions, UTC, LocalDate.of(2026, 8, 30));

            assertThat(streaks.current()).isEqualTo(3);
            assertThat(streaks.max()).isEqualTo(3);
        }

        @Test
        @DisplayName("shouldReturnZeroStreaks_whenNoSessions")
        void shouldReturnZero_whenEmpty() {
            Streaks streaks = StatsCalculator.streaks(List.of(), UTC, LocalDate.of(2026, 8, 30));

            assertThat(streaks.current()).isZero();
            assertThat(streaks.max()).isZero();
        }
    }

    @Nested
    @DisplayName("distribution")
    class DistributionTests {

        @Test
        @DisplayName("shouldAccumulateByLanguageDescending")
        void shouldAccumulateLanguages_whenMultipleLanguages() {
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-30T10:00:00"),
                                    at("2026-08-30T11:00:00"),
                                    "a",
                                    "Java"),
                            session(
                                    at("2026-08-30T11:00:00"),
                                    at("2026-08-30T13:00:00"),
                                    "b",
                                    "Kotlin"));

            List<DistributionEntry> entries =
                    StatsCalculator.accumulateBy(sessions, UTC, CodingSession::getLanguage);

            assertThat(entries).hasSize(2);
            assertThat(entries.get(0).name()).isEqualTo("Kotlin");
            assertThat(entries.get(0).seconds()).isEqualTo(7200);
            assertThat(entries.get(1).name()).isEqualTo("Java");
            assertThat(entries.get(1).seconds()).isEqualTo(3600);
        }

        @Test
        @DisplayName("shouldAccumulateByProject")
        void shouldAccumulateProjects_whenSessionsShareProject() {
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-30T10:00:00"),
                                    at("2026-08-30T11:00:00"),
                                    "ctt",
                                    "Java"),
                            session(
                                    at("2026-08-30T11:00:00"),
                                    at("2026-08-30T12:00:00"),
                                    "ctt",
                                    "Java"));

            List<DistributionEntry> entries =
                    StatsCalculator.accumulateBy(sessions, UTC, CodingSession::getProjectName);

            assertThat(entries).hasSize(1);
            assertThat(entries.getFirst().seconds()).isEqualTo(7200);
        }
    }

    @Nested
    @DisplayName("hourly")
    class HourlyTests {

        @Test
        @DisplayName("shouldSplitAcrossHoursAndAverageByActiveDays")
        void shouldComputeHourly_whenSessionsSpanHours() {
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-29T09:30:00"),
                                    at("2026-08-29T10:30:00"),
                                    "a",
                                    "Java"),
                            session(
                                    at("2026-08-30T09:00:00"),
                                    at("2026-08-30T09:30:00"),
                                    "b",
                                    "Java"));

            List<HourlyPoint> points = StatsCalculator.hourlyDistribution(sessions, UTC);

            assertThat(points.get(9).averageSeconds()).isEqualTo(1800);
            assertThat(points.get(9).activeDays()).isEqualTo(2);
            assertThat(points.get(10).averageSeconds()).isEqualTo(900);
        }
    }

    @Nested
    @DisplayName("dailyWindow")
    class DailyWindowTests {

        @Test
        @DisplayName("shouldIntersectWindow_whenSessionInsideWindow")
        void shouldCountWindowDuration_whenSessionInsideWindow() {
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-30T07:00:00"),
                                    at("2026-08-30T08:00:00"),
                                    "a",
                                    "Java"));

            long seconds =
                    StatsCalculator.mergedDurationInDailyWindow(
                            sessions,
                            UTC,
                            6,
                            9,
                            odt("2026-08-30T00:00:00"),
                            odt("2026-08-31T00:00:00"));

            assertThat(seconds).isEqualTo(3600);
        }

        @Test
        @DisplayName("shouldClipWindow_whenSessionPartiallyInside")
        void shouldClipWindow_whenSessionPartiallyInside() {
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-30T08:00:00"),
                                    at("2026-08-30T10:00:00"),
                                    "a",
                                    "Java"));

            long seconds =
                    StatsCalculator.mergedDurationInDailyWindow(
                            sessions,
                            UTC,
                            6,
                            9,
                            odt("2026-08-30T00:00:00"),
                            odt("2026-08-31T00:00:00"));

            // only 08:00-09:00 falls inside the 06:00-09:00 window
            assertThat(seconds).isEqualTo(3600);
        }

        @Test
        @DisplayName("shouldMergeOverlaps_whenSessionsOverlapInsideWindow")
        void shouldMergeOverlaps_whenSessionsOverlapInsideWindow() {
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-30T07:00:00"),
                                    at("2026-08-30T09:30:00"),
                                    "a",
                                    "Java"),
                            session(
                                    at("2026-08-30T08:00:00"),
                                    at("2026-08-30T09:00:00"),
                                    "b",
                                    "Kotlin"));

            long seconds =
                    StatsCalculator.mergedDurationInDailyWindow(
                            sessions,
                            UTC,
                            6,
                            9,
                            odt("2026-08-30T00:00:00"),
                            odt("2026-08-31T00:00:00"));

            // merged 07:00-09:30 clipped to 09:00 = 2h, overlap not double counted
            assertThat(seconds).isEqualTo(7200);
        }

        @Test
        @DisplayName("shouldAttributeEarlyMorning_whenSessionStartsBeforeWindowHour")
        void shouldAttributeEarlyMorning_whenSessionStartsBeforeWindowHour() {
            // 01:00-03:00 belongs to the previous day's 22:00-05:00 window, not the current day's
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-30T01:00:00"),
                                    at("2026-08-30T03:00:00"),
                                    "a",
                                    "Java"));

            long seconds =
                    StatsCalculator.mergedDurationInDailyWindow(
                            sessions,
                            UTC,
                            22,
                            5,
                            odt("2026-08-30T00:00:00"),
                            odt("2026-08-31T00:00:00"));

            assertThat(seconds).isEqualTo(7200);
        }

        @DisplayName("shouldCountAcrossDays_whenSessionCrossesMidnight")
        void shouldCountAcrossDays_whenSessionCrossesMidnight() {
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-30T23:00:00"),
                                    at("2026-08-31T04:00:00"),
                                    "a",
                                    "Java"));

            // 08-30 window: 23:00-05:00 -> 1h (23:00-24:00)
            // 08-31 window: 22:00-05:00 -> 4h (00:00-04:00)
            long seconds =
                    StatsCalculator.mergedDurationInDailyWindow(
                            sessions,
                            UTC,
                            22,
                            5,
                            odt("2026-08-30T00:00:00"),
                            odt("2026-08-31T00:00:00"));

            assertThat(seconds).isEqualTo(3600);
        }

        @Test
        @DisplayName("shouldIgnoreWindow_whenSessionOutsidePeriod")
        void shouldIgnoreWindow_whenSessionOutsidePeriod() {
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-31T07:00:00"),
                                    at("2026-08-31T08:00:00"),
                                    "a",
                                    "Java"));

            long seconds =
                    StatsCalculator.mergedDurationInDailyWindow(
                            sessions,
                            UTC,
                            6,
                            9,
                            odt("2026-08-30T00:00:00"),
                            odt("2026-08-31T00:00:00"));

            assertThat(seconds).isZero();
        }
    }
}
