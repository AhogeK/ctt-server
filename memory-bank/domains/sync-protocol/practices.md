# sync-protocol — practices

## Pull: fetch one extra row to answer "is there more"

```java
List<SessionChange> fetched = repo.findAfter(queryCursor, userId, Limit.of(pullBatchSize + 1));
boolean hasMore = fetched.size() > pullBatchSize;
List<SessionChange> page = hasMore ? fetched.subList(0, pullBatchSize) : fetched;
long nextCursor = page.isEmpty() ? queryCursor : Math.max(page.getLast().getChangeId(), clientCursor);
repo.advancePullWatermark(userId, deviceId, nextCursor);
```

One query answers both "the page" and "is there a next page" — no `COUNT` round trip. The client
loops while `hasMore` is true, persisting the cursor after each applied page.

**Trap**: advancing the watermark before returning means a client that fails to apply a page cannot
re-request it (principle 8). Do not "fix" that by letting the client rewind the watermark — the
monotonic guard exists to stop re-delivery storms. The real fix would be client-confirmed cursors.

## Push: route, then write in three batches

1. Load existing rows **including soft-deleted ones** (`findAllByUserIdAndSessionUuidIn`) — a
   live-only lookup makes a re-push of a soft-deleted session collide with the unique constraint.
2. Route each DTO through `ConflictResolver`; collect creates, updates, and the matching change drafts.
3. Persist: one multi-row `INSERT` for creates, `saveAll` for updates, one multi-row `INSERT` for
   `session_changes` — all inside the same transaction.

**Trap**: `saveAll` on creates. Hibernate batching and PG's `reWriteBatchedInserts` still degrade to
one single-row statement per row for these statements; the hand-written multi-row insert is what
avoids the round-trip storm.

## Device registration

`POST /api/v1/devices` with a SYNC key: upsert the row, bind the key (`api_keys.device_id`), audit.
Re-registering the same id clears `revoked_at` (the client's self-heal path after a 404).

**Trap**: client-provided primary keys need a specific JPA mapping — see
[`systemPatterns.md`](../../systemPatterns.md) 的「客户端分配 ID 实体模式」章节（横切持久层规范，
此处不重复）。

## Failure-tolerant side effects

Materialization refresh, achievement-cache eviction, and leaderboard recomputation run **after** the
accepted batch and swallow their own failures — the next push self-heals. Wrap them so a Redis or
materialization hiccup can never roll back accepted session data.

## Test recipe

1. Push → pull increments → pull again returns empty with the same cursor (idempotent).
2. Same session pushed twice with different versions → exactly one row, the winner's fields.
3. Push a delete for a session the server never had → no-op, no change-log entry.
4. Paging: set a small `pull-batch-size`, push more than one page, drain with `hasMore` and assert no
   duplicates, ascending ids, and a final cursor equal to the push cursor.
5. Device boundaries: foreign/revoked device → 404 `COMMON_002` on both endpoints.

## A sync is device activity, and the device record has to say so

`devices.last_seen_at` is documented as the last activity from that device, but for a long time only
registration wrote it. The plugin syncs every round and the field stayed at the registration day, so
the device page reported a machine that pushes daily as inactive after seven days.

Both `pull` and `push` now call `DeviceRepository.touchLastSeen(userId, deviceId, now)` right after
the ownership and revocation checks:

- **Pull matters as much as push.** A round with nothing to push still pulls, so the heartbeat has to
  hang off pull; hanging it off push alone leaves an idle-but-syncing device looking abandoned.
- **One statement, not a save.** `Device` carries `@Version`; a read-modify-write from two syncs of
  the same installation (the device id is shared by every plugin in that install) would raise an
  optimistic-lock failure for a heartbeat with no business meaning. The bulk update also leaves the
  version untouched, so a heartbeat cannot invalidate a concurrent registration.
- **Revoked devices do not advance.** The update's predicate excludes `revokedAt is not null`, and
  the caller has already answered 404 for them; a revoked device stays visibly revoked.
- **Only successful requests count.** The touch runs inside the service transaction, so a push that
  fails later rolls the heartbeat back with everything else.
