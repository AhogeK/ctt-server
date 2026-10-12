package com.ahogek.cttserver.stats.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StatsComparison")
class StatsComparisonTest {

    @Test
    @DisplayName("shouldKeepPreviousSeconds_whenBaselineExists")
    void shouldKeepPreviousSeconds_whenBaselineExists() {
        StatsComparison comparison = StatsComparison.of(1000, 2500);

        assertThat(comparison.previousSeconds()).isEqualTo(2500);
        assertThat(comparison.changePercent()).isEqualTo(-60.0);
    }

    @Test
    @DisplayName("shouldReportNullPercent_whenPreviousIsZero")
    void shouldReportNullPercent_whenPreviousIsZero() {
        // No baseline, so no change can be stated - not 100, not infinity.
        assertThat(StatsComparison.of(0, 0).changePercent()).isNull();
        assertThat(StatsComparison.of(3600, 0).changePercent()).isNull();
        assertThat(StatsComparison.of(3600, 0).previousSeconds()).isZero();
    }

    @Test
    @DisplayName("shouldReportMinusHundredPercent_whenCurrentIsZero")
    void shouldReportMinusHundredPercent_whenCurrentIsZero() {
        StatsComparison comparison = StatsComparison.of(0, 3600);

        assertThat(comparison.previousSeconds()).isEqualTo(3600);
        assertThat(comparison.changePercent()).isEqualTo(-100.0);
    }

    @Test
    @DisplayName("shouldReportPositiveChange_whenCurrentExceedsPrevious")
    void shouldReportPositiveChange_whenCurrentExceedsPrevious() {
        assertThat(StatsComparison.of(3000, 1000).changePercent()).isEqualTo(200.0);
    }

    @Test
    @DisplayName("shouldRoundToTwoDecimals_whenChangeIsRepeating")
    void shouldRoundToTwoDecimals_whenChangeIsRepeating() {
        // (1 - 3) / 3 * 100 = -66.666...
        assertThat(StatsComparison.of(1, 3).changePercent()).isEqualTo(-66.67);
    }

    @Test
    @DisplayName("shouldRoundHalfAwayFromZero_whenDigitAfterCutIsFive")
    void shouldRoundHalfAwayFromZero_whenDigitAfterCutIsFive() {
        // -89.965%: half away from zero gives -89.97, half-even would give -89.96
        assertThat(StatsComparison.of(2007, 20000).changePercent()).isEqualTo(-89.97);
    }
}
