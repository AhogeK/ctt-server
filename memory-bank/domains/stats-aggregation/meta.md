# stats-aggregation — meta

## Boundary

All coding-session aggregation served under `/api/v1/stats/**`: period summaries, daily heatmaps,
streaks, dimension distributions, hourly and weekday-hour heatmaps, option lists for filters, and
the materialized per-day rows those reads are served from.

**In scope**: duration semantics, timezone bucketing, window clipping, bucket boundaries,
denominators/averages, existence rules for option lists, materialization contract, plugin parity.

**Out of scope**: leaderboard scoring (`leaderboard/` aggregates its own dimensions),
achievement definitions (thresholds live in `stats/achievement/` but read the same calculators),
sync ingestion (`sync-protocol` domain).

## Owned paths

- `src/main/java/com/ahogek/cttserver/stats/` — controller, service, pure calculator, DTOs, enums
- `src/main/java/com/ahogek/cttserver/stats/materialization/` — daily rows + bootstrapping
- `src/main/java/com/ahogek/cttserver/sync/repository/CodingSessionRepository.java` — the session
  queries stats reads from

## Where to start

1. `stats/service/StatsCalculator.java` — pure domain math, no Spring/DB. Almost every rule in
   `principles.md` is enforced here.
2. `stats/service/StatsService.java` — source selection (materialized vs live), filters, option lists.
3. `stats/controller/StatsController.java` — request params, scopes, rate limits, OpenAPI.
4. `stats/materialization/service/DailyStatsMaterializer.java` — when/who rebuilds daily rows.

## Terminology

| Term | Meaning |
| --- | --- |
| **Merged / union** | Overlapping intervals collapsed to non-overlapping ones; concurrent sessions count once |
| **Raw accumulation** | Each session contributes its own duration; two overlapping sessions count twice (intended for category dimensions) |
| **Time-axis dimension** | A dimension whose buckets partition wall-clock time (summary, heatmap, streaks, TIME_OF_DAY) |
| **Category dimension** | A dimension that labels each session (languages, projects, devices, IDEs, weekday) |
| **Apportionment** | Largest-remainder distribution of a truncated total across buckets so the parts sum to the whole |
| **Window clipping** | Clamping sessions to an inclusive local-date range before aggregating |
| **Active day** | A local day with at least one second of coding (the average denominator) |
| **Materialized day** | A `daily_stats` row: per-user per-UTC-day merged seconds, incrementally maintained on push |
| **Zone** | The caller's `timezoneOffset` (minutes east of UTC, −720..720); all bucketing happens after shifting sessions into it |
