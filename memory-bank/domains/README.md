# Domain Knowledge Map — ctt-server

Structured, domain-first knowledge base for this project. Governed by **AGENTS.md R25** — that rule
carries the constraints; this file carries the operating procedure.

## Two layers, different jobs

| Layer | Location | Answers | Nature |
| --- | --- | --- | --- |
| Timeline | `memory-bank/*.md` | "what is happening / just changed" | Chronological, superseded by time |
| **Domain (this tree)** | `memory-bank/domains/<domain>/` | "what is true and what to do here" | Durable, judged, never a changelog |

Cross-cutting conventions (naming, package-by-feature, UTC-first, error envelope, test style)
stay in `systemPatterns.md`. Domain files hold the **domain-specific judgement** and must not
duplicate it — link instead.

**Why structure beats search here.** A fixed file set is a *coverage constraint*: it states what
must be understood before acting in this domain. Retrieval can tell you what looks related; it
cannot tell you that you are missing the constraint that decides the answer. Structure first,
search for what the structure does not yet cover.

## How a domain is read (progressive disclosure)

Do not load a whole domain tree. Each file answers one question, and the next file is chosen by the
previous answer — loading everything up front spends attention on knowledge the current judgement
does not need.

| Step | Read | To answer |
| --- | --- | --- |
| 1 | `meta.md` | Does this domain own the problem at all? What is it called here? |
| 2 | `scenarios.md` → `principles.md` | Which judgement applies; what decides when two options conflict |
| 3 | `practices.md` | How to actually do it — parameters, code shapes, traps |
| 4 | `references.md` | The facts: endpoints, codes, keys, paths |
| 5 | the source code | Whether those facts are still true (R25 回源) |

Steps 2–4 are not a fixed sequence. A contract question can start at `references.md`; a "why is it
like this" question starts at `practices.md`.

## Domains

| Domain | Scope | Entry point |
| --- | --- | --- |
| [`stats-aggregation`](./stats-aggregation/meta.md) | `/api/v1/stats/**`: duration semantics (merge vs accumulate), timezone bucketing, window clipping, materialized daily rows, option lists (years/months/filters), plugin parity | `stats-aggregation/meta.md` |
| [`leaderboard`](./leaderboard/meta.md) | `/api/v1/leaderboard`: rank semantics (competition ranking), dimension score units, dimension/period compatibility, Redis key bucketing & TTL, write-on-push recompute, response fields for placing a rank in context | `leaderboard/meta.md` |
| [`sync-protocol`](./sync-protocol/meta.md) | `/api/v1/sync/**`: LWW conflict resolution, change log & cursors, paging, push atomicity, device registration, plugin-side contract | `sync-protocol/meta.md` |
| [`api-contract`](./api-contract/meta.md) | Public API surface: response envelopes, error codes, scopes, rate limits, OpenAPI annotations, endpoint consistency | `api-contract/meta.md` |
| [`auth-lifecycle`](./auth-lifecycle/meta.md) | Identity flows: JWT/API-key authentication, OAuth login & binding, email verification, password reset/change, terms acceptance, account lockout | `auth-lifecycle/meta.md` |

## File set inside every domain (AGENTS.md R25 — build it filled, never as a stub)

| File | Holds | Read it when |
| --- | --- | --- |
| `meta.md` | Boundary, owned paths, terminology, where to start | You do not yet know whether this domain applies |
| `principles.md` | Invariants and first principles — the tie-breakers | Two options conflict and you need the deciding rule |
| `scenarios.md` | Trigger → judgement → action | A familiar-looking problem appears |
| `practices.md` | Concrete how-to, parameters, code shapes, traps | You are about to write the code |
| `references.md` | Contracts, endpoints, codes, config keys, paths | You need a fact, not a judgement |

A file you cannot fill honestly means the domain is not ready to exist — leave it unbuilt rather
than creating a stub.

## Maintenance

**Incremental — triggered by a change.** When a contract or structure moves (API signature, DB
semantics, state machine, error code, config key), update the domain files that describe it, in the
same change. Automation's job is to notice the change, surface the affected files and block the
omission — never to rewrite the knowledge. A code change does not by itself redefine what a field
means; for high-risk knowledge the semantic confirmation is human (R25).

**Calibration — triggered by reflection.** After a large refactor, an incident review, or a release
retrospective, read the domain files against the code and check for drift. Drift is found by
looking, not by waiting for someone to trip over stale guidance.

**Drift handling.** When a domain file and the code disagree, decide which one is the fact source
*before* editing either:

| Situation | Action |
| --- | --- |
| Code is the current behaviour; the domain file is stale | Fix the domain file |
| The domain file records an intended rule the code violates | That is a defect — report it; do not "fix" the knowledge to match the code |
| Neither is clearly authoritative (intent unclear) | Mark the claim **待确认** with what would settle it; do not silently pick one |

**Verification baseline.** Each `meta.md` states when the domain was last checked against the code
and at which version, so a reader can judge how much to trust it before re-verifying.

**Index integrity.** This file is the map; a map that disagrees with the territory is worse than no
map. After adding, renaming or removing a domain, update the table above in the same change.

## Decisions already made — do not relitigate

| Question | Decision | Why |
| --- | --- | --- |
| Co-locate with the code, or a separate repo? | **Co-located** (`memory-bank/` is git-tracked) | Knowledge must move in the same commit as the change it describes (R5/R6.5); an agent working in the repo reads it with no extra tooling or sync step |
| Markdown or YAML? | **Markdown** | These files are read by a human *and* an agent; markdown tables already carry the structure, and at this corpus size (5 domains) the density argument for YAML does not pay for the loss of human readability. Revisit only if a large, machine-only corpus appears |
| Store each fact here, or link to it? | **Link** | Duplication is what makes knowledge drift; every fact gets one home |
| Structure first, or retrieval first? | **Structure first** | Retrieval finds what looks related; it cannot tell you which constraint you are missing. Search is for corroboration and long-tail discovery |
