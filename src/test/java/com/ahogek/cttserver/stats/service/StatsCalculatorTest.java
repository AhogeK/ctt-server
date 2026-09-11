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
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
        @DisplayName("shouldUsePluginBoundaries_whenBucketing")
        void shouldUsePluginBoundaries_whenBucketing() {
            // New boundaries: Night 0-5, Morning 6-11, Daytime 12-17, Evening 18-23.
            // 05:59 falls in Night, 06:00 in Morning, 11:59 in Morning, 12:00 in Daytime.
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-30T05:00:00"),
                                    at("2026-08-30T05:59:00"),
                                    "a",
                                    "Java"),
                            session(
                                    at("2026-08-30T06:00:00"),
                                    at("2026-08-30T06:59:00"),
                                    "a",
                                    "Java"),
                            session(
                                    at("2026-08-30T12:00:00"),
                                    at("2026-08-30T12:59:00"),
                                    "a",
                                    "Java"),
                            session(
                                    at("2026-08-30T18:00:00"),
                                    at("2026-08-30T18:59:00"),
                                    "a",
                                    "Java"));

            List<DistributionEntry> entries = StatsCalculator.timeOfDayDistribution(sessions, UTC);

            assertThat(entries)
                    .extracting(DistributionEntry::name, DistributionEntry::seconds)
                    .containsExactlyInAnyOrder(
                            tuple("NIGHT", 3540L),
                            tuple("MORNING", 3540L),
                            tuple("DAYTIME", 3540L),
                            tuple("EVENING", 3540L));
        }

        @Test
        @DisplayName("shouldSliceAcrossBuckets_whenSessionSpansBoundary")
        void shouldSliceAcrossBuckets_whenSessionSpansBoundary() {
            // 11:00-13:00 splits into Morning 11:00-12:00 (1h) and Daytime 12:00-13:00 (1h);
            // nothing is attributed wholly to the start bucket.
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-30T11:00:00"),
                                    at("2026-08-30T13:00:00"),
                                    "a",
                                    "Java"));

            List<DistributionEntry> entries = StatsCalculator.timeOfDayDistribution(sessions, UTC);

            assertThat(entries)
                    .extracting(DistributionEntry::name, DistributionEntry::seconds)
                    .containsExactlyInAnyOrder(tuple("MORNING", 3600L), tuple("DAYTIME", 3600L));
        }

        @Test
        @DisplayName("shouldMergeOverlappingSessionsBeforeBucketing")
        void shouldMergeOverlappingSessions_whenParallelWindows() {
            // 10:00-11:00 and 10:30-10:45 overlap: union 10:00-11:00 = 1h in Morning,
            // not 5100s raw accumulation.
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-30T10:00:00"),
                                    at("2026-08-30T11:00:00"),
                                    "a",
                                    "Java"),
                            session(
                                    at("2026-08-30T10:30:00"),
                                    at("2026-08-30T10:45:00"),
                                    "b",
                                    "Kotlin"));

            List<DistributionEntry> entries = StatsCalculator.timeOfDayDistribution(sessions, UTC);

            assertThat(entries)
                    .singleElement()
                    .satisfies(
                            entry -> {
                                assertThat(entry.name()).isEqualTo("MORNING");
                                assertThat(entry.seconds()).isEqualTo(3600);
                            });
        }

        @Test
        @DisplayName("shouldSumToSessionTime_whenSpanningMidnight")
        void shouldSumToSessionTime_whenSpanningMidnight() {
            // 22:00-02:00 crosses midnight: Evening 22:00-24:00 (2h) + Night 00:00-02:00 (2h).
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-30T22:00:00"),
                                    at("2026-08-31T02:00:00"),
                                    "a",
                                    "Java"));

            List<DistributionEntry> entries = StatsCalculator.timeOfDayDistribution(sessions, UTC);

            assertThat(entries)
                    .extracting(DistributionEntry::name, DistributionEntry::seconds)
                    .containsExactlyInAnyOrder(tuple("EVENING", 7200L), tuple("NIGHT", 7200L));
        }

        @Test
        @DisplayName("shouldNotLoseSubSecondRemainders_whenSessionsStartOffSecond")
        void shouldNotLoseSubSeconds_whenSessionsStartOffSecond() {
            // Regression: per-slice Duration.toSeconds() floored away the sub-second tail of
            // every session starting off the second, deflating buckets vs summary.total.
            // 5 sessions of 59.9s each, each inside Morning: total 299.5s -> 299 after a
            // single end-of-aggregation truncation (raw 5*59.9 = 299.5).
            List<CodingSession> sessions = new java.util.ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Instant start =
                        at("2026-08-30T06:00:00").plusSeconds(i * 3600L).plusNanos(900_000_000);
                CodingSession s =
                        session(start, start.plusSeconds(59).plusNanos(900_000_000), "a", "Java");
                sessions.add(s);
            }

            List<DistributionEntry> entries = StatsCalculator.timeOfDayDistribution(sessions, UTC);

            long total = entries.stream().mapToLong(DistributionEntry::seconds).sum();
            // full-precision 5 * 59.9 = 299.5s; single truncation keeps 299
            assertThat(total).isEqualTo(299);
        }

        @Test
        @DisplayName("shouldApportionRemainderSoBucketsSumToFullPrecisionTotal")
        void shouldApportionRemainder_soBucketSumEqualsTruncatedTotal() {
            // Four sessions in four different buckets, each with a 0.9s fractional tail:
            // naive per-bucket flooring would lose 3s; largest-remainder hands the 3
            // leftover seconds back to the three buckets with the biggest remainders,
            // so the bucket sum equals the single-truncated full-precision total (396s).
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-30T02:00:00").plusNanos(100_000_000),
                                    at("2026-08-30T03:00:00"),
                                    "a",
                                    "Java"),
                            session(
                                    at("2026-08-30T06:00:00").plusNanos(200_000_000),
                                    at("2026-08-30T07:00:00"),
                                    "a",
                                    "Java"),
                            session(
                                    at("2026-08-30T12:00:00").plusNanos(300_000_000),
                                    at("2026-08-30T13:00:00"),
                                    "a",
                                    "Java"),
                            session(
                                    at("2026-08-30T18:00:00").plusNanos(900_000_000),
                                    at("2026-08-30T19:00:00"),
                                    "a",
                                    "Java"));

            List<DistributionEntry> entries = StatsCalculator.timeOfDayDistribution(sessions, UTC);

            long sum = entries.stream().mapToLong(DistributionEntry::seconds).sum();
            // full-precision total = 4*3600 - (0.1+0.2+0.3+0.9) = 14398.5 -> truncates to 14398
            assertThat(sum).isEqualTo(14398);
            // leftover 3s (fractional remainders .9/.8/.7/.1 per bucket) goes to the
            // three buckets with the largest remainders: NIGHT, MORNING, DAYTIME; EVENING
            // keeps its floor. Sum still equals the truncated full-precision total.
            assertThat(entries)
                    .extracting(DistributionEntry::name, DistributionEntry::seconds)
                    .containsExactlyInAnyOrder(
                            tuple("NIGHT", 3600L),
                            tuple("MORNING", 3600L),
                            tuple("DAYTIME", 3599L),
                            tuple("EVENING", 3599L));
        }

        @Test
        @DisplayName("shouldNotLoseSubSecondRemainders_whenAccumulatingLanguages")
        void shouldNotLoseSubSeconds_whenAccumulatingLanguages() {
            // Two 59.9s sessions in different languages: per-session flooring would give
            // 59+59=118, full-precision accumulation truncates once to 119.
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-30T10:00:00").plusNanos(100_000_000),
                                    at("2026-08-30T10:00:00")
                                            .plusNanos(100_000_000)
                                            .plusSeconds(59)
                                            .plusNanos(900_000_000),
                                    "a",
                                    "Java"),
                            session(
                                    at("2026-08-30T11:00:00").plusNanos(200_000_000),
                                    at("2026-08-30T11:00:00")
                                            .plusNanos(200_000_000)
                                            .plusSeconds(59)
                                            .plusNanos(900_000_000),
                                    "a",
                                    "Kotlin"));

            List<DistributionEntry> entries =
                    StatsCalculator.accumulateBy(sessions, UTC, CodingSession::getLanguage);

            long total = entries.stream().mapToLong(DistributionEntry::seconds).sum();
            assertThat(total).isEqualTo(119);
            assertThat(entries)
                    .extracting(DistributionEntry::name, DistributionEntry::seconds)
                    .containsExactlyInAnyOrder(tuple("Java", 60L), tuple("Kotlin", 59L));
        }

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
        @DisplayName("shouldClipWindowAndCountWindowActiveDays_whenRangeGiven")
        void shouldClipHourlyWindow_whenStartAndEndGiven() {
            // 08-25 sits outside the 09-01..09-07 window and must be dropped; activeDays
            // counts only in-window coding days.
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-25T10:00:00"),
                                    at("2026-08-25T11:00:00"),
                                    "a",
                                    "Java"),
                            session(
                                    at("2026-09-02T10:00:00"),
                                    at("2026-09-02T11:00:00"),
                                    "a",
                                    "Java"));

            List<HourlyPoint> points =
                    StatsCalculator.hourlyDistribution(
                            sessions, UTC, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7));

            assertThat(points.get(10).averageSeconds()).isEqualTo(3600);
            assertThat(points.get(10).activeDays()).isEqualTo(1);
        }

        @Test
        @DisplayName("shouldCountSingleBoundActiveDays_whenOnlyStartGiven")
        void shouldClipHourlyWindow_whenOnlyStartGiven() {
            // Regression: one-sided bounds must drop out-of-window sessions, not crash.
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-20T10:00:00"),
                                    at("2026-08-20T11:00:00"),
                                    "a",
                                    "Java"),
                            session(
                                    at("2026-09-05T10:00:00"),
                                    at("2026-09-05T11:00:00"),
                                    "a",
                                    "Java"));

            List<HourlyPoint> points =
                    StatsCalculator.hourlyDistribution(
                            sessions, UTC, LocalDate.of(2026, 9, 1), null);

            assertThat(points.get(10).averageSeconds()).isEqualTo(3600);
            assertThat(points.get(10).activeDays()).isEqualTo(1);
        }

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

            List<HourlyPoint> points =
                    StatsCalculator.hourlyDistribution(sessions, UTC, null, null);

            assertThat(points.get(9).averageSeconds()).isEqualTo(1800);
            assertThat(points.get(9).activeDays()).isEqualTo(2);
            assertThat(points.get(10).averageSeconds()).isEqualTo(900);
        }
    }

    @Nested
    @DisplayName("weekHour")
    class WeekHourTests {

        @Test
        @DisplayName("shouldSliceAcrossHoursWithinOneWeekday")
        void shouldSliceAcrossHours_whenSessionCrossesHourBoundary() {
            // 2026-09-01 is a Tuesday; 10:30-12:15 slices at hour boundaries into
            // 30 min at hour 10, 60 min at hour 11 and 15 min at hour 12 (6300 seconds total).
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-09-01T10:30:00"),
                                    at("2026-09-01T12:15:00"),
                                    "a",
                                    "Java"));

            StatsCalculator.WeekHourDistribution distribution =
                    StatsCalculator.weekHourDistribution(sessions, UTC, null, null);

            assertThat(distribution.points())
                    .extracting(
                            StatsCalculator.WeekHourPoint::dayOfWeek,
                            StatsCalculator.WeekHourPoint::hour,
                            StatsCalculator.WeekHourPoint::averageSeconds)
                    .containsExactly(tuple(2, 10, 1800L), tuple(2, 11, 3600L), tuple(2, 12, 900L));
            long total =
                    distribution.points().stream()
                            .mapToLong(StatsCalculator.WeekHourPoint::averageSeconds)
                            .sum();
            assertThat(total).isEqualTo(6300);
            assertThat(distribution.weekdayCounts()).containsEntry(2, 1);
        }

        @Test
        @DisplayName("shouldShiftWeekdayAndHour_whenZoneShifted")
        void shouldShiftWeekdayAndHour_whenZoneShifted() {
            // 2026-09-01T18:30Z is 2026-09-02T02:30 in UTC+8: Wednesday, hour 2.
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-09-01T18:30:00"),
                                    at("2026-09-01T20:15:00"),
                                    "a",
                                    "Java"));

            StatsCalculator.WeekHourDistribution distribution =
                    StatsCalculator.weekHourDistribution(
                            sessions, ZoneOffset.ofHours(8), null, null);

            assertThat(distribution.points())
                    .extracting(
                            StatsCalculator.WeekHourPoint::dayOfWeek,
                            StatsCalculator.WeekHourPoint::hour,
                            StatsCalculator.WeekHourPoint::averageSeconds)
                    .containsExactly(tuple(3, 2, 1800L), tuple(3, 3, 3600L), tuple(3, 4, 900L));
        }

        @Test
        @DisplayName("shouldAverageByWeekdayAppearances_whenSameWeekdayRepeats")
        void shouldAverageByWeekdayAppearances_whenSameWeekdayRepeats() {
            // Two Tuesdays (09-01 and 09-08), one hour each at hour 10: total 7200 / 2 days = 3600.
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-09-01T10:00:00"),
                                    at("2026-09-01T11:00:00"),
                                    "a",
                                    "Java"),
                            session(
                                    at("2026-09-08T10:00:00"),
                                    at("2026-09-08T11:00:00"),
                                    "a",
                                    "Java"));

            StatsCalculator.WeekHourDistribution distribution =
                    StatsCalculator.weekHourDistribution(
                            sessions, UTC, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 14));

            assertThat(distribution.weekdayCounts()).containsEntry(2, 2);
            assertThat(distribution.points())
                    .singleElement()
                    .satisfies(
                            point -> {
                                assertThat(point.dayOfWeek()).isEqualTo(2);
                                assertThat(point.hour()).isEqualTo(10);
                                assertThat(point.averageSeconds()).isEqualTo(3600);
                            });
        }

        @Test
        @DisplayName("shouldClipWindowAndCountAllWindowDays")
        void shouldClipWindow_whenStartAndEndGiven() {
            // Window 09-01..09-07 counts every day once; a session before the window start is
            // clipped away.
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-25T10:00:00"),
                                    at("2026-08-25T11:00:00"),
                                    "a",
                                    "Java"),
                            session(
                                    at("2026-09-02T10:00:00"),
                                    at("2026-09-02T11:00:00"),
                                    "a",
                                    "Java"));

            StatsCalculator.WeekHourDistribution distribution =
                    StatsCalculator.weekHourDistribution(
                            sessions, UTC, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7));

            assertThat(distribution.weekdayCounts())
                    .containsEntry(1, 1)
                    .containsEntry(2, 1)
                    .containsEntry(3, 1)
                    .containsEntry(7, 1)
                    .hasSize(7);
            assertThat(distribution.points())
                    .singleElement()
                    .satisfies(
                            point -> {
                                assertThat(point.dayOfWeek()).isEqualTo(3);
                                assertThat(point.hour()).isEqualTo(10);
                                assertThat(point.averageSeconds()).isEqualTo(3600);
                            });
        }

        @Test
        @DisplayName("shouldClipSingleBoundWithoutThrowing_whenSessionsFallOutside")
        void shouldClipSingleBound_whenOnlyStartGiven() {
            // Regression: with only a start bound, sessions entirely before it used to crash
            // (TimeInterval rejects start >= end) instead of being dropped.
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-20T10:00:00"),
                                    at("2026-08-20T11:00:00"),
                                    "a",
                                    "Java"),
                            session(
                                    at("2026-09-05T10:00:00"),
                                    at("2026-09-05T11:00:00"),
                                    "a",
                                    "Java"));

            StatsCalculator.WeekHourDistribution distribution =
                    StatsCalculator.weekHourDistribution(
                            sessions, UTC, LocalDate.of(2026, 9, 1), null);

            // 2026-09-05 is a Saturday (dayOfWeek 6)
            assertThat(distribution.points())
                    .singleElement()
                    .satisfies(
                            point -> {
                                assertThat(point.dayOfWeek()).isEqualTo(6);
                                assertThat(point.hour()).isEqualTo(10);
                                assertThat(point.averageSeconds()).isEqualTo(3600);
                            });
            // Weekday counts derive from the sessions' own (clipped) span
            assertThat(distribution.weekdayCounts()).containsEntry(6, 1);
        }

        @Test
        @DisplayName("shouldMergeOverlappingSessionsBeforeSlicing")
        void shouldMergeOverlappingSessions_whenParallelWindows() {
            // Two overlapping sessions at 10:00-11:00 and 10:30-10:45 describe the same
            // activity; the union is 10:00-11:00 and hour 10 must count 3600s, not 3900s.
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-09-01T10:00:00"),
                                    at("2026-09-01T11:00:00"),
                                    "a",
                                    "Java"),
                            session(
                                    at("2026-09-01T10:30:00"),
                                    at("2026-09-01T10:45:00"),
                                    "b",
                                    "Kotlin"));

            StatsCalculator.WeekHourDistribution distribution =
                    StatsCalculator.weekHourDistribution(sessions, UTC, null, null);

            assertThat(distribution.points())
                    .singleElement()
                    .satisfies(
                            point -> {
                                assertThat(point.dayOfWeek()).isEqualTo(2);
                                assertThat(point.hour()).isEqualTo(10);
                                assertThat(point.averageSeconds()).isEqualTo(3600);
                            });
        }

        @Test
        @DisplayName("shouldReturnEmpty_whenNoSessions")
        void shouldReturnEmpty_whenNoSessions() {
            StatsCalculator.WeekHourDistribution distribution =
                    StatsCalculator.weekHourDistribution(List.of(), UTC, null, null);

            assertThat(distribution.points()).isEmpty();
            assertThat(distribution.weekdayCounts()).isEmpty();
        }
    }

    @Nested
    @DisplayName("activeYearMonths")
    class ActiveYearMonthsTests {

        @Test
        @DisplayName("shouldConvertZoneBeforeTruncating_whenSessionNearMonthBoundary")
        void shouldConvertZoneBeforeTruncating_whenSessionNearMonthBoundary() {
            // Local 2026-09-01 00:30 (+08:00) = 2026-08-31T16:30Z: the month list must report
            // September for a UTC+8 caller, not the UTC month it was stored under.
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-31T16:30:00"),
                                    at("2026-08-31T17:00:00"),
                                    "a",
                                    "Java"));

            assertThat(StatsCalculator.activeYearMonths(sessions, UTC_PLUS_8))
                    .containsExactly(YearMonth.of(2026, 9));
            assertThat(StatsCalculator.activeYearMonths(sessions, UTC))
                    .containsExactly(YearMonth.of(2026, 8));
        }

        @Test
        @DisplayName("shouldListBothMonths_whenSessionCrossesMonthBoundary")
        void shouldListBothMonths_whenSessionCrossesMonthBoundary() {
            // 2026-08-31 23:00 -> 2026-09-01 01:00 contributes a non-zero day to both months.
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-31T23:00:00"),
                                    at("2026-09-01T01:00:00"),
                                    "a",
                                    "Java"));

            assertThat(StatsCalculator.activeYearMonths(sessions, UTC))
                    .containsExactly(YearMonth.of(2026, 9), YearMonth.of(2026, 8));
        }

        @Test
        @DisplayName("shouldListBothYears_whenSessionCrossesYearBoundary")
        void shouldListBothYears_whenSessionCrossesYearBoundary() {
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2025-12-31T23:00:00"),
                                    at("2026-01-01T01:00:00"),
                                    "a",
                                    "Java"));

            assertThat(StatsCalculator.activeYearMonths(sessions, UTC))
                    .containsExactly(YearMonth.of(2026, 1), YearMonth.of(2025, 12));
        }

        @Test
        @DisplayName("shouldMergeOverlapsAndIgnoreInvalid_whenDerivingMonths")
        void shouldMergeOverlapsAndIgnoreInvalid_whenDerivingMonths() {
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-06-10T10:00:00"),
                                    at("2026-06-10T11:00:00"),
                                    "a",
                                    "Java"),
                            session(
                                    at("2026-06-10T10:30:00"),
                                    at("2026-06-10T11:30:00"),
                                    "b",
                                    "Kotlin"),
                            // zero-duration: must not register a month of its own
                            session(
                                    at("2026-07-01T10:00:00"),
                                    at("2026-07-01T10:00:00"),
                                    "c",
                                    "Java"));

            assertThat(StatsCalculator.activeYearMonths(sessions, UTC))
                    .containsExactly(YearMonth.of(2026, 6));
        }

        @Test
        @DisplayName("shouldReturnEmpty_whenNoSessions")
        void shouldReturnEmpty_whenNoSessions() {
            assertThat(StatsCalculator.activeYearMonths(List.of(), UTC)).isEmpty();
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

        @Test
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

        @Test
        @DisplayName("shouldCountActiveDays_whenWindowHasCodingOnDistinctDays")
        void shouldCountActiveDays_whenWindowHasCodingOnDistinctDays() {
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-30T07:00:00"),
                                    at("2026-08-30T08:00:00"),
                                    "a",
                                    "Java"),
                            session(
                                    at("2026-08-30T07:30:00"),
                                    at("2026-08-30T08:30:00"),
                                    "b",
                                    "Kotlin"),
                            session(
                                    at("2026-08-31T07:00:00"),
                                    at("2026-08-31T08:00:00"),
                                    "a",
                                    "Java"));

            int days = StatsCalculator.activeDaysInDailyWindow(sessions, UTC, 6, 9);

            // 08-30 and 08-31, same-day overlaps collapse into one active day
            assertThat(days).isEqualTo(2);
        }

        @Test
        @DisplayName("shouldCountPreviousDayWindow_whenSessionStartsBeforeWindowHour")
        void shouldCountPreviousDayWindow_whenSessionStartsBeforeWindowHour() {
            // 01:00 belongs to the previous day's 22:00-05:00 window
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-30T01:00:00"),
                                    at("2026-08-30T02:00:00"),
                                    "a",
                                    "Java"));

            int days = StatsCalculator.activeDaysInDailyWindow(sessions, UTC, 22, 5);

            assertThat(days).isEqualTo(1);
        }

        @Test
        @DisplayName("shouldReturnZeroActiveDays_whenNoWindowCoding")
        void shouldReturnZeroActiveDays_whenNoWindowCoding() {
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-30T10:00:00"),
                                    at("2026-08-30T11:00:00"),
                                    "a",
                                    "Java"));

            int days = StatsCalculator.activeDaysInDailyWindow(sessions, UTC, 6, 9);

            assertThat(days).isZero();
        }
    }

    @Nested
    @DisplayName("maxDaily")
    class MaxDailyTests {

        @Test
        @DisplayName("shouldReturnLongestSingleDay_whenSessionsSpanDays")
        void shouldReturnLongestSingleDay_whenSessionsSpanDays() {
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-30T09:00:00"),
                                    at("2026-08-30T13:00:00"),
                                    "a",
                                    "Java"),
                            session(
                                    at("2026-08-30T14:00:00"),
                                    at("2026-08-30T16:00:00"),
                                    "a",
                                    "Java"),
                            session(
                                    at("2026-08-31T10:00:00"),
                                    at("2026-08-31T11:00:00"),
                                    "b",
                                    "Kotlin"));

            long max = StatsCalculator.maxDailySeconds(sessions, UTC);

            // 08-30: 09:00-13:00 + 14:00-16:00 = 6h = 21600s (no overlap, both count)
            assertThat(max).isEqualTo(21600);
        }

        @Test
        @DisplayName("shouldCollapseOverlapsWithinSameDay")
        void shouldCollapseOverlapsWithinSameDay() {
            List<CodingSession> sessions =
                    List.of(
                            session(
                                    at("2026-08-30T09:00:00"),
                                    at("2026-08-30T13:00:00"),
                                    "a",
                                    "Java"),
                            session(
                                    at("2026-08-30T12:00:00"),
                                    at("2026-08-30T15:00:00"),
                                    "b",
                                    "Kotlin"));

            long max = StatsCalculator.maxDailySeconds(sessions, UTC);

            // merged 09:00-15:00 = 6h, not 7h
            assertThat(max).isEqualTo(21600);
        }

        @Test
        @DisplayName("shouldReturnZero_whenNoSessions")
        void shouldReturnZero_whenNoSessions() {
            assertThat(StatsCalculator.maxDailySeconds(List.of(), UTC)).isZero();
        }
    }

    @Nested
    @DisplayName("perfectMonth")
    class PerfectMonthTests {

        @Test
        @DisplayName("shouldReturnTrue_whenOneMonthCodedEveryDay")
        void shouldReturnTrue_whenOneMonthCodedEveryDay() {
            // August 2026 has 31 days; code 1h every day
            List<CodingSession> sessions =
                    java.util.stream.IntStream.rangeClosed(1, 31)
                            .mapToObj(
                                    day ->
                                            session(
                                                    at(
                                                            "2026-08-"
                                                                    + String.format("%02d", day)
                                                                    + "T10:00:00"),
                                                    at(
                                                            "2026-08-"
                                                                    + String.format("%02d", day)
                                                                    + "T11:00:00"),
                                                    "a",
                                                    "Java"))
                            .toList();

            assertThat(StatsCalculator.hasPerfectMonth(sessions, UTC)).isTrue();
        }

        @Test
        @DisplayName("shouldReturnFalse_whenOneDayMissing")
        void shouldReturnFalse_whenOneDayMissing() {
            List<CodingSession> sessions =
                    java.util.stream.IntStream.rangeClosed(1, 30)
                            .mapToObj(
                                    day ->
                                            session(
                                                    at(
                                                            "2026-08-"
                                                                    + String.format("%02d", day)
                                                                    + "T10:00:00"),
                                                    at(
                                                            "2026-08-"
                                                                    + String.format("%02d", day)
                                                                    + "T11:00:00"),
                                                    "a",
                                                    "Java"))
                            .toList();

            assertThat(StatsCalculator.hasPerfectMonth(sessions, UTC)).isFalse();
        }

        @Test
        @DisplayName("shouldReturnFalse_whenNoSessions")
        void shouldReturnFalse_whenNoSessions() {
            assertThat(StatsCalculator.hasPerfectMonth(List.of(), UTC)).isFalse();
        }
    }
}
