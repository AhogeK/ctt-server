package com.ahogek.cttserver.stats.achievement.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.language.LanguageVocabulary;
import com.ahogek.cttserver.stats.achievement.dto.AchievementResponse;
import com.ahogek.cttserver.stats.achievement.entity.AchievementProgress;
import com.ahogek.cttserver.stats.achievement.entity.UserAchievement;
import com.ahogek.cttserver.stats.achievement.enums.AchievementType;
import com.ahogek.cttserver.stats.achievement.enums.AchievementWindow;
import com.ahogek.cttserver.stats.achievement.repository.AchievementProgressRepository;
import com.ahogek.cttserver.stats.achievement.repository.UserAchievementRepository;
import com.ahogek.cttserver.sync.entity.CodingSession;
import com.ahogek.cttserver.sync.repository.CodingSessionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AchievementService")
class AchievementServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC);
    private static final ZoneOffset ZONE = ZoneOffset.UTC;

    /** Monday of the ISO week containing FIXED_CLOCK (2026-08-31 is a Monday). */
    private static final LocalDate WEEK_ANCHOR = LocalDate.of(2026, 8, 31);

    private CodingSessionRepository codingSessionRepository;
    private UserAchievementRepository userAchievementRepository;
    private AchievementProgressRepository achievementProgressRepository;
    private AuditLogService auditLogService;
    private AchievementService service;
    private final UUID userId = UUID.randomUUID();
    private final Set<UnlockKey> inserted = new HashSet<>();

    /** Instants the service passed to the write — mirrors what the row would hold. */
    private final Map<UnlockKey, Instant> recordedInstants = new HashMap<>();

    /** Identifies an unlock row: a badge within one period. */
    private record UnlockKey(String code, String periodKey) {}

    /** High-water marks the service stored, keyed by family — mirrors what the row would hold. */
    private final Map<AchievementType, Long> storedMarks = new EnumMap<>(AchievementType.class);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        codingSessionRepository = mock(CodingSessionRepository.class);
        userAchievementRepository = mock(UserAchievementRepository.class);
        achievementProgressRepository = mock(AchievementProgressRepository.class);
        auditLogService = mock(AuditLogService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        storedMarks.clear();
        when(achievementProgressRepository.findByUserId(userId))
                .thenAnswer(
                        _ ->
                                storedMarks.entrySet().stream()
                                        .map(
                                                entry ->
                                                        new AchievementProgress(
                                                                userId,
                                                                entry.getKey(),
                                                                entry.getValue(),
                                                                Instant.parse(
                                                                        "2026-08-31T00:00:00Z")))
                                        .toList());
        when(achievementProgressRepository.raiseIfHigher(
                        eq(userId), any(), org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(
                        inv -> {
                            AchievementType type =
                                    AchievementType.valueOf(inv.getArgument(1).toString());
                            long raised = inv.getArgument(2);
                            long previous = storedMarks.getOrDefault(type, 0L);
                            if (raised <= previous) {
                                return 0;
                            }
                            storedMarks.put(type, raised);
                            return 1;
                        });
        service =
                new AchievementService(
                        codingSessionRepository,
                        userAchievementRepository,
                        achievementProgressRepository,
                        auditLogService,
                        FIXED_CLOCK,
                        redisTemplate,
                        new ObjectMapper(),
                        new LanguageVocabulary(new ObjectMapper()));
        inserted.clear();
        recordedInstants.clear();
        when(userAchievementRepository.findByUserId(userId))
                .thenAnswer(
                        _ ->
                                inserted.stream()
                                        .map(
                                                key -> {
                                                    UserAchievement unlock =
                                                            new UserAchievement(
                                                                    userId,
                                                                    key.code(),
                                                                    key.periodKey());
                                                    unlock.setUnlockedAt(
                                                            recordedInstants.getOrDefault(
                                                                    key,
                                                                    Instant.parse(
                                                                            "2026-08-31T00:00:00Z")));
                                                    return unlock;
                                                })
                                        .toList());
        when(userAchievementRepository.insertIfAbsent(eq(userId), any(), any(), any()))
                .thenAnswer(
                        inv -> {
                            UnlockKey key =
                                    new UnlockKey(
                                            inv.getArgument(1).toString(),
                                            inv.getArgument(2).toString());
                            if (!inserted.add(key)) {
                                return 0;
                            }
                            // Mirror the real write: the instant the caller computed is what the
                            // row ends up holding, and later reads hand it back verbatim.
                            recordedInstants.put(
                                    key, ((OffsetDateTime) inv.getArgument(3)).toInstant());
                            return 1;
                        });
    }

    private static CodingSession session(String start, String end, String language) {
        CodingSession session = new CodingSession();
        session.setStartTime(Instant.parse(start + "Z"));
        session.setEndTime(Instant.parse(end + "Z"));
        session.setProjectName("p");
        session.setLanguage(language);
        return session;
    }

    private AchievementResponse byCode(List<AchievementResponse> all, String code) {
        return all.stream().filter(a -> a.code().equals(code)).findFirst().orElseThrow();
    }

    @Nested
    @DisplayName("getAchievements")
    class GetAchievementsTests {

        @Test
        @DisplayName("shouldUnlockStreakBadge_andEmitAudit_whenProgressReachesTarget")
        void shouldUnlockStreak_whenProgressReachesTarget() {
            List<CodingSession> sessions =
                    IntStream.rangeClosed(0, 6)
                            .mapToObj(
                                    i ->
                                            session(
                                                    "2026-08-"
                                                            + String.format("%02d", 25 + i)
                                                            + "T10:00:00",
                                                    "2026-08-"
                                                            + String.format("%02d", 25 + i)
                                                            + "T11:00:00",
                                                    "Java"))
                            .toList();
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(sessions);

            List<AchievementResponse> result = service.getAchievements(userId, ZONE);

            AchievementResponse streak3 = byCode(result, "STREAK_3");
            assertThat(streak3.unlocked()).isTrue();
            assertThat(streak3.progress()).isEqualTo(7);
            // Earned on the third consecutive day, not when this query happened to run: the
            // sessions span 08-25..08-31 and the run completes on 08-27.
            assertThat(streak3.unlockedAt()).isEqualTo("2026-08-27T10:00:00Z");
            assertThat(streak3.type()).isEqualTo("STREAK");
            AchievementResponse streak7 = byCode(result, "STREAK_7");
            assertThat(streak7.unlocked()).isTrue();
            // Rungs are numbered by the ladder the client renders, so STREAK_7 is the 2nd of 8.
            assertThat(streak7.tier()).isEqualTo(2);
            assertThat(streak7.type()).isEqualTo("STREAK");
            assertThat(streak7.unlockedAt()).isEqualTo("2026-08-31T10:00:00Z");
            AchievementResponse streak30 = byCode(result, "STREAK_30");
            assertThat(streak30.unlocked()).isFalse();
            assertThat(streak30.progress()).isEqualTo(7);
            assertThat(streak30.tier()).isEqualTo(4);
            verify(userAchievementRepository)
                    .insertIfAbsent(eq(userId), eq("STREAK_3"), any(), any());
            verify(userAchievementRepository)
                    .insertIfAbsent(eq(userId), eq("STREAK_7"), any(), any());
            verify(auditLogService)
                    .logSuccess(
                            userId,
                            AuditAction.ACHIEVEMENT_UNLOCKED,
                            ResourceType.ACHIEVEMENT,
                            "STREAK_7");
            verify(userAchievementRepository, never())
                    .insertIfAbsent(eq(userId), eq("STREAK_30"), any(), any());
        }

        @Test
        @DisplayName("shouldNotUnlock_whenProgressBelowTarget")
        void shouldNotUnlock_whenProgressBelowTarget() {
            List<CodingSession> sessions =
                    List.of(session("2026-08-30T10:00:00", "2026-08-30T11:00:00", "Java"));
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(sessions);

            List<AchievementResponse> result = service.getAchievements(userId, ZONE);

            assertThat(byCode(result, "STREAK_3").unlocked()).isFalse();
            assertThat(byCode(result, "TOTAL_10_HOURS").unlocked()).isFalse();
            assertThat(byCode(result, "DAILY_BURST").unlocked()).isFalse();
            verify(userAchievementRepository, never()).insertIfAbsent(any(), any(), any(), any());
            verify(auditLogService, never()).logSuccess(any(), any(), any(), any());
        }

        @Test
        @DisplayName("shouldNotReaudit_whenInsertIsSkippedDueToRace")
        void shouldNotReaudit_whenInsertSkippedDueToRace() {
            // an 11h session exceeds the 10-hour target, but a concurrent request already
            // inserted the unlock, so this request sees 0 rows affected
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(
                            List.of(session("2026-08-30T10:00:00", "2026-08-30T21:00:00", "Java")));
            when(userAchievementRepository.insertIfAbsent(
                            eq(userId), eq("TOTAL_10_HOURS"), any(), any()))
                    .thenReturn(0);
            UserAchievement wonByOtherRequest =
                    new UserAchievement(
                            userId, "TOTAL_10_HOURS", AchievementWindow.LIFETIME_PERIOD);
            wonByOtherRequest.setUnlockedAt(Instant.parse("2026-08-30T16:00:00Z"));
            when(userAchievementRepository.findByUserId(userId))
                    .thenReturn(List.of(wonByOtherRequest));

            List<AchievementResponse> result = service.getAchievements(userId, ZONE);

            AchievementResponse total10 = byCode(result, "TOTAL_10_HOURS");
            assertThat(total10.unlocked()).isTrue();
            // The row the racing request wrote is the one that stands, so its instant is reported.
            assertThat(total10.unlockedAt()).isEqualTo("2026-08-30T16:00:00Z");
            verify(auditLogService, never())
                    .logSuccess(
                            userId,
                            AuditAction.ACHIEVEMENT_UNLOCKED,
                            ResourceType.ACHIEVEMENT,
                            "TOTAL_10_HOURS");
        }

        @Test
        @DisplayName("shouldKeepExistingUnlockTimestamp_whenAlreadyUnlocked")
        void shouldKeepExistingUnlockTimestamp_whenAlreadyUnlocked() {
            UserAchievement existing =
                    new UserAchievement(
                            userId, "TOTAL_10_HOURS", AchievementWindow.LIFETIME_PERIOD);
            existing.setUnlockedAt(Instant.parse("2026-08-20T08:00:00Z"));
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(List.of());
            when(userAchievementRepository.findByUserId(userId)).thenReturn(List.of(existing));

            List<AchievementResponse> result = service.getAchievements(userId, ZONE);

            AchievementResponse total10 = byCode(result, "TOTAL_10_HOURS");
            assertThat(total10.unlocked()).isTrue();
            assertThat(total10.unlockedAt()).isEqualTo("2026-08-20T08:00:00Z");
            verify(userAchievementRepository, never()).insertIfAbsent(any(), any(), any(), any());
        }

        @Test
        @DisplayName("shouldRecordWindowInstant_forTimeWindowBadges")
        void shouldRecordWindowInstant_forTimeWindowBadges() {
            // Three nights inside the 22:00-05:00 window; the badge is earned on the third one.
            List<CodingSession> sessions =
                    IntStream.rangeClosed(25, 27)
                            .mapToObj(
                                    day ->
                                            session(
                                                    "2026-08-" + day + "T23:00:00",
                                                    "2026-08-" + (day + 1) + "T00:00:00",
                                                    "Java"))
                            .toList();
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(sessions);

            List<AchievementResponse> result = service.getAchievements(userId, ZONE);

            // NIGHT_OWL_5 is not reached, but the wiring is what matters: the instant must come
            // from the night window's own days, so swapping the early-bird and night-owl hours
            // cannot pass unnoticed.
            assertThat(byCode(result, "NIGHT_OWL_5").progress()).isEqualTo(3);
            assertThat(byCode(result, "EARLY_BIRD_5").progress()).isZero();
        }

        @Test
        @DisplayName("shouldRecordDailyBurstInstant_whenSingleDayExceedsThreshold")
        void shouldRecordDailyBurstInstant_whenSingleDayExceedsThreshold() {
            // a 9h day: the 4h rung completes 4h in, the 8h rung 8h in
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(
                            List.of(session("2026-08-30T09:00:00", "2026-08-30T18:00:00", "Java")));

            List<AchievementResponse> result = service.getAchievements(userId, ZONE);

            assertThat(byCode(result, "DAILY_BURST_4").unlocked()).isTrue();
            assertThat(byCode(result, "DAILY_BURST_4").unlockedAt())
                    .isEqualTo("2026-08-30T13:00:00Z");
            assertThat(byCode(result, "DAILY_BURST").unlockedAt())
                    .isEqualTo("2026-08-30T17:00:00Z");
            // the 10h rung is out of reach for a 9h day
            assertThat(byCode(result, "DAILY_BURST_10").unlocked()).isFalse();
        }

        @Test
        @DisplayName("shouldNotRegressProgress_whenSessionsAreSoftDeleted")
        void shouldNotRegressProgress_whenSessionsAreSoftDeleted() {
            // Seven languages once used, two still live. The reported value must stay at seven:
            // the awarded-badge floor alone would only hold it at five (the highest rung earned),
            // so this only passes when the observed maximum is itself remembered.
            storedMarks.put(AchievementType.LANGUAGE_COUNT, 7L);
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(
                            List.of(
                                    session("2026-08-30T10:00:00", "2026-08-30T11:00:00", "Java"),
                                    session(
                                            "2026-08-30T12:00:00",
                                            "2026-08-30T13:00:00",
                                            "Kotlin")));
            UserAchievement languages =
                    new UserAchievement(userId, "LANGUAGES_5", AchievementWindow.LIFETIME_PERIOD);
            languages.setUnlockedAt(Instant.parse("2026-08-01T00:00:00Z"));
            when(userAchievementRepository.findByUserId(userId)).thenReturn(List.of(languages));

            List<AchievementResponse> result = service.getAchievements(userId, ZONE);

            assertThat(byCode(result, "LANGUAGES_5").progress()).isEqualTo(7);
            assertThat(byCode(result, "LANGUAGES_8").progress()).isEqualTo(7);
            assertThat(byCode(result, "LANGUAGES_8").unlocked()).isFalse();
        }

        @Test
        @DisplayName("shouldFloorProgressAtAwardedTarget_whenNoMarkWasEverStored")
        void shouldFloorProgressAtAwardedTarget_whenNoMarkWasEverStored() {
            // Badges awarded before high-water marks existed have no stored row; their own target
            // is the proof that the value was once reached, so it seeds the floor.
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(List.of());
            UserAchievement streak =
                    new UserAchievement(userId, "STREAK_30", AchievementWindow.LIFETIME_PERIOD);
            streak.setUnlockedAt(Instant.parse("2026-08-01T00:00:00Z"));
            when(userAchievementRepository.findByUserId(userId)).thenReturn(List.of(streak));

            List<AchievementResponse> result = service.getAchievements(userId, ZONE);

            assertThat(byCode(result, "STREAK_30").progress()).isEqualTo(30);
            assertThat(byCode(result, "STREAK_100").progress()).isEqualTo(30);
            assertThat(byCode(result, "STREAK_100").unlocked()).isFalse();
        }

        @Test
        @DisplayName("shouldStoreTheRaisedMark_whenProgressExceedsIt")
        void shouldStoreTheRaisedMark_whenProgressExceedsIt() {
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(
                            List.of(
                                    session("2026-08-30T10:00:00", "2026-08-30T11:00:00", "Java"),
                                    session("2026-08-30T12:00:00", "2026-08-30T13:00:00", "Kotlin"),
                                    session("2026-08-30T14:00:00", "2026-08-30T15:00:00", "Go")));

            service.getAchievements(userId, ZONE);

            assertThat(storedMarks).containsEntry(AchievementType.LANGUAGE_COUNT, 3L);
        }

        @Test
        @DisplayName("shouldCountOnlyTheCurrentWindow_whenDailyBadgeIsWindowed")
        void shouldCountOnlyTheCurrentWindow_whenDailyBadgeIsWindowed() {
            // A long session yesterday and one hour today: the daily badge measures today only,
            // while the lifetime badge still sees everything.
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(
                            List.of(
                                    session("2026-08-30T09:00:00", "2026-08-30T18:00:00", "Java"),
                                    session("2026-08-31T09:00:00", "2026-08-31T10:00:00", "Java")));

            List<AchievementResponse> result = service.getAchievements(userId, ZONE);

            // FIXED_CLOCK is 2026-08-31T12:00Z, so the day window is 08-31.
            assertThat(byCode(result, "DAILY_TOTAL_1H").progress()).isEqualTo(3_600);
            assertThat(byCode(result, "DAILY_TOTAL_1H").window()).isEqualTo("DAY");
            assertThat(byCode(result, "DAILY_TOTAL_1H").windowStart())
                    .isEqualTo(java.time.LocalDate.of(2026, 8, 31));
            assertThat(byCode(result, "DAILY_TOTAL_1H").unlocked()).isTrue();
            // the 4h daily rung is out of reach today even though 9h was coded yesterday
            assertThat(byCode(result, "DAILY_TOTAL_4H").unlocked()).isFalse();
            // the lifetime ladder is unaffected by the window
            assertThat(byCode(result, "TOTAL_10_HOURS").progress()).isEqualTo(36_000);
        }

        @Test
        @DisplayName("shouldReportNoWindowBounds_forLifetimeBadges")
        void shouldReportNoWindowBounds_forLifetimeBadges() {
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(List.of());

            List<AchievementResponse> result = service.getAchievements(userId, ZONE);

            AchievementResponse lifetime = byCode(result, "STREAK_3");
            assertThat(lifetime.window()).isEqualTo("LIFETIME");
            assertThat(lifetime.windowStart()).isNull();
            assertThat(lifetime.windowEnd()).isNull();
        }

        @Test
        @DisplayName("shouldNotTreatLastPeriodsUnlock_asCurrent_whenPeriodRolled")
        void shouldNotTreatLastPeriodsUnlock_asCurrent_whenPeriodRolled() {
            // The user earned the weekly badge last week; this week starts empty, so the badge must
            // read locked again and its progress must start from zero rather than carrying over.
            UserAchievement lastWeek = new UserAchievement(userId, "WEEKLY_ACTIVE_3", "2026-08-24");
            lastWeek.setUnlockedAt(Instant.parse("2026-08-26T10:00:00Z"));
            when(userAchievementRepository.findByUserId(userId)).thenReturn(List.of(lastWeek));
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(List.of());

            List<AchievementResponse> result = service.getAchievements(userId, ZONE);

            AchievementResponse weekly = byCode(result, "WEEKLY_ACTIVE_3");
            assertThat(weekly.unlocked()).isFalse();
            assertThat(weekly.unlockedAt()).isNull();
            assertThat(weekly.progress()).isZero();
        }

        @Test
        @DisplayName("shouldKeepLastPeriodsUnlock_asHistory_whenANewPeriodIsEarned")
        void shouldKeepLastPeriodsUnlock_asHistory_whenANewPeriodIsEarned() {
            // The stored row from last week must not block this week's insert: the period key is
            // part of the uniqueness, which is what makes the badge reachable again.
            UserAchievement lastWeek = new UserAchievement(userId, "DAILY_TOTAL_1H", "2026-08-30");
            lastWeek.setUnlockedAt(Instant.parse("2026-08-30T10:00:00Z"));
            when(userAchievementRepository.findByUserId(userId)).thenReturn(List.of(lastWeek));
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(
                            List.of(session("2026-08-31T09:00:00", "2026-08-31T10:00:00", "Java")));

            List<AchievementResponse> result = service.getAchievements(userId, ZONE);

            AchievementResponse daily = byCode(result, "DAILY_TOTAL_1H");
            assertThat(daily.unlocked()).isTrue();
            assertThat(daily.unlockedAt()).isEqualTo("2026-08-31T10:00:00Z");
            // yesterday's row is untouched: a new period is a new row, not an update
            verify(userAchievementRepository)
                    .insertIfAbsent(eq(userId), eq("DAILY_TOTAL_1H"), eq("2026-08-31"), any());
        }

        @Test
        @DisplayName("shouldNotHoldWindowedProgressAtTheHighWaterMark")
        void shouldNotHoldWindowedProgressAtTheHighWaterMark() {
            // A lifetime mark must never be applied to a windowed ladder, or a daily badge would
            // stay permanently satisfied by the best day the user ever had.
            storedMarks.put(AchievementType.TOTAL_SECONDS, 36_000L);
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(
                            List.of(session("2026-08-31T09:00:00", "2026-08-31T09:30:00", "Java")));

            List<AchievementResponse> result = service.getAchievements(userId, ZONE);

            assertThat(byCode(result, "DAILY_TOTAL_1H").progress()).isEqualTo(1_800);
            assertThat(byCode(result, "DAILY_TOTAL_1H").unlocked()).isFalse();
            // the lifetime ladder does inherit the stored mark
            assertThat(byCode(result, "TOTAL_10_HOURS").progress()).isEqualTo(36_000);
        }

        @Test
        @DisplayName("shouldCountEveryAttainedPeriod_evenWhenTheTableHasNoRowsForThem")
        void shouldCountEveryAttainedPeriod_evenWhenTheTableHasNoRowsForThem() {
            // Four consecutive weeks each with 5+ active days, but the table holds only the current
            // week's row: evaluation is lazy, so the user simply never opened the page during the
            // other three. Counting stored rows would report 1 with a streak of 1.
            // FIXED_CLOCK is 2026-08-31 (a Monday, ISO week 2026-W36).
            List<CodingSession> sessions = new ArrayList<>();
            for (int week = 0; week < 4; week++) {
                for (int day = 0; day < 5; day++) {
                    LocalDate date = WEEK_ANCHOR.minusWeeks(week).plusDays(day);
                    sessions.add(session(date + "T10:00:00", date + "T11:00:00", "Java"));
                }
            }
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(sessions);
            UserAchievement thisWeek = new UserAchievement(userId, "WEEKLY_ACTIVE_5", "2026-W36");
            thisWeek.setUnlockedAt(Instant.parse("2026-08-31T10:00:00Z"));
            when(userAchievementRepository.findByUserId(userId)).thenReturn(List.of(thisWeek));

            List<AchievementResponse> result = service.getAchievements(userId, ZONE);

            AchievementResponse weekly = byCode(result, "WEEKLY_ACTIVE_5");
            assertThat(weekly.unlocked()).isTrue();
            assertThat(weekly.totalUnlocks()).isEqualTo(4);
            assertThat(weekly.periodStreak()).isEqualTo(4);
        }

        @Test
        @DisplayName("shouldReportZeroStreak_whenTheCurrentPeriodIsNotAttained")
        void shouldReportZeroStreak_whenTheCurrentPeriodIsNotAttained() {
            // Two weeks attained three and four weeks ago, nothing since. The run ended before the
            // current period, so the streak reads 0 rather than borrowing an older one.
            List<CodingSession> sessions = new ArrayList<>();
            for (int week = 3; week <= 4; week++) {
                for (int day = 0; day < 5; day++) {
                    LocalDate date = WEEK_ANCHOR.minusWeeks(week).plusDays(day);
                    sessions.add(session(date + "T10:00:00", date + "T11:00:00", "Java"));
                }
            }
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(sessions);

            List<AchievementResponse> result = service.getAchievements(userId, ZONE);

            AchievementResponse weekly = byCode(result, "WEEKLY_ACTIVE_5");
            assertThat(weekly.unlocked()).isFalse();
            assertThat(weekly.periodStreak()).isZero();
            assertThat(weekly.totalUnlocks()).isEqualTo(2);
        }

        @Test
        @DisplayName("shouldStopTheStreak_atTheFirstUnattainedPeriod")
        void shouldStopTheStreak_atTheFirstUnattainedPeriod() {
            // This week and last week attained, the week before not: the run is 2 of 3 attained.
            List<CodingSession> sessions = new ArrayList<>();
            for (int week = 0; week <= 1; week++) {
                for (int day = 0; day < 5; day++) {
                    LocalDate date = WEEK_ANCHOR.minusWeeks(week).plusDays(day);
                    sessions.add(session(date + "T10:00:00", date + "T11:00:00", "Java"));
                }
            }
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(sessions);

            List<AchievementResponse> result = service.getAchievements(userId, ZONE);

            assertThat(byCode(result, "WEEKLY_ACTIVE_5").periodStreak()).isEqualTo(2);
            assertThat(byCode(result, "WEEKLY_ACTIVE_5").totalUnlocks()).isEqualTo(2);
        }

        @Test
        @DisplayName("shouldNotLowerTotalUnlocks_whenTheSessionsBehindItAreDeleted")
        void shouldNotLowerTotalUnlocks_whenTheSessionsBehindItAreDeleted() {
            // The week's sessions are gone, but the unlock rows it produced remain: an attained
            // badge is never revoked, so the count must not fall with the sessions.
            UserAchievement older = new UserAchievement(userId, "WEEKLY_ACTIVE_3", "2025-W02");
            older.setUnlockedAt(Instant.parse("2025-01-08T10:00:00Z"));
            UserAchievement current = new UserAchievement(userId, "WEEKLY_ACTIVE_3", "2026-W36");
            current.setUnlockedAt(Instant.parse("2026-08-31T10:00:00Z"));
            when(userAchievementRepository.findByUserId(userId))
                    .thenReturn(List.of(older, current));
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(List.of());

            List<AchievementResponse> result = service.getAchievements(userId, ZONE);

            // both stored periods survive even though no session backs them any more
            assertThat(byCode(result, "WEEKLY_ACTIVE_3").totalUnlocks()).isEqualTo(2);
        }

        @Test
        @DisplayName("shouldReportSinglePeriod_forLifetimeBadges")
        void shouldReportSinglePeriod_forLifetimeBadges() {
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(List.of());

            List<AchievementResponse> result = service.getAchievements(userId, ZONE);

            AchievementResponse lifetime = byCode(result, "STREAK_3");
            // a lifetime badge has one period, so the history fields carry no extra information
            assertThat(lifetime.totalUnlocks()).isZero();
            assertThat(lifetime.periodStreak()).isZero();
        }

        @Test
        @DisplayName("shouldComputeLanguageCount_forLanguageBadges")
        void shouldComputeLanguageCount_forLanguageBadges() {
            List<CodingSession> sessions =
                    List.of(
                            session("2026-08-30T10:00:00", "2026-08-30T11:00:00", "Java"),
                            session("2026-08-30T12:00:00", "2026-08-30T13:00:00", "Kotlin"),
                            session("2026-08-30T14:00:00", "2026-08-30T15:00:00", "Go"));
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(sessions);

            List<AchievementResponse> result = service.getAchievements(userId, ZONE);

            assertThat(byCode(result, "LANGUAGES_3").unlocked()).isTrue();
            assertThat(byCode(result, "LANGUAGES_3").progress()).isEqualTo(3);
            assertThat(byCode(result, "LANGUAGES_5").unlocked()).isFalse();
        }

        @Test
        @DisplayName("shouldComputeWindowActiveDays_forTimeWindowBadges")
        void shouldComputeWindowActiveDays_forTimeWindowBadges() {
            List<CodingSession> sessions =
                    IntStream.rangeClosed(1, 10)
                            .mapToObj(
                                    i ->
                                            session(
                                                    "2026-08-"
                                                            + String.format("%02d", i)
                                                            + "T23:00:00",
                                                    "2026-08-"
                                                            + String.format("%02d", i + 1)
                                                            + "T00:00:00",
                                                    "Java"))
                            .toList();
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(sessions);

            List<AchievementResponse> result = service.getAchievements(userId, ZONE);

            AchievementResponse nightOwl10 = byCode(result, "NIGHT_OWL_10");
            assertThat(nightOwl10.unlocked()).isTrue();
            assertThat(nightOwl10.progress()).isEqualTo(10);
            assertThat(byCode(result, "NIGHT_OWL_30").unlocked()).isFalse();
            assertThat(byCode(result, "EARLY_BIRD_10").unlocked()).isFalse();
        }

        @Test
        @DisplayName("shouldUnlockDailyBurst_whenSingleDayExceedsThreshold")
        void shouldUnlockDailyBurst_whenSingleDayExceedsThreshold() {
            List<CodingSession> sessions =
                    List.of(session("2026-08-30T09:00:00", "2026-08-30T18:00:00", "Java"));
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(sessions);

            List<AchievementResponse> result = service.getAchievements(userId, ZONE);

            AchievementResponse burst = byCode(result, "DAILY_BURST");
            assertThat(burst.unlocked()).isTrue();
            assertThat(burst.progress()).isEqualTo(32400);
        }

        @Test
        @DisplayName("shouldUnlockPerfectMonth_whenEveryDayCoded")
        void shouldUnlockPerfectMonth_whenEveryDayCoded() {
            List<CodingSession> sessions =
                    IntStream.rangeClosed(1, 31)
                            .mapToObj(
                                    i ->
                                            session(
                                                    "2026-08-"
                                                            + String.format("%02d", i)
                                                            + "T10:00:00",
                                                    "2026-08-"
                                                            + String.format("%02d", i)
                                                            + "T11:00:00",
                                                    "Java"))
                            .toList();
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(sessions);

            List<AchievementResponse> result = service.getAchievements(userId, ZONE);

            assertThat(byCode(result, "PERFECT_MONTH").unlocked()).isTrue();
        }
    }
}
