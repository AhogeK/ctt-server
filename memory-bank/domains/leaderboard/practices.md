# leaderboard — practices

## The rank rule, in code

```java
// count of members strictly better than this score, +1. nextUp excludes equal scores so ties tie.
private long rankFor(double score, String key) {
    return zSet.count(key, Math.nextUp(score), Double.POSITIVE_INFINITY) + 1;
}
```

Building a page: the **first** entry's rank comes from `rankFor`; each later entry keeps the previous
rank when its score equals the previous score, otherwise takes `offset + i + 1` (its absolute
position, which is already gap-correct once the page is not opening mid-tie).

```java
long rank = rankFor(topScore, key);          // page head: absolute, handles opening mid-tie
for (int i = 0; i < tuples.size(); i++) {
    if (i > 0) rank = (score == previousScore) ? rank : offset + i + 1L;
    previousScore = score;
    entries.add(new LeaderboardEntryDto(userId, displayName, (long) score, rank));
}
```

`currentUserRank` uses the same helper, from the user's own score:

```java
Double score = zSet.score(key, userId.toString());
Long rank = score == null ? null : rankFor(score, key);   // null ⇒ not ranked ⇒ key omitted
```

Never use `reverseRank` for a public rank, and never `offset + 1` for a page head.

## The period helpers

`LeaderboardPeriod` owns the boundary arithmetic so no caller re-derives it:

| Method | Returns |
| --- | --- |
| `start(LocalDate)` / `endExclusive(LocalDate)` | Window bounds, or `null` for `ALL` |
| `keySuffix(LocalDate)` | `""` for `ALL`; `":week:<ISO Monday>"`, `":month:<1st>"`, `":year:<Jan 1>"` otherwise |

Weeks are ISO (Monday start) via `date.with(DayOfWeek.MONDAY)`, which keeps week-bucketing consistent
across year boundaries. `WEEK`/`MONTH`/`YEAR` shift with `plusWeeks`/`plusMonths`/`plusYears`, never
by adding a day count (`GROWTH` compares adjacent windows, so a 30-day "month" would drift).

For an unbounded dimension use `periodStartOrMin` / `periodEndOrMax` rather than hand-writing
`OffsetDateTime.MIN`/`MAX` at each call site.

## Sharing the expensive work

`buildViews(sessions, today)` produces a `SessionViews` record — intervals, per-day seconds, lifetime
total — and `recomputeAndWriteAll` builds it **once** before looping the 20 legal pairs. Interval
merging and day splitting are `O(n log n)`; rebuilding per pair makes cost scale with key count.

```java
SessionViews views = buildViews(sessions, today);
for (LeaderboardDimension d : LeaderboardDimension.values())
    for (LeaderboardPeriod p : LeaderboardPeriod.values()) {
        if (!d.supports(p)) continue;          // the enum decides, not this loop
        redisTemplate.opsForZSet().add(key(d, p, today), userId.toString(), computeScore(views, d, p));
        Duration ttl = ttlFor(p);
        if (ttl != null) redisTemplate.expire(key(d, p, today), ttl);
    }
```

`computeScore(views, dimension, period)` is a `switch` over dimension; `ALL` short-circuits to the
lifetime values already in `views` rather than re-scanning.

## Test shapes that actually catch rank bugs

Two tests are load-bearing and must not be reduced to "it returns something":

- **Ties and the gap** — feed `[100, 90, 90, 80]` and assert `containsExactly(1L, 2L, 2L, 4L)`. A
  naive tie counter yields `1, 2, 2, 5`, which this catches.
- **Page head mid-tie** — mock the page starting at `offset = 2` with the top score tied above it and
  assert the head's rank is `2`, not `3`. This is the exact defect the fix removed; keep it
  reproducible by reverting `rankFor` to `offset + 1` and watching it fail.

When stubbing, the argument must be the **same expression** production computes:
`Math.nextUp(10800.0)`, not `10801.0` — Mockito matches on the value, and an approximation silently
stubs nothing.

## Integration-test traps

- **The ranking is global and Redis is shared across the suite.** Absolute member counts and "who is
  `entries[0]`" are not stable. Assert invariants instead: size ≥ the users you ranked, the caller's
  rank ≥ 1, a dimension's unit (days vs seconds), the legal/illegal matrix by status.
- **The suite runs on the real clock.** Test sessions dated into a fixed past window will fall
  outside `WEEK`/`MONTH` depending on the day the suite runs. Either use `ALL`, or anchor data to
  `LocalDate.now()` as the stats tests do.
- **Drive the compatibility matrix from the enum**, not from a hand-written list, so the test cannot
  drift from the rule the controller enforces:

```java
for (LeaderboardDimension d : LeaderboardDimension.values())
    for (LeaderboardPeriod p : LeaderboardPeriod.values())
        assertThat(mvc.get().uri("...dimension=" + d + "&period=" + p).exchange())
            .hasStatus(d.supports(p) ? 200 : 400);
```

## Traps

- `Score` is stored as a `double` (ZSet requirement) but exposed as `long`; a `GROWTH` score may be
  negative, which `@Schema(example=...)` should reflect rather than implying a non-negative count.
- `totalParticipants` must be the ZSet's own size for **one** key. A count summed across keys is a
  different number and wrong.
- Lock release in this service historically used `redisTemplate.delete(lockKey)`, bypassing
  `RedisLockService.release()`. It is functionally equivalent today but diverges from the
  documented "always release in a finally block" contract — align it if the lock code is touched.
