# stats-aggregation — references

Facts to look up. No judgement here.

## Endpoints

| Endpoint | Params | Scope | Rate |
| --- | --- | --- | --- |
| `/api/v1/stats/summary` | zone, deviceId, ideName | READ | 60/min |
| `/api/v1/stats/heatmap` | zone, start, end, deviceId, ideName | READ | 60/min |
| `/api/v1/stats/streaks` | zone, deviceId, ideName | READ | 60/min |
| `/api/v1/stats/distribution` | type, zone, start, end, deviceId, ideName | READ | 60/min |
| `/api/v1/stats/hourly` | zone, start, end, deviceId, ideName | READ | 60/min |
| `/api/v1/stats/hourly-days` | zone, start, end, deviceId, ideName | READ | 60/min |
| `/api/v1/stats/week-hour` | zone, start, end, deviceId, ideName | READ | 60/min |
| `/api/v1/stats/recent` | limit, deviceId, ideName | READ | 60/min |
| `/api/v1/stats/heatmap-years` | zone | READ | 60/min |
| `/api/v1/stats/heatmap-months` | zone | READ | 60/min |
| `/api/v1/stats/ide-filters` | — | READ | 60/min |
| `/api/v1/stats/achievements` | zone | READ | 60/min |

`zone` = `timezoneOffset` minutes east of UTC (−720..720, default 0). `heatmap`/`hourly`/
`hourly-days`/`distribution`/`week-hour` dates are inclusive ISO `yyyy-MM-dd`; omitted bounds =
full history. `hourly-days` returns at most the **31 most recent** active days, oldest first, while
its `activeDays` still counts every coded day in the window (so it matches `hourly`).

`ideName` is matched per session (`coding_sessions.ide_name`), falling back to the origin device's
registered IDE for rows that carry none; `ide-filters` returns the union of both sources. `deviceId`
and `ideName` are mutually exclusive.

## Duration semantics per surface

Two semantics coexist. Which one a surface uses is deliberate, and mixing them silently changes what
a number means — read this table before adding or interpreting a surface.

| Semantics | Surfaces |
| --- | --- |
| **Merged** — overlapping intervals are unioned first | `summary`, `heatmap`, `streaks`, `distribution?type=TIME_OF_DAY`, `week-hour` |
| **Raw per session** — each session contributes its own duration | `distribution?type=LANGUAGES` / `PROJECTS` / `WEEKDAY` / `DEVICES` / `IDES` (category labels via `aggregateByLabel`), `hourly`, `hourly-days` |

`timeOfDayDistribution` is merged while `weekdayDistribution` is raw, and `weekHourDistribution` is
merged while `weekdayDistribution` is not: near-identical names are the most common source of
misreads. `StatsCalculator`'s class Javadoc and the README keep the same list — update all three
together.

## Summary response shape

| Field | Meaning |
| --- | --- |
| `today` / `thisWeek` / `thisMonth` / `thisYear` | merged seconds in the current local day / ISO week (Monday start) / calendar month / calendar year |
| `dailyAverage` | lifetime total ÷ days from the first coding day to today, inclusive |
| `total` | lifetime merged seconds — has **no** comparison |
| `comparisons.{today,dailyAverage,thisWeek,thisMonth,thisYear}` | `previousSeconds` + `changePercent` (two decimals, or `null`) |

All values are whole seconds; `dailyAverage`'s `previousSeconds` is seconds **per day**. The five
comparison keys are always present (`total` has none), and `changePercent` is `null` exactly when
`previousSeconds` is 0 — the two sources being an empty previous period and the first coding day for
`dailyAverage`. The field opts out of the global `non_null` policy so the null key survives; see
`api-contract/practices.md`.

## Dimensions

| `DistributionType` | Family | Source |
| --- | --- | --- |
| `LANGUAGES`, `PROJECTS` | category | session fields, raw durations |
| `WEEKDAY` | category | session start weekday (ISO name) |
| `TIME_OF_DAY` | **time axis** | merged intervals sliced at bucket boundaries |
| `DEVICES`, `IDES` | category | device registry labels; `IDES` prefers the session's own `ide_name` and falls back to the origin device's registration (`Unknown IDE` when neither exists) |

## Time-of-day buckets (plugin-aligned)

| Bucket | Local hours |
| --- | --- |
| `NIGHT` | 00:00–05:59 |
| `MORNING` | 06:00–11:59 |
| `DAYTIME` | 12:00–17:59 |
| `EVENING` | 18:00–23:59 |

Separate windows owned elsewhere (do not couple): achievement/leaderboard early bird 06:00–09:00,
night owl 22:00–05:00.

## Config keys

| Key | Default | Meaning |
| --- | --- | --- |
| `ctt.sync.pull-batch-size` | 1000 | (sync domain) page ceiling for pull |
| `ctt.security.password.*` | 5 / 900s / 30m | (auth domain) lockout thresholds |

No stats-specific config keys: bucket boundaries and window semantics are code constants.

## Tables and indexes

- `coding_sessions` — the only source; validity rule `is_deleted = false AND start_time < end_time`
- `idx_sessions_user_time (user_id, start_time, end_time)` — partial index backing per-user range reads
- `daily_stats (user_id, utc_date)` — materialized merged seconds per user per **UTC** day, plus a
  `bootstrapped` marker column

## Code map

| Concern | File |
| --- | --- |
| Pure math (merge, slice, clip, apportion, existence) | `stats/service/StatsCalculator.java` |
| Source selection, filters, option lists, averages | `stats/service/StatsService.java` |
| Request params, scopes, rate limits, OpenAPI | `stats/controller/StatsController.java` |
| Daily rows maintenance + lazy bootstrap | `stats/materialization/service/DailyStatsMaterializer.java` |
| Time-of-day boundaries + slicing helper | `stats/enums/TimeOfDay.java` |
| Distribution dimensions | `stats/enums/DistributionType.java` |
| Session queries | `sync/repository/CodingSessionRepository.java` |

## Language vocabulary contract

| | |
| --- | --- |
| File | `src/main/resources/language/vocabulary.json` |
| Shape | `version` · `canonical` (name → Linguist category) · `aliases` (raw lowercase → canonical) · `nonLanguages` (raw lowercase) |
| Current | **v2: 842 canonical / 489 aliases / 76 nonLanguages** — `canonical` is the whole Linguist set plus local extensions, not a subset derived from the IDEs installed somewhere |
| Consumers | Server read paths (`StatsCalculator.languageDistribution`) and **copies vendored by both IDE plugins** for their local statistics views |

**The file is a published contract, not an internal table.** Both plugins hold a byte-identical
copy and have no way to notice the server's copy changing, so:

- **Every change to the file bumps `version`.** A new alias, a new canonical name or a new
  non-language entry is a change; a whitespace-only edit is not.
- **Announce it when the version changes** — state the old and new version and what was added, so
  the plugin maintainers can refresh, re-check the checksum and re-run their vocabulary tests.
- Verify a vendored copy by checksum (`shasum -a 256`) rather than by reading the JSON.

Drift is graceful rather than fatal: a stale copy keeps working and keeps storing raw values, but a
newly added alias will not merge in the plugin's local view until the copy is refreshed — the local
view and the web view disagree until then. The server is unaffected because it reads its own file.
