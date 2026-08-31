package com.ahogek.cttserver.stats.achievement.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.stats.achievement.dto.AchievementResponse;
import com.ahogek.cttserver.stats.achievement.entity.UserAchievement;
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
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
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
    private AuditLogService auditLogService;
    private AchievementService service;
    private final UUID userId = UUID.randomUUID();
    private final Set<String> inserted = new HashSet<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        codingSessionRepository = mock(CodingSessionRepository.class);
        userAchievementRepository = mock(UserAchievementRepository.class);
        auditLogService = mock(AuditLogService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service =
                new AchievementService(
                        codingSessionRepository,
                        userAchievementRepository,
                        auditLogService,
                        FIXED_CLOCK,
                        redisTemplate,
                        new ObjectMapper());
        inserted.clear();
        when(userAchievementRepository.findByUserId(userId))
                .thenAnswer(
                        _ ->
                                inserted.stream()
                                        .map(
                                                code -> {
                                                    UserAchievement unlock =
                                                            new UserAchievement(userId, code);
                                                    unlock.setUnlockedAt(
                                                            Instant.parse("2026-08-31T00:00:00Z"));
                                                    return unlock;
                                                })
                                        .toList());
        when(userAchievementRepository.insertIfAbsent(eq(userId), any()))
                .thenAnswer(inv -> inserted.add(inv.getArgument(1)) ? 1 : 0);
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
            assertThat(streak3.unlockedAt()).isEqualTo("2026-08-31T00:00:00Z");
            AchievementResponse streak7 = byCode(result, "STREAK_7");
            assertThat(streak7.unlocked()).isTrue();
            AchievementResponse streak30 = byCode(result, "STREAK_30");
            assertThat(streak30.unlocked()).isFalse();
            assertThat(streak30.progress()).isEqualTo(7);
            verify(userAchievementRepository).insertIfAbsent(userId, "STREAK_3");
            verify(userAchievementRepository).insertIfAbsent(userId, "STREAK_7");
            verify(auditLogService)
                    .logSuccess(
                            userId,
                            AuditAction.ACHIEVEMENT_UNLOCKED,
                            ResourceType.ACHIEVEMENT,
                            "STREAK_7");
            verify(userAchievementRepository, never()).insertIfAbsent(userId, "STREAK_30");
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
            verify(userAchievementRepository, never()).insertIfAbsent(any(), any());
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
            when(userAchievementRepository.insertIfAbsent(userId, "TOTAL_10_HOURS")).thenReturn(0);
            when(userAchievementRepository.findByUserId(userId)).thenReturn(List.of());

            List<AchievementResponse> result = service.getAchievements(userId, ZONE);

            AchievementResponse total10 = byCode(result, "TOTAL_10_HOURS");
            assertThat(total10.unlocked()).isTrue();
            assertThat(total10.unlockedAt()).isNull();
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
            verify(userAchievementRepository, never()).insertIfAbsent(any(), any());
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
