package com.ahogek.cttserver.stats.service;

import com.ahogek.cttserver.stats.dto.DailyStatPoint;
import com.ahogek.cttserver.stats.dto.DistributionEntryDto;
import com.ahogek.cttserver.stats.dto.DistributionResponse;
import com.ahogek.cttserver.stats.dto.HeatmapResponse;
import com.ahogek.cttserver.stats.dto.HourlyDistributionResponse;
import com.ahogek.cttserver.stats.dto.HourlyStatPoint;
import com.ahogek.cttserver.stats.dto.RecentSessionResponse;
import com.ahogek.cttserver.stats.dto.StatsSummaryResponse;
import com.ahogek.cttserver.stats.dto.StreakStatsResponse;
import com.ahogek.cttserver.stats.enums.DistributionType;
import com.ahogek.cttserver.stats.enums.TimeOfDay;
import com.ahogek.cttserver.stats.materialization.entity.DailyStats;
import com.ahogek.cttserver.stats.materialization.repository.DailyStatsRepository;
import com.ahogek.cttserver.stats.materialization.service.DailyStatsMaterializer;
import com.ahogek.cttserver.sync.entity.CodingSession;
import com.ahogek.cttserver.sync.repository.CodingSessionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Statistics aggregation service.
 *
 * <p>Loads the user's live coding sessions and delegates to the pure {@link StatsCalculator} for
 * every dashboard dimension. All aggregations are timezone-aware via the caller-provided {@code
 * ZoneOffset}.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-30
 */
@Service
public class StatsService {

    private final CodingSessionRepository codingSessionRepository;
    private final DailyStatsRepository dailyStatsRepository;
    private final DailyStatsMaterializer dailyStatsMaterializer;

    public StatsService(
            CodingSessionRepository codingSessionRepository,
            DailyStatsRepository dailyStatsRepository,
            DailyStatsMaterializer dailyStatsMaterializer) {
        this.codingSessionRepository = codingSessionRepository;
        this.dailyStatsRepository = dailyStatsRepository;
        this.dailyStatsMaterializer = dailyStatsMaterializer;
    }

    /**
     * Computes the period summary for the user.
     *
     * @param userId the owning user
     * @param zone aggregation timezone
     * @return the summary in seconds
     */
    @Transactional(readOnly = true)
    public StatsSummaryResponse summary(UUID userId, ZoneOffset zone) {
        if (!ZoneOffset.UTC.equals(zone) || !dailyStatsMaterializer.bootstrapIfNeeded(userId)) {
            return liveSummary(userId, zone);
        }
        // UTC path reads the materialized per-day totals (week/month boundaries are whole-day
        // sums, so day-granular materialization is exact). The lower bound covers every
        // materialized day; the upper bound is open so future-dated sessions are not dropped.
        List<DailyStats> days = dailyStatsRepository.findByUserIdOrderByUtcDateAsc(userId);
        long total = days.stream().mapToLong(DailyStats::getMergedSeconds).sum();
        long todaySeconds =
                days.stream()
                        .filter(day -> day.getUtcDate().equals(LocalDate.now(ZoneOffset.UTC)))
                        .mapToLong(DailyStats::getMergedSeconds)
                        .findFirst()
                        .orElse(0);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        long week =
                days.stream()
                        .filter(day -> !day.getUtcDate().isBefore(today.with(DayOfWeek.MONDAY)))
                        .mapToLong(DailyStats::getMergedSeconds)
                        .sum();
        long month =
                days.stream()
                        .filter(day -> !day.getUtcDate().isBefore(today.withDayOfMonth(1)))
                        .mapToLong(DailyStats::getMergedSeconds)
                        .sum();
        long year =
                days.stream()
                        .filter(day -> !day.getUtcDate().isBefore(today.withDayOfYear(1)))
                        .mapToLong(DailyStats::getMergedSeconds)
                        .sum();
        LocalDate firstDate =
                days.stream().map(DailyStats::getUtcDate).min(LocalDate::compareTo).orElse(today);
        long daysSinceFirst = ChronoUnit.DAYS.between(firstDate, today) + 1;
        long dailyAverage = total / Math.max(daysSinceFirst, 1);
        return new StatsSummaryResponse(todaySeconds, dailyAverage, week, month, year, total);
    }

    private StatsSummaryResponse liveSummary(UUID userId, ZoneOffset zone) {
        StatsCalculator.Summary summary =
                StatsCalculator.summary(sessionsOf(userId), zone, LocalDate.now(zone));
        return new StatsSummaryResponse(
                summary.today(),
                summary.dailyAverage(),
                summary.thisWeek(),
                summary.thisMonth(),
                summary.thisYear(),
                summary.total());
    }

