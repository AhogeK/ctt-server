# stats-aggregation — scenarios

Trigger → judgement → action.

## Adding a new stats endpoint

| Trigger | Judgement | Action |
| --- | --- | --- |
| New read-only stats endpoint | What does it measure — time axis or category? | Time axis → merge + apportion; category → raw accumulation + apportion |
| Endpoint accepts `timezoneOffset` | Socket bucketing must follow the caller | `@Min(-720) @Max(720)`, default 0, convert via the shared `zoneOffset(minutes)` helper |
| Endpoint accepts `deviceId` / `ideName` | Filters are mutually exclusive | Build a `SessionFilter`; the canonical constructor raises `COMMON_003` on both-set |
| Endpoint accepts `start`/`end` | Inclusive local dates, open bounds = full history | Validate `end < start → COMMON_003`; clip with `clipSessions` |
| Error docs for a sibling-shaped endpoint | Sibling endpoints with identical params must list the same `@ApiResponses` codes | Copy the sibling's block rather than inventing a subset |

## Adding a distribution type

| Trigger | Judgement | Action |
| --- | --- | --- |
| New `DistributionType` value | Is the bucket derived from session fields, or from a registry? | Session fields → `accumulateBy`; registry labels (devices/IDEs) → `aggregateByLabel` |
| Bucket needs a completeness guarantee | Does the caller expect bucket sums to match the overview? | Only for time-axis dimensions; add the apportion + equality test |
| Bucket boundaries change | Are achievements / leaderboard windows affected? | No — they own independent windows (early bird 06:00–09:00, night owl 22:00–05:00); never couple them |

## Adding an option list (filters, pickers)

| Trigger | Judgement | Action |
| --- | --- | --- |
| "Which X have data?" endpoint | The list must never offer an empty selection | Derive from `activeYearMonths`-style predicates over merged per-day seconds, never SQL `EXTRACT` |
| Picker feeds a panel | Year, month, and panel must agree | Share one resolver between the year and month lists so drift is structurally impossible |
| Empty user | Consistent contract | Return `[]`, not `null` and not an error |

## Diagnosing a "totals disagree" report

| Trigger | Judgement | Action |
| --- | --- | --- |
| Panel total ≠ overview total | Is the panel time-axis or category? | Category → expected (principle 1); time axis → real defect |
| Time-axis mismatch of seconds (not minutes/hours) | Smells like truncation or apportionment | Check per-item `toSeconds()`; check parts-vs-whole apportionment |
| Two endpoints disagree by hours | Compare data sources and request params first | Materialized vs live, and whether the caller sent the same `timezoneOffset`/filters |
| Numbers change after a restart | Old jar may still be running | Confirm the running build before re-diagnosing the math |

## Changing materialization

| Trigger | Judgement | Action |
| --- | --- | --- |
| New column / new granularity in `daily_stats` | Existing migrations are immutable | New migration per R22; never edit an applied file |
| Read path changed | Live and materialized must stay interchangeable | Re-run the UTC-vs-zone equality tests; add one if absent |
