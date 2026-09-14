package com.ahogek.cttserver.stats.achievement.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the window boundary rules.
 *
 * <p>Boundaries are what makes a periodic badge correct or off by a day, and they are resolved in
 * the caller's timezone rather than UTC, so the cases worth fencing are the interesting calendar
 * ones: a week straddling New Year, a leap February, and a period key that must differ between
 * consecutive periods (or a badge could never be earned twice).
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-09-13
 */
@DisplayName("AchievementWindow")
class AchievementWindowTest {

    @Nested
    @DisplayName("bounds")
    class BoundTests {

        @Test
        @DisplayName("shouldReturnNoBounds_forLifetime")
        void shouldReturnNoBounds_forLifetime() {
            LocalDate today = LocalDate.of(2026, 9, 13);

            assertThat(AchievementWindow.LIFETIME.start(today)).isNull();
            assertThat(AchievementWindow.LIFETIME.end(today)).isNull();
        }

        @Test
        @DisplayName("shouldSpanTheWholePeriod_whenDayWeekMonthYear")
        void shouldSpanTheWholePeriod_whenDayWeekMonthYear() {
            // Sunday 2026-09-13; its ISO week runs Monday 09-07 through Sunday 09-13
            LocalDate today = LocalDate.of(2026, 9, 13);

            assertThat(AchievementWindow.DAY.start(today)).isEqualTo(today);
            assertThat(AchievementWindow.DAY.end(today)).isEqualTo(today);

            assertThat(AchievementWindow.WEEK.start(today)).isEqualTo(LocalDate.of(2026, 9, 7));
            assertThat(AchievementWindow.WEEK.end(today)).isEqualTo(LocalDate.of(2026, 9, 13));

            assertThat(AchievementWindow.MONTH.start(today)).isEqualTo(LocalDate.of(2026, 9, 1));
            assertThat(AchievementWindow.MONTH.end(today)).isEqualTo(LocalDate.of(2026, 9, 30));

            assertThat(AchievementWindow.YEAR.start(today)).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(AchievementWindow.YEAR.end(today)).isEqualTo(LocalDate.of(2026, 12, 31));
        }

        @Test
        @DisplayName("shouldSpansLeapFebruary_whenLeapYear")
        void shouldSpansLeapFebruary_whenLeapYear() {
            LocalDate today = LocalDate.of(2028, 2, 10);

            assertThat(AchievementWindow.MONTH.start(today)).isEqualTo(LocalDate.of(2028, 2, 1));
            // 2028 is a leap year, so the month ends on the 29th rather than the 28th
            assertThat(AchievementWindow.MONTH.end(today)).isEqualTo(LocalDate.of(2028, 2, 29));
        }

        @Test
        @DisplayName("shouldStartOnMonday_whenPeriodCrossesNewYear")
        void shouldStartOnMonday_whenPeriodCrossesNewYear() {
            // Thursday 2026-01-01; its ISO week began Monday 2025-12-29
            LocalDate today = LocalDate.of(2026, 1, 1);

            assertThat(AchievementWindow.WEEK.start(today)).isEqualTo(LocalDate.of(2025, 12, 29));
            assertThat(AchievementWindow.WEEK.end(today)).isEqualTo(LocalDate.of(2026, 1, 4));
        }
    }

    @Nested
    @DisplayName("period key")
    class PeriodKeyTests {

        @Test
        @DisplayName("shouldUseTheLifetimeMarker_forLifetime")
        void shouldUseTheLifetimeMarker_forLifetime() {
            assertThat(AchievementWindow.LIFETIME.periodKey(LocalDate.of(2026, 9, 13)))
                    .isEqualTo(AchievementWindow.LIFETIME_PERIOD);
        }

