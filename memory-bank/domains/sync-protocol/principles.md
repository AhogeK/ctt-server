# sync-protocol — principles

## 1. Conflict resolution is a fixed priority chain

Evaluated in order, first match wins:

1. **Delete wins** — a soft-deleted state beats a live one. Both deleted → fall through to version rules.
2. **Server version** — both sides carry a server version (`> 0`) → higher wins (replay/merge).
3. **Client version** — equal server versions, or either side is a fresh submission (`serverVersion == 0`) → higher client version wins.
4. **Client modified at** — client versions equal → later `clientModifiedAt` wins.

Before the version rules, two **live** states with identical business content (`projectName`,
`language`, `startTime`, `endTime`) resolve to `KEEP_EXISTING` — a re-push of unchanged content is an
idempotent no-op even when version or clock metadata drifted. Deleted states are exempt so
delete-version competition keeps its meaning.

## 2. The client never rewinds the server

The effective query cursor is `max(persisted watermark, client cursor)`. A stale client cannot make
the server re-deliver changes it already considers consumed, and a fresh device resumes from its own
last-known position.

## 3. Push is atomic per request

A batch applies in one transaction: either the whole batch lands (sessions + change-log entries) or
nothing does. Partial application must never be observable. This is why the client can mark its
batch synced on a 2xx and why a failure is safely retryable.

## 4. Idempotency over exactly-once

- Re-pushing identical content is a no-op (principle 1).
- Deleting a session the server never had is a no-op.
- A pull with no new changes returns an empty list and the current cursor.
- Repeating any request must not corrupt state; the protocol optimizes for safe retry, not for
  suppressing duplicates by tracking client intent.

## 5. Origin and last-writer are different facts

`origin_device_id` records **who first created** the session (stamped on create, never rewritten);
`updated_by_device_id` records who wrote last. Device-scoped statistics must use origin, because
last-writer drifts to whichever device edited the row. Backfills approximate origin as the
last writer for legacy rows.

## 6. Device registration is the authorization prerequisite

A device must be registered by a SYNC-scoped key before pull/push succeed; registration binds the
key to the device. Unknown, foreign, or revoked devices answer **404 `COMMON_002`** — never 403 —
so device existence is not leaked across accounts.

## 7. Every accepted mutation is observable to other devices

Each write appends exactly one change-log entry; the entry count and the session mutations must agree
within a transaction. A write that changes no state (keep-existing, no-op delete) appends nothing, so
downstream devices are not woken for nothing.

## 8. The server watermark is authoritative but optimistic

The watermark advances when a page is **generated**, not when the client confirms applying it. A
client that crashes after receiving a page can therefore miss it for that device; the session data
still exists server-side and reconverges on the next write to those sessions. This is accepted, not
ideal — a client-confirmed watermark would make it at-least-once. Paging shrinks the exposure from
"whole response" to "one page".
