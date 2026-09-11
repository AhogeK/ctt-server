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
| `/api/v1/stats/week-hour` | zone, start, end, deviceId, ideName | READ | 60/min |
| `/api/v1/stats/recent` | limit, deviceId, ideName | READ | 60/min |
| `/api/v1/stats/heatmap-years` | zone | READ | 60/min |
| `/api/v1/stats/heatmap-months` | zone | READ | 60/min |
| `/api/v1/stats/ide-filters` | — | READ | 60/min |
| `/api/v1/stats/achievements` | zone | READ | 60/min |

`zone` = `timezoneOffset` minutes east of UTC (−720..720, default 0). `heatmap`/`hourly`/
`distribution`/`week-hour` dates are inclusive ISO `yyyy-MM-dd`; omitted bounds = full history.

## Dimensions

| `DistributionType` | Family | Source |
| --- | --- | --- |
| `LANGUAGES`, `PROJECTS` | category | session fields, raw durations |
| `WEEKDAY` | category | session start weekday (ISO name) |
| `TIME_OF_DAY` | **time axis** | merged intervals sliced at bucket boundaries |
| `DEVICES`, `IDES` | category | device registry labels (`Unknown device` / `Unknown IDE` fallbacks) |

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
