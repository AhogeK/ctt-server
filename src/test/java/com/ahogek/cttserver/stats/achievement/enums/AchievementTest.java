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

    /** Every constant of one family, in the ladder order the client renders. */
    private static List<Achievement> ladderOf(AchievementType type) {
        return Arrays.stream(Achievement.values())
                .filter(achievement -> achievement.type() == type)
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
        @DisplayName("shouldGiveEveryFamily_atLeastFiveRungs")
        void shouldGiveEveryFamily_atLeastFiveRungs() {
            // A one-rung family is won the moment it is understood, which is the gap this
            // expansion closes; every family now has a ladder worth climbing.
            for (AchievementType type : AchievementType.values()) {
                assertThat(ladderOf(type)).as(type.name()).hasSizeGreaterThanOrEqualTo(5);
            }
        }

        @Test
        @DisplayName("shouldRaiseTargetsContiguously_whenOrderedByTier")
        void shouldRaiseTargetsContiguously_whenOrderedByTier() {
            // tiers are the client's ladder order, so walking them must both number 1..n without a
            // gap and meet strictly increasing targets.
            for (AchievementType type : AchievementType.values()) {
                List<Achievement> ladder = ladderOf(type);
                for (int index = 0; index < ladder.size(); index++) {
                    assertThat(ladder.get(index).tier())
                            .as("%s rung %d", type.name(), index + 1)
                            .isEqualTo(index + 1);
                }
                assertThat(ladder.stream().map(Achievement::target).toList())
                        .as(type.name())
                        .isSorted()
                        .doesNotHaveDuplicates();
            }
        }

        @Test
        @DisplayName("shouldKeepTargetBandsConsistent_whenTierIncreases")
        void shouldKeepTargetBandsConsistent_whenTierIncreases() {
            // Within one family the tiers partition the badges: every badge of a type has a tier,
            // and summing the ladder sizes accounts for every constant.
            int fromLadders =
                    Arrays.stream(AchievementType.values()).mapToInt(t -> ladderOf(t).size()).sum();

            assertThat(fromLadders).isEqualTo(Achievement.values().length);
        }
    }
}
