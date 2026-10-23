package com.ahogek.cttserver.leaderboard.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Window arithmetic for the leaderboard periods.
 *
 * <p>The windows decide both what a score is measured over and which Redis key it is stored under,
 * so an off-by-one here would silently rank users against the wrong span while looking healthy.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-20
 */
@DisplayName("Leaderboard period windows")
class LeaderboardPeriodTest {

    private static final LocalDate MID_Q2 = LocalDate.of(2026, 5, 17);
    private static final LocalDate MID_Q4 = LocalDate.of(2026, 11, 5);

    @Test
    @DisplayName("Should start a quarter on the first day of its own quarter")
    void shouldStartQuarter_atTheFirstDayOfItsQuarter() {
        assertThat(LeaderboardPeriod.QUARTER.start(MID_Q2)).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(LeaderboardPeriod.QUARTER.start(MID_Q4)).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(LeaderboardPeriod.QUARTER.endExclusive(MID_Q2))
                .isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(LeaderboardPeriod.QUARTER.endExclusive(MID_Q4))
                .isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    @DisplayName("Should start a half year in January or July")
    void shouldStartHalfYear_inJanuaryOrJuly() {
        assertThat(LeaderboardPeriod.HALF_YEAR.start(MID_Q2)).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(LeaderboardPeriod.HALF_YEAR.start(MID_Q4)).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(LeaderboardPeriod.HALF_YEAR.endExclusive(MID_Q2))
                .isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(LeaderboardPeriod.HALF_YEAR.endExclusive(MID_Q4))
                .isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    @DisplayName("Should cover every month exactly once per year")
    void shouldCoverEveryMonth_oncePerYear() {
        for (int month = 1; month <= 12; month++) {
            LocalDate day = LocalDate.of(2026, month, 15);
            LocalDate quarterStart = LeaderboardPeriod.QUARTER.start(day);
            LocalDate halfStart = LeaderboardPeriod.HALF_YEAR.start(day);
            assertThat(quarterStart).isBeforeOrEqualTo(day);
            assertThat(LeaderboardPeriod.QUARTER.endExclusive(day)).isAfter(day);
            assertThat(halfStart).isBeforeOrEqualTo(quarterStart);
            assertThat(LeaderboardPeriod.HALF_YEAR.endExclusive(day))
                    .isAfterOrEqualTo(LeaderboardPeriod.QUARTER.endExclusive(day));
        }
    }

    @Test
    @DisplayName("Should name the key by period start and keep the lifetime key empty")
    void shouldNameTheKey_byPeriodStart() {
        assertThat(LeaderboardPeriod.QUARTER.keySuffix(MID_Q2)).isEqualTo(":quarter:2026-04-01");
        assertThat(LeaderboardPeriod.HALF_YEAR.keySuffix(MID_Q2))
                .isEqualTo(":half-year:2026-01-01");
        assertThat(LeaderboardPeriod.MONTH.keySuffix(MID_Q2)).isEqualTo(":month:2026-05-01");
        assertThat(LeaderboardPeriod.ALL.keySuffix(MID_Q2)).isEmpty();
    }
}
