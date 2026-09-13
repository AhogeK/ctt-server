package com.ahogek.cttserver.stats.achievement.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the achievement ladder's invariants.
 *
 * <p>The enum name is the {@code user_achievements.achievement_code} key, so the two failure modes
 * worth fencing are structural: a code that disappears orphans every unlock row already written for
 * it, and a ladder order that disagrees with the targets misnumbers every rung a client draws.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-09-11
 */
@DisplayName("Achievement")
class AchievementTest {

    /**
     * The codes shipped before the ladder expansion. Every one of them may have unlock rows in the
     * database, so none may be renamed or removed — this is the regression fence for that rule.
     */
    private static final Set<String> ORIGINALLY_SHIPPED =
            Set.of(
                    "STREAK_3",
                    "STREAK_7",
                    "STREAK_30",
                    "TOTAL_10_HOURS",
                    "TOTAL_100_HOURS",
                    "TOTAL_500_HOURS",
                    "LANGUAGES_3",
                    "LANGUAGES_5",
                    "LANGUAGES_10",
                    "EARLY_BIRD_10",
                    "EARLY_BIRD_30",
                    "NIGHT_OWL_10",
                    "NIGHT_OWL_30",
                    "DAILY_BURST",
                    "PERFECT_MONTH");

    /** Every constant of one ladder (family + window), in the order the client renders. */
    private static List<Achievement> ladderOf(Achievement.LadderKey ladder) {
        return Arrays.stream(Achievement.values())
                .filter(
                        achievement ->
                                achievement.type() == ladder.type()
                                        && achievement.window() == ladder.window())
                .sorted(Comparator.comparingInt(Achievement::tier))
                .toList();
    }

    @Nested
    @DisplayName("code stability")
    class CodeStabilityTests {

        @Test
        @DisplayName("shouldKeepEveryOriginallyShippedCode_whenLaddersExpand")
        void shouldKeepEveryOriginallyShippedCode_whenLaddersExpand() {
            Set<String> current =
                    Arrays.stream(Achievement.values()).map(Enum::name).collect(Collectors.toSet());

            assertThat(current).containsAll(ORIGINALLY_SHIPPED);
        }
    }

    @Nested
    @DisplayName("ladder")
    class LadderTests {

        @Test
        @DisplayName("shouldGiveEveryLifetimeLadder_atLeastFiveRungs")
        void shouldGiveEveryLifetimeLadder_atLeastFiveRungs() {
            // A one-rung ladder is won the moment it is understood, which is the gap the expansion
            // closes. Only ladders that exist are checked: a family may be window-only (its rungs
            // refresh every period, so a short ladder still has something to reach), and such a
            // family legitimately has no perpetual ladder at all.
            List<Achievement.LadderKey> lifetimeLadders =
                    Achievement.LadderKey.allInDeclarationOrder().stream()
                            .filter(ladder -> ladder.window() == AchievementWindow.LIFETIME)
                            .toList();

            assertThat(lifetimeLadders).isNotEmpty();
            for (Achievement.LadderKey ladder : lifetimeLadders) {
                assertThat(ladderOf(ladder))
                        .as(ladder.type().name())
                        .hasSizeGreaterThanOrEqualTo(5);
            }
        }

        @Test
        @DisplayName("shouldRaiseTargetsContiguously_whenOrderedByTier")
        void shouldRaiseTargetsContiguously_whenOrderedByTier() {
            // tiers are the client's ladder order, so walking them must both number 1..n without a
            // gap and meet strictly increasing targets. Numbering is per (family, window), so a
            // day's 2-hour goal must not be counted against the lifetime ladder's thresholds.
            for (Achievement.LadderKey ladder : Achievement.LadderKey.allInDeclarationOrder()) {
                List<Achievement> rungs = ladderOf(ladder);
                for (int index = 0; index < rungs.size(); index++) {
                    assertThat(rungs.get(index).tier())
                            .as("%s/%s rung %d", ladder.type(), ladder.window(), index + 1)
                            .isEqualTo(index + 1);
                }
                assertThat(rungs.stream().map(Achievement::target).toList())
                        .as("%s/%s", ladder.type(), ladder.window())
                        .isSorted()
                        .doesNotHaveDuplicates();
            }
        }

        @Test
        @DisplayName("shouldPartitionEveryBadgeIntoExactlyOneLadder")
        void shouldPartitionEveryBadgeIntoExactlyOneLadder() {
            // The ladders together must account for every constant exactly once, or a badge would
            // be unreachable from the client's grouping.
            int fromLadders =
                    Achievement.LadderKey.allInDeclarationOrder().stream()
                            .mapToInt(ladder -> ladderOf(ladder).size())
                            .sum();

            assertThat(fromLadders).isEqualTo(Achievement.values().length);
        }

        @Test
        @DisplayName("shouldKeepEveryLadderInsideOneWindow")
        void shouldKeepEveryLadderInsideOneWindow() {
            // A ladder spanning two windows would compare incomparable thresholds, so every rung in
            // one ladder must share its window and unit.
            for (Achievement.LadderKey ladder : Achievement.LadderKey.allInDeclarationOrder()) {
                List<Achievement> rungs = ladderOf(ladder);
                assertThat(rungs).as("%s/%s", ladder.type(), ladder.window()).isNotEmpty();
                assertThat(rungs.stream().map(Achievement::window).distinct().toList())
                        .as("%s/%s windows", ladder.type(), ladder.window())
                        .hasSize(1);
                assertThat(rungs.stream().map(Achievement::unit).distinct().toList())
                        .as("%s/%s units", ladder.type(), ladder.window())
                        .hasSize(1);
            }
        }
    }
}
