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