    /**
     * Computes the daily heatmap over the inclusive date range.
     *
     * @param userId the owning user
     * @param zone aggregation timezone
     * @param start first date (inclusive)
     * @param end last date (inclusive)
     * @return daily points in date order
     */
    @Transactional(readOnly = true)
    public HeatmapResponse heatmap(UUID userId, ZoneOffset zone, LocalDate start, LocalDate end) {
        if (ZoneOffset.UTC.equals(zone) && dailyStatsMaterializer.bootstrapIfNeeded(userId)) {
            // UTC path: per-day totals, dense over the range (zero days included), matching the
            // live aggregation contract.
            Map<LocalDate, Long> secondsByDay =
                    dailyStatsRepository
                            .findByUserIdAndUtcDateBetweenOrderByUtcDateAsc(userId, start, end)
                            .stream()
                            .collect(
                                    Collectors.toMap(
                                            DailyStats::getUtcDate, DailyStats::getMergedSeconds));
            List<DailyStatPoint> points = new ArrayList<>();
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                points.add(new DailyStatPoint(date, secondsByDay.getOrDefault(date, 0L)));
            }
            return new HeatmapResponse(points);
        }
        List<DailyStatPoint> points =
                StatsCalculator.heatmap(sessionsOf(userId), zone, start, end).stream()
                        .map(point -> new DailyStatPoint(point.date(), point.seconds()))
                        .toList();
        return new HeatmapResponse(points);
    }

    /**
     * Computes current and longest consecutive coding streaks.
     *
     * @param userId the owning user
     * @param zone aggregation timezone
     * @return streak lengths
     */
    @Transactional(readOnly = true)
    public StreakStatsResponse streaks(UUID userId, ZoneOffset zone) {
        if (!ZoneOffset.UTC.equals(zone) || !dailyStatsMaterializer.bootstrapIfNeeded(userId)) {
            StatsCalculator.Streaks streaks =
                    StatsCalculator.streaks(sessionsOf(userId), zone, LocalDate.now(zone));
            return new StreakStatsResponse(streaks.current(), streaks.max());
        }
        // UTC path: streaks derive from the set of days with coding, which the materialized
        // rows (mergedSeconds > 0) encode exactly.
        List<DailyStats> days =
                dailyStatsRepository.findByUserIdOrderByUtcDateAsc(userId).stream()
                        .filter(day -> day.getMergedSeconds() > 0)
                        .toList();
        long current = currentStreak(days);
        long max = maxStreak(days);
        return new StreakStatsResponse((int) current, (int) max);
    }

    private static long currentStreak(List<DailyStats> activeDays) {
        if (activeDays.isEmpty()) {
            return 0;
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate latest = activeDays.getLast().getUtcDate();
        LocalDate cursor;
        // today without coding yet keeps yesterday's streak alive
        if (latest.equals(today)) {
            cursor = today;
        } else if (latest.equals(today.minusDays(1))) {
            cursor = today.minusDays(1);
        } else {
            return 0;
        }
        long run = 0;
        for (int i = activeDays.size() - 1; i >= 0; i--) {
            LocalDate day = activeDays.get(i).getUtcDate();
            if (day.equals(cursor)) {
                run++;
                cursor = cursor.minusDays(1);
            } else if (day.isBefore(cursor)) {
                break;
            }
        }
        return run;
    }

    private static long maxStreak(List<DailyStats> activeDays) {
        long max = 0;
        long run = 0;
        LocalDate previous = null;
        for (DailyStats row : activeDays) {
            LocalDate day = row.getUtcDate();
            run = previous != null && day.equals(previous.plusDays(1)) ? run + 1 : 1;
            max = Math.max(max, run);
            previous = day;
        }
        return max;
    }

    /**
     * Computes a duration distribution by the requested dimension.
     *
     * @param userId the owning user
     * @param zone aggregation timezone
     * @param type the distribution dimension
     * @return buckets ordered by duration descending
     */
    @Transactional(readOnly = true)
    public DistributionResponse distribution(UUID userId, ZoneOffset zone, DistributionType type) {
        List<CodingSession> sessions = sessionsOf(userId);
        List<StatsCalculator.DistributionEntry> entries =
                switch (type) {
                    case LANGUAGES ->
                            StatsCalculator.accumulateBy(
                                    sessions, zone, CodingSession::getLanguage);
                    case PROJECTS ->
                            StatsCalculator.accumulateBy(
                                    sessions, zone, CodingSession::getProjectName);
                    case TIME_OF_DAY ->
                            StatsCalculator.accumulateBy(
                                    sessions,
                                    zone,
                                    session ->
                                            TimeOfDay.fromHour(
                                                            session.getStartTime()
                                                                    .atOffset(zone)
                                                                    .getHour())
                                                    .name());
                    case WEEKDAY -> StatsCalculator.weekdayDistribution(sessions, zone);
                };
        List<DistributionEntryDto> dtoEntries =
                entries.stream()
                        .map(entry -> new DistributionEntryDto(entry.name(), entry.seconds()))
                        .toList();
        return new DistributionResponse(type, dtoEntries);
    }

    /**
     * Computes per-hour average usage across active days.
     *
     * @param userId the owning user
     * @param zone aggregation timezone
     * @return per-hour averages and the active-day denominator
     */
    @Transactional(readOnly = true)
    public HourlyDistributionResponse hourly(UUID userId, ZoneOffset zone) {
        List<StatsCalculator.HourlyPoint> hourly =
                StatsCalculator.hourlyDistribution(sessionsOf(userId), zone);
        List<HourlyStatPoint> points =
                hourly.stream()
                        .map(point -> new HourlyStatPoint(point.hour(), point.averageSeconds()))
                        .toList();
        int activeDays = hourly.isEmpty() ? 0 : hourly.getFirst().activeDays();
        return new HourlyDistributionResponse(points, activeDays);
    }

    /**
     * Lists the most recent sessions, ordered by start time descending.
     *
     * @param userId the owning user
     * @param limit maximum number of sessions
     * @return recent sessions
     */
    @Transactional(readOnly = true)
    public List<RecentSessionResponse> recent(UUID userId, int limit) {
        return sessionsOf(userId).stream()
                .sorted(Comparator.comparing(CodingSession::getStartTime).reversed())
                .limit(limit)
                .map(this::toRecentSession)
                .toList();
    }

    private List<CodingSession> sessionsOf(UUID userId) {
        return codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId);
    }

    private RecentSessionResponse toRecentSession(CodingSession session) {
        return new RecentSessionResponse(
                session.getId(),
                session.getSessionUuid(),
                session.getProjectName(),
                session.getLanguage(),
                session.getStartTime(),
                session.getEndTime(),
                Duration.between(session.getStartTime(), session.getEndTime()).toSeconds());
    }
}
