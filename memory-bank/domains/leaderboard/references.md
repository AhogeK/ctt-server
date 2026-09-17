# leaderboard — references

## Endpoint

| | |
| --- | --- |
| Path | `GET /api/v1/leaderboard` |
| Scope | `READ` |
| Rate limit | `RATE_LIMIT_001`, 60 req/min |
| Errors | 400 `COMMON_003` (illegal dimension/period pair, bad `period` value), 401, 403, 429 |

## Parameters

| Param | Type | Default | Bounds |
| --- | --- | --- | --- |
| `dimension` | enum | required | `TOTAL` \| `STREAK` \| `NIGHT_OWL` \| `EARLY_BIRD` \| `GROWTH` \| `ACTIVE_DAYS` |
| `period` | enum | `dimension.defaultPeriod()` | `ALL` \| `WEEK` \| `MONTH` \| `YEAR` |
| `limit` | int | 20 | 1..100 |
| `offset` | int | 0 | ≥ 0 |

## Response

```json
{ "success": true, "message": "Operation successful",
  "data": {
    "entries": [ { "userId": "<uuid>", "displayName": "Alice", "score": 10800, "rank": 1 } ],
    "currentUserRank": 1,
    "totalParticipants": 42
  }, "timestamp": "<ISO-8601>" }
```

| Field | Type | Notes |
| --- | --- | --- |
| `entries[].userId` | string | UUID |
| `entries[].displayName` | string \| null | null when the user row is missing |
| `entries[].score` | long | Dimension's unit; may be negative for `GROWTH` |
| `entries[].rank` | long | Competition rank; ties share it, next skips the gap |
| `currentUserRank` | long \| null | null when the caller has no score in this key; key omitted |
| `totalParticipants` | long | ZCARD for this key; always present (primitive) |

## Dimension → score → supported periods

| Dimension | Score | Unit | `ALL` | `WEEK` | `MONTH` | `YEAR` | Default |
| --- | --- | --- | :-: | :-: | :-: | :-: | --- |
| `TOTAL` | Merged overlap-collapsed duration | seconds | ✓ | ✓ | ✓ | ✓ | `ALL` |
| `STREAK` | Longest consecutive coding-day streak | days | ✓ | — | — | — | `ALL` |
| `NIGHT_OWL` | Merged duration inside 22:00–05:00 | seconds | ✓ | ✓ | ✓ | ✓ | `ALL` |
| `EARLY_BIRD` | Merged duration inside 06:00–09:00 | seconds | ✓ | ✓ | ✓ | ✓ | `ALL` |
| `GROWTH` | Current period minus immediately preceding period | signed seconds | — | ✓ | ✓ | ✓ | `WEEK` |
| `ACTIVE_DAYS` | Distinct coding days | days | ✓ | ✓ | ✓ | ✓ | `ALL` |
| `LANGUAGE` | Merged duration in one language | seconds | ✓ | ✓ | ✓ | ✓ | `ALL` |

20 legal pairs of 24 possible for the non-partitioned dimensions; 15 carry a TTL (every pair
except the five `ALL` keys). `LANGUAGE` is partitioned: one board per language a user has used, so
its key count grows with the caller's languages rather than being fixed — and shrinks again when they
stop using one.

## Redis keys

| Key | Example |
| --- | --- |
| `leaderboard:<dimension>` | `leaderboard:total` (`ALL`) |
| `leaderboard:<dimension>:week:<ISO Monday>` | `leaderboard:total:week:2026-08-31` |
| `leaderboard:<dimension>:month:<1st>` | `leaderboard:total:month:2026-08-01` |
| `leaderboard:<dimension>:year:<Jan 1>` | `leaderboard:total:year:2026-01-01` |
| `leaderboard:language:<name>[<period suffix>]` | `leaderboard:language:Java:week:2026-08-31` |
| `leaderboard:lock:<userId>` | per-user recompute lock |
| `leaderboard:languages` | SET: languages that currently have a board |
| `leaderboard:user:languages:<userId>` | STRING: the languages that user is ranked in, `\n`-joined |

ZSet member = user UUID, score = the measured value. `ALL` keys never expire; period keys expire via
`ttlFor(period)`. The last two are bookkeeping rather than rankings: the index answers the directory,
the per-user string tells the next recompute which boards to remove the user from.

## Redis operations used

| Call | Purpose |
| --- | --- |
| `opsForZSet().add(key, member, score)` | write a score |
| `opsForZSet().reverseRangeWithScores(key, offset, offset + limit - 1)` | one page, high score first |
| `opsForZSet().count(key, min, max)` (`ZCOUNT`) | `rankFor` — count of strictly better scores |
| `opsForZSet().score(key, member)` (`ZSCORE`) | the caller's own score, for `currentUserRank` |
| `opsForZSet().size(key)` (`ZCARD`) | `totalParticipants`, and whether a board emptied |
| `opsForZSet().remove(key, member)` (`ZREM`) | drop a user from a board their data no longer covers |
| `opsForSet().add/remove("leaderboard:languages", …)` | keep the index at both ends |
| `opsForValue().get/set("leaderboard:user:languages:<id>")` | what the last recompute ranked the user in |

`reverseRank` exists in the API but is deliberately unused — it ranks by physical position with
member-id tie-breaking, which disagrees with competition ranking.

## Client recipes

| Goal | Use |
| --- | --- |
| "Rank 12 of 340" | `currentUserRank` + `totalParticipants` |
| Percentile | `(totalParticipants - currentUserRank) / totalParticipants` |
| Language selector | `/leaderboard/languages` (boards that hold someone); add `includeEmpty=true` to browse the vocabulary |
| Period leaderboard tabs | Same endpoint, `period=WEEK\|MONTH\|YEAR`; `STREAK` is `ALL`-only, `GROWTH` has no `ALL` |
| Consistency-flavored board | `ACTIVE_DAYS` (favors regular users over high-volume ones) |
| Detect end of list | Fewer than `limit` entries returned for a stable key (there is no `hasMore` field) |
