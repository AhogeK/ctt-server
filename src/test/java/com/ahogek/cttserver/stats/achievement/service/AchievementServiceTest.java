package com.ahogek.cttserver.stats.achievement.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.stats.achievement.dto.AchievementResponse;
import com.ahogek.cttserver.stats.achievement.entity.AchievementProgress;
import com.ahogek.cttserver.stats.achievement.entity.UserAchievement;
import com.ahogek.cttserver.stats.achievement.enums.AchievementType;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

    private CodingSessionRepository codingSessionRepository;
    private UserAchievementRepository userAchievementRepository;
    private AchievementProgressRepository achievementProgressRepository;
    private AuditLogService auditLogService;
    private AchievementService service;
    private final UUID userId = UUID.randomUUID();
    private final Set<String> inserted = new HashSet<>();

    /**
     * Instants the service passed to the write, keyed by code — mirrors what the row would hold.
     */
    private final Map<String, Instant> recordedInstants = new HashMap<>();

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
                        new ObjectMapper());
        inserted.clear();
        recordedInstants.clear();
        when(userAchievementRepository.findByUserId(userId))
                .thenAnswer(
                        _ ->
                                inserted.stream()
                                        .map(
                                                code -> {
                                                    UserAchievement unlock =
                                                            new UserAchievement(userId, code);
                                                    unlock.setUnlockedAt(
                                                            recordedInstants.getOrDefault(
                                                                    code,
                                                                    Instant.parse(
                                                                            "2026-08-31T00:00:00Z")));
                                                    return unlock;
                                                })
                                        .toList());
        when(userAchievementRepository.insertIfAbsent(eq(userId), any(), any()))
                .thenAnswer(
                        inv -> {
                            String code = inv.getArgument(1);
                            if (!inserted.add(code)) {
                                return 0;
                            }
                            // Mirror the real write: the instant the caller computed is what the
                            // row ends up holding, and later reads hand it back verbatim.
                            recordedInstants.put(
                                    code, ((OffsetDateTime) inv.getArgument(2)).toInstant());
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
            verify(userAchievementRepository).insertIfAbsent(eq(userId), eq("STREAK_3"), any());
            verify(userAchievementRepository).insertIfAbsent(eq(userId), eq("STREAK_7"), any());
            verify(auditLogService)
                    .logSuccess(
                            userId,
                            AuditAction.ACHIEVEMENT_UNLOCKED,
                            ResourceType.ACHIEVEMENT,
                            "STREAK_7");
            verify(userAchievementRepository, never())
                    .insertIfAbsent(eq(userId), eq("STREAK_30"), any());
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
            verify(userAchievementRepository, never()).insertIfAbsent(any(), any(), any());
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
            when(userAchievementRepository.insertIfAbsent(eq(userId), eq("TOTAL_10_HOURS"), any()))
                    .thenReturn(0);
            UserAchievement wonByOtherRequest = new UserAchievement(userId, "TOTAL_10_HOURS");
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
            UserAchievement existing = new UserAchievement(userId, "TOTAL_10_HOURS");
            existing.setUnlockedAt(Instant.parse("2026-08-20T08:00:00Z"));
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(List.of());
            when(userAchievementRepository.findByUserId(userId)).thenReturn(List.of(existing));

            List<AchievementResponse> result = service.getAchievements(userId, ZONE);

            AchievementResponse total10 = byCode(result, "TOTAL_10_HOURS");
            assertThat(total10.unlocked()).isTrue();
            assertThat(total10.unlockedAt()).isEqualTo("2026-08-20T08:00:00Z");
            verify(userAchievementRepository, never()).insertIfAbsent(any(), any(), any());
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
            UserAchievement languages = new UserAchievement(userId, "LANGUAGES_5");
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
            UserAchievement streak = new UserAchievement(userId, "STREAK_30");
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
