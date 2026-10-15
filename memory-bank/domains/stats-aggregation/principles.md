# stats-aggregation — principles

Invariants and first principles. When two options conflict, these decide.

## 1. Time-axis dimensions conserve time; category dimensions do not

- **Time-axis** (summary, heatmap, streaks, `TIME_OF_DAY`): buckets partition wall-clock time, so
  concurrent sessions are the *same* activity. Merge overlaps. Bucket sums equal `summary.total`.
- **Category** (languages, projects, weekday, devices, IDEs): buckets label *who/what* contributed.
  Two languages in one second are two real contributions. Raw accumulation is correct, and
  `sum(entries) >= summary.total` — the excess is parallel work, not error.
- **Consequence**: never "fix" a category dimension so its sum matches the overview, and never let
  a category dimension silently merge. The dimension's question decides its semantics.

## 2. Truncate once, never per slice or per session

Every duration enters a bucket at full precision (nanosecond `Duration`); conversion to whole
seconds happens **once**, when a bucket is emitted. Flooring earlier drops each item's sub-second
tail — the plugin writes milliseconds, so a typical history loses ~0.45s per session (measured:
1471s over 3460 sessions). This bug shipped twice (per-slice, then per-session); treat any
`toSeconds()` inside a loop as a defect.

## 3. Timezone first, then truncate

Bucketing runs on sessions shifted into the caller's `ZoneOffset`. A session stored
`2026-08-31T16:30Z` belongs to September for a UTC+8 caller. Deriving a month/year directly from
stored UTC values in SQL produces option lists whose entries render empty.

## 4. Parts sum to the whole for time-axis output

When several buckets are emitted from one truncated total (e.g. the four time-of-day buckets),
apportion the remainder (largest remainder) so `sum(parts) == truncated_total` exactly — for every
user, zone, and future data set. This is a construction guarantee, not a tolerance. Keep a test
asserting equality so a future semantic drift fails CI.

## 5. A valid session is `is_deleted = false AND start_time < end_time`

Zero-duration and inverted rows are skipped everywhere, never counted as a data point. Option lists
(zero-day months, empty days) must not exist because a row was degenerate.

## 6. Option lists derive from the same rule as the surface they feed

Heatmap year/month options are "months or years containing at least one **non-zero** local day" —
the same predicate the heatmap renders. A session crossing a month or year boundary contributes to
both. Judging by `startTime`'s month, or by raw `> 0` duration before truncation, desynchronizes the
picker from the panel.

## 7. Materialized reads must be indistinguishable from live reads

`daily_stats` is an optimization, never a second truth. UTC + no device/IDE filter + bootstrapped →
materialized; anything else → live aggregation. Source selection is `canUseMaterializedDays`; any
change that makes the two disagree is a defect even if no test fails.

## 8. Plugin parity, with the server as tie-breaker

The plugin's own statistics define display semantics (bucket boundaries, which dimensions slice).
When the plugin's math is wrong, the server keeps the correct semantics and the divergence becomes a
plugin bug report — never a silent server-side regression to match a bug. Boundaries currently
mirrored: Night 00:00–05:59, Morning 06:00–11:59, Daytime 12:00–17:59, Evening 18:00–23:59.

## 9. A lazily-written row is not a history

`user_achievements` rows are written by `evaluate`, which runs only when `GET /achievements` is
called (`SyncPushService` evicts the cache, it does not evaluate). So the table records **the periods
the user happened to open the page**, not the periods they attained — a weekly badge met every week
and viewed once has exactly one row.

Any "how many times / how many in a row" question must therefore be answered by **recomputing from
`coding_sessions`**, not by counting rows. Union the stored rows in as well: a period whose sessions
were later soft-deleted must still count, so an attained badge is never revoked (same monotonicity
rule as `achievement_progress`).

**Generalization**: before treating a derived table as a complete record, find what *writes* it and
when. Lazy/on-demand writers produce gaps that look like data.

## 10. A comparison is stated against a closed period, from the same source as the value

`summary.comparisons` measures each metric against the **previous complete period** — yesterday,
last week, last month, last year — not the same period to date. A change against a partial baseline
drifts as the current period fills and reads as noise, so each previous window is a bounded
half-open range `[previousStart, currentStart)`.

Four consequences that are easy to get wrong:

- **The previous value must come from the same primitive as the value it compares.** Materialized
  path: sum the `daily_stats` rows inside the window. Live path: cut the window out of the *same*
  merged interval list. Mixing primitives (say per-day truncated sums against a once-truncated
  current value) is principle 2 broken across two values, and it is invisible at the second level.
- **Each rule has one enforcing site.** The wire rules — a zero previous period yielding `null`,
  and percent rounded to two decimals half away from zero — live in `StatsComparison.of`; the
  `dailyAverage` baseline lives in `StatsCalculator.previousDailyAverage`. Both source paths call
  them instead of re-deriving, which is what keeps the materialized and live answers comparable.
- **`dailyAverage` has no period window.** It compares against itself as it stood at the end of
  yesterday — `(total − today) / (daysElapsed − 1)` — and has no baseline at all on the first coding
  day (0, which the wire format renders as a null change, never as −100%).
- **A zero previous period means no change can be stated**, not "infinite growth" and not −100%:
  the DTO emits `null`. `current == 0, previous > 0` is the case that legitimately reports −100%.
- **Cross-path equality is bounded by the materialization granularity, not a goal.** The materialized
  path sums per-day truncated `merged_seconds`; the live path truncates the window sum once. For a
  window of ≥2 days holding sub-second (ms-tail) sessions the two can therefore differ by up to the
  number of days in the window — bounded, known, and older than the comparisons themselves (the
  current week/month/year sums have always had it). It cannot be removed without storing remainders
  in `daily_stats`, and it is **not** a bug to "fix" by moving one path onto the other's primitive:
  that would break the value the comparison is stated against. Single-day windows (today, and the
  `dailyAverage` baseline) are exact in both paths.

## 11. An hourly average's denominator is "days with coding", and its numerator is one accumulation

`activeDays` counts local days holding at least one whole second of coding — **not** days on which a
session merely *started*. A session crossing local midnight codes on two local days and belongs to
both; counting start days (what the code did until 2026-10) silently inflates every average for
users with late-night sessions.

`hourly` and `hourly-days` must never slice independently. `StatsCalculator.hourlyByDay` is the single
accumulator: `hourlyDistribution` builds its 24 hour-sums from the same per-day buckets
`hourlySecondsByDay` returns, and the 31-day cap is applied only at the edge of the per-day public
method, never inside the derivation. Summing `hourly-days` hour by hour and dividing by `activeDays`
therefore reproduces the average curve **by construction** — an invariant the integration test
asserts instead of assuming. The same call returns the window's active-day count alongside the
truncated series (`DayHourlyWindow`), so the denominator never needs a second pass over the sessions.

**Basis**: `StatsCalculator.hourlyByDay` / `hourlySecondsByDay`, `StatsService.hourlyDays`,
`StatsIntegrationTest.shouldReturnHourlyDays_reproducingHourlyCurve` (2026-10-15, v0.79.0).
