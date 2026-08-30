package com.ahogek.cttserver.stats.service;

import com.ahogek.cttserver.sync.entity.CodingSession;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Pure domain calculator for coding-session statistics.
 *
 * <p>Aggregates a user's live coding sessions into the dashboard dimensions used by the plugin
 * StatisticsView: summary, daily heatmap, streaks, language/project/time-of-day/hourly/weekday
 * distributions and recent activity. No Spring or database dependencies, so every dimension is
 * unit-testable against plain session lists.
 *
 * <p>Two duration semantics exist by design and mirror the plugin side:
 *
 * <ul>
 *   <li><b>Merged</b> (summary, heatmap, streaks): overlapping intervals are merged so concurrent
 *       sessions across projects or windows are not double counted.
 *   <li><b>Raw accumulation</b> (languages, projects, time-of-day, hourly, weekday): each session
 *       contributes its own duration, matching the plugin's distribution providers.
 * </ul>
 *
 * <p>All boundaries are computed in the caller-provided zone offset: sessions are stored as UTC
 * {@code Instant} and shifted to the local timezone before day/week/month/year/period bucketing.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-30
 */
public final class StatsCalculator {

    /** A half-open coding interval expressed in the caller's zone. */
    public record TimeInterval(OffsetDateTime start, OffsetDateTime end) {

        public TimeInterval {
            if (!start.isBefore(end)) {
                throw new IllegalArgumentException("interval start must be before end");
            }
        }

        /** Returns the interval duration. */
        public Duration duration() {
            return Duration.between(start, end);
        }
    }

    /** Period summary, all values in seconds. */
    public record Summary(
            long today,
            long dailyAverage,
            long thisWeek,
            long thisMonth,
            long thisYear,
            long total) {}

    /** One day of coding in the heatmap. */
    public record DailyPoint(LocalDate date, long seconds) {}

    /** Consecutive coding days. */
    public record Streaks(int current, int max) {}

    /** A named distribution bucket (language, project, time-of-day or weekday). */
    public record DistributionEntry(String name, long seconds) {}

    /** Per-hour usage averaged across active days. */
    public record HourlyPoint(int hour, long averageSeconds, int activeDays) {}

    private StatsCalculator() {}

    /**
     * Converts sessions to time intervals in the given zone.
     *
     * @param sessions live coding sessions owned by the user
     * @param zone the aggregation timezone
     * @return the intervals
     */
    public static List<TimeInterval> toIntervals(List<CodingSession> sessions, ZoneOffset zone) {
        return sessions.stream()
                .map(
                        session ->
                                new TimeInterval(
                                        session.getStartTime().atOffset(zone),
                                        session.getEndTime().atOffset(zone)))
                .toList();
    }

    /**
     * Merges overlapping intervals so concurrent sessions are not double counted.
     *
     * @param intervals the raw intervals (any order)
     * @return merged, start-sorted, non-overlapping intervals
     */
    public static List<TimeInterval> mergeOverlapping(List<TimeInterval> intervals) {
        List<TimeInterval> sorted =
                intervals.stream().sorted(Comparator.comparing(TimeInterval::start)).toList();
        List<TimeInterval> merged = new ArrayList<>();
        for (TimeInterval interval : sorted) {
            if (merged.isEmpty() || interval.start().isAfter(merged.getLast().end())) {
                merged.add(interval);
            } else if (interval.end().isAfter(merged.getLast().end())) {
                TimeInterval last = merged.removeLast();
                merged.add(new TimeInterval(last.start(), interval.end()));
            }
        }
        return merged;
    }

    /**
     * Clips intervals to a period, dropping those entirely outside.
     *
     * @param intervals the intervals
     * @param start period start (inclusive)
     * @param end period end (exclusive)
     * @return clipped intervals
     */
    public static List<TimeInterval> clipTo(
            List<TimeInterval> intervals, OffsetDateTime start, OffsetDateTime end) {
        return intervals.stream()
                .filter(interval -> interval.end().isAfter(start) && interval.start().isBefore(end))
                .map(
                        interval ->
                                new TimeInterval(
                                        max(interval.start(), start), min(interval.end(), end)))
                .filter(interval -> interval.start().isBefore(interval.end()))
                .toList();
    }

