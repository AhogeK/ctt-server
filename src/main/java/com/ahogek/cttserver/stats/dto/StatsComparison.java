package com.ahogek.cttserver.stats.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One summary metric measured against the same metric in the previous complete period.
 *
 * <p>The previous period is a closed historical window — yesterday, last week, last month, last
 * year — never the same period to date, so the value is final instead of partial. For {@code
 * dailyAverage} it is the average as it stood at the end of yesterday.
 *
 * @param previousSeconds seconds coded in the previous complete period, or seconds per day for
 *     {@code dailyAverage}; 0 when that period holds no coding
 * @param changePercent change against {@code previousSeconds} in percent, rounded to two decimals;
 *     {@code null} when {@code previousSeconds} is 0 (nothing to divide by) or when the baseline is
 *     undefined, which only occurs for {@code dailyAverage} on the first coding day. Clients hide
 *     the change rather than rendering a value against no baseline.
 */
@Schema(description = "A summary metric compared against its previous complete period")
public record StatsComparison(
        @Schema(
                        description =
                                "Seconds coded in the previous complete period; 0 when it has none",
                        example = "1234")
                long previousSeconds,
        @Schema(
                        description =
                                "Percent change against previousSeconds, rounded to two decimals;"
                                        + " null when previousSeconds is 0 or the baseline is"
                                        + " undefined (dailyAverage on the first coding day)",
                        example = "-100.00",
                        nullable = true)
                @JsonInclude(JsonInclude.Include.ALWAYS)
                Double changePercent) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * Measures {@code currentSeconds} against {@code previousSeconds}.
     *
     * <p>Rounding is half away from zero on the decimal value, so a change of exactly 0.005 percent
     * rounds to 0.01 in both directions. A zero previous value leaves {@code changePercent} null
     * rather than infinite or 100: there is no baseline to state a change against.
     *
     * @param currentSeconds seconds coded in the current period
     * @param previousSeconds seconds coded in the previous complete period
     * @return the comparison, with a null {@code changePercent} when there is no baseline
     */
    public static StatsComparison of(long currentSeconds, long previousSeconds) {
        if (previousSeconds == 0) {
            return new StatsComparison(previousSeconds, null);
        }
        BigDecimal percent =
                BigDecimal.valueOf(currentSeconds - previousSeconds)
                        .multiply(HUNDRED)
                        .divide(BigDecimal.valueOf(previousSeconds), 2, RoundingMode.HALF_UP);
        return new StatsComparison(previousSeconds, percent.doubleValue());
    }
}
