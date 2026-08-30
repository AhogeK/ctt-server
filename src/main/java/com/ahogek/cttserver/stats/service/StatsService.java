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
import com.ahogek.cttserver.sync.entity.CodingSession;
import com.ahogek.cttserver.sync.repository.CodingSessionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

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

    public StatsService(CodingSessionRepository codingSessionRepository) {
        this.codingSessionRepository = codingSessionRepository;
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
        StatsCalculator.Streaks streaks =
                StatsCalculator.streaks(sessionsOf(userId), zone, LocalDate.now(zone));
        return new StreakStatsResponse(streaks.current(), streaks.max());
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
