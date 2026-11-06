# sync-protocol — references

## Endpoints

| Endpoint | Request | Response | Scope | Rate |
| --- | --- | --- | --- | --- |
| `POST /api/v1/sync/pull` | `{deviceId, lastPulledChangeId, replayFromChangeId?}` | `{changes[], nextCursor, hasMore}` | SYNC | 600/min per account |
| `POST /api/v1/sync/push` | `{deviceId, sessions[]}`; each session may carry `ideName` (≤100) and `ideVersion` (≤50) | `{nextCursor}` | SYNC | 120/min per account |
| `POST /api/v1/devices` | `{deviceId, deviceName, platform, ideName?, ideVersion?, appVersion?}` | device row (submitted values) | SYNC | 10/hour per user |
| `GET /api/v1/devices` | — | device list; `ideName`/`ideVersion` = most recently used IDE, registration as fallback | READ or SYNC | — |
| `DELETE /api/v1/devices/{deviceId}` | — | 204 | WRITE | — |

## Cursor contract

| Question | Answer |
| --- | --- |
| Where does a plain pull read from? | `max(persisted watermark, client cursor)` — a stale client cannot rewind the server |
| Where does a replaying pull read from? | `replayFromChangeId`, capped by the watermark — the client's own position is authoritative |
| What is `nextCursor`? | The end of the page actually delivered, i.e. the client's resume point; never the watermark |
| Where does monotonicity live? | In the upsert (`GREATEST(last_pulled_change_id, excluded)`), not in the response |
| Who sends the replay field? | The plugin, on every pull, equal to its persisted cursor; a server without the field answers `400` and the plugin falls back for an hour |

## Change DTO fields

| Field | Meaning |
| --- | --- |
| `changeId` | Monotonic log id (also the cursor unit) |
| `sessionId` | Server primary key of the affected session |
| `sessionUuid` | Client-generated identity used for local matching; `null` when the row was physically removed |
| `op` | `UPSERT` or `DELETE` |
| `serverVersion` | Session version after the change |
| `happenedAt` | Change timestamp (ISO-8601) |
| `projectName`, `language`, `startTime`, `endTime`, `clientModifiedAt`, `clientVersion`, `ideName`, `ideVersion`, `deleted` | Winning session snapshot (`ideName`/`ideVersion` are null when the row has none) |

## Session fields relevant to sync

| Column | Role |
| --- | --- |
| `session_uuid` | Client identity; unique per user (`uk_coding_sessions_user_session_uuid`) |
| `client_version`, `client_modified_at` | Client-side LWW inputs |
| `server_version` | Server-assigned, bumped on each accepted change |
| `origin_device_id` | First pusher (device-scoped stats filter on this) |
| `updated_by_device_id` | Last writer |
| `is_deleted`, `deleted_at` | Soft delete state |
| `ide_name`, `ide_version` | IDE that produced the session (nullable; blank normalized to null; not part of LWW content identity) |

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

`SYNC_PULL`, `SYNC_PUSH` on resource type `CODING_SESSION`; **failures only** (the failure carries
the error-code name). Success moved to the application log on 2026-11-05: at 406–540 pulls/day/device
the success rows were 90% of `audit_logs` and answered "is the plugin alive", which the device
list already reports via `lastSeenAt`.

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
| Leaderboard recompute queue | `leaderboard/service/LeaderboardRecomputeQueue.java` |
| Instance-wide request ceiling | `common/ratelimit/core/GlobalRequestGuard.java` |

## 容量实测（2026-11-05，生产库只读测量 + `EXPLAIN ANALYZE`）

口径：`psql` 直查部署机的生产库 `ctt_server`（主机地址不入库，见私密运维记录）；研究全文见 `.omp/research/2026-11-05/sync-capacity-study.md`。

| 事实 | 数值 |
| --- | --- |
| 最重真实用户 | 3,729 会话 / 258 天 = **14.45 会话/天**，214 活跃日，34 种语言 |
| `coding_sessions` 单行成本 | 199 B 行 + 798 B 索引 ≈ **997 B**（索引占 80%） |
| `session_changes` 单行成本 | **≈ 305 B**（只存 id/op/version，无内容） |
| `audit_logs` 单行成本 | **≈ 380 B**；29,737 行里 90% 是 `SYNC_PULL` + `API_KEY_USED` |
| 真实轮询频率 | **406–540 次/天/设备**（插件默认 5 分钟，因多 IDE 共享安装级 device id 而翻倍） |
| 真实推送频率 | 1–8 次/天，每批约 14 条 |
| 单条变更 JSON | **实测 506 B**（DTO 推算 380–430 B）⇒ 1000 条/页 ≈ 0.5 MB；服务端自 2026-11-05 起按请求协商 gzip（json/plain，≥2 KB），插件 0.22.0 起发送 `Accept-Encoding` 并自行解压，字节量按上表计 |
| 拉取一页耗时 | **2.7–5.9 ms**（1001 条 join）；`(user_id, change_id)` 复合索引实测命中 |
| 排行榜全量加载（3,729 行） | SQL **2.7 ms**（Java 侧水合 + 区间合并才是大头） |
| 全局限流桶（**已移除 2026-11-05**） | 曾为 `rate_limit:api:SyncController.pull` / `…push` = 120/分钟、无用户维度 ⇒ 约 320–430 台设备即触顶；现为 pull `USER` 600/分钟、push `USER` 120/分钟 + `GlobalRequestGuard` 每实例 200/s |
| 20 年外推（每用户） | 会话 ≈105 MB + 变更 ≈32 MB；审计按收口后的增量计（见下行），旧的「2.7 GB/用户」推算已随写入收敛失效 |
| 审计增量（2026-11-06 实测） | 29,749 行 / **11 MB**（68 天）；89% 是已停写的 `SYNC_PULL` + `API_KEY_USED`。收口后按**每活跃 key 至多 24 行/天**（`API_KEY_USED` 每小时 1 行）计，另加少量认证事件：当前库合计 ≈28 行/天（≈4 MB/年），300 个活跃 key ≈**1 GB/年** ⇒ 不做清理或分区，复查阈值：`audit_logs` >1 GB 或审计查询变慢 |

无查询支撑的索引（占 `coding_sessions` 索引空间约 47%）已于 2026-11-05 删除（迁移 `V20261105120000`）：`idx_sessions_user_project_time`、`idx_sessions_user_language_time`、`idx_sessions_sync_lookup`；同批删除 `sync_cursors.last_push_at`（无读写方）与仅测试调用的 server-version 仓库方法。
