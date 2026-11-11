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
   Three decisions do **not** produce writes: `SKIP_UNCHANGED` (identical content, idempotent re-push) and
   `KEEP_EXISTING` (the client lost a race) leave the row untouched, and a matching delete removes nothing
   that is already deleted — all three still advance the cursor.
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

## IDE is session metadata, not a device attribute

A device id is installation-scoped: every JetBrains IDE on one machine shares it, so
`devices.ide_name` can only name whichever IDE registered last. The push payload therefore carries
`ideName`/`ideVersion` per session, stored in `coding_sessions`.

- **Optional and nullable.** Older plugins send neither; a new row stays null and readers fall back
  to the origin device's registration. Never reject a push because the IDE is missing.
- **An update only writes a reported IDE.** Absence means "this client cannot report it", not
  "unknown": a blank or missing value must not erase what a newer client stored, so
  `applyIncomingFields` sets the columns only when the incoming value is non-blank. A client that
  starts reporting one fills the field in on its next winning update.
- **Pull carries it.** `SyncChangeDto` includes the winning snapshot's `ideName`/`ideVersion` (null
  when the row has none), so another device builds its local copy with the source IDE instead of the
  pulling IDE.
- **Blank means absent.** `applyIncomingFields` normalizes whitespace-only values to null, otherwise
  an empty string becomes a bucket of its own in the IDE distribution and an entry in the filter list.
- **Not part of LWW content identity.** `ConflictResolver.sameContent` deliberately ignores the IDE:
  a session's IDE does not change over its life, and comparing it would turn every re-push from a
  plugin that started reporting the field into a modification, defeating the idempotent no-op path.
- **No backfill.** Old rows keep null and are attributed through the device registration; guessing an
  IDE for them would record a fact nobody observed.
- **The device list projects, it does not re-register.** `DeviceResponse.ideName`/`ideVersion` are the
  newest session's IDE (one `DISTINCT ON` query for the whole list), falling back to the registration
  values. Registration still answers with the values submitted in that call.

## 容量：每条路径的成本不是同一个量纲

2026-11-05 实测（见 `references.md` 的「容量实测」段与 `.omp/research/2026-11-05/sync-capacity-study.md`）：

- **Pull 是 O(页大小)，不是 O(历史)。** `session_changes(user_id, change_id)` 复合索引覆盖「用户 + 游标 + 排序」，实测 1001 条一页 2.7–5.9 ms。**不要为了「优化拉取」去改这个查询**，它已经不是瓶颈。
- **Push 曾是 O(用户全部历史)，这是会随年限变慢的那条路径——已移出写路径。** `SyncPushService.updateLeaderboard` → `LeaderboardService.recomputeAndWriteAll` → `findAllByUserIdAndIsDeletedFalse(userId)`，即每次 push 加载该用户**一生**的会话，重算 7 维度 × 6 周期（语言维度再乘语言数）并整体写 Redis，且在 push 事务内、持用户锁。**2026-11-05 改为脏标记 + 定时排空**：push 只做一次 `SADD leaderboard:dirty`（`afterCommit` 之后才写，避免排空读到未提交快照），`LeaderboardRecomputeQueue` 按 `ctt.leaderboard.recompute-drain-interval-ms`（默认 5 s）`SPOP` 批量重算；同一用户在一个排空周期内的 N 次 push 只重算一次。**陷阱仍在**：写路径上的派生值重算会让成本与用户生涯数据量成正比（10 万行 ≈ 100 MB，第 10 年单次 push 数百毫秒 + 上百 MB 堆），所以规则是「要么改增量/按天聚合，要么移出写路径」——本次选的是后者。
- **限流桶不是「防滥用」而是「部署规模上限」。** `RateLimitType.API` 的键只有端点路径 ⇒ `/sync/pull` 与 `/sync/push` 曾各有一个**全局** 120/分钟的桶：按实测轮询频率只支撑约 320–430 台设备，一台 20 年历史的新设备排空 106 页就能占满它一分钟。**已于 2026-11-05 修复**——`RateLimitType.API` 枚举值删除，pull/push 改为 `USER` 维度（600/120 每分钟，账户 = API key 属主），另加 `GlobalRequestGuard` 每实例全局限流（`global-max-requests-per-second`，默认 200）在维度桶之前兜底。端点级限流必须带用户/设备维度。
- **空轮询也不再写库（2026-11-06 收口）。** 每次 poll 原本 = API key SELECT+UPDATE、device 心跳 UPDATE、`sync_cursors` upsert、2 条审计 INSERT + 1 条 `API_KEY_USED`。**2026-11-05 起**：成功同步不再写审计（失败仍写）、`API_KEY_USED` 每 key 每小时至多 1 行、`last_used_at` 每小时至多 UPDATE 1 次。**2026-11-06 起**：游标没有推进就不 upsert（空轮询不产生行版本）、设备心跳在 `DeviceRepository.LAST_SEEN_WRITE_INTERVAL`（15 分钟）内不重写。剩余只有 API key SELECT——认证必须读那一行，缓存会让撤销延迟生效，不值得换。**陷阱的量化结论**：审计表实测 29,749 行 / 11 MB，其中 89% 是已停写的 `SYNC_PULL`+`API_KEY_USED`，现增量 ≈28 行/天（≈4 MB/年）⇒ **不做清理或分区**；「无结果的读请求尽量不落库」仍是要守的规则，但「20 年后 2.7 GB」的推算已随写入收敛失效。
- **压缩必须协商，不能单方面开。** 插件不发 `Accept-Encoding`，JDK `HttpClient` 也不自动解压 ⇒ 服务端若无条件 gzip，插件会把 gzip 字节当 JSON 解析失败。**2026-11-05 起**开启 Tomcat 协商压缩（`application/json`/`text/plain`，≥2 KB）：不发的客户端拿到原字节，浏览器按请求 opt-in。`ResponseCompressionProbeTest` 用真端口 + 插件同款客户端验证两条分支。
- **乐观 watermark 在长排空下会变成丢数据——插件已接 replay 闭合。** 服务端在交付页面**之前**就推进该设备的 watermark（同一事务），排空页数随历史线性增长（20 年 = 106 页），中途应用失败 ⇒ 该页对这台设备**永久不再重发**。这是容量问题直接转化为正确性问题。**2026-11-05 起** pull 接受可选 `replayFromChangeId`：读起点从该值开始（上限为 watermark），`nextCursor` 等于**实际下发页面的末尾**（不是 watermark），持久化 watermark 由 upsert 的 `GREATEST` 保持单调。**插件 0.22.0 已接入**：每轮 pull 都用本机持久化游标作重放点、每页应用后写游标，崩溃或应用失败丢的页会在下一轮被重读；遇到不识别该字段的老服务端回退为普通请求并按小时重探。
