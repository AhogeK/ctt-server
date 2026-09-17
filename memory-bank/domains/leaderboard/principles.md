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

## 5. A catalogue is a vocabulary fact; membership is an activity fact

The language directory lists every language the vocabulary knows, and reports separately whether a
board has members. Deriving the list from activity instead was a real defect: the index is written
when a user's scores are recomputed, so a board for a language nobody had pushed since the dimension
existed answered `200` while the directory did not offer it. The endpoint could tell "unknown
language" from "no members" and the catalogue could not, which made the two disagree about one
question.

The same rule generalizes: anything that enumerates what a client may ask for is bounded by the
vocabulary, and anything that describes current usage is separate and optional. Mixing them produces
a list that is neither complete nor a faithful activity report.

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
