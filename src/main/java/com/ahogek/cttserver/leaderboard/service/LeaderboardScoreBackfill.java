package com.ahogek.cttserver.leaderboard.service;

import com.ahogek.cttserver.language.LanguageVocabulary;
import com.ahogek.cttserver.leaderboard.enums.LeaderboardDimension;
import com.ahogek.cttserver.leaderboard.enums.LeaderboardPeriod;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Recomputes every user's ranking scores once after a change that invalidates them.
 *
 * <p>Scores are written when a user pushes, which leaves a gap the moment the scoring rules change:
 * a user who does not push again keeps the scores computed under the old rules, and a dimension
 * added since their last push has no score at all. For a new dimension that gap is every existing
 * user, so the board reads as empty for reasons that have nothing to do with the data.
 *
 * <p>A marker guards the work, and its key carries the three things that invalidate scores: the
 * rule generation, bumped by hand when a formula changes; a fingerprint of the score matrix — every
 * legal (dimension, period) pair — which moves on its own when a dimension or period is added, so
 * that change can never be forgotten; and the vocabulary version, which moves on its own when the
 * language table changes because canonical names are part of the score. All three are in the key
 * rather than in a comment so that a needed recompute cannot be silently skipped.
 *
 * <p>The matrix fingerprint was added after a real miss: opening {@code STREAK} to every period
 * left the generation at 2, so the marker written by the previous release still matched and the
 * sweep skipped every user who had not pushed since — their windowed boards read as empty while the
 * lifetime one looked healthy.
 *
 * <p>The marker is a Redis key, so a flushed Redis brings the work back — which is the right
 * outcome, since the scores it guards were in Redis too. A missing marker therefore means "scores
 * are absent or were produced by older rules", never "the work is pending for no reason".
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-09-17
 */
@Component
public class LeaderboardScoreBackfill {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardScoreBackfill.class);

    /**
     * Bumped by hand whenever a score stops meaning what it meant.
     *
     * <p>A changed formula, or a change to which sessions count, leaves existing scores wrong while
     * looking perfectly healthy. Adding a dimension or opening one to more periods does not need a
     * bump: the matrix fingerprint in the marker moves on its own.
     */
    private static final int SCORE_RULE_GENERATION = 3;

    private static final String MARKER_PREFIX = "leaderboard:recompute:";

    /**
     * Fingerprint of every score the write path produces, derived from the enums.
     *
     * <p>Derived rather than hand-written: adding a dimension, or opening one to more periods,
     * changes the set of keys a recompute writes, and scores the marker still calls current would
     * then be missing from boards nobody had pushed to since. Deriving the fingerprint makes that
     * invalidation automatic instead of a checklist item.
     */
    static String coverageToken() {
        return coverageToken(legalPairs());
    }

    static List<String> legalPairs() {
        List<String> pairs = new ArrayList<>();
        for (LeaderboardDimension dimension : LeaderboardDimension.values()) {
            for (LeaderboardPeriod period : LeaderboardPeriod.values()) {
                if (dimension.supports(period)) {
                    pairs.add(dimension.name() + ":" + period.name());
                }
            }
        }
        return pairs;
    }

    /**
     * First 8 hex characters of the SHA-256 over the sorted pairs.
     *
     * <p>Sorted so the order the enums happen to declare cannot change the fingerprint, and hashed
     * so a growing matrix does not push the key towards Redis' length limit.
     */
    static String coverageToken(Collection<String> pairs) {
        String joined = String.join(",", new TreeSet<>(pairs));
        try {
            byte[] hash =
                    MessageDigest.getInstance("SHA-256")
                            .digest(joined.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }

    private final UserRepository userRepository;
    private final LeaderboardService leaderboardService;
    private final StringRedisTemplate redisTemplate;
    private final LanguageVocabulary languageVocabulary;

    public LeaderboardScoreBackfill(
            UserRepository userRepository,
            LeaderboardService leaderboardService,
            StringRedisTemplate redisTemplate,
            LanguageVocabulary languageVocabulary) {
        this.userRepository = userRepository;
        this.leaderboardService = leaderboardService;
        this.redisTemplate = redisTemplate;
        this.languageVocabulary = languageVocabulary;
    }

    /**
     * Recomputes all users' scores when the marker says they were produced by older rules.
     *
     * <p>Delayed past startup so the sweep does not compete with the application coming up, and
     * repeated on a long interval so a run that failed before writing its marker is retried rather
     * than lost. Once the marker exists the check costs one Redis lookup.
     */
    @Scheduled(
            initialDelayString = "${ctt.leaderboard.backfill-initial-delay-ms:60000}",
            fixedDelayString = "${ctt.leaderboard.backfill-interval-ms:86400000}")
    public void backfillScores() {
        String marker =
                MARKER_PREFIX
                        + "g"
                        + SCORE_RULE_GENERATION
                        + ":cov"
                        + coverageToken()
                        + ":vocab"
                        + languageVocabulary.version();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(marker))) {
            return;
        }

        List<UUID> userIds = userRepository.findAllIds();
        log.atInfo().log(
                "Leaderboard score backfill starting: {} users, generation {}, vocabulary v{}",
                userIds.size(),
                SCORE_RULE_GENERATION,
                languageVocabulary.version());

        int recomputed = 0;
        int failed = 0;
        for (UUID userId : userIds) {
            // Per user, so one unreadable history cannot abort the sweep: updateUserScores opens
            // its
            // own transaction and takes its own lock.
            try {
                leaderboardService.updateUserScores(userId);
                recomputed++;
            } catch (RuntimeException e) {
                failed++;
                log.atWarn().setCause(e).log("Skipped user {} during leaderboard backfill", userId);
            }
        }

        // Written even when some users failed: a partial sweep still removes the stale scores for
        // everyone it reached, and the users it missed are recomputed on their next push. Leaving
        // the marker unset would instead repeat the whole sweep daily, which costs far more than
        // the
        // users it would recover.
        redisTemplate.opsForValue().set(marker, "done");
        if (failed == 0) {
            log.atInfo().log(
                    "Leaderboard score backfill complete: {} users recomputed", recomputed);
        } else {
            log.atWarn()
                    .log(
                            "Leaderboard score backfill complete: {} recomputed, {} failed and left"
                                    + " to their next push",
                            recomputed,
                            failed);
        }
    }
}
