# leaderboard — principles

## 1. One rank rule, computed once, used everywhere

Competition ranking is the only rank this API reports: tied scores share a rank, and the next
distinct score resumes after the gap (`1, 2, 2, 4`). Every rank in a response — `entries[].rank`
and `currentUserRank` — must come from this same rule.

The failure this prevents is a response that contradicts itself: `entries[].rank` derived from a
page-local tie counter while `currentUserRank` came from the member's physical position
(`reverseRank + 1`, which breaks ties by member id). A user could appear at `rank = 2` in the list
and be told `currentUserRank = 4`, with no way to know which was true.

**Corollary**: never derive a rank from `offset`. `offset` is a position in a page; a rank is a
property of the score. When a page opens in the middle of a tie, `offset + 1` reports a rank that
does not exist.

## 2. A rank is `count(strictly better) + 1`

The definition above is computed directly in Redis: `ZCOUNT(key, nextUp(score), +inf) + 1`. `nextUp`
turns the inclusive lower bound into a strict one, excluding equal scores from the "better" count —
which is exactly what makes ties share a rank.

This is O(log n) per distinct score on the page and needs no full scan. `reverseRank` is not used:
it answers a different question (position after member-id tie-breaking) and disagrees with this rule.

## 3. The period is part of the dimension's contract

A dimension declares which periods it supports, and the same enum owns the default
(`defaultPeriod()`). Keeping both in one place makes "the default must always be legal for its
dimension" true by construction rather than by convention.

The judgements behind the matrix:

- **A measurement can be windowed; a run cannot.** `TOTAL`, `NIGHT_OWL`, `EARLY_BIRD`, `ACTIVE_DAYS`
  are quantities accumulated inside a window, so every period is meaningful. `STREAK` is a run
  length, and every period window is shorter than the runs it rewards — ranking it weekly is
  incoherent, so it is `ALL` only.
- **A comparison needs a predecessor.** `GROWTH` compares a window against the one before it; an
  unbounded history (`ALL`) has no predecessor, so `GROWTH` excludes `ALL`.
- **Do not lock a capability that already generalizes.** `GROWTH`'s delta is the same
  `periodsSeconds(..., 0) - periodsSeconds(..., 1)` for any period, and the time-window dimensions
  already accepted `periodStart`/`periodEnd` — the old code passed `MIN`..tomorrow and called it
  `ALL`. Widening those windows was exposing existing capability, not building new math.

## 4. A partitioned dimension has no single board

`LANGUAGE` is the one dimension with no board of its own: it is one board per language, chosen by the
caller. Two consequences follow, and both are deliberate.

The key space is bounded by the **vocabulary**, not by client input. Only languages the vocabulary
recognizes and does not classify as `Other` get a board; an unrecognized value is stored, counted in
the distribution and reported for classification, but has no board until someone classifies it.
Without that rule the board count would grow with whatever strings clients submit — the property
that makes a per-language key worth having is the same one that forces the bound.

A language argument is validated rather than ignored: a required parameter that is missing, or an
optional one supplied to a dimension that has no use for it, is a `400`. Silently ignoring it would
answer a different question than the caller asked, and silently defaulting it would read a board
nobody writes.

## 5. A catalogue answers whichever question its caller asked

`GET /leaderboard/languages` offers both readings, because both are legitimate: the boards that hold
someone (the default) and every language the vocabulary knows (`includeEmpty=true`).

The default is the smaller list because the endpoint's consumer is a selector. The vocabulary is
large and the set of languages anyone has pushed is small, so a list of the vocabulary buries the
boards that can be opened under hundreds that answer with an empty page. The larger list stays
available because "which boards exist at all" is a different and also valid question, and every entry
carries `hasMembers` when it is asked.

The defect this replaced was **not** that the list came from activity — it was that the index it came
from was neither complete nor accurate. It was written only when a user pushed, so a language nobody
had pushed since the dimension shipped was missing while its board answered `200`; and it was never
pruned, so a language whose last member had gone was still offered. The index is now maintained at
both ends for that reason: a language enters when someone is ranked in it and leaves when its last
member stops coding in it.

