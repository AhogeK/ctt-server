package com.ahogek.cttserver.stats.service;

import com.ahogek.cttserver.stats.enums.TimeOfDay;
import com.ahogek.cttserver.sync.entity.CodingSession;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
                .filter(session -> session.getStartTime().isBefore(session.getEndTime()))
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
     * Clips intervals to an inclusive calendar-date window, dropping sessions that fall entirely
     * outside it.
     *
     * <p>Either bound may be {@code null} for an open-ended window. The end bound closes at the end
     * date's midnight (exclusive), so a session starting before it or spilling past it is clipped
     * rather than dropped whole.
     *
     * @param intervals zone-shifted session intervals
     * @param zone aggregation timezone
     * @param windowStart window start date (inclusive), or {@code null} for no lower bound
     * @param windowEnd window end date (inclusive), or {@code null} for no upper bound
     * @return intervals clipped to the window
     */
    public static List<TimeInterval> clipToWindow(
            List<TimeInterval> intervals,
            ZoneOffset zone,
            LocalDate windowStart,
            LocalDate windowEnd) {
        if (windowStart == null && windowEnd == null) {
            return intervals;
        }
        OffsetDateTime from =
                windowStart != null ? windowStart.atStartOfDay(zone).toOffsetDateTime() : null;
        OffsetDateTime to =
                windowEnd != null
                        ? windowEnd.plusDays(1).atStartOfDay(zone).toOffsetDateTime()
                        : null;
        if (from != null && to != null) {
            return clipTo(intervals, from, to);
        }
        // One-sided bound: clamp each side independently and drop sessions that fall entirely
        // outside the window. Bounds are clamped on raw values and only non-empty results are
        // materialized, since TimeInterval rejects start >= end.
        final OffsetDateTime floor = from;
        final OffsetDateTime ceiling = to;
        return intervals.stream()
                .map(
                        interval -> {
                            OffsetDateTime start =
                                    floor != null && interval.start().isBefore(floor)
                                            ? floor
                                            : interval.start();
                            OffsetDateTime end =
                                    ceiling != null && interval.end().isAfter(ceiling)
                                            ? ceiling
                                            : interval.end();
                            return start.isBefore(end) ? new TimeInterval(start, end) : null;
                        })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Clips live sessions to an inclusive calendar-date window, dropping sessions that fall
     * entirely outside it. Session identity is preserved (project/language/device attribution stay
     * intact); only the start/end instants are clamped. Either bound may be {@code null} for an
     * open-ended window.
     *
     * @param sessions live coding sessions owned by the user
     * @param zone aggregation timezone
     * @param windowStart window start date (inclusive), or {@code null} for no lower bound
     * @param windowEnd window end date (inclusive), or {@code null} for no upper bound
     * @return sessions whose instants are clipped to the window
     */
    public static List<CodingSession> clipSessions(
            List<CodingSession> sessions,
            ZoneOffset zone,
            LocalDate windowStart,
            LocalDate windowEnd) {
        if (windowStart == null && windowEnd == null) {
            return sessions;
        }
        OffsetDateTime from =
                windowStart != null ? windowStart.atStartOfDay(zone).toOffsetDateTime() : null;
        OffsetDateTime to =
                windowEnd != null
                        ? windowEnd.plusDays(1).atStartOfDay(zone).toOffsetDateTime()
                        : null;
        List<CodingSession> clipped = new ArrayList<>();
        List<TimeInterval> intervals = toIntervals(sessions, zone);
        for (int i = 0; i < intervals.size(); i++) {
            TimeInterval interval = intervals.get(i);
            OffsetDateTime start =
                    from != null && interval.start().isBefore(from) ? from : interval.start();
            OffsetDateTime end = to != null && interval.end().isAfter(to) ? to : interval.end();
            if (!start.isBefore(end)) {
                continue;
            }
            CodingSession session = sessions.get(i);
            CodingSession copy = new CodingSession();
            copy.setId(session.getId());
            copy.setSessionUuid(session.getSessionUuid());
            copy.setProjectName(session.getProjectName());
            copy.setLanguage(session.getLanguage());
            copy.setStartTime(start.toInstant());
            copy.setEndTime(end.toInstant());
            copy.setClientModifiedAt(session.getClientModifiedAt());
            copy.setClientVersion(session.getClientVersion());
            copy.setOriginDeviceId(session.getOriginDeviceId());
            clipped.add(copy);
        }
        return clipped;
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
        // Full-precision accumulation: flooring per session would lose each session's
        // sub-second tail (the plugin writes milliseconds) and deflate the bucket totals
        // relative to summary.total; truncate once per bucket instead.
        Map<String, Duration> byKey = new LinkedHashMap<>();
        for (CodingSession session : sessions) {
            if (!session.getStartTime().isBefore(session.getEndTime())) {
                continue;
            }
            byKey.merge(
                    keyExtractor.apply(session),
                    Duration.between(
                            session.getStartTime().atOffset(zone),
                            session.getEndTime().atOffset(zone)),
                    Duration::plus);
        }
        return apportion(byKey);
    }

    /**
     * Computes per-hour average usage across active days.
     *
     * <p>Each hour bucket accumulates the seconds coded in that hour; the average is the bucket
     * total divided by the number of active days. When a date window is given, sessions are clipped
     * to it first and {@code activeDays} counts the days with coding inside the window only.
     *
     * @param sessions live sessions
     * @param zone aggregation timezone
     * @param windowStart window start date (inclusive), or {@code null} for the full history
     * @param windowEnd window end date (inclusive), or {@code null} for the full history
     * @return one entry per hour (0-23), hour order
     */
    public static List<HourlyPoint> hourlyDistribution(
            List<CodingSession> sessions,
            ZoneOffset zone,
            LocalDate windowStart,
            LocalDate windowEnd) {
        List<TimeInterval> intervals =
                clipToWindow(toIntervals(sessions, zone), zone, windowStart, windowEnd);
        long[] hourSeconds = new long[24];
        for (TimeInterval interval : intervals) {
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
                intervals.stream()
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
     * Computes the time-of-day distribution with the plugin's weekly-statistics semantics.
     *
     * <p>Overlapping sessions are merged into unions first (earliest start to latest end), then
     * each union is sliced at the time-of-day bucket boundaries (Night 00:00-05:59, Morning
     * 06:00-11:59, Daytime 12:00-17:59, Evening 18:00-23:59) so every second lands in exactly one
     * bucket. The bucket totals therefore sum to the same coding time the summary reports.
     *
     * @param sessions live sessions
     * @param zone aggregation timezone
     * @return entries ordered by duration descending
     */
    public static List<DistributionEntry> timeOfDayDistribution(
            List<CodingSession> sessions, ZoneOffset zone) {
        // Buckets accumulate full-precision durations; truncating per slice would floor away
        // the sub-second remainder of every session that starts off the second (the plugin
        // writes milliseconds), losing ~0.5s per session versus summary.total.
        Map<TimeOfDay, Duration> byBucket = new LinkedHashMap<>();
        for (TimeInterval interval : mergeOverlapping(toIntervals(sessions, zone))) {
            OffsetDateTime cursor = interval.start();
            while (cursor.isBefore(interval.end())) {
                TimeOfDay bucket = TimeOfDay.fromHour(cursor.getHour());
                int boundaryHour = TimeOfDay.boundaryAfter(cursor.getHour());
                // Evening ends at midnight (24), which is the next day's 00:00; the other
                // buckets close within the same day.
                OffsetDateTime nextBoundary =
                        boundaryHour == 24
                                ? cursor.plusDays(1).truncatedTo(ChronoUnit.DAYS)
                                : cursor.withHour(boundaryHour).truncatedTo(ChronoUnit.HOURS);
                OffsetDateTime sliceEnd =
                        nextBoundary.isBefore(interval.end()) ? nextBoundary : interval.end();
                byBucket.merge(bucket, Duration.between(cursor, sliceEnd), Duration::plus);
                cursor = sliceEnd;
            }
        }
        Map<String, Duration> byLabel = new LinkedHashMap<>();
        byBucket.forEach((bucket, duration) -> byLabel.put(bucket.name(), duration));
        return apportion(byLabel);
    }

    /** Average coding seconds for one weekday-hour cell. */
    public record WeekHourPoint(int dayOfWeek, int hour, long averageSeconds) {}

    /** Weekday-hour grid averaged per weekday appearance, plus the per-weekday day counts. */
    public record WeekHourDistribution(
            List<WeekHourPoint> points, Map<Integer, Integer> weekdayCounts) {}

    /**
     * Computes per-weekday per-hour average usage, matching the plugin's weekly heatmap.
     *
     * <p>Each session is sliced at hour boundaries in the caller's zone; a slice lands in the
     * (localWeekday, localHour) bucket of its slice start. A bucket's average divides its total
     * seconds by the number of days that weekday appears in the aggregation window, so the
     * denominator matches how many times that cell could have been exercised. Only exercised cells
     * are returned; the caller renders missing cells as zero.
     *
     * @param sessions live sessions
     * @param zone aggregation timezone
     * @param windowStart window start date (inclusive), or {@code null} for the sessions' own
     *     earliest date
     * @param windowEnd window end date (inclusive), or {@code null} for the sessions' own latest
     *     date
     * @return exercised weekday-hour cells with per-weekday denominators
     */
    public static WeekHourDistribution weekHourDistribution(
            List<CodingSession> sessions,
            ZoneOffset zone,
            LocalDate windowStart,
            LocalDate windowEnd) {
        // Overlapping sessions describe the same wall-clock activity (e.g. parallel project
        // windows): merge them first so a shared hour is counted once, matching the summary and
        // heatmap semantics. The union spans the earliest start to the latest end.
        // Overlapping sessions describe the same wall-clock activity (e.g. parallel project
        // windows): merge them first so a shared hour is counted once, matching the summary
        // and heatmap semantics. The union spans the earliest start to the latest end.
        List<TimeInterval> intervals =
                clipToWindow(
                        mergeOverlapping(toIntervals(sessions, zone)),
                        zone,
                        windowStart,
                        windowEnd);
        long[][] cellSeconds = new long[7][24];
        for (TimeInterval interval : intervals) {
            OffsetDateTime cursor = interval.start();
            while (cursor.isBefore(interval.end())) {
                int weekday = cursor.getDayOfWeek().getValue() - 1;
                int hour = cursor.getHour();
                OffsetDateTime nextHour = cursor.truncatedTo(ChronoUnit.HOURS).plusHours(1);
                OffsetDateTime sliceEnd =
                        nextHour.isBefore(interval.end()) ? nextHour : interval.end();
                cellSeconds[weekday][hour] += Duration.between(cursor, sliceEnd).toSeconds();
                cursor = sliceEnd;
            }
        }
        Map<Integer, Integer> weekdayCounts =
                weekdayAppearanceCounts(intervals, windowStart, windowEnd);
        List<WeekHourPoint> points = new ArrayList<>();
        for (int weekday = 0; weekday < 7; weekday++) {
            int dayOfWeek = weekday + 1;
            Integer days = weekdayCounts.get(dayOfWeek);
            if (days == null || days == 0) {
                continue;
            }
            for (int hour = 0; hour < 24; hour++) {
                if (cellSeconds[weekday][hour] > 0) {
                    points.add(
                            new WeekHourPoint(dayOfWeek, hour, cellSeconds[weekday][hour] / days));
                }
            }
        }
        return new WeekHourDistribution(points, weekdayCounts);
    }

    /**
     * Counts how often each weekday occurs in the aggregation window, falling back to the sessions'
     * own date span when the window is open.
     *
     * @param intervals zone-shifted session intervals
     * @param windowStart window start date (inclusive), or {@code null} to derive from sessions
     * @param windowEnd window end date (inclusive), or {@code null} to derive from sessions
     * @return ISO weekday (1=Monday..7=Sunday) to day count
     */
    private static Map<Integer, Integer> weekdayAppearanceCounts(
            List<TimeInterval> intervals, LocalDate windowStart, LocalDate windowEnd) {
        if (intervals.isEmpty()) {
            return Map.of();
        }
        LocalDate start = windowStart;
        LocalDate end = windowEnd;
        if (start == null || end == null) {
            List<TimeInterval> sorted =
                    intervals.stream().sorted(Comparator.comparing(TimeInterval::start)).toList();
            LocalDate firstDay = sorted.getFirst().start().toLocalDate();
            LocalDate lastDay = sorted.getLast().end().minusNanos(1).toLocalDate();
            start = start != null ? start : firstDay;
            end = end != null ? end : lastDay;
        }
        if (end.isBefore(start)) {
            return Map.of();
        }
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            counts.merge(date.getDayOfWeek().getValue(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Converts full-precision per-bucket durations into second-granularity entries via
     * largest-remainder apportionment: each bucket is floored, and the leftover seconds (0..n-1)
     * are handed to the buckets with the biggest fractional remainders. The assigned sum equals the
     * full-precision total truncated once — the same single truncation summary.total performs — so
     * every distribution's bucket sum equals the summary total.
     *
     * @param byBucket full-precision durations keyed by bucket label
     * @return entries ordered by assigned seconds descending
     */
    public static List<DistributionEntry> apportion(Map<String, Duration> byBucket) {
        long fullTotal =
                byBucket.values().stream().reduce(Duration.ZERO, Duration::plus).toSeconds();
        long flooredSum = 0;
        Map<String, Long> assigned = new LinkedHashMap<>();
        for (Map.Entry<String, Duration> entry : byBucket.entrySet()) {
            long seconds = entry.getValue().toSeconds();
            flooredSum += seconds;
            assigned.put(entry.getKey(), seconds);
        }
        List<String> order =
                byBucket.entrySet().stream()
                        .sorted(Map.Entry.<String, Duration>comparingByValue().reversed())
                        .map(Map.Entry::getKey)
                        .toList();
        for (int i = 0; i < fullTotal - flooredSum; i++) {
            String bucket = order.get(i % order.size());
            assigned.put(bucket, assigned.get(bucket) + 1);
        }
        return assigned.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new DistributionEntry(entry.getKey(), entry.getValue()))
                .toList();
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
     * Computes the total merged duration inside a daily recurring window over a period.
     *
     * <p>The window is expressed in hours of day and may cross midnight (e.g. a night-owl window
     * 22:00 to 05:00). Intervals are merged first so overlapping sessions are not double counted,
     * then clipped to the period and intersected with each covered day's window.
     *
     * @param sessions live sessions
     * @param zone aggregation timezone
     * @param windowStartHour first hour of the window (inclusive, 0-23)
     * @param windowEndHour hour just after the window ends (exclusive, 0-23; may be before {@code
     *     windowStartHour} to cross midnight)
     * @param periodStart period start (inclusive)
     * @param periodEnd period end (exclusive)
     * @return merged seconds inside the daily window within the period
     */
    public static long mergedDurationInDailyWindow(
            List<CodingSession> sessions,
            ZoneOffset zone,
            int windowStartHour,
            int windowEndHour,
            OffsetDateTime periodStart,
            OffsetDateTime periodEnd) {
        List<TimeInterval> intervals =
                mergeOverlapping(clipTo(toIntervals(sessions, zone), periodStart, periodEnd));
        return windowDays(intervals, zone, windowStartHour, windowEndHour).stream()
                .mapToLong(day -> day.duration().toSeconds())
                .sum();
    }

    /**
     * Computes the number of distinct days with any coding inside a daily recurring window.
     *
     * <p>Uses the same window-day attribution as {@link #mergedDurationInDailyWindow}: a session
     * beginning before the window start hour belongs to the previous day's window. Only the
     * lifetime is considered (no period bound), matching the cumulative nature of achievements.
     *
     * @param sessions live sessions
     * @param zone aggregation timezone
     * @param windowStartHour first hour of the window (inclusive, 0-23)
     * @param windowEndHour hour just after the window ends (exclusive, 0-23; may be before {@code
     *     windowStartHour} to cross midnight)
     * @return the number of distinct active window days
     */
    public static int activeDaysInDailyWindow(
            List<CodingSession> sessions, ZoneOffset zone, int windowStartHour, int windowEndHour) {
        return (int)
                windowDays(
                                mergeOverlapping(toIntervals(sessions, zone)),
                                zone,
                                windowStartHour,
                                windowEndHour)
                        .stream()
                        .map(WindowDay::day)
                        .distinct()
                        .count();
    }

    /**
     * Returns the longest single-day merged coding duration in seconds.
     *
     * @param sessions live sessions
     * @param zone aggregation timezone
     * @return the maximum daily duration in seconds (0 when there are no sessions)
     */
    public static long maxDailySeconds(List<CodingSession> sessions, ZoneOffset zone) {
        Map<LocalDate, Duration> byDay = new HashMap<>();
        for (TimeInterval interval : mergeOverlapping(toIntervals(sessions, zone))) {
            splitIntervalByDay(interval, byDay);
        }
        return byDay.values().stream().mapToLong(Duration::toSeconds).max().orElse(0);
    }

    /**
     * Returns the highest percentage of a calendar month's days that carry coding, across all
     * months (0-100).
     *
     * <p>Expressed as a fraction of the month's own length rather than a raw day count so that
     * every month means the same thing: 28/28 in February and 31/31 in August are both 100, while
     * 29/31 is 93 either way. A raw count would make the same number of days worth different
     * percentages depending on which month it fell in.
     *
     * <p>An in-progress month is measured against its full length like any other, so its ratio
     * rises as the month fills instead of resetting or sitting at 100 on day one.
     *
     * <p>A day counts as active when its overlap-collapsed duration is a positive number of
     * seconds, matching the existence rule the heatmap and month list use.
     *
     * @param sessions live sessions
     * @param zone aggregation timezone
     * @return the best month's coverage as a whole percentage (0 when there are no sessions)
     */
    public static long bestPerfectMonthPercent(List<CodingSession> sessions, ZoneOffset zone) {
        Map<YearMonth, Set<LocalDate>> activeByMonth = new HashMap<>();
        for (Map.Entry<LocalDate, Long> entry : mergedSecondsByDay(sessions, zone).entrySet()) {
            if (entry.getValue() > 0) {
                activeByMonth
                        .computeIfAbsent(YearMonth.from(entry.getKey()), _ -> new HashSet<>())
                        .add(entry.getKey());
            }
        }
        long best = 0;
        for (Map.Entry<YearMonth, Set<LocalDate>> entry : activeByMonth.entrySet()) {
            long percent = 100L * entry.getValue().size() / entry.getKey().lengthOfMonth();
            best = Math.max(best, percent);
        }
        return best;
    }

    /**
     * A day that overlaps a daily recurring window together with the overlap's start and duration.
     *
     * @param day the window day
     * @param start the moment the window overlap began
     * @param duration the coding duration inside the window on that day
     */
    private record WindowDay(LocalDate day, OffsetDateTime start, Duration duration) {}

    /**
     * Intersects intervals with a daily recurring window, emitting one entry per window day that
     * overlaps any interval.
     *
     * <p>A window day starts at the window's start hour, so an interval beginning before that hour
     * (e.g. 01:00 under a 22:00-05:00 window) belongs to the previous day's window.
     *
     * @param intervals merged, period-clipped intervals
     * @param zone aggregation timezone
     * @param windowStartHour first hour of the window (inclusive, 0-23)
     * @param windowEndHour hour just after the window ends (exclusive, 0-23; may be before {@code
     *     windowStartHour} to cross midnight)
     * @return overlapping window-day entries
     */
    private static List<WindowDay> windowDays(
            List<TimeInterval> intervals, ZoneOffset zone, int windowStartHour, int windowEndHour) {
        List<WindowDay> result = new ArrayList<>();
        int windowHours = windowEndHour - windowStartHour;
        if (windowHours <= 0) {
            windowHours += 24;
        }
        for (TimeInterval interval : intervals) {
            LocalDate firstWindowDay = interval.start().toLocalDate();
            if (interval.start().getHour() < windowStartHour) {
                firstWindowDay = firstWindowDay.minusDays(1);
            }
            for (LocalDate day = firstWindowDay; ; day = day.plusDays(1)) {
                OffsetDateTime dayStart = day.atStartOfDay().atOffset(zone);
                OffsetDateTime windowStart = dayStart.plusHours(windowStartHour);
                OffsetDateTime windowEnd = windowStart.plusHours(windowHours);
                OffsetDateTime overlapStart = max(interval.start(), windowStart);
                OffsetDateTime overlapEnd = min(interval.end(), windowEnd);
                if (overlapStart.isBefore(overlapEnd)) {
                    result.add(
                            new WindowDay(
                                    day, overlapStart, Duration.between(overlapStart, overlapEnd)));
                }
                if (!windowEnd.isBefore(interval.end())) {
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Computes the overlap-collapsed coding seconds attributed to each UTC day.
     *
     * <p>Sessions are merged first so overlapping spans are not double counted, then split at UTC
     * midnight boundaries. This is the exact per-day aggregation the materialized daily-stats table
     * stores.
     *
     * @param sessions live sessions
     * @param zone aggregation timezone
     * @return UTC day to merged seconds
     */
    public static Map<LocalDate, Long> mergedSecondsByDay(
            List<CodingSession> sessions, ZoneOffset zone) {
        Map<LocalDate, Duration> byDay = new HashMap<>();
        for (TimeInterval interval : mergeOverlapping(toIntervals(sessions, zone))) {
            splitIntervalByDay(interval, byDay);
        }
        Map<LocalDate, Long> secondsByDay = new HashMap<>();
        byDay.forEach((day, duration) -> secondsByDay.put(day, duration.toSeconds()));
        return secondsByDay;
    }

    /**
     * Lists the calendar months that contain at least one non-zero coding day in the given zone,
     * newest first.
     *
     * <p>This is the existence test the heatmap renders: a month qualifies when some local day
     * inside it carries a positive (second-granularity) total. Sessions straddling a month or year
     * boundary contribute to both months, and the zone conversion happens before truncation, so the
     * list never advertises a month whose heatmap would be empty.
     *
     * @param sessions live sessions
     * @param zone aggregation timezone
     * @return the active months in descending order
     */
    public static List<YearMonth> activeYearMonths(List<CodingSession> sessions, ZoneOffset zone) {
        return mergedSecondsByDay(sessions, zone).entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> YearMonth.from(entry.getKey()))
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    /**
     * Returns the earliest instant at which the merged coding duration reached a target.
     *
     * <p>Progress accumulates continuously while a session runs, so the crossing generally falls
     * inside an interval rather than on a boundary: the interval that crosses contributes only the
     * remainder the achievement still needed when it began. Progress counts whole seconds like
     * {@link #summary}, so the crossing instant is where the raw duration reaches the target
     * exactly — the same point at which the truncated total first reports the target.
     *
     * @param sessions live sessions
     * @param zone aggregation timezone
     * @param targetSeconds target duration in seconds
     * @return the attaining instant, or {@code null} when the target is never reached
     */
    public static Instant totalSecondsAchievedAt(
            List<CodingSession> sessions, ZoneOffset zone, long targetSeconds) {
        Duration accumulated = Duration.ZERO;
        for (TimeInterval interval : mergeOverlapping(toIntervals(sessions, zone))) {
            Duration next = accumulated.plus(interval.duration());
            if (next.toSeconds() >= targetSeconds) {
                return interval.start()
                        .plus(Duration.ofSeconds(targetSeconds).minus(accumulated))
                        .toInstant();
            }
            accumulated = next;
        }
        return null;
    }

    /**
     * Returns the earliest instant at which a single day's merged coding duration reached a target.
     *
     * <p>Each day accumulates on its own, and intervals are walked in chronological order, so the
     * first crossing found is the earliest one across all days.
     *
     * @param sessions live sessions
     * @param zone aggregation timezone
     * @param targetSeconds target duration in seconds
     * @return the attaining instant, or {@code null} when no day reaches the target
     */
    public static Instant maxDailySecondsAchievedAt(
            List<CodingSession> sessions, ZoneOffset zone, long targetSeconds) {
        Map<LocalDate, Duration> byDay = new HashMap<>();
        for (TimeInterval interval : mergeOverlapping(toIntervals(sessions, zone))) {
            OffsetDateTime cursor = interval.start();
            while (cursor.isBefore(interval.end())) {
                LocalDate day = cursor.toLocalDate();
                OffsetDateTime dayEnd = day.plusDays(1).atStartOfDay().atOffset(cursor.getOffset());
                OffsetDateTime sliceEnd = dayEnd.isBefore(interval.end()) ? dayEnd : interval.end();
                Duration before = byDay.getOrDefault(day, Duration.ZERO);
                Duration after = before.plus(Duration.between(cursor, sliceEnd));
                if (after.toSeconds() >= targetSeconds) {
                    return cursor.plus(Duration.ofSeconds(targetSeconds).minus(before)).toInstant();
                }
                byDay.put(day, after);
                cursor = sliceEnd;
            }
        }
        return null;
    }

    /**
     * Returns the earliest instant at which consecutive coding days reached a target.
     *
     * <p>Each day is stamped with the moment it became active — the session start that touched it,
     * or local midnight when a session crossed into it — and the runs are walked in date order, so
     * the reported instant is when the qualifying run's last day began.
     *
     * @param sessions live sessions
     * @param zone aggregation timezone
     * @param targetDays required run length in days
     * @return the attaining instant, or {@code null} when no run reaches the target
     */
    public static Instant streakAchievedAt(
            List<CodingSession> sessions, ZoneOffset zone, int targetDays) {
        Map<LocalDate, OffsetDateTime> firstActiveAt = firstActiveMomentByDay(sessions, zone);
        int run = 0;
        LocalDate previous = null;
        for (Map.Entry<LocalDate, OffsetDateTime> entry : firstActiveAt.entrySet()) {
            run = (previous != null && entry.getKey().equals(previous.plusDays(1))) ? run + 1 : 1;
            if (run >= targetDays) {
                return entry.getValue().toInstant();
            }
            previous = entry.getKey();
        }
        return null;
    }

    /**
     * Returns the earliest instant at which the number of distinct languages reached a target.
     *
     * <p>Sessions are walked in start order, so the instant is when the session carrying the
     * qualifying language began.
     *
     * @param sessions live sessions
     * @param zone aggregation timezone
     * @param targetLanguages required distinct language count
     * @return the attaining instant, or {@code null} when the target is never reached
     */
    public static Instant languageCountAchievedAt(
            List<CodingSession> sessions, ZoneOffset zone, int targetLanguages) {
        Set<String> seen = new HashSet<>();
        List<CodingSession> ordered =
                sessions.stream()
                        .filter(session -> session.getStartTime().isBefore(session.getEndTime()))
                        .sorted(Comparator.comparing(CodingSession::getStartTime))
                        .toList();
        for (CodingSession session : ordered) {
            if (seen.add(session.getLanguage()) && seen.size() >= targetLanguages) {
                return session.getStartTime();
            }
        }
        return null;
    }

    /**
     * Returns the earliest instant at which distinct active days inside a daily recurring window
     * reached a target.
     *
     * <p>Uses the same window-day attribution and counting rule as {@link
     * #activeDaysInDailyWindow}, so the instant reported here is the overlap start that made the
     * qualifying window day active.
     *
     * @param sessions live sessions
     * @param zone aggregation timezone
     * @param windowStartHour first hour of the window (inclusive, 0-23)
     * @param windowEndHour hour just after the window ends (exclusive, 0-23; may be before {@code
     *     windowStartHour} to cross midnight)
     * @param targetDays required distinct window-day count
     * @return the attaining instant, or {@code null} when the target is never reached
     */
    public static Instant activeWindowDaysAchievedAt(
            List<CodingSession> sessions,
            ZoneOffset zone,
            int windowStartHour,
            int windowEndHour,
            int targetDays) {
        Set<LocalDate> seen = new HashSet<>();
        for (WindowDay windowDay :
                windowDays(
                        mergeOverlapping(toIntervals(sessions, zone)),
                        zone,
                        windowStartHour,
                        windowEndHour)) {
            if (seen.add(windowDay.day()) && seen.size() >= targetDays) {
                return windowDay.start().toInstant();
            }
        }
        return null;
    }

    /**
     * Coding time attributed to one period: merged seconds plus how many distinct days carried
     * coding.
     *
     * @param seconds overlap-collapsed coding seconds inside the period
     * @param activeDays number of distinct days inside the period with a positive amount of coding
     */
    public record PeriodTotals(long seconds, int activeDays) {}

    /**
     * Aggregates per-day coding totals into periods designated by the caller's key function.
     *
     * <p>Built on {@link #mergedSecondsByDay}, so a session crossing midnight contributes to both
     * days and a zero-second overlap contributes to none — the same day set every other statistics
     * dimension uses. Two numbers come out because the two windowed achievement families measure
     * different things from the same days: total time sums the seconds, active days counts the
     * days.
     *
     * <p>The key function is supplied by the caller rather than derived from a window here, which
     * keeps this class free of achievement knowledge while still doing the work once: the caller
     * passes its own period-key rule, so the grouping and the achievement's notion of a period
     * cannot drift apart.
     *
     * @param sessions live sessions
     * @param zone aggregation timezone
     * @param periodKeyOf maps a local day to the key of the period containing it
     * @return period key to totals, containing only periods that carry coding
     */
    public static Map<String, PeriodTotals> totalsByPeriod(
            List<CodingSession> sessions,
            ZoneOffset zone,
            Function<LocalDate, String> periodKeyOf) {
        Map<String, MutableTotals> accumulated = new HashMap<>();
        for (Map.Entry<LocalDate, Long> day : mergedSecondsByDay(sessions, zone).entrySet()) {
            if (day.getValue() <= 0) {
                continue;
            }
            MutableTotals totals =
                    accumulated.computeIfAbsent(
                            periodKeyOf.apply(day.getKey()), _ -> new MutableTotals());
            totals.seconds += day.getValue();
            totals.activeDays++;
        }
        Map<String, PeriodTotals> byPeriod = new HashMap<>();
        accumulated.forEach(
                (key, totals) ->
                        byPeriod.put(key, new PeriodTotals(totals.seconds, totals.activeDays)));
        return byPeriod;
    }

    /** Accumulator for {@link #totalsByPeriod}; one instance per period, not per day. */
    private static final class MutableTotals {

        private long seconds;
        private int activeDays;
    }

    /**
     * Returns the number of distinct days carrying a positive amount of coding time.
     *
     * <p>Counts the same day set as the heatmap, so a session crossing midnight contributes to both
     * days and a zero-second overlap contributes to none.
     *
     * @param sessions live sessions
     * @param zone aggregation timezone
     * @return the number of active days
     */
    public static int activeDayCount(List<CodingSession> sessions, ZoneOffset zone) {
        return (int)
                mergedSecondsByDay(sessions, zone).entrySet().stream()
                        .filter(entry -> entry.getValue() > 0)
                        .count();
    }

    /**
     * Returns the earliest instant at which distinct active days reached a target.
     *
     * <p>Walks the days in order, so the reported instant is the moment the qualifying day became
     * active — the same first-active moment {@link #streakAchievedAt} uses.
     *
     * @param sessions live sessions
     * @param zone aggregation timezone
     * @param targetDays required distinct day count
     * @return the attaining instant, or {@code null} when the target is never reached
     */
    public static Instant activeDaysAchievedAt(
            List<CodingSession> sessions, ZoneOffset zone, int targetDays) {
        int seen = 0;
        for (Map.Entry<LocalDate, OffsetDateTime> entry :
                firstActiveMomentByDay(sessions, zone).entrySet()) {
            if (++seen >= targetDays) {
                return entry.getValue().toInstant();
            }
        }
        return null;
    }

    /**
     * Returns the earliest instant at which a calendar month's coverage reached a target
     * percentage.
     *
     * <p>Coverage is measured the way {@link #bestPerfectMonthPercent} reports it — active days
     * over the month's own length — so the qualifying day count is the ceiling of {@code
     * targetPercent} of that month's length, which is the day count at which the truncated
     * percentage first reaches the target.
     *
     * @param sessions live sessions
     * @param zone aggregation timezone
     * @param targetPercent required coverage percentage (0-100)
     * @return the attaining instant, or {@code null} when no month reaches the target
     */
    public static Instant perfectMonthPercentAchievedAt(
            List<CodingSession> sessions, ZoneOffset zone, long targetPercent) {
        Map<YearMonth, Integer> activeDaysByMonth = new HashMap<>();
        for (Map.Entry<LocalDate, OffsetDateTime> entry :
                firstActiveMomentByDay(sessions, zone).entrySet()) {
            YearMonth month = YearMonth.from(entry.getKey());
            int activeDays = activeDaysByMonth.merge(month, 1, Integer::sum);
            long required = (targetPercent * month.lengthOfMonth() + 99) / 100;
            if (activeDays >= required) {
                return entry.getValue().toInstant();
            }
        }
        return null;
    }

    /**
     * Maps each coding day to the moment it became active, in date order.
     *
     * <p>A day becomes active when its first coding starts; when a session crosses midnight the day
     * is active from its own start. Merging does not change which days are touched, so the merged
     * intervals are enough to establish the first moment of every day.
     *
     * @param sessions live sessions
     * @param zone aggregation timezone
     * @return active day to first coding moment, ascending by date
     */
    private static Map<LocalDate, OffsetDateTime> firstActiveMomentByDay(
            List<CodingSession> sessions, ZoneOffset zone) {
        Map<LocalDate, OffsetDateTime> firstActiveAt = new TreeMap<>();
        for (TimeInterval interval : mergeOverlapping(toIntervals(sessions, zone))) {
            OffsetDateTime cursor = interval.start();
            while (cursor.isBefore(interval.end())) {
                LocalDate day = cursor.toLocalDate();
                firstActiveAt.putIfAbsent(day, cursor);
                cursor = day.plusDays(1).atStartOfDay().atOffset(cursor.getOffset());
            }
        }
        return firstActiveAt;
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
