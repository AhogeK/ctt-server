package com.ahogek.cttserver.stats.service;

import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.common.exception.ValidationException;
import com.ahogek.cttserver.device.entity.Device;
import com.ahogek.cttserver.device.repository.DeviceRepository;
import com.ahogek.cttserver.stats.dto.DistributionResponse;
import com.ahogek.cttserver.stats.dto.HeatmapResponse;
import com.ahogek.cttserver.stats.dto.HourlyDistributionResponse;
import com.ahogek.cttserver.stats.dto.StatsSummaryResponse;
import com.ahogek.cttserver.stats.dto.StreakStatsResponse;
import com.ahogek.cttserver.stats.dto.WeekHourDistributionResponse;
import com.ahogek.cttserver.stats.enums.DistributionType;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private DeviceRepository deviceRepository;
    private StatsService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID deviceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        codingSessionRepository = mock(CodingSessionRepository.class);
        dailyStatsRepository = mock(DailyStatsRepository.class);
        dailyStatsMaterializer = mock(DailyStatsMaterializer.class);
        deviceRepository = mock(DeviceRepository.class);
        service =
                new StatsService(
                        codingSessionRepository,
                        dailyStatsRepository,
                        dailyStatsMaterializer,
                        deviceRepository);
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

    private static Device device(UUID id) {
        Device device = new Device();
        device.setId(id);
        device.setUserId(UUID.randomUUID());
        device.setDeviceName("MacBook Pro");
        device.setLastSeenAt(Instant.now());
        return device;
    }

    private static Device deviceWithIde(UUID id, String ideName) {
        Device device = device(id);
        device.setIdeName(ideName);
        return device;
    }

    @Nested
    @DisplayName("UTC materialized path")
    class UtcMaterializedTests {

        @Test
        @DisplayName("summary shouldReadMaterializedDays_whenUtcAndBootstrapped")
        void summaryShouldReadMaterializedDays_whenUtcAndBootstrapped() {
            // Data rows are anchored to the real "today" so the summary's week/month/year
            // boundaries (computed against LocalDate.now(UTC)) include the expected rows; a
            // fixed past date would fall outside the current period and zero out the sums.
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            when(dailyStatsMaterializer.bootstrapIfNeeded(userId)).thenReturn(true);
            when(dailyStatsRepository.findByUserIdOrderByUtcDateAsc(userId))
                    .thenReturn(
                            List.of(
                                    day(today.minusDays(2).toString(), 3600),
                                    day(today.minusDays(1).toString(), 7200),
                                    day(today.toString(), 1800)));

            StatsSummaryResponse summary = service.summary(userId, ZoneOffset.UTC, null);

            assertThat(summary.total()).isEqualTo(12600);
            assertThat(summary.today()).isEqualTo(1800);
            // total / (first day .. today inclusive) = 12600 / 3
            assertThat(summary.dailyAverage()).isEqualTo(4200);
            // week / month / year boundaries depend on the run date (rows are anchored to the
            // real today, so period cutoffs vary), so assert the stable invariant: each period
            // at least contains today's row and never exceeds the lifetime total.
            assertThat(summary.thisWeek()).isBetween(1800L, 12600L);
            assertThat(summary.thisMonth()).isBetween(1800L, 12600L);
            assertThat(summary.thisYear()).isBetween(1800L, 12600L);
            verify(dailyStatsRepository).findByUserIdOrderByUtcDateAsc(userId);
            verify(codingSessionRepository, never()).findAllByUserIdAndIsDeletedFalse(any());
        }

        @Test
        @DisplayName("summaryShouldFallBackToLive_whenNonUtcZone")
        void summaryShouldFallBackToLive_whenNonUtcZone() {
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(List.of(session("2026-08-30T10:00:00", "2026-08-30T11:00:00")));

            StatsSummaryResponse summary = service.summary(userId, ZoneOffset.ofHours(8), null);

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
                            LocalDate.of(2026, 8, 31),
                            null);

            // dense output: zero day included
            assertThat(heatmap.points()).hasSize(2);
            assertThat(heatmap.points().get(0).seconds()).isEqualTo(3600);
            assertThat(heatmap.points().get(1).seconds()).isZero();
        }

        @Test
        @DisplayName("streaksShouldReadActiveDays_whenUtcAndBootstrapped")
        void streaksShouldReadActiveDays_whenUtcAndBootstrapped() {
            // Days are anchored to the real "today" so the current streak stays alive:
            // currentStreak requires the latest active day to be today (or yesterday),
            // which fixed past dates would outgrow as time passes.
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            when(dailyStatsMaterializer.bootstrapIfNeeded(userId)).thenReturn(true);
            when(dailyStatsRepository.findByUserIdOrderByUtcDateAsc(userId))
                    .thenReturn(
                            List.of(
                                    day(today.minusDays(2).toString(), 3600),
                                    day(today.minusDays(1).toString(), 3600),
                                    day(today.toString(), 3600)));

            StreakStatsResponse streaks = service.streaks(userId, ZoneOffset.UTC, null);

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

            StatsSummaryResponse summary = service.summary(userId, ZoneOffset.UTC, null);

            assertThat(summary.total()).isEqualTo(3600);
        }
    }

    @Nested
    @DisplayName("device dimension")
    class DeviceDimensionTests {

        @Test
        @DisplayName("summaryShouldFilterByOriginDevice_whenDeviceIdProvided")
        void summaryShouldFilterByOriginDevice_whenDeviceIdProvided() {
            when(deviceRepository.findByIdAndUserId(deviceId, userId))
                    .thenReturn(Optional.of(device(deviceId)));
            when(codingSessionRepository.findAllByUserIdAndOriginDeviceIdAndIsDeletedFalse(
                            userId, deviceId))
                    .thenReturn(List.of(session("2026-08-30T10:00:00", "2026-08-30T11:00:00")));

            StatsSummaryResponse summary =
                    service.summary(
                            userId, ZoneOffset.UTC, new StatsService.SessionFilter(deviceId, null));

            assertThat(summary.total()).isEqualTo(3600);
            verify(codingSessionRepository, never()).findAllByUserIdAndIsDeletedFalse(any());
            // per-device reads bypass the UTC materialized path (materialization is per-user)
            verify(dailyStatsMaterializer, never()).bootstrapIfNeeded(any());
        }

        @Test
        @DisplayName("summaryShouldFallBackToLive_whenDeviceFilterWithNonUtcZone")
        void summaryShouldFallBackToLive_whenDeviceFilterWithNonUtcZone() {
            when(deviceRepository.findByIdAndUserId(deviceId, userId))
                    .thenReturn(Optional.of(device(deviceId)));
            when(codingSessionRepository.findAllByUserIdAndOriginDeviceIdAndIsDeletedFalse(
                            userId, deviceId))
                    .thenReturn(List.of(session("2026-08-30T10:00:00", "2026-08-30T11:00:00")));

            StatsSummaryResponse summary =
                    service.summary(
                            userId,
                            ZoneOffset.ofHours(8),
                            new StatsService.SessionFilter(deviceId, null));

            assertThat(summary.total()).isEqualTo(3600);
        }

        @Test
        @DisplayName("distributionShouldReturnDevicesBucket_whenTypeDevices")
        void distributionShouldReturnDevicesBucket_whenTypeDevices() {
            CodingSession fromMac = session("2026-08-30T10:00:00", "2026-08-30T11:00:00");
            fromMac.setOriginDeviceId(deviceId);
            CodingSession unnamed = session("2026-08-30T11:00:00", "2026-08-30T12:30:00");
            unnamed.setOriginDeviceId(UUID.randomUUID()); // deleted/foreign device -> fallback
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(List.of(fromMac, unnamed));
            Device named = device(deviceId);
            when(deviceRepository.findByUserIdOrderByLastSeenAtDesc(userId))
                    .thenReturn(List.of(named));

            DistributionResponse response =
                    service.distribution(userId, ZoneOffset.UTC, DistributionType.DEVICES, null);

            assertThat(response.entries()).hasSize(2);
            // ordered by duration descending: 5400 before 3600
            assertThat(response.entries().get(0).name()).isEqualTo("Unknown device");
            assertThat(response.entries().get(0).seconds()).isEqualTo(5400);
            assertThat(response.entries().get(1).name()).isEqualTo("MacBook Pro");
            assertThat(response.entries().get(1).seconds()).isEqualTo(3600);
        }

        @Test
        @DisplayName("distributionShouldBucketByIdeName_whenTypeIdes")
        void distributionShouldBucketByIdeName_whenTypeIdes() {
            CodingSession fromIdea = session("2026-08-30T10:00:00", "2026-08-30T12:00:00");
            fromIdea.setOriginDeviceId(deviceId);
            CodingSession legacy = session("2026-08-30T12:00:00", "2026-08-30T13:00:00");
            legacy.setOriginDeviceId(UUID.randomUUID()); // deleted device -> Unknown IDE
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(List.of(fromIdea, legacy));
            Device device = device(deviceId);
            device.setIdeName("IntelliJ IDEA");
            when(deviceRepository.findByUserIdOrderByLastSeenAtDesc(userId))
                    .thenReturn(List.of(device));

            DistributionResponse response =
                    service.distribution(userId, ZoneOffset.UTC, DistributionType.IDES, null);

            assertThat(response.entries()).hasSize(2);
            assertThat(response.entries().get(0).name()).isEqualTo("IntelliJ IDEA");
            assertThat(response.entries().get(0).seconds()).isEqualTo(7200);
            assertThat(response.entries().get(1).name()).isEqualTo("Unknown IDE");
            assertThat(response.entries().get(1).seconds()).isEqualTo(3600);
        }

        @Test
        @DisplayName("distributionShouldFallbackToUnknownIde_whenDeviceHasNoIdeName")
        void distributionShouldFallbackToUnknownIde_whenDeviceHasNoIdeName() {
            CodingSession fromUnnamed = session("2026-08-30T10:00:00", "2026-08-30T11:00:00");
            fromUnnamed.setOriginDeviceId(deviceId);
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(List.of(fromUnnamed));
            Device unnamed = device(deviceId);
            unnamed.setIdeName(null);
            when(deviceRepository.findByUserIdOrderByLastSeenAtDesc(userId))
                    .thenReturn(List.of(unnamed));

            DistributionResponse response =
                    service.distribution(userId, ZoneOffset.UTC, DistributionType.IDES, null);

            assertThat(response.entries()).hasSize(1);
            assertThat(response.entries().getFirst().name()).isEqualTo("Unknown IDE");
            assertThat(response.entries().getFirst().seconds()).isEqualTo(3600);
        }

        @Test
        @DisplayName("distributionShouldFallbackToUnknownIde_whenIdeNameIsBlank")
        void distributionShouldFallbackToUnknownIde_whenIdeNameIsBlank() {
            CodingSession fromBlank = session("2026-08-30T10:00:00", "2026-08-30T11:00:00");
            fromBlank.setOriginDeviceId(deviceId);
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(List.of(fromBlank));
            Device blank = device(deviceId);
            blank.setIdeName("");
            when(deviceRepository.findByUserIdOrderByLastSeenAtDesc(userId))
                    .thenReturn(List.of(blank));

            DistributionResponse response =
                    service.distribution(userId, ZoneOffset.UTC, DistributionType.IDES, null);

            assertThat(response.entries()).hasSize(1);
            assertThat(response.entries().getFirst().name()).isEqualTo("Unknown IDE");
            assertThat(response.entries().getFirst().seconds()).isEqualTo(3600);
        }

        @Test
        @DisplayName("summaryShouldFilterByIdeName_whenIdeFilterMatchesDevices")
        void summaryShouldFilterByIdeName_whenIdeFilterMatchesDevices() {
            UUID ideaDevice = UUID.randomUUID();
            UUID otherDevice = UUID.randomUUID();
            when(deviceRepository.findByUserIdOrderByLastSeenAtDesc(userId))
                    .thenReturn(
                            List.of(
                                    deviceWithIde(ideaDevice, "IntelliJ IDEA"),
                                    deviceWithIde(otherDevice, "PyCharm")));
            when(codingSessionRepository.findAllByUserIdAndOriginDeviceIdInAndIsDeletedFalse(
                            userId, List.of(ideaDevice)))
                    .thenReturn(List.of(session("2026-08-30T10:00:00", "2026-08-30T12:00:00")));

            StatsSummaryResponse summary =
                    service.summary(
                            userId,
                            ZoneOffset.UTC,
                            new StatsService.SessionFilter(null, "IntelliJ IDEA"));

            assertThat(summary.total()).isEqualTo(7200);
            verify(codingSessionRepository, never()).findAllByUserIdAndIsDeletedFalse(any());
        }

        @Test
        @DisplayName("summaryShouldThrow_whenIdeMatchesNoDevice")
        void summaryShouldThrow_whenIdeMatchesNoDevice() {
            when(deviceRepository.findByUserIdOrderByLastSeenAtDesc(userId))
                    .thenReturn(List.of(deviceWithIde(UUID.randomUUID(), "PyCharm")));

            StatsService.SessionFilter filter = new StatsService.SessionFilter(null, "WebStorm");
            assertThatThrownBy(() -> service.summary(userId, ZoneOffset.UTC, filter))
                    .isInstanceOf(NotFoundException.class);
            verify(codingSessionRepository, never())
                    .findAllByUserIdAndOriginDeviceIdInAndIsDeletedFalse(any(), any());
        }

        @Test
        @DisplayName("ideFiltersShouldReturnDistinctNonBlankNamesSorted")
        void ideFiltersShouldReturnDistinctNonBlankNamesSorted() {
            when(deviceRepository.findByUserIdOrderByLastSeenAtDesc(userId))
                    .thenReturn(
                            List.of(
                                    deviceWithIde(UUID.randomUUID(), "PyCharm"),
                                    deviceWithIde(UUID.randomUUID(), "IntelliJ IDEA"),
                                    deviceWithIde(UUID.randomUUID(), "PyCharm"),
                                    deviceWithIde(UUID.randomUUID(), ""),
                                    deviceWithIde(UUID.randomUUID(), null)));

            assertThat(service.ideFilters(userId)).containsExactly("IntelliJ IDEA", "PyCharm");
        }

        @Test
        @DisplayName("heatmapYearsShouldReturnDescending_whenSessionsExist")
        void heatmapYearsShouldReturnDescending_whenSessionsExist() {
            when(codingSessionRepository.findDistinctYearsByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(List.of(2024, 2026, 2025));

            assertThat(service.heatmapYears(userId)).containsExactly(2026, 2025, 2024);
        }

        @Test
        @DisplayName("heatmapYearsShouldReturnEmpty_whenNoSessions")
        void heatmapYearsShouldReturnEmpty_whenNoSessions() {
            when(codingSessionRepository.findDistinctYearsByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(List.of());

            assertThat(service.heatmapYears(userId)).isEmpty();
        }

        @Test
        @DisplayName("distributionShouldThrow_whenDeviceNotOwnedByUser")
        void distributionShouldThrow_whenDeviceNotOwnedByUser() {
            when(deviceRepository.findByIdAndUserId(deviceId, userId)).thenReturn(Optional.empty());

            StatsService.SessionFilter filter = new StatsService.SessionFilter(deviceId, null);
            assertThatThrownBy(
                            () ->
                                    service.distribution(
                                            userId,
                                            ZoneOffset.UTC,
                                            DistributionType.LANGUAGES,
                                            filter))
                    .isInstanceOf(NotFoundException.class);
            verify(codingSessionRepository, never())
                    .findAllByUserIdAndOriginDeviceIdAndIsDeletedFalse(any(), any());
        }

        @Test
        @DisplayName("hourlyShouldClipRange_whenRangeGiven")
        void hourlyShouldClipRange_whenRangeGiven() {
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(
                            List.of(
                                    session("2026-08-25T10:00:00", "2026-08-25T11:00:00"),
                                    session("2026-09-02T10:00:00", "2026-09-02T11:00:00")));

            HourlyDistributionResponse response =
                    service.hourly(
                            userId,
                            ZoneOffset.UTC,
                            LocalDate.of(2026, 9, 1),
                            LocalDate.of(2026, 9, 7),
                            null);

            assertThat(response.points().get(10).averageSeconds()).isEqualTo(3600);
            assertThat(response.activeDays()).isEqualTo(1);
        }

        @Test
        @DisplayName("hourlyShouldThrow_whenEndBeforeStart")
        void hourlyShouldThrow_whenEndBeforeStart() {
            assertThatThrownBy(
                            () ->
                                    service.hourly(
                                            userId,
                                            ZoneOffset.UTC,
                                            LocalDate.of(2026, 9, 7),
                                            LocalDate.of(2026, 9, 1),
                                            null))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("weekHourShouldClipRangeAndDelegate_whenRangeGiven")
        void weekHourShouldClipRangeAndDelegate_whenRangeGiven() {
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(
                            List.of(
                                    session("2026-08-25T10:00:00", "2026-08-25T11:00:00"),
                                    session("2026-09-02T10:00:00", "2026-09-02T11:00:00")));

            WeekHourDistributionResponse response =
                    service.weekHour(
                            userId,
                            ZoneOffset.UTC,
                            LocalDate.of(2026, 9, 1),
                            LocalDate.of(2026, 9, 7),
                            null);

            assertThat(response.points()).hasSize(1);
            assertThat(response.points().getFirst().dayOfWeek()).isEqualTo(3);
            assertThat(response.points().getFirst().averageSeconds()).isEqualTo(3600);
            assertThat(response.weekdayCounts()).hasSize(7);
        }

        @Test
        @DisplayName("weekHourShouldReturnEmpty_whenNoSessions")
        void weekHourShouldReturnEmpty_whenNoSessions() {
            when(codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId))
                    .thenReturn(List.of());

            WeekHourDistributionResponse response =
                    service.weekHour(userId, ZoneOffset.UTC, null, null, null);

            assertThat(response.points()).isEmpty();
            assertThat(response.weekdayCounts()).isEmpty();
        }

        @Test
        @DisplayName("sessionFilterShouldReject_whenBothFiltersSet")
        void sessionFilterShouldReject_whenBothFiltersSet() {
            // The both-set guard lives in SessionFilter's canonical constructor, so a service
            // call can never receive a filter with both deviceId and ideName set.
            assertThatThrownBy(() -> new StatsService.SessionFilter(deviceId, "IDE"))
                    .isInstanceOf(ValidationException.class);
        }
    }
}
