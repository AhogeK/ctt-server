# stats-aggregation — practices

Concrete how-to. Includes traps and why they bite.

## Accumulate at full precision, emit once

```java
Map<String, Duration> byBucket = new LinkedHashMap<>();
for (TimeInterval interval : intervals) {
    byBucket.merge(label, Duration.between(cursor, sliceEnd), Duration::plus);  // never .toSeconds() here
}
return apportion(byBucket);   // floors per bucket and hands the remainder to the largest fractions
```

**Trap**: `Duration.between(a, b).toSeconds()` inside the accumulation loop. Sub-second tails vanish
once per item. Symptom: panel totals a few hundred seconds below the overview on real data, and
exactly correct on whole-second fixtures — which is why fixtures must include fractional starts.

## Window clipping two ways

| Need | Helper | Notes |
| --- | --- | --- |
| Clip session objects, keep identity (language/project/device) | `StatsCalculator.clipSessions(sessions, zone, start, end)` | Copies the session and clamps instants; drops emptied rows |
| Clip already-converted intervals | `StatsCalculator.clipToWindow(intervals, zone, start, end)` | Both bounds → `clipTo`; one bound → clamp per side and drop empties |

Both treat dates as inclusive and close the window at `end + 1 day`'s local midnight. A one-sided
window must clamp **before** constructing `TimeInterval`, because its canonical constructor rejects
`start >= end` — clamping after construction throws `DateTimeException` on sessions fully outside.

## Existence predicates for option lists

```java
public static List<YearMonth> activeYearMonths(List<CodingSession> sessions, ZoneOffset zone) {
    return mergedSecondsByDay(sessions, zone).entrySet().stream()
            .filter(e -> e.getValue() > 0)          // truncation-aware: matches what the chart renders
            .map(e -> YearMonth.from(e.getKey()))
            .distinct()
            .sorted(Comparator.reverseOrder())
            .toList();
}
```

`mergedSecondsByDay` is the same merge-and-split the heatmap uses, so the predicate cannot drift
from the rendering. `> 0` is deliberate — a sub-second day floors to zero and the chart shows
nothing there.

## Source selection

`canUseMaterializedDays(userId, zone, filter)` is the single gate: UTC **and** no device/IDE filter
**and** bootstrapped. Everything else aggregates live. Option lists should call the same gate so
years/months read from the same source as the heatmap they feed.

## Test recipe for precision and windows

1. A session with a **fractional** start (`.plusNanos(...)`) so truncation is exercised.
2. A session **outside** the window, to prove clipping (not just filtering by start).
3. A session **crossing** the window or bucket boundary, asserting both sides receive a share.
4. Sum the emitted buckets and assert equality with the merged total — the invariant, not just the
   individual numbers.
5. For regression guards: re-inject the old math and watch the test fail (red), then restore (green).
   A guard that has only ever passed pins nothing.

## Traps seen in practice

| Trap | Why it bites | Avoid |
| --- | --- | --- |
| `EXTRACT(YEAR FROM start_time)` in SQL | Untouched by `timezoneOffset`; misses boundary sessions and both-sides attribution | Derive in Java from merged local days |
| Replacing a large code block by line-marker | Markers drift; a slice can swallow neighbouring methods | Anchor on exact text and diff the boundary before/after |
| Asserting a value the fixture cannot express (e.g. 59-minute span as 3600s) | Red herrings that look like product bugs | Compute the expected seconds from the fixture |
| Comparing averages to totals | `hourly`/`week-hour` emit **averages** (divided by active days), not sums | Compare like with like |