    /**
     * Total merged duration within a period, in seconds.
     *
     * @param intervals the intervals
     * @param start period start (inclusive)
     * @param end period end (exclusive)
     * @return merged duration in seconds
     */
    public static long mergedDurationSeconds(
            List<TimeInterval> intervals, OffsetDateTime start, OffsetDateTime end) {
        return mergeOverlapping(clipTo(intervals, start, end)).stream()
                .map(TimeInterval::duration)
                .reduce(Duration.ZERO, Duration::plus)
                .toSeconds();
    }

    /**
     * Computes the period summary for the reference date.
     *
     * <p>Weeks start on Monday (ISO 8601). {@code dailyAverage} is the lifetime total divided by
     * the number of days from the first session day to the reference day inclusive, matching the
     * plugin SummaryDataProvider.
     *
     * @param sessions live sessions
     * @param zone aggregation timezone
     * @param today reference date
     * @return the summary in seconds
     */
    public static Summary summary(List<CodingSession> sessions, ZoneOffset zone, LocalDate today) {
        List<TimeInterval> intervals = toIntervals(sessions, zone);
        if (intervals.isEmpty()) {
            return new Summary(0, 0, 0, 0, 0, 0);
        }
        OffsetDateTime dayStart = today.atStartOfDay().atOffset(zone);
        OffsetDateTime weekStart = today.with(DayOfWeek.MONDAY).atStartOfDay().atOffset(zone);
        OffsetDateTime monthStart = today.withDayOfMonth(1).atStartOfDay().atOffset(zone);
        OffsetDateTime yearStart = today.withDayOfYear(1).atStartOfDay().atOffset(zone);

        long total =
                mergeOverlapping(intervals).stream()
                        .map(TimeInterval::duration)
                        .reduce(Duration.ZERO, Duration::plus)
                        .toSeconds();
        long todaySeconds = mergedDurationSeconds(intervals, dayStart, dayStart.plusDays(1));
        long weekSeconds = mergedDurationSeconds(intervals, weekStart, weekStart.plusWeeks(1));
        long monthSeconds = mergedDurationSeconds(intervals, monthStart, monthStart.plusMonths(1));
        long yearSeconds = mergedDurationSeconds(intervals, yearStart, yearStart.plusYears(1));

        LocalDate firstDate =
                intervals.stream()
                        .map(interval -> interval.start().toLocalDate())
                        .min(LocalDate::compareTo)
                        .orElse(today);
        long daysSinceFirst = ChronoUnit.DAYS.between(firstDate, today) + 1;
        long dailyAverage = total / Math.max(daysSinceFirst, 1);

        return new Summary(
                todaySeconds, dailyAverage, weekSeconds, monthSeconds, yearSeconds, total);
    }

    /**
     * Computes the daily heatmap over the inclusive date range.
     *
     * <p>Intervals are merged first, then split across day boundaries so a session crossing
     * midnight contributes to both days without double counting within a day.
     *
     * @param sessions live sessions
     * @param zone aggregation timezone
     * @param startRange first date (inclusive)
     * @param endRange last date (inclusive)
     * @return daily points in date order
     */
    public static List<DailyPoint> heatmap(
            List<CodingSession> sessions,
            ZoneOffset zone,
            LocalDate startRange,
            LocalDate endRange) {
        Map<LocalDate, Duration> byDay = new TreeMap<>();
        for (TimeInterval interval : mergeOverlapping(toIntervals(sessions, zone))) {
            splitIntervalByDay(interval, byDay);
        }
        List<DailyPoint> points = new ArrayList<>();
        for (LocalDate date = startRange; !date.isAfter(endRange); date = date.plusDays(1)) {
            points.add(new DailyPoint(date, byDay.getOrDefault(date, Duration.ZERO).toSeconds()));
        }
        return points;
    }