        @Test
        @DisplayName("shouldProduceStableAndDistinctKeys_acrossPeriods")
        void shouldProduceStableAndDistinctKeys_acrossPeriods() {
            LocalDate today = LocalDate.of(2026, 9, 13);

            assertThat(AchievementWindow.DAY.periodKey(today)).isEqualTo("2026-09-13");
            assertThat(AchievementWindow.MONTH.periodKey(today)).isEqualTo("2026-09");
            assertThat(AchievementWindow.YEAR.periodKey(today)).isEqualTo("2026");

            // consecutive days/months/years must differ, or a windowed badge could never be
            // re-earned
            assertThat(AchievementWindow.DAY.periodKey(today))
                    .isNotEqualTo(AchievementWindow.DAY.periodKey(today.plusDays(1)));
            assertThat(AchievementWindow.MONTH.periodKey(today))
                    .isNotEqualTo(AchievementWindow.MONTH.periodKey(today.plusMonths(1)));
            assertThat(AchievementWindow.YEAR.periodKey(today))
                    .isNotEqualTo(AchievementWindow.YEAR.periodKey(today.plusYears(1)));
        }

        @Test
        @DisplayName("shouldKeepOneKey_whenTwoDatesShareAnIsoWeek")
        void shouldKeepOneKey_whenTwoDatesShareAnIsoWeek() {
            // Monday and the Sunday of the same ISO week are one period
            LocalDate monday = LocalDate.of(2026, 9, 7);
            LocalDate sunday = LocalDate.of(2026, 9, 13);

            assertThat(AchievementWindow.WEEK.periodKey(monday))
                    .isEqualTo(AchievementWindow.WEEK.periodKey(sunday));
        }

        @Test
        @DisplayName("shouldUseTheIsoWeekYear_whenWeekStraddlesNewYear")
        void shouldUseTheIsoWeekYear_whenWeekStraddlesNewYear() {
            // 2025-12-29 (Mon) through 2026-01-04 (Sun) is ISO week 2026-W01, so both ends must
            // carry the week-based year rather than their own calendar year.
            assertThat(AchievementWindow.WEEK.periodKey(LocalDate.of(2025, 12, 29)))
                    .isEqualTo("2026-W01");
            assertThat(AchievementWindow.WEEK.periodKey(LocalDate.of(2026, 1, 4)))
                    .isEqualTo("2026-W01");
        }
    }

    @Nested
    @DisplayName("previous period")
    class PreviousPeriodTests {

        @Test
        @DisplayName("shouldReturnNoPreviousPeriod_forLifetime")
        void shouldReturnNoPreviousPeriod_forLifetime() {
            assertThat(AchievementWindow.LIFETIME.previousPeriod(LocalDate.of(2026, 9, 13)))
                    .isNull();
        }

        @Test
        @DisplayName("shouldLandInThePrecedingPeriod_forEachWindow")
        void shouldLandInThePrecedingPeriod_forEachWindow() {
            LocalDate friday = LocalDate.of(2026, 9, 11);

            assertThat(
                            AchievementWindow.DAY.periodKey(
                                    AchievementWindow.DAY.previousPeriod(friday)))
                    .isEqualTo("2026-09-10");
            assertThat(
                            AchievementWindow.WEEK.periodKey(
                                    AchievementWindow.WEEK.previousPeriod(friday)))
                    .isEqualTo("2026-W36");
            assertThat(
                            AchievementWindow.MONTH.periodKey(
                                    AchievementWindow.MONTH.previousPeriod(friday)))
                    .isEqualTo("2026-08");
            assertThat(
                            AchievementWindow.YEAR.periodKey(
                                    AchievementWindow.YEAR.previousPeriod(friday)))
                    .isEqualTo("2025");
        }

        @Test
        @DisplayName("shouldStepMonthsByTheCalendar_notByThirtyDays")
        void shouldStepMonthsByTheCalendar_notByThirtyDays() {
            // Stepping back from March 1 with a 30-day subtraction would land in January for a
            // 31-day month; the calendar step must land in February regardless of month length.
            assertThat(AchievementWindow.MONTH.previousPeriod(LocalDate.of(2026, 3, 31)))
                    .isEqualTo(LocalDate.of(2026, 2, 28));
        }

        @Test
        @DisplayName("shouldStepWeeksAcrossTheYearBoundary_asOnePeriod")
        void shouldStepWeeksAcrossTheYearBoundary_asOnePeriod() {
            // 2026-01-01 falls in ISO week 2026-W01; the week before it is 2025-W52, and the step
            // must land there rather than in a week of the wrong year.
            LocalDate previous = AchievementWindow.WEEK.previousPeriod(LocalDate.of(2026, 1, 1));

            assertThat(AchievementWindow.WEEK.periodKey(previous)).isEqualTo("2025-W52");
        }
    }
}