Nothing is lost by defaulting to the smaller list, because an empty board and a language outside the
vocabulary were never the same statement — the board endpoint already separates them on its own (an
empty page against a `400`).

## 6. Score units differ per dimension; never assume seconds

`TOTAL`, `NIGHT_OWL`, `EARLY_BIRD` yield seconds. `ACTIVE_DAYS` yields a **count of distinct days**.
`STREAK` yields a **count of consecutive days**. `GROWTH` yields a **signed seconds delta** and may
be negative. A client rendering "score" without knowing the dimension will mislabel it; the
dimension is the unit.

## 7. Ties have no defined order — and that is a decision, not an oversight

Equal scores are ordered by Redis member id (a UUID), so the order is **stable but meaningless**: it
does not change between requests, and it does not mean anything either.

A deterministic, meaningful order (e.g. whoever reached the score first) would require encoding the
achievement time into the score itself, making the stored value no longer the readable measurement
the dimension promises. The cost outweighs the benefit, so tied entries are documented as unordered
rather than given a ranking they cannot support.

## 8. Recompute on push, and write every legal key

A user's scores are recomputed from the database after a successful session push, so a new session
appears in the ranking without a full rebuild. The recompute covers **every legal (dimension,
period) pair**, not just the ones a client happens to request — a ranking that is only correct for
previously-requested keys is a latent wrong answer.

Because the same session views feed every pair, the expensive work (interval merge and day split) is
built once per recompute and shared. Cost then scales with the user's history, not with the number
of keys.

## 9. A score computed under old rules does not notice that it is stale

Scores are written when a user pushes, so a change to the rules leaves every user who has not pushed
since holding numbers that are wrong while looking healthy — and a dimension added since their last
push has no number at all. For a new dimension that is every existing user, and the board then reads
as empty for reasons unrelated to the data.

Recomputation is therefore a first-class step of any scoring change, not an operational afterthought,
and it is guarded by a marker whose key names both the rule generation and the vocabulary version:
the generation covers a changed formula, the version covers canonical names shifting underneath the
scores. Keeping them in the key rather than in a comment means a needed sweep cannot be skipped by
forgetting to bump something. The marker lives in Redis because the scores do, so losing both
together restores the work instead of hiding a gap.

## 10. Key naming must not orphan existing data

`ALL` appends an empty key suffix, so `leaderboard:total` stays byte-identical to the key already
written by earlier versions. Introduced suffixed keys (`:week:<date>`) are new, but no existing
member is left behind in a key the service no longer reads. Redis persists across deployments, so
key changes are data migrations whether or not they are treated as such.

## 11. A size is not inferable from a page

`totalParticipants` (ZCARD) is reported because a client cannot derive the ranking size from the
entries: a full page means "there may be more", and a short page means "this is the end" only for
that key. "Rank N of M" requires M explicitly. It is a primitive `long`, because the response
configuration drops null fields and a wrapper type would omit the key exactly when the value is 0.

## 12. A ranking holds who is ranked now

`LANGUAGE` is the only conditional dimension: every other dimension writes a score for a user whether
or not they have time in the window, while a language board is written only while the user still has
sessions in it. A recompute that writes the current set and nothing else therefore leaves the
previous set behind — a user whose last session in a language is deleted stays ranked in it, at the
score it had, and nothing removes them: the service issued no `ZREM`.

The recompute now records the languages it ranked the user in and drops the difference on the next
run. The record is a string per user rather than a set, because "ranked in nothing" is a state worth
recording: it is what keeps a user with no rankable language from re-deriving that fact on every
push. A user has no record on their first recompute, so the board index answers for them instead —
which is also what makes the fix clean up entries that predate it, one user at a time, as they next
push.

The index is maintained in the same step: a language leaves it when its last member does, so the
catalogue describes the boards that exist rather than the boards that ever did.

The deletion is not the only path here, which is why the fix sits in the recompute rather than at the
deletion: a session whose language changes strands the old language's score just as completely, and
the recompute is the one place that sees the user's data as it is now.
