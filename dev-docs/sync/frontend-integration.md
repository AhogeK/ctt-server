# 双向同步协议插件接入指南

本文档面向 JetBrains 插件（code-time-tracker）开发者，说明如何在客户端实现与 CTT Server 的双向同步（Pull / Push）协议。文档以插件侧可独立完成集成为目标，所有字段、示例值与错误码均与后端源码保持一致。

## 流程总览

双向同步协议用于多设备间的编码会话（coding session）数据同步。插件在本地维护会话数据，通过 Pull 拉取服务端变更，通过 Push 推送本地变更，冲突采用 LWW（Last-Write-Wins，后写优先）策略解决。每个设备维护独立的同步游标（cursor），服务端按游标增量下发变更。

```
插件启动 / 定时触发同步
        │
        ▼
POST /api/v1/sync/pull（携带 deviceId + lastPulledChangeId）
        │
        ▼
服务端返回 changes[]（按 changeId 升序）+ nextCursor
        │
        ▼
插件按 op 应用变更（UPSERT 覆盖本地 / DELETE 软删除本地）
        │
        ▼
插件将本地脏会话打包为 sessions[] 推送
        │
        ▼
POST /api/v1/sync/push（携带 deviceId + sessions[]）
        │
        ▼
服务端 LWW 处理后返回 nextCursor
        │
        ▼
插件持久化 nextCursor，再次 Pull 确认收敛
```

关键约束：**游标（cursor）必须由插件持久化**，Pull 与 Push 返回的 `nextCursor` 是下一次 Pull 的起点。游标丢失会导致重复拉取，但协议是幂等的，重复拉取不会破坏数据。

## 认证

同步端点要求 API Key 认证，且 Key 必须包含 `SYNC` 权限（scope）。JWT 认证的用户同样可以访问，且 JWT 用户绕过 scope 检查。

```
Authorization: Bearer cttak_<prefix>_<secret>
```

### 获取带 SYNC scope 的 API Key

API Key 通过 API Key 管理端点创建（需要 JWT 认证）：

```
POST /api/v1/auth/api-keys
```

请求体示例：