    /**
     * Computes current and longest consecutive coding streaks.
     *
     * @param sessions live sessions
     * @param zone aggregation timezone
     * @param today reference date
     * @return current and maximum streak lengths
     */
    public static Streaks streaks(List<CodingSession> sessions, ZoneOffset zone, LocalDate today) {
        Set<LocalDate> activeDays = new TreeSet<>();
        for (TimeInterval interval : toIntervals(sessions, zone)) {
            Map<LocalDate, Duration> byDay = new TreeMap<>();
            splitIntervalByDay(interval, byDay);
            activeDays.addAll(byDay.keySet());
        }
        if (activeDays.isEmpty()) {
            return new Streaks(0, 0);
        }
        int current = 0;
        LocalDate cursor = activeDays.contains(today) ? today : today.minusDays(1);
        while (activeDays.contains(cursor)) {
            current++;
            cursor = cursor.minusDays(1);
        }
        int max = 0;
        int run = 0;
        LocalDate previous = null;
        for (LocalDate date : activeDays) {
            run = (previous != null && date.equals(previous.plusDays(1))) ? run + 1 : 1;
            max = Math.max(max, run);
            previous = date;
        }
        return new Streaks(current, max);
    }

    /**
     * Accumulates raw session durations by the given key extractor.
     *
     * @param sessions live sessions
     * @param zone aggregation timezone
     * @param keyExtractor bucket key per session
     * @return entries ordered by duration descending
     */
    public static List<DistributionEntry> accumulateBy(
            List<CodingSession> sessions,
            ZoneOffset zone,
            Function<CodingSession, String> keyExtractor) {
        Map<String, Long> byKey = new LinkedHashMap<>();
        for (CodingSession session : sessions) {
            long seconds =
                    Duration.between(
                                    session.getStartTime().atOffset(zone),
                                    session.getEndTime().atOffset(zone))
                            .toSeconds();
            byKey.merge(keyExtractor.apply(session), seconds, Long::sum);
        }
        return byKey.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new DistributionEntry(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * Computes per-hour average usage across active days.
     *
     * <p>Each hour bucket accumulates the seconds coded in that hour; the average is the bucket
     * total divided by the number of active days.
     *
     * @param sessions live sessions
     * @param zone aggregation timezone
     * @return one entry per hour (0-23), hour order
     */
    public static List<HourlyPoint> hourlyDistribution(
            List<CodingSession> sessions, ZoneOffset zone) {
        long[] hourSeconds = new long[24];
        for (TimeInterval interval : toIntervals(sessions, zone)) {
            OffsetDateTime cursor = interval.start();
            while (cursor.isBefore(interval.end())) {
                int hour = cursor.getHour();
                OffsetDateTime nextHour = cursor.truncatedTo(ChronoUnit.HOURS).plusHours(1);
                OffsetDateTime sliceEnd =
                        nextHour.isBefore(interval.end()) ? nextHour : interval.end();
                hourSeconds[hour] += Duration.between(cursor, sliceEnd).toSeconds();
                cursor = sliceEnd;
            }
        }
        long activeDays =
                toIntervals(sessions, zone).stream()
                        .map(interval -> interval.start().toLocalDate())
                        .distinct()
                        .count();
        List<HourlyPoint> points = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            long average = activeDays == 0 ? 0 : hourSeconds[hour] / activeDays;
            points.add(new HourlyPoint(hour, average, (int) activeDays));
        }
        return points;
    }

    /**
     * Computes weekday distribution from raw session durations.
     *
     * @param sessions live sessions
     * @param zone aggregation timezone
     * @return entries keyed by ISO weekday name, duration descending
     */
    public static List<DistributionEntry> weekdayDistribution(
            List<CodingSession> sessions, ZoneOffset zone) {
        return accumulateBy(
                sessions,
                zone,
                session -> session.getStartTime().atOffset(zone).getDayOfWeek().name());
    }

    /**
     * Splits a single interval across day boundaries, merging into the per-day map.
     *
     * @param interval the interval
     * @param byDay mutable per-day accumulator
     */
    private static void splitIntervalByDay(TimeInterval interval, Map<LocalDate, Duration> byDay) {
        OffsetDateTime cursor = interval.start();
        while (cursor.isBefore(interval.end())) {
            LocalDate day = cursor.toLocalDate();
            OffsetDateTime dayEnd = day.plusDays(1).atStartOfDay().atOffset(cursor.getOffset());
            OffsetDateTime sliceEnd = dayEnd.isBefore(interval.end()) ? dayEnd : interval.end();
            byDay.merge(day, Duration.between(cursor, sliceEnd), Duration::plus);
            cursor = sliceEnd;
        }
    }

    private static OffsetDateTime max(OffsetDateTime a, OffsetDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static OffsetDateTime min(OffsetDateTime a, OffsetDateTime b) {
        return a.isBefore(b) ? a : b;
    }
}
