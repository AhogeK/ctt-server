# sync-protocol — references

## Endpoints

| Endpoint | Request | Response | Scope | Rate |
| --- | --- | --- | --- | --- |
| `POST /api/v1/sync/pull` | `{deviceId, lastPulledChangeId}` | `{changes[], nextCursor, hasMore}` | SYNC | 120/min |
| `POST /api/v1/sync/push` | `{deviceId, sessions[]}` | `{nextCursor}` | SYNC | 120/min |
| `POST /api/v1/devices` | `{deviceId, deviceName, platform, ideName?}` | device row | SYNC | 10/hour per user |
| `GET /api/v1/devices` | — | device list | READ or SYNC | — |
| `DELETE /api/v1/devices/{deviceId}` | — | 204 | WRITE | — |

## Change DTO fields

| Field | Meaning |
| --- | --- |
| `changeId` | Monotonic log id (also the cursor unit) |
| `sessionId` | Server primary key of the affected session |
| `sessionUuid` | Client-generated identity used for local matching; `null` when the row was physically removed |
| `op` | `UPSERT` or `DELETE` |
| `serverVersion` | Session version after the change |
| `happenedAt` | Change timestamp (ISO-8601) |
| `projectName`, `language`, `startTime`, `endTime`, `clientModifiedAt`, `clientVersion`, `deleted` | Winning session snapshot |

## Session fields relevant to sync

| Column | Role |
| --- | --- |
| `session_uuid` | Client identity; unique per user (`uk_coding_sessions_user_session_uuid`) |
| `client_version`, `client_modified_at` | Client-side LWW inputs |
| `server_version` | Server-assigned, bumped on each accepted change |
| `origin_device_id` | First pusher (device-scoped stats filter on this) |
| `updated_by_device_id` | Last writer |
| `is_deleted`, `deleted_at` | Soft delete state |

## Config keys

| Key | Default | Constraint |
| --- | --- | --- |
| `ctt.sync.pull-batch-size` | 1000 | `@Min(1) @Max(10_000)`; env `SYNC_PULL_BATCH_SIZE` |

## Error codes

| Scenario | Code | HTTP |
| --- | --- | --- |
| Device unknown / foreign / revoked | `COMMON_002` | 404 |
| Device id already owned by another user | `DEVICE_001` | 409 |
| Missing SYNC scope | `AUTH_020` | 403 |
| Rate limit exceeded | `RATE_LIMIT_001` | 429 |

## Audit events

`SYNC_PULL`, `SYNC_PUSH` on resource type `CODING_SESSION`; success and failure both recorded
(failure carries the error-code name).

## Code map

| Concern | File |
| --- | --- |
| LWW decision | `sync/service/ConflictResolver.java` |
| Pull / cursor / paging | `sync/service/SyncPullService.java` |
| Push / batch writes / side effects | `sync/service/SyncPushService.java` |
| Cursor atomic upsert | `sync/repository/SyncCursorRepository.java` |
| Change-log queries | `sync/repository/SessionChangeRepository.java` |
| Session queries (incl. soft-deleted lookup) | `sync/repository/CodingSessionRepository.java` |
| Device registration / revocation | `device/service/DeviceService.java` |
| Paging config | `common/config/properties/SyncProperties.java` |
