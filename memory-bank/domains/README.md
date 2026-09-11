# Domain Knowledge Map — ctt-server

Structured, domain-first knowledge base for this project. Governed by **AGENTS.md R25**.

## Two layers, different jobs

| Layer | Location | Answers | Nature |
| --- | --- | --- | --- |
| Timeline | `memory-bank/*.md` | "what is happening / just changed" | Chronological, superseded by time |
| **Domain (this tree)** | `memory-bank/domains/<domain>/` | "what is true and what to do here" | Durable, judged, never a changelog |

Cross-cutting conventions (naming, package-by-feature, UTC-first, error envelope, test style)
stay in `systemPatterns.md`. Domain files hold the **domain-specific judgement** and must not
duplicate it — link instead.

## Domains

| Domain | Scope | Entry point |
| --- | --- | --- |
| [`stats-aggregation`](./stats-aggregation/meta.md) | `/api/v1/stats/**`: duration semantics (merge vs accumulate), timezone bucketing, window clipping, materialized daily rows, option lists (years/months/filters), plugin parity | `stats-aggregation/meta.md` |
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

Rules of growth: build a domain only when reusable knowledge exists (few and real beats many and
empty); file the knowledge in the right domain rather than in the timeline; keep this index in
sync with the directories.
