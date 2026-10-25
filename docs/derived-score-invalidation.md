# Derived Score Invalidation

Some values in this service are derived from data that already lives elsewhere, and are written
ahead of reads rather than computed on every request. When the rule behind such a value changes, the
stored copies do not notice: they keep answering confidently with numbers produced by the old rule.

Two things are derived today:

| Derived value | Where it lives | Written by |
| --- | --- | --- |
| Leaderboard scores | Redis ZSets, one key per legal (dimension, period) pair | every successful sync push, plus a startup backfill |
| Daily statistics | `daily_stats` rows, one per user per UTC day | every successful sync push, plus a first-read bootstrap |

## The leaderboard marker

The startup backfill is guarded by a Redis key:

```
leaderboard:recompute:g{ruleGeneration}:cov{fingerprint}:vocab{vocabularyVersion}
```

| Component | Covers | Moves on its own? |
| --- | --- | --- |
| `g{ruleGeneration}` | a changed formula, or a change to which sessions count | **no — bump it by hand** |
| `cov{fingerprint}` | a dimension appearing, or a dimension gaining a period | **yes** — derived from `LeaderboardDimension.values() × supports()`, hashed |
| `vocab{version}` | canonical language names changing underneath the scores | **yes** — the vocabulary owns its version |

A marker exists for exactly one combination of those three. A missing marker means "the scores were
produced by older inputs, or are absent", and the next start recomputes every user. A run that fails
part-way still writes the marker, so the sweep is not repeated daily; the users it missed are
recomputed on their next push.

## Checklist for a scoring change

1. **Added a dimension, or opened one to more periods?** Nothing to bump — the fingerprint changes
   with the enums. Confirm with a unit test that the marker differs from the previous format.
2. **Changed a formula, a threshold, which sessions count, or which scores are written at all?**
   Increment `SCORE_RULE_GENERATION` in `LeaderboardScoreBackfill`. The recompute is the first-class
   step, not an operational afterthought. Excluding zero-score members, for example, is a write-rule
   change: the sweep is what removes the members already stored at `0`.
3. **Changed the language vocabulary?** Nothing to bump — its version is already in the key.
4. **Deploy**, then wait for `ctt.leaderboard.backfill-initial-delay-ms` (default 60 s) before
   checking boards.

## Verification after a deploy

The backfill is done when every windowed board covers the lifetime board it derives from:

```bash
ZCARD leaderboard:streak
ZCARD leaderboard:streak:month:<first day of month>
ZCARD leaderboard:streak:quarter:<first day of quarter>
ZCARD leaderboard:streak:half-year:<first day of half year>
ZCARD leaderboard:streak:year:<Jan 1>
```

The windowed counts must equal the lifetime count. A windowed board that is empty while the
lifetime one is populated is the signature of a skipped recompute, not of missing data: a missing
ZSet answers `200` with an empty page, so clients render an empty state rather than an error.

## Why the fingerprint exists

Release 0.85.0 opened `STREAK` to every period but left the generation at 2. The marker written by
0.84.0 still matched, the sweep returned immediately, and every user who had not pushed since kept a
lifetime score with no windowed score — visible as four empty boards and no error anywhere. The
fingerprint was added so that "a dimension or period appeared" invalidates the marker structurally,
leaving only formula changes to human memory.
