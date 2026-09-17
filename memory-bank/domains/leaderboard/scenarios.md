# leaderboard — scenarios

Trigger → judgement → action.

## Changing how a rank is computed

| Trigger | Judgement | Action |
| --- | --- | --- |
| A rank looks wrong on a page past the first | Is it derived from `offset`? | `offset` is a page position, never a rank. Use `rankFor(score, key)` = `count(strictly better) + 1` |
| Ties must share a rank | Equal scores must not count as "better" | Lower bound is `Math.nextUp(score)`, not `score` |
| `currentUserRank` disagrees with `entries[].rank` | Two rules exist | Find the second one (`reverseRank` was the historical offender) and route it through the same `rankFor` |
| Need an ordering *between* tied members | This is the tie-breaker question | Do not add one ad hoc. Encoding time into score changes the stored value's meaning — treat as a contract decision, weigh against the readable-score guarantee |

## Adding or widening a dimension/period

| Trigger | Judgement | Action |
| --- | --- | --- |
| New dimension proposed | Does the score have a unit distinct from existing ones? Is it reachable by users with modest volume? | `ACTIVE_DAYS` was added because every prior dimension rewarded accumulated volume, which structurally favors long-tenured users |
| Widening a dimension to more periods | Does a windowed value still mean something? | Measurements can be windowed (`TOTAL`/`NIGHT_OWL`/`EARLY_BIRD`/`ACTIVE_DAYS`); a run cannot (`STREAK` stays `ALL`); a comparison needs a predecessor (`GROWTH` excludes `ALL`) |
| A dimension gains/loses a period | The legal set and the default are decided in one place | Update `supports()` and check `defaultPeriod()` still returns a legal value — they live together for this reason |
| Legal combination rejected at runtime | `supports()` and the controller disagree | The controller must not re-derive the rule; call `dimension.supports(period)` / `dimension.defaultPeriod()` |
| Key set changes | Old keys must not orphan | `keySuffix()` returns `""` for `ALL` so existing keys stay byte-identical; a *changed* suffix is a data migration |

## Changing the response shape

| Trigger | Judgement | Action |
| --- | --- | --- |
| Adding a field | Is it additive and non-breaking? | Add it; document whether it is always present |
| Field is a count that can be 0 | `non_null` inclusion drops the key | Use a primitive (`long totalParticipants`), never a wrapper, or the key vanishes exactly at 0 |
| Field is a list index or position | Indexes are page-local | Never expose a page-local index as a rank; see `entries[].rank` |
| A client wants "N of M" | M is not derivable from a page | Provide the size explicitly (`totalParticipants` via ZCARD) |

## Writing scores

| Trigger | Judgement | Action |
| --- | --- | --- |
| A session is pushed | The ranking must reflect it without a rebuild | `recomputeAndWriteAll` runs on push, covering every legal pair |
| Multiple keys need the same underlying data | Rebuilding per pair repeats the expensive work | Build `SessionViews` once per recompute (intervals, per-day seconds, lifetime total) and share it |
| A key's window closes | It must not accumulate stale members forever | `ttlFor(period)` expires period keys; `ALL` never expires |
| A user stops coding in a language (their session is deleted, or its language changes) | `LANGUAGE` is the only conditional dimension — the board was written, so it must be unwritten | The recompute removes the difference against `leaderboard:user:languages:<id>`, and the index drops the language if no board holds anyone. Do not fix this at the deletion site: a changed language strands the old board just as completely, and the recompute is the one place that sees the data as it is now |
| A language's board shows a member who no longer codes in it | `expected: 0L but was: 1L` is the symptom | That is the strand above; `shouldDropUserFromLanguageBoard_whenTheirLastSessionIsDeleted` pins it end to end |
| A user's account is deleted | Their entry should leave the ranking | `LeaderboardService.removeUserFromRankings`, called by `DELETE /api/v1/users/me` after the transaction commits. A recompute cannot do it: a deleted account never pushes again, so nothing would ever rewrite its entries. It keeps only ids the `users` table still holds, and prunes a language from the index once no board holds anyone |

## Perceiving a bug from the outside

| Trigger | Judgement | Action |
| --- | --- | --- |
| Duplicate ranks appear out of order (`1, 2, 2, 5` vs `1, 2, 2, 4`) | Off-by-one in how the gap is skipped | The rule is `count(strictly better) + 1`; verify against `[100,90,90,80] → 1,2,2,4` |
| Rank is correct on page 1, wrong after paging | Page-position leakage | Reproduce with `offset` landing mid-tie: `[100,90,90,80]` at `offset=2` must yield `2,4`, not `3,4` (the wider the tie, the larger the drift — `[100,90,90,90,80]` at `offset=3` must yield `2,5`, not `4,5`) |
| First entry of a later page shows rank `offset+1` | The original defect | `shouldReportGlobalRank_whenPageStartsMidRanking` pins this; keep it |
