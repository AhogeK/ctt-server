package com.ahogek.cttserver.leaderboard.service;

import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ValidationException;
import com.ahogek.cttserver.common.lock.RedisLockService;
import com.ahogek.cttserver.language.CanonicalLanguage;
import com.ahogek.cttserver.language.LanguageType;
import com.ahogek.cttserver.language.LanguageVocabulary;
import com.ahogek.cttserver.leaderboard.dto.LanguageBoardDto;
import com.ahogek.cttserver.leaderboard.dto.LeaderboardEntryDto;
import com.ahogek.cttserver.leaderboard.dto.LeaderboardResponse;
import com.ahogek.cttserver.leaderboard.enums.LeaderboardDimension;
import com.ahogek.cttserver.leaderboard.enums.LeaderboardPeriod;
import com.ahogek.cttserver.stats.service.StatsCalculator;
import com.ahogek.cttserver.sync.entity.CodingSession;
import com.ahogek.cttserver.sync.repository.CodingSessionRepository;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Global leaderboard backed by Redis ZSets.
 *
 * <p>Scores are recomputed from the database for the affected user and written with {@code ZADD}
 * (never incrementally accumulated): a soft-deleted or conflict-updated session would otherwise
 * make a running total drift from the real value, while an incremental write still cannot reflect a
 * streak change at all. Recomputing one user's scores keeps the ranking immediately correct with a
 * single-user cost; other members' scores are untouched. The leaderboard is derived state, so a
 * Redis failure is logged and swallowed — the next push recomputes the scores and self-heals.
 *
 * <p>Ranking semantics: {@link LeaderboardDimension#TOTAL} is the merged coding duration (same
 * semantics as the stats summary total) over the requested {@link LeaderboardPeriod}, {@link
 * LeaderboardDimension#STREAK} is the longest consecutive coding-day streak, {@link
 * LeaderboardDimension#NIGHT_OWL} and {@link LeaderboardDimension#EARLY_BIRD} are merged durations
 * inside the 22:00-05:00 and 06:00-09:00 daily windows, and {@link LeaderboardDimension#GROWTH} is
 * the week-over-week net growth in seconds. All boundaries use UTC so the global ranking has a
 * single, server-deterministic timezone. Period keys are bucketed by their period start (ISO Monday
 * for weeks) and expire once the period closes ({@link LeaderboardPeriod#ALL} never expires).
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-31
 */
@Service
public class LeaderboardService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);

    private static final String KEY_PREFIX = "leaderboard:";

    /**
     * Set of languages that have a board, so a client can offer a selector without guessing.
     *
     * <p>A SET rather than a scan: enumerating ZSet keys by pattern is O(total keys), and the index
     * is written alongside the scores it describes.
     */
    private static final String LANGUAGE_BOARDS_KEY = KEY_PREFIX + "languages";

    private static final String LOCK_PREFIX = "leaderboard:lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);

    private final StringRedisTemplate redisTemplate;
    private final RedisLockService redisLock;
    private final CodingSessionRepository codingSessionRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final LanguageVocabulary languageVocabulary;

    @Autowired
    public LeaderboardService(
            StringRedisTemplate redisTemplate,
            RedisLockService redisLock,
            CodingSessionRepository codingSessionRepository,
            UserRepository userRepository,
            LanguageVocabulary languageVocabulary) {
        this(
                redisTemplate,
                redisLock,
                codingSessionRepository,
                userRepository,
                Clock.systemUTC(),
                languageVocabulary);
    }

    LeaderboardService(
            StringRedisTemplate redisTemplate,
            RedisLockService redisLock,
            CodingSessionRepository codingSessionRepository,
            UserRepository userRepository,
            Clock clock,
            LanguageVocabulary languageVocabulary) {
        this.redisTemplate = redisTemplate;
        this.redisLock = redisLock;
        this.codingSessionRepository = codingSessionRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.languageVocabulary = languageVocabulary;
    }

    /**
     * Recomputes the user's score for every supported dimension/period combination and writes them
     * into their ZSet keys.
     *
     * <p>The database read joins the surrounding transaction, so a push-triggered call observes the
     * sessions just written by the same push. Failures are logged and swallowed — the leaderboard
     * is derived state and the next push recomputes the scores.
     *
     * @param userId the owning user
     */
    @Transactional(readOnly = true)
    public void updateUserScores(UUID userId) {
        // Serialize the read-compute-write per user: under concurrent same-user pushes a plain
        // recompute could read a pre-commit snapshot and overwrite the other push's sessions in
        // the ZSet. The short-lived per-user lock makes that interleaving impossible; on contention
        // we retry briefly, then fall through with a best-effort recompute (the next push heals any
        // residual staleness anyway).
        String lockKey = LOCK_PREFIX + userId;
        try {
            if (redisLock.tryAcquire(lockKey, LOCK_TTL)) {
                try {
                    recomputeAndWriteAll(userId);
                } finally {
                    redisTemplate.delete(lockKey);
                }
            } else {
                log.warn("Leaderboard lock contention for user {}, recomputing anyway", userId);
                recomputeAndWriteAll(userId);
            }
        } catch (Exception e) {
            log.error("Failed to update leaderboard scores for user {}", userId, e);
        }
    }

    /**
     * Returns one page of the leaderboard with the calling user's rank.
     *
     * <p>Ranks use standard competition ranking: equal scores share a position, and the next
     * distinct score resumes after the gap (1, 2, 2, 4). The page's first rank therefore cannot be
     * derived from {@code offset} — a page starting mid-tie must report the same position its
     * members hold globally — so it is resolved with one {@code ZCOUNT} for the number of strictly
     * higher scores, and subsequent distinct scores are numbered by their absolute position.
     *
     * <p>The caller's own rank is resolved by the same rule rather than by Redis's physical
     * position, because {@code reverseRank} breaks ties by member order: the two would otherwise
     * disagree inside a single response whenever the caller is tied.
     *
     * @param dimension the ranking dimension
     * @param period the time window
     * @param language the canonical language, required for {@link LeaderboardDimension#LANGUAGE}
     *     and rejected for the others
     * @param limit page size
     * @param offset zero-based start index
     * @param currentUserId the calling user
     * @return the ranked page, the caller's rank (or {@code null} when not ranked) and the ranking
     *     size
     * @throws ValidationException when the dimension does not support the period, when a
     *     partitioned dimension is requested without a language, or when the named language is not
     *     one the vocabulary recognizes
     */
    @Transactional(readOnly = true)
    public LeaderboardResponse getLeaderboard(
            LeaderboardDimension dimension,
            LeaderboardPeriod period,
            String language,
            int limit,
            int offset,
            UUID currentUserId) {
        if (!dimension.supports(period)) {
            throw new ValidationException(
                    ErrorCode.COMMON_003,
                    "Dimension " + dimension + " does not support period " + period);
        }
        String board = resolveBoard(dimension, language);
        LocalDate today = LocalDate.now(clock);
        String key = key(dimension, period, today, board);
        ZSetOperations<String, String> zSet = redisTemplate.opsForZSet();
        Set<ZSetOperations.TypedTuple<String>> tuples =
                zSet.reverseRangeWithScores(key, offset, (long) offset + limit - 1);

        List<LeaderboardEntryDto> entries = buildEntries(tuples, key, offset);
        Long currentUserRank = rankOf(zSet, key, currentUserId);
        Long total = zSet.size(key);
        return new LeaderboardResponse(
                entries, currentUserRank, total != null ? total : entries.size());
    }

    /**
     * Returns every language a caller can rank inside, so a client can offer a selector.
     *
     * <p>The list is the vocabulary, not the set of boards that happen to hold scores. Which boards
     * exist is a property of the vocabulary: a language is rankable the moment it is recognized,
     * and it is recognized whether or not anyone has pushed since the dimension was added. Deriving
     * the list from activity instead produced a directory that omitted legitimate languages — a
     * board for a language nobody had pushed yet answered `200`, while the directory did not offer
     * it — so the two disagreed about the same question.
     *
     * <p>{@code hasMembers} carries the activity fact separately, where it belongs: the set of
     * languages with scores is still maintained, but as a hint for ordering rather than as a filter
     * over what exists. One set read answers it for every entry, so a client can sort populated
     * boards first without the server issuing a count per board.
     *
     * @return every canonical language, ordered by name
     */
    @Transactional(readOnly = true)
    public List<LanguageBoardDto> languageBoards() {
        Set<String> populated = redisTemplate.opsForSet().members(LANGUAGE_BOARDS_KEY);
        Set<String> withMembers = populated == null ? Set.of() : populated;
        return languageVocabulary.languages().stream()
                .map(
                        language ->
                                LanguageBoardDto.from(
                                        language, withMembers.contains(language.name())))
                .toList();
    }

    /**
     * Validates the language argument and returns the board name, or {@code null} for dimensions
     * that are not partitioned.
     *
     * <p>A language the vocabulary classifies as {@code Other} has no board by construction, so it
     * is rejected here rather than answered with an empty ranking — an empty page for a language
     * that cannot have one is a different statement from "nobody is ranked yet".
     *
     * @param dimension the requested dimension
     * @param language the requested language, possibly absent
     * @return the canonical language name, or {@code null} when the dimension needs none
     * @throws ValidationException when the parameter is missing, unexpected, or unknown
     */
    private String resolveBoard(LeaderboardDimension dimension, String language) {
        boolean provided = language != null && !language.isBlank();
        if (!dimension.requiresLanguage()) {
            if (provided) {
                throw new ValidationException(
                        ErrorCode.COMMON_003,
                        "Dimension " + dimension + " is not per-language; omit language");
            }
            return null;
        }
        if (!provided) {
            throw new ValidationException(
                    ErrorCode.COMMON_003, "Dimension " + dimension + " requires a language");
        }
        CanonicalLanguage canonical = languageVocabulary.normalize(language);
        if (!canonical.recognized() || canonical.type() == LanguageType.OTHER) {
            throw new ValidationException(ErrorCode.COMMON_003, "Unknown language: " + language);
        }
        return canonical.name();
    }

    /**
     * Maps one page of ZSet members to ranked entries.
     *
     * @param tuples the page's members with scores, score-descending
     * @param key the ranking key, used to resolve where the page starts
     * @param offset the page's zero-based start index
     * @return the ranked entries
     */
    private List<LeaderboardEntryDto> buildEntries(
            Set<ZSetOperations.TypedTuple<String>> tuples, String key, int offset) {
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        // A ZSet member always carries a value and score, but the API annotates both as nullable;
        // skip malformed entries defensively.
        List<ZSetOperations.TypedTuple<String>> valid =
                tuples.stream()
                        .filter(tuple -> tuple.getValue() != null && tuple.getScore() != null)
                        .toList();
        if (valid.isEmpty()) {
            return List.of();
        }

        List<UUID> userIds =
                valid.stream()
                        .map(tuple -> UUID.fromString(Objects.requireNonNull(tuple.getValue())))
                        .toList();
        Map<UUID, User> users =
                userRepository.findAllById(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        List<LeaderboardEntryDto> entries = new ArrayList<>(valid.size());
        boolean firstOfPage = true;
        long rank = 0;
        double previousScore = Double.NaN;
        for (int index = 0; index < valid.size(); index++) {
            ZSetOperations.TypedTuple<String> tuple = valid.get(index);
            double score = Objects.requireNonNull(tuple.getScore());
            if (firstOfPage) {
                rank = rankFor(score, key);
                firstOfPage = false;
            } else if (score != previousScore) {
                rank = offset + (long) index + 1;
            }
            previousScore = score;
            UUID userId = UUID.fromString(Objects.requireNonNull(tuple.getValue()));
            User user = users.get(userId);
            entries.add(
                    new LeaderboardEntryDto(
                            userId,
                            user != null ? user.getDisplayName() : null,
                            (long) score,
                            rank));
        }
        return entries;
    }

    /**
     * Resolves the competition rank of a score: one more than the number of strictly higher scores.
     *
     * @param score the score to place
     * @param key the ranking key
     * @return the 1-based rank that score holds
     */
    private long rankFor(double score, String key) {
        // The exclusive lower bound is the next representable double above the score, so equally
        // scored members are excluded from the count and share the resulting rank.
        Long higher =
                redisTemplate.opsForZSet().count(key, Math.nextUp(score), Double.POSITIVE_INFINITY);
        return (higher != null ? higher : 0L) + 1;
    }

    /**
     * Resolves the caller's competition rank, or {@code null} when they have no score.
     *
     * @param zSet the ZSet operations handle
     * @param key the ranking key
     * @param userId the calling user
     * @return the 1-based rank, or {@code null} when unranked
     */
    private Long rankOf(ZSetOperations<String, String> zSet, String key, UUID userId) {
        Double score = zSet.score(key, userId.toString());
        if (score == null) {
            return null;
        }
        return rankFor(score, key);
    }

    private void recomputeAndWriteAll(UUID userId) {
        List<CodingSession> sessions =
                codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId);
        LocalDate today = LocalDate.now(clock);
        SessionViews views = buildViews(sessions, today);
        for (LeaderboardDimension dimension : LeaderboardDimension.values()) {
            for (LeaderboardPeriod period : LeaderboardPeriod.values()) {
                if (!dimension.supports(period)) {
                    continue;
                }
                if (dimension.requiresLanguage()) {
                    // One board per language the user actually used; a user with no sessions in a
                    // language is simply absent from its board.
                    for (String language : views.intervalsByLanguage().keySet()) {
                        writeScore(
                                key(dimension, period, today, language),
                                userId,
                                computeScore(views, dimension, period, language),
                                period);
                        redisTemplate.opsForSet().add(LANGUAGE_BOARDS_KEY, language);
                    }
                } else {
                    writeScore(
                            key(dimension, period, today, null),
                            userId,
                            computeScore(views, dimension, period, null),
                            period);
                }
            }
        }
    }

    private void writeScore(String key, UUID userId, long score, LeaderboardPeriod period) {
        redisTemplate.opsForZSet().add(key, userId.toString(), score);
        Duration ttl = ttlFor(period);
        if (ttl != null) {
            redisTemplate.expire(key, ttl);
        }
    }

    /**
     * Computes one score from precomputed session views.
     *
     * <p>The interval list and the per-day totals are built once per recompute and shared across
     * every dimension/period pair: they are the expensive part (interval merge and day splitting
     * are {@code O(n log n)}), and rebuilding them per pair would make the cost scale with the
     * number of keys rather than with the user's history.
     *
     * @param views the shared session views
     * @param dimension the ranking dimension
     * @param period the time window
     * @return the score
     */
    private long computeScore(
            SessionViews views,
            LeaderboardDimension dimension,
            LeaderboardPeriod period,
            String language) {
        ZoneOffset utc = ZoneOffset.UTC;
        LocalDate today = views.today();
        return switch (dimension) {
            case TOTAL -> {
                if (period == LeaderboardPeriod.ALL) {
                    yield views.lifetimeSeconds();
                }
                yield periodsSeconds(views.intervals(), period, today, 0);
            }
            case ACTIVE_DAYS -> activeDaysIn(views, period, today);
            case LANGUAGE -> languageSeconds(views, language, period);
            case STREAK -> StatsCalculator.streaks(views.sessions(), utc, today).max();
            case NIGHT_OWL, EARLY_BIRD ->
                    StatsCalculator.mergedDurationInDailyWindow(
                            views.sessions(),
                            utc,
                            dimension.windowStartHour(),
                            dimension.windowEndHour(),
                            periodStartOrMin(period, today),
                            periodEndOrMax(period, today));
            case GROWTH ->
                    periodsSeconds(views.intervals(), period, today, 0)
                            - periodsSeconds(views.intervals(), period, today, 1);
        };
    }

    /**
     * Merged coding seconds inside a period an offset of periods away from the current one.
     *
     * @param intervals the user's intervals
     * @param period the window
     * @param today the reference date
     * @param periodsBack how many periods to shift back ({@code 0} is the current one)
     * @return merged seconds in that window
     */
    private static long periodsSeconds(
            List<StatsCalculator.TimeInterval> intervals,
            LeaderboardPeriod period,
            LocalDate today,
            int periodsBack) {
        LocalDate start = period.start(today);
        LocalDate end = period.endExclusive(today);
        if (start == null || end == null) {
            return 0;
        }
        LocalDate windowStart = shift(start, period, -periodsBack);
        LocalDate windowEnd = shift(end, period, -periodsBack);
        return StatsCalculator.mergedDurationSeconds(
                intervals,
                windowStart.atStartOfDay().atOffset(ZoneOffset.UTC),
                windowEnd.atStartOfDay().atOffset(ZoneOffset.UTC));
    }

    /**
     * Merged seconds the user spent in one language inside a period.
     *
     * <p>Merged rather than summed so two overlapping sessions in the same language count once —
     * the rule every duration dimension uses. A language the user has no sessions in scores zero,
     * which for a board simply means they do not appear on it.
     *
     * @param views the shared session views
     * @param language the canonical language name
     * @param period the time window
     * @return merged seconds in that language
     */
    private long languageSeconds(SessionViews views, String language, LeaderboardPeriod period) {
        List<StatsCalculator.TimeInterval> intervals =
                views.intervalsByLanguage().get(language == null ? "" : language);
        if (intervals == null || intervals.isEmpty()) {
            return 0;
        }
        // ALL has no bounds, and the bounded helper answers zero for it — so the lifetime board
        // needs the merged total directly rather than a window that does not exist.
        if (period == LeaderboardPeriod.ALL) {
            return mergedSeconds(intervals);
        }
        return periodsSeconds(intervals, period, views.today(), 0);
    }

    /**
     * Overlap-collapsed total of a set of intervals.
     *
     * @param intervals the intervals
     * @return the merged duration in seconds
     */
    private static long mergedSeconds(List<StatsCalculator.TimeInterval> intervals) {
        return StatsCalculator.mergeOverlapping(intervals).stream()
                .map(StatsCalculator.TimeInterval::duration)
                .reduce(Duration.ZERO, Duration::plus)
                .toSeconds();
    }

    /** Number of distinct coding days inside the current period. */
    private static long activeDaysIn(
            SessionViews views, LeaderboardPeriod period, LocalDate today) {
        LocalDate start = period.start(today);
        LocalDate end = period.endExclusive(today);
        if (start == null || end == null) {
            return views.secondsByDay().size();
        }
        return views.secondsByDay().entrySet().stream()
                .filter(
                        entry ->
                                entry.getValue() > 0
                                        && !entry.getKey().isBefore(start)
                                        && entry.getKey().isBefore(end))
                .count();
    }

    /** Shifts a date by a whole number of periods (positive forward, negative back). */
    private static LocalDate shift(LocalDate date, LeaderboardPeriod period, int periods) {
        return switch (period) {
            case ALL -> date;
            case WEEK -> date.plusWeeks(periods);
            case MONTH -> date.plusMonths(periods);
            case YEAR -> date.plusYears(periods);
        };
    }

    private static OffsetDateTime periodStartOrMin(LeaderboardPeriod period, LocalDate today) {
        LocalDate start = period.start(today);
        return start != null ? start.atStartOfDay().atOffset(ZoneOffset.UTC) : OffsetDateTime.MIN;
    }

    private static OffsetDateTime periodEndOrMax(LeaderboardPeriod period, LocalDate today) {
        LocalDate end = period.endExclusive(today);
        return end != null ? end.atStartOfDay().atOffset(ZoneOffset.UTC) : OffsetDateTime.MAX;
    }

    /**
     * Session views built once per recompute and shared by every dimension.
     *
     * @param sessions the user's live sessions
     * @param intervals the merged-capable intervals in UTC
     * @param secondsByDay overlap-collapsed seconds per UTC day
     * @param lifetimeSeconds merged lifetime total (the {@code TOTAL}/{@code ALL} score)
     * @param today the reference date
     */
    private record SessionViews(
            List<CodingSession> sessions,
            List<StatsCalculator.TimeInterval> intervals,
            Map<String, List<StatsCalculator.TimeInterval>> intervalsByLanguage,
            Map<LocalDate, Long> secondsByDay,
            long lifetimeSeconds,
            LocalDate today) {}

    private SessionViews buildViews(List<CodingSession> sessions, LocalDate today) {
        ZoneOffset utc = ZoneOffset.UTC;
        List<StatsCalculator.TimeInterval> intervals = StatsCalculator.toIntervals(sessions, utc);
        long total = mergedSeconds(intervals);
        return new SessionViews(
                sessions,
                intervals,
                intervalsByCanonicalLanguage(sessions, utc),
                StatsCalculator.mergedSecondsByDay(sessions, utc),
                total,
                today);
    }

    /**
     * Groups sessions by canonical language and converts each group to intervals.
     *
     * <p>Only languages the vocabulary recognizes and does not classify as {@code Other} appear:
     * that is what keeps the board key space bounded by the vocabulary instead of by whatever
     * strings clients submit. An unrecognized value is still stored, still counted in the
     * distribution, and still reported for classification — it simply has no board until someone
     * classifies it.
     *
     * <p>Built once per recompute and shared, like the other views: the work is the same as one
     * pass over the sessions because the groups partition them.
     *
     * @param sessions live sessions
     * @param utc the aggregation zone
     * @return canonical language name to that language's intervals
     */
    private Map<String, List<StatsCalculator.TimeInterval>> intervalsByCanonicalLanguage(
            List<CodingSession> sessions, ZoneOffset utc) {
        Map<String, List<CodingSession>> grouped = new LinkedHashMap<>();
        for (CodingSession session : sessions) {
            CanonicalLanguage language = languageVocabulary.normalize(session.getLanguage());
            if (!language.recognized() || language.type() == LanguageType.OTHER) {
                continue;
            }
            grouped.computeIfAbsent(language.name(), _ -> new ArrayList<>()).add(session);
        }
        Map<String, List<StatsCalculator.TimeInterval>> byLanguage = new LinkedHashMap<>();
        grouped.forEach(
                (language, ofLanguage) ->
                        byLanguage.put(language, StatsCalculator.toIntervals(ofLanguage, utc)));
        return byLanguage;
    }

    private String key(
            LeaderboardDimension dimension,
            LeaderboardPeriod period,
            LocalDate today,
            String language) {
        String base = KEY_PREFIX + dimension.name().toLowerCase();
        if (language != null) {
            base = base + ":" + language;
        }
        return base + period.keySuffix(today);
    }

    private static Duration ttlFor(LeaderboardPeriod period) {
        return switch (period) {
            case ALL -> null;
            case WEEK -> Duration.ofDays(8);
            case MONTH -> Duration.ofDays(32);
            case YEAR -> Duration.ofDays(370);
        };
    }
}
