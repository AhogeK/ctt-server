# sync-protocol — meta

## Boundary

The bidirectional sync engine served under `/api/v1/sync/**` plus the device registration that
gates it: conflict resolution, the change log, per-device cursors, paging, push atomicity, and the
plugin-side contract those endpoints imply.

**In scope**: LWW rules, idempotency, cursors and watermarks, paging, batch atomicity, device/key
binding, downstream side effects of a push (materialization, leaderboard, achievement cache).

**Out of scope**: authentication mechanics (`auth-lifecycle`), how aggregated numbers are computed
once the data lands (`stats-aggregation`), plugin-side storage (`../code-time-tracker`, read-only).

## Owned paths

- `src/main/java/com/ahogek/cttserver/sync/` — controller, services, entities, DTOs, repositories
- `src/main/java/com/ahogek/cttserver/device/` — registration, revocation, key binding
- `src/main/java/com/ahogek/cttserver/common/config/properties/SyncProperties.java` — paging config

## Where to start

1. `sync/service/ConflictResolver.java` — pure LWW decision logic; no Spring, no DB.
2. `sync/service/SyncPushService.java` — batch routing + side effects.
3. `sync/service/SyncPullService.java` — cursor math and paging.
4. `sync/controller/SyncController.java` — scopes, rate limits, request shapes.
5. `device/service/DeviceService.java` — the registration prerequisite.

## Terminology

| Term | Meaning |
| --- | --- |
| **LWW** | Last-write-wins: the newer state of a session replaces the older one |
| **Change log** | `session_changes`, an append-only per-user log with a monotonic `change_id` |
| **Watermark** | Per-device `last_pulled_change_id` persisted server-side, monotonically advancing |
| **Cursor** | The `change_id` a device sends/receives to resume pulling from |
| **Page** | One pull response, bounded by `pull-batch-size`; `hasMore` flags another page |
| **Origin device** | The device that first pushed a session; stamped once, never rewritten |
| **Last-writing device** | The device of the most recent accepted mutation |
| **Soft delete** | `is_deleted = true` with `deleted_at`; rows are never physically removed by sync |
| **BOLA** | Broken object-level authorization: another user's device/key must not be reachable |
