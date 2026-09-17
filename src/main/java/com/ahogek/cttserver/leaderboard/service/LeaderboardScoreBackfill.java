package com.ahogek.cttserver.leaderboard.service;

import com.ahogek.cttserver.language.LanguageVocabulary;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Recomputes every user's ranking scores once after a change that invalidates them.
 *
 * <p>Scores are written when a user pushes, which leaves a gap the moment the scoring rules change:
 * a user who does not push again keeps the scores computed under the old rules, and a dimension
 * added since their last push has no score at all. For a new dimension that gap is every existing
 * user, so the board reads as empty for reasons that have nothing to do with the data.
 *
 * <p>A marker guards the work, and its key carries the two things that invalidate scores: the rule
 * generation, bumped by hand when a dimension or a formula changes, and the vocabulary version,
 * which moves on its own when the language table changes because canonical names are part of the
 * score. Both are in the key rather than in a comment so that forgetting to bump something cannot
 * silently skip a needed recompute.
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
     * <p>Adding a dimension, changing a formula, or altering which sessions count all leave
     * existing scores wrong while looking perfectly healthy.
     */
    private static final int SCORE_RULE_GENERATION = 2;

    private static final String MARKER_PREFIX = "leaderboard:recompute:";

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