```json
{
  "name": "MacBook Pro - IntelliJ IDEA",
  "scopes": ["SYNC"],
  "expiresAt": "2027-01-01T00:00:00+09:00"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 1-100 字符，建议用设备名 + IDE 名 |
| `scopes` | array | 是 | 至少一个。可选值：`READ`, `WRITE`, `SYNC`, `ADMIN`。插件需包含 `SYNC`（设备注册/查询与同步均要求 `SYNC` scope） |
| `expiresAt` | ISO8601 | 否 | 过期时间，不传则永不过期。若填写必须为未来时间 |

创建成功后，响应中的 `data.rawKey` 是**唯一一次**返回原始 Key（格式 `cttak_<prefix>_<secret>`），插件必须立即保存。之后只能通过列表接口查看 Key 元数据（`keyPrefix`、`scopes`、`status` 等），无法再次获取原始 Key。

### 401 / 403 语义

| HTTP | Code | 场景 | 插件处理 |
|------|------|------|---------|
| 401 | AUTH_010 | API Key 无效（不存在 / 不属于该用户 / BOLA 防护） | 提示用户重新配置 API Key |
| 401 | AUTH_011 | API Key 已过期 | 提示用户重新创建 API Key |
| 401 | AUTH_021 | Authorization 头格式错误（非 `Bearer <key>`） | 检查请求头构造逻辑 |
| 403 | AUTH_012 | API Key 已被吊销 | 提示用户重新创建 API Key |
| 403 | AUTH_020 | API Key 缺少 `SYNC` scope | 提示用户创建包含 `SYNC` scope 的 Key |

## 设备注册

设备注册是同步的前置条件：服务端按设备隔离同步游标，pull/push 校验 `deviceId` 归属（未注册 → 404 `COMMON_002`）。插件在绑定 SYNC-scope API key 后，应调用一次设备注册端点登记本设备。

```
POST /api/v1/devices
```

限流：每用户每小时 10 次（`RATE_LIMIT_001`）。认证：API key 需 **SYNC** scope（JWT 自动绕过）。

### 请求体

```json
{
  "deviceId": "3f2a1b4c-5d6e-4f7a-8b9c-0d1e2f3a4b5c",
  "deviceName": "MacBook Pro",
  "platform": "macOS",
  "ideName": "IntelliJ IDEA",
  "ideVersion": "2026.1",
  "appVersion": "1.2.0"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `deviceId` | UUID | 是 | 客户端生成的设备标识（插件建议用本机用户 ID），作为同步游标维度 |
| `deviceName` | string | 否 | 人类可读设备名（≤255 字符） |
| `platform` | string | 否 | 操作系统平台（≤50 字符） |
| `ideName` | string | 否 | IDE 名称（≤100 字符） |
| `ideVersion` | string | 否 | IDE 版本（≤50 字符） |
| `appVersion` | string | 否 | 插件版本（≤50 字符） |

### 语义

- **Upsert**：`deviceId` 已注册且归属当前用户 → 更新元数据与最后活动时间；未注册 → 创建。
- **Key 绑定**：使用 API key 认证时，服务端把 `deviceId` 写入该 key 的 `device_id` 字段（key ↔ 设备绑定，一个 key 绑定最新注册的设备）。
- **归属冲突**：`deviceId` 已被其他用户注册 → 409 `DEVICE_001`。
- 重复注册幂等无害，可在每次同步前调用。

### 响应体（200 OK）

```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "id": "3f2a1b4c-5d6e-4f7a-8b9c-0d1e2f3a4b5c",
    "deviceName": "MacBook Pro",
    "platform": "macOS",
    "ideName": "IntelliJ IDEA",
    "ideVersion": "2026.1",
    "appVersion": "1.2.0",
    "createdAt": "2026-08-28T10:00:00Z",
    "lastSeenAt": "2026-08-28T10:00:00Z",
    "revokedAt": null
  },
  "timestamp": "2026-08-28T10:00:00Z"
}
```

### 对接建议

绑定 API key 成功后调用一次；设备状态检查复用 `GET /api/v1/devices`（READ 或 SYNC scope 均可），对比本机 `deviceId` 是否已注册。

## 获取当前用户

插件进行账号维度数据隔离时，需要知道当前 API key 对应的服务端用户 id。调用用户资料端点即可（API key 或 JWT 认证均可，无 scope 限制——返回的是当前 key 持有者本人的信息）：

```
GET /api/v1/users/me
```

### 响应体（200 OK）

```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "displayName": "SyncTestUser",
    "emailVerified": true,
    "emailChangePending": false,
    "hasPassword": true,
    "createdAt": "2026-08-01T10:00:00Z",
    "lastLoginAt": "2026-08-29T04:30:00Z",
    "termsVersion": "1.0.0"
  },
  "timestamp": "2026-08-29T04:30:00Z"
}
```

### 使用建议

- 绑定 API key 成功后调用一次，取 `data.id` 作为服务端用户 id 保存到本地；换绑 key 时重新获取并切换账号维度。
- 本地会话按此 id 标记归属（push 成功 / pull 应用时写入），统计与查询只过滤当前绑定用户的数据——换绑后不展示前用户的同步会话，实现账号维度隔离。

## Pull 接口

拉取自上次同步点以来的服务端变更。

```
POST /api/v1/sync/pull
```

限流：每端点每分钟 120 次（`RATE_LIMIT_001`）。

### 请求体

```json
{
  "deviceId": "3f2a1b4c-5d6e-4f7a-8b9c-0d1e2f3a4b5c",
  "lastPulledChangeId": 42
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `deviceId` | UUID | 是 | 拥有同步游标的客户端设备 id |
| `lastPulledChangeId` | long | 是 | 客户端已应用的最后变更 id，服务端从该位置之后继续下发。必须大于等于 0 |

### 响应体（200 OK）

```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "changes": [
      {
        "changeId": 43,
        "sessionId": "9f8e7d6c-5b4a-4c3d-8e2f-1a0b9c8d7e6f",
        "sessionUuid": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
        "op": "UPSERT",
        "serverVersion": 3,
        "happenedAt": "2026-08-25T10:30:00Z",
        "projectName": "ctt-server",
        "language": "Java",
        "startTime": "2026-08-25T09:00:00Z",
        "endTime": "2026-08-25T10:00:00Z",
        "clientModifiedAt": "2026-08-25T10:00:00Z",
        "clientVersion": 2,
        "deleted": false
      }
    ],
    "nextCursor": 43,
    "hasMore": false
  },
  "timestamp": "2026-08-25T10:30:00Z"
}
```

响应外层为统一响应包裹（`RestApiResponse`）：`success`（是否成功）、`message`（消息）、`data`（业务数据）、`timestamp`（ISO 8601 时间戳）。业务数据 `data` 结构如下：

| 字段 | 类型 | 说明 |
|------|------|------|
| `changes` | array | 需要应用的变更，按 changeId 升序排列；无新变更时为空数组 |
| `hasMore` | boolean | 是否还有更多变更未下发；为 `true` 时应立即用 `nextCursor` 继续 Pull |
| `nextCursor` | long | 下一次 Pull 应携带的游标 |

### changes[] 字段说明（SyncChangeDto）

| 字段 | 类型 | 说明 |
|------|------|------|
| `changeId` | long | 单调递增的变更 id |
| `sessionId` | UUID | 受影响的编码会话主键 |
| `sessionUuid` | UUID | 客户端生成的会话标识，按用户唯一；会话已物理清除的 DELETE 变更为 null |
| `op` | enum | 应用到会话的操作：`UPSERT`（创建或更新）或 `DELETE`（软删除） |
| `serverVersion` | long | 该变更之后会话的服务端版本 |
| `happenedAt` | Instant | 变更记录时间（ISO 8601） |
| `projectName` | string | 项目或仓库名称 |
| `language` | string | 主要编程语言 |
| `startTime` | Instant | 会话开始时间（ISO 8601） |
| `endTime` | Instant | 会话结束时间（ISO 8601） |
| `clientModifiedAt` | Instant | 客户端最后一次修改时间（ISO 8601） |
| `clientVersion` | int | 客户端版本计数器 |
| `deleted` | boolean | 会话是否已软删除 |

### 游标语义

- **首次拉取**：`lastPulledChangeId` 传 `0`，服务端从第一条变更开始下发。
- **游标复用**：每次 Pull 后，将响应中的 `nextCursor` 作为下一次请求的 `lastPulledChangeId`。
- **幂等性**：当没有新变更时，服务端返回空 `changes` 数组和当前游标（`nextCursor` 不变），重复拉取不会产生副作用。
- **水印单调性**：服务端按设备持久化游标，实际查询游标取「服务端持久化游标」与「客户端传入游标」的较大值，因此客户端无法回退水印；即使客户端传入过期游标，也不会重复下发已消费的变更。
- **服务端分页（v0.62.0 起）**：单次 Pull 最多返回 `ctt.sync.pull-batch-size`（默认 1000）条变更。响应 `hasMore=true` 表示还有剩余，客户端应**循环 Pull**（用返回的 `nextCursor` 继续请求）直到 `hasMore=false`。每页应用后持久化游标，中断后可从该游标续拉，协议幂等。
- **旧客户端兼容**：忽略 `hasMore` 字段的客户端仍能正常工作——本次拿到前 N 条并推进游标，下次同步续拉剩余部分，数据不丢失，只是收敛变慢。
- **变更顺序**：`changes` 按 `changeId` 升序返回，插件应按顺序应用。

## Push 接口

将本地会话状态批量推送到服务端，服务端按 LWW 策略处理冲突。

```
POST /api/v1/sync/push
```

限流：每端点每分钟 120 次（`RATE_LIMIT_001`）。

### 请求体

```json
{
  "deviceId": "3f2a1b4c-5d6e-4f7a-8b9c-0d1e2f3a4b5c",
  "sessions": [
    {
      "sessionUuid": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
      "projectName": "ctt-server",
      "language": "Java",
      "startTime": "2026-08-25T09:00:00Z",
      "endTime": "2026-08-25T10:00:00Z",
      "clientModifiedAt": "2026-08-25T10:00:00Z",
      "clientVersion": 2,
      "deleted": false
    }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `deviceId` | UUID | 是 | 产生这些变更的客户端设备 id |
| `sessions` | array | 是 | 要推送的会话状态，作为一个批次原子处理，不能为空 |

### sessions[] 字段说明（SyncSessionDto）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `sessionUuid` | UUID | 是 | 客户端生成的会话 UUID，同一用户内唯一 |
| `projectName` | string | 是 | 项目或仓库名称 |
| `language` | string | 是 | 主要编程语言 |
| `startTime` | Instant | 是 | 会话开始时间（ISO 8601） |
| `endTime` | Instant | 是 | 会话结束时间（ISO 8601） |
| `clientModifiedAt` | Instant | 是 | 客户端最后一次修改时间（ISO 8601） |
| `clientVersion` | int | 是 | 用于 LWW 冲突解决的客户端版本计数器，必须大于等于 0 |
| `deleted` | boolean | 否 | 客户端是否删除了该会话 |

### 响应体（200 OK）

```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "nextCursor": 43
  },
  "timestamp": "2026-08-25T10:30:00Z"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `nextCursor` | long | 处理完成后记录的最高变更 id，作为下一次 Pull 的游标 |

### 单会话语义（LWW 结果）

服务端对每个会话执行 LWW 冲突解决，结果分三种：

| 结果 | 含义（从插件视角） | 服务端行为 |
|------|------------------|-----------|
| 新会话创建 | 服务端没有该 `sessionUuid`，且 `deleted=false` | 创建会话，`serverVersion` 置为 1，记录 `UPSERT` 变更 |
| 删除不存在的会话（无操作） | 服务端没有该 `sessionUuid`，且 `deleted=true` | 不创建任何行，不记录变更（删除一个不存在的东西是幂等无操作） |
| 客户端胜出（UPSERT 应用） | 客户端提交的活跃状态胜出 | 应用客户端字段，服务端版本 +1，记录 `UPSERT` 变更 |
| 客户端删除胜出（DELETE 应用） | 客户端提交的删除胜出 | 软删除服务端会话，服务端版本 +1，记录 `DELETE` 变更 |
| 服务端胜出（无操作） | 服务端状态胜出，或状态完全相同 | 保持服务端行不变，不记录变更，不提升版本（幂等无操作） |

LWW 判定优先级（与后端 `ConflictResolver` 一致）：

1. **删除优先**：软删除状态胜过活跃状态；删除是最强的终态。双方都删除时继续按版本规则比较。
2. **服务端版本**：双方都携带服务端版本（大于 0）时，版本高者胜出。
3. **客户端版本**：服务端版本相等，或任一方尚无服务端版本（新提交的 `serverVersion` 为 0）时，客户端版本高者胜出。
4. **客户端修改时间**：客户端版本也相等时，`clientModifiedAt` 较晚者胜出。
5. **完全相同**：所有比较字段一致时，保持服务端行不变（重复提交为幂等无操作）。

### 原子性

整个批次在**单个事务**中应用：任一会话处理失败，整个批次回滚，不会出现部分应用。因此插件收到成功响应即代表整批已生效；收到错误响应则整批未生效，可安全重试。

### 响应游标

响应中的 `nextCursor` 是处理完成后该用户记录的最高变更 id。插件应将其持久化，作为下一次 Pull 的 `lastPulledChangeId`。

## 错误码映射表

所有错误响应遵循统一错误格式（`ErrorResponse`，RFC 7807 风格）：

```json
{
  "code": "COMMON_003",
  "message": "Validation error",
  "details": [
    {
      "field": "deviceId",
      "message": "deviceId is required"
    }
  ],
  "traceId": "abc-123",
  "httpStatus": 400,
  "timestamp": "2026-07-13T10:30:00Z"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | string | 错误码标识 |
| `message` | string | 人类可读的错误消息 |
| `details` | array | 字段级校验错误列表（`field` + `message`），无则空数组 |
| `traceId` | string | 分布式追踪 id，用于排查 |
| `httpStatus` | integer | HTTP 状态码 |
| `timestamp` | Instant | 错误时间（ISO 8601） |
| `retryAfter` | Instant | 限流响应的重试时间戳（ISO 8601），仅 429 携带 |

### 同步端点相关错误码

| HTTP | Code | 场景 | 插件处理建议 |
|------|------|------|-------------|
| 400 | COMMON_003 | 请求体校验失败（如 `deviceId` 缺失、`sessions` 为空、字段格式错误） | 检查请求构造，修复后重试；`details` 中给出具体字段 |
| 401 | AUTH_010 | API Key 无效（不存在 / 不属于该用户） | 提示用户重新配置 API Key，停止同步 |
| 401 | AUTH_011 | API Key 已过期 | 提示用户重新创建 API Key，停止同步 |
| 401 | AUTH_021 | Authorization 头格式错误 | 检查请求头是否严格为 `Bearer <key>` |
| 403 | AUTH_012 | API Key 已被吊销 | 提示用户重新创建 API Key，停止同步 |
| 403 | AUTH_020 | API Key 缺少 `SYNC` scope | 提示用户创建包含 `SYNC` scope 的 Key |
| 404 | COMMON_002 | 设备不存在、无权访问或**已吊销**（`deviceId` 不属于当前用户 / 设备被 Revoke） | 通过 `POST /api/v1/devices` 注册（重新激活）设备后再同步；已吊销设备重新注册即恢复，见「设备注册」章节 |
| 429 | RATE_LIMIT_001 | 超过限流（每端点每分钟 120 次） | 按 `Retry-After` 头或 `retryAfter` 字段退避后重试，见下节 |

## 限流与重试策略

Pull 与 Push 端点各自限流：**每端点每分钟 120 次**（`RateLimitType.API`，limit 120，window 60 秒）。两个端点独立计数。

### 429 响应中的重试信息

429 响应同时携带两个重试信息来源：

1. **`Retry-After` HTTP 头**：RFC 7231 定义的 delta-seconds（剩余秒数），例如 `Retry-After: 60`。
2. **响应体 `retryAfter` 字段**：ISO 8601 绝对时间戳，例如 `"retryAfter": "2026-07-13T10:31:00Z"`。

**解析优先级（与 Web 前端一致）**：先读 `Retry-After` 头（秒数），头缺失时回退到响应体 `retryAfter`（绝对时间戳，需与当前时间相减得到秒数）。两者都缺失时按默认退避处理。

### 推荐退避策略

- 收到 429 后，等待时间 = 解析出的重试秒数 + 随机抖动（jitter，建议 0 到 2 秒），避免多个设备同时重试造成惊群。
- 若无法解析重试时间，使用指数退避（如 1s、2s、4s、8s，上限 60s）。
- **禁止无限重试**：设置最大重试次数（建议 3 到 5 次），超过后停止本次同步，保留本地脏数据，等待下一次同步周期。
- 重试应只针对可重试错误（429、网络超时、5xx）；4xx 业务错误（401、403、404、400）不应盲目重试，应提示用户或修复请求。

## 对接流程建议

### 典型同步流程

```
1. Pull：POST /api/v1/sync/pull，携带持久化的 lastPulledChangeId
2. 应用变更：遍历 data.changes[]，按 op 处理
   - op = UPSERT：用 change 中的字段覆盖本地会话（以 sessionId 定位）
   - op = DELETE：软删除本地会话（保留记录，标记已删除）
3. 持久化游标：保存 data.nextCursor
4. Push：将本地脏会话（新建 / 修改 / 删除）打包为 sessions[] 推送
5. 持久化游标：保存 push 响应中的 data.nextCursor
6. 再次 Pull：确认收敛（可选，用于拉取本设备推送后其他设备产生的变更）
```

### 应用 UPSERT 与 DELETE

- **UPSERT**：`changes[]` 中每个条目已携带胜出会话的完整快照（`sessionUuid`、`projectName`、`language`、`startTime`、`endTime`、`clientModifiedAt`、`clientVersion`、`deleted`），插件无需额外查询即可直接应用。以 `sessionUuid` 作为本地主键定位（本地新建或按 `sessionUuid` 匹配既有行后覆盖）。
- **DELETE**：`op = DELETE` 表示该会话已被软删除。插件应标记本地会话为已删除（或从活跃列表移除），不要删除本地记录本身，以便后续冲突解决时保留历史。
- 匹配键：`changes[]` 同时携带 `sessionId`（服务端主键）与 `sessionUuid`（客户端生成的会话标识）。插件应直接以 `sessionUuid` 作为本地主键定位（服务端按用户保证 `sessionUuid` 唯一），无需维护 `sessionId` ↔ `sessionUuid` 映射。

### 游标持久化

- 插件必须在本地持久化每个设备的 `nextCursor`（Pull 与 Push 响应都会返回）。
- 建议在每次成功 Pull / Push 后立即写入本地存储（如 SQLite），避免崩溃后重复拉取。
- 首次使用（无持久化游标）时 `lastPulledChangeId` 传 `0`。

### 冲突处理注意事项

- 插件本地修改会话时，应递增本地 `clientVersion` 并更新 `clientModifiedAt`，作为 LWW 比较依据。
- 删除会话时，将 `deleted` 置为 `true` 并推送，而不是从本地直接移除（除非本地从未同步过该会话）。
- 服务端胜出（无操作）时，插件应以下一次 Pull 返回的服务端状态为准，覆盖本地冲突状态。

## 附录

### Pull 请求 / 响应完整示例

请求：

```json
{
  "deviceId": "3f2a1b4c-5d6e-4f7a-8b9c-0d1e2f3a4b5c",
  "lastPulledChangeId": 0
}
```

响应（200 OK，含 UPSERT 与 DELETE 两种变更）：

```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "changes": [
      {
        "changeId": 1,
        "sessionId": "9f8e7d6c-5b4a-4c3d-8e2f-1a0b9c8d7e6f",
        "sessionUuid": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
        "op": "UPSERT",
        "serverVersion": 1,
        "happenedAt": "2026-08-25T09:00:00Z",
        "projectName": "ctt-server",
        "language": "Java",
        "startTime": "2026-08-25T08:00:00Z",
        "endTime": "2026-08-25T09:00:00Z",
        "clientModifiedAt": "2026-08-25T09:00:00Z",
        "clientVersion": 1,
        "deleted": false
      },
      {
        "changeId": 2,
        "sessionId": "7a6b5c4d-3e2f-4a1b-9c8d-0e1f2a3b4c5d",
        "sessionUuid": "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e",
        "op": "DELETE",
        "serverVersion": 2,
        "happenedAt": "2026-08-25T10:00:00Z",
        "projectName": "ctt-web",
        "language": "TypeScript",
        "startTime": "2026-08-25T07:00:00Z",
        "endTime": "2026-08-25T08:00:00Z",
        "clientModifiedAt": "2026-08-25T10:00:00Z",
        "clientVersion": 2,
        "deleted": true
      }
    ],
    "nextCursor": 2
  },
  "timestamp": "2026-08-25T10:00:00Z"
}
```

### Push 请求 / 响应完整示例

请求（推送一个新建会话和一个删除会话）：

```json
{
  "deviceId": "3f2a1b4c-5d6e-4f7a-8b9c-0d1e2f3a4b5c",
  "sessions": [
    {
      "sessionUuid": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
      "projectName": "ctt-server",
      "language": "Java",
      "startTime": "2026-08-25T09:00:00Z",
      "endTime": "2026-08-25T10:00:00Z",
      "clientModifiedAt": "2026-08-25T10:00:00Z",
      "clientVersion": 2,
      "deleted": false
    },
    {
      "sessionUuid": "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e",
      "projectName": "ctt-web",
      "language": "TypeScript",
      "startTime": "2026-08-25T07:00:00Z",
      "endTime": "2026-08-25T08:00:00Z",
      "clientModifiedAt": "2026-08-25T10:00:00Z",
      "clientVersion": 3,
      "deleted": true
    }
  ]
}
```

响应（200 OK）：

```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "nextCursor": 42
  },
  "timestamp": "2026-08-25T10:00:00Z"
}
```

### 429 限流响应完整示例

```json
{
  "code": "RATE_LIMIT_001",
  "message": "Too many requests",
  "details": [],
  "traceId": "abc-123",
  "httpStatus": 429,
  "timestamp": "2026-07-13T10:30:00Z",
  "retryAfter": "2026-07-13T10:31:00Z"
}
```

同时携带 `Retry-After: 60` HTTP 头（delta-seconds）。

## 相关文档

- [API Key 前端接入指南](../apikey/frontend-integration.md)：API Key 的创建、列表、吊销与删除
- [开发者手册](../../docs/developer-handbook.md)：错误码注册表、审计事件、API Key 认证流程
- [API 治理](../../docs/api-governance.md)：API Key 安全层级、限流策略
