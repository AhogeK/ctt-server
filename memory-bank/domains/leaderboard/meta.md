# leaderboard — meta

## Boundary

Everything under `/api/v1/leaderboard` and the Redis ZSets that back it: how a score is measured for
each dimension, how rank is derived, how period windows bucket into keys, and what the response
promises a client about ordering and scale.

**In scope**: rank semantics, dimension definitions and their score units, dimension/period
compatibility, key naming and TTL, pagination correctness, write path (when scores are recomputed),
response fields that clients use to place a rank in context.

**Out of scope**: the session aggregation primitives it borrows (`StatsCalculator.toIntervals`,
`mergeOverlapping`, `mergedSecondsByDay`, `mergedDurationInDailyWindow`, `streaks` — owned by
`stats-aggregation`), achievement thresholds (`stats/achievement/`, which reads the same
calculators), sync ingestion (`sync-protocol`).

## Owned paths

- `src/main/java/com/ahogek/cttserver/leaderboard/`
  - `service/LeaderboardService.java` — score computation, rank derivation, key management
  - `controller/LeaderboardController.java` — params, scopes, rate limit, OpenAPI
  - `enums/LeaderboardDimension.java` — dimensions **and** their legal periods + default period
  - `enums/LeaderboardPeriod.java` — window boundaries, key suffixes, TTL
  - `dto/LeaderboardResponse.java`, `dto/LeaderboardEntryDto.java` — the response contract

## Terminology

| Term | Meaning here |
| --- | --- |
| Dimension | What is being measured (`TOTAL`, `STREAK`, `NIGHT_OWL`, `EARLY_BIRD`, `GROWTH`, `ACTIVE_DAYS`) |
| Period | The window the measurement is taken over (`ALL`, `WEEK`, `MONTH`, `YEAR`) |
| Competition rank | Ties share a rank; the next distinct score skips the gap (1, 2, 2, 4) |
| Physical position | 0-based index inside the ZSet, ties broken by member id — **not** the public rank |
| Score | The raw measured value stored in the ZSet, in the dimension's own unit (seconds, days, or a signed delta) |
| Legal pair | A (dimension, period) combination the enum admits — 20 of the 24 possible |

## Where to start

1. `enums/LeaderboardDimension.java` — `supports()` and `defaultPeriod()`. The legal matrix and the
   default live together so an omitted period can never select one the same dimension rejects.
2. `service/LeaderboardService.java` — `computeScore` (one dimension's math), `rankFor` (the single
   rank rule), `key` (bucketing), `recomputeAndWriteAll` (the write path).
3. `dto/LeaderboardResponse.java` — what a client receives, including `totalParticipants`.

## Why this is not part of stats-aggregation

`stats-aggregation` measures *one user's* coding over a window and serves it directly. The
leaderboard measures *many users* and then **orders** them — ranking, tie handling, key bucketing
by period, and the write-on-push path are judgements the stats domain explicitly excludes. The two
share the calculators and nothing else.
