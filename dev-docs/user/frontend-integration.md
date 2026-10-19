# User Profile API 前端集成指南

本文档面向前端开发者，说明如何获取已登录用户的 profile 信息（用于 AppHeader dropdown、个人设置页等场景）。

## Endpoint 规格

| 项       | 值                                                 |
|---------|---------------------------------------------------|
| HTTP 方法 | `GET`                                             |
| 路径      | `/api/v1/users/me`                                |
| 鉴权      | **Bearer JWT 必填**                                 |
| 响应（成功）  | `200 OK` + `RestApiResponse<UserProfileResponse>` |
| 响应（错误）  | `401`（无 JWT / JWT 过期）                             |

## Response 字段

| 字段              | 类型                           | 说明       | 示例                                       |
|-----------------|------------------------------|----------|------------------------------------------|
| `id`            | UUID                         | 用户唯一 ID  | `"550e8400-e29b-41d4-a716-446655440000"` |
| `email`         | String                       | 邮箱地址     | `"user@example.com"`                     |
| `displayName`   | String                       | 显示名      | `"John Doe"`                             |
| `emailVerified` | boolean                      | 邮箱是否已验证  | `true`                                   |
| `createdAt`     | Instant (ISO 8601)           | 账号创建时间   | `"2026-01-15T10:30:00Z"`                 |
| `lastLoginAt`   | Instant (ISO 8601, nullable) | 最后登录时间   | `"2026-07-01T09:15:00Z"`                 |
| `termsVersion`  | String                       | 已接受的条款版本 | `"1.0.0"`                                |

**响应 JSON 示例**：
```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "displayName": "John Doe",
    "emailVerified": true,
    "createdAt": "2026-01-15T10:30:00Z",
    "lastLoginAt": "2026-07-01T09:15:00Z",
    "termsVersion": "1.0.0"
  },
  "timestamp": "2026-07-01T10:00:00Z"
}
```

## Avatar 字段

**后端不返回 avatar URL，也不存储 avatar 字段**。前端应自行生成 avatar：

- **推荐方案**：使用 `md5(userId.toString())` 作为头像源（如 Gravatar、Identicon、UI Avatars 等服务）
- **示例**：
  ```typescript
  const avatarUrl = `https://www.gravatar.com/avatar/${md5(userId)}?d=identicon`;
  ```
- **优点**：零额外存储，跨设备一致，用户可控（注册 Gravatar 可绑定真实头像）

## 调用示例

```typescript
// TypeScript (fetch)
const response = await fetch('/api/v1/users/me', {
  headers: { 'Authorization': `Bearer ${accessToken}` }
});

if (response.status === 200) {
  const { data } = await response.json();
  // data.displayName, data.email, data.emailVerified, ...
} else if (response.status === 401) {
  // Token 过期或无效 → 触发重新登录
  redirectToLogin();
}
```

```typescript
// Vue 3 Composable 示例
export function useCurrentUser() {
  const user = ref<UserProfileResponse | null>(null);
  const loading = ref(false);
  const error = ref<string | null>(null);

  async function fetchProfile() {
    loading.value = true;
    try {
      const res = await fetch('/api/v1/users/me', {
        headers: { 'Authorization': `Bearer ${useAuth().accessToken}` }
      });
      if (res.status === 401) {
        error.value = 'AUTHENTICATION_REQUIRED';
        return;
      }
      const { data } = await res.json();
      user.value = data;
    } catch (e) {
      error.value = 'NETWORK_ERROR';
    } finally {
      loading.value = false;
    }
  }

  return { user, loading, error, fetchProfile };
}
```

## 错误码 → Toast 映射

| HTTP 状态 | 错误码        | 触发场景        | 前端 Toast      |
|---------|------------|-------------|---------------|
| 200     | —          | 成功          | （静默）          |
| 401     | `AUTH_002` | Token 过期或无效 | "登录已过期，请重新登录" |
| 5xx     | —          | 后端异常        | "服务异常，请稍后重试"  |

## 注意事项

- **不要缓存过久**：profile 信息可能变化（displayName 修改、邮箱验证状态变化等），建议每次 AppHeader 渲染时调用，或缓存 5 分钟
- **不要在前端过滤 401**：401 是触发重新登录的明确信号，应统一处理（与现有 AuthContext 集成）
- **敏感字段已脱敏**：后端已自动排除 `passwordHash`、`lastLoginIp`、`version` 等敏感字段，前端无需处理

## 个人设置（阶段六）端点

**已实现（v0.80.0）**：`PATCH /api/v1/users/me` —— 修改显示名，请求体 `{"displayName": "..."}`（校验同注册：2–50，中日韩 / 拉丁 / 数字 / `_` / `-`），返回最新 `UserProfileResponse`。同名提交 = 成功且不写库不审计；`USER` 10 次/小时；JWT 或带 **WRITE** scope 的 API key。错误码零新增（`400 COMMON_003` / `403 AUTH_020` / `403 AUTH_022`）。

**已实现**：登录会话列表与吊销（v0.82.0）、数据导出（v0.83.0）——契约见下方两节。设计见仓库 `.plans/ctt-server-development-plan.md` 的「👤 个人设置（账号自助与数据可携带）」。

| 方法 | 路径 | 用途 | 关键约束 |
|---|---|---|---|
| `GET` | `/api/v1/users/me/sessions` | 登录会话列表（含 `current` 标记） | `limit` 默认 20 / 上限 100；`current` 由后端判定——**前端不要传 refresh token 来比对**；每行另含 `userAgent`（原文、可空，供你们派生 "Chrome · macOS" 之类的显示名） |
| `DELETE` | `/api/v1/users/me/sessions/{id}` | 吊销单个登录会话 | 成功 `204`；非本人或不存在 `404 COMMON_002`；允许删当前会话（等价登出） |
| `GET` | `/api/v1/users/me/export` | 导出全部个人数据 | 响应为**单一 JSON 文档**（`.json` 附件，插件端可导入）；`timezoneOffset` **必填**（±720，超范围 400）——详见下方 v0.83.0 节 |

**错误码零新增**（复用 `COMMON_002` / `COMMON_003` / `AUTH_008` / `AUTH_020` / `AUTH_022`）；跨端一律以 `ErrorCode.java` 为准 —— 例：`AUTH_018` = "Cannot unlink last credential"（409）；某个会话被吊销后，该客户端下次刷新会得到 `AUTH_008`（Refresh token revoked）。

## 登录会话列表与吊销（v0.83.0）

| 项 | 值 |
|---|---|
| HTTP 方法 | `GET` / `DELETE` |
| 路径 | `/api/v1/users/me/sessions`、`/api/v1/users/me/sessions/{sessionId}` |
| 鉴权 | **Bearer JWT 必填**（DELETE 也接受带 `WRITE` scope 的 API key） |
| 限流 | GET 60/小时、DELETE 10/小时（超出 `429 RATE_LIMIT_001`） |
| 响应（成功） | GET `200 OK` + `RestApiResponse<LoginSessionResponse[]>`；DELETE `204 No Content` |
| 响应（错误） | `401`（无 JWT）、`403 AUTH_020`（API key 缺 WRITE scope）、`403 AUTH_022`（账号非活跃）、`404 COMMON_002`（会话不存在或不属于本人） |

### 语义要点（对接前务必知道）

- **一次登录 = 一条会话**，刷新不会新增条目：轮换只是同一 `session_id` 下换一行。
- 每条会话的 `createdAt` 是**登录时刻**，`lastUsedAt` 是**最近一次刷新**（从未刷新则为 `null`），`expiresAt` 是该会话最新令牌的到期时间。
- `current` 表示"这条会话就是当前这次请求所用的会话"；用 API key 调用时没有会话身份，所有条目 `current` 均为 `false`。
- 排序按 `lastUsedAt` 倒序（最近活动在前），当前会话通常靠前但不保证第一。
- `deviceName` / `ideName` 仅在设备绑定（`kind = "PLUGIN"`）会话上有值，浏览器会话为 `null`。
- `userAgent` 是客户端原样上送的值（服务端不解析、按 512 字符截断），前端可自行派生 "Chrome · macOS" 这类显示名。
- **可空字段的键始终存在**（值为 `null`），便于区分"该会话没有设备"与"前端不认识该字段"。

### Response 字段

| 字段 | 类型 | 说明 | 示例 |
|---|---|---|---|
| `id` | UUID | 会话标识（删除时的路径参数） | `"9f1c8f7a-2b4e-4d5a-9c3e-1a2b3c4d5e6f"` |
| `kind` | String | 会话类型 `WEB` / `PLUGIN` | `"PLUGIN"` |
| `deviceName` | String (nullable) | 设备注册名 | `"MacBook Pro"` |
| `ideName` | String (nullable) | IDE 名称 | `"IntelliJ IDEA"` |
| `userAgent` | String (nullable) | 客户端 UA 原值 | `"Mozilla/5.0 (Macintosh; ...)"` |
| `createdAt` | Instant | 登录时刻 | `"2026-10-19T09:12:31Z"` |
| `lastUsedAt` | Instant (nullable) | 最近刷新时刻 | `"2026-10-19T10:04:07Z"` |
| `expiresAt` | Instant | 会话最新令牌到期时间 | `"2026-11-18T09:12:31Z"` |
| `current` | boolean | 是否为当前请求所用会话 | `true` |

### 吊销语义

- 吊销会失效该会话**全部未过期令牌**，该客户端需重新登录；**允许吊销当前会话**（等价登出）。
- 对非本人或不存在的会话统一返回 `404 COMMON_002`，不泄露会话是否存在。
- 已签发的 access token 在到期前（≤15 分钟）仍然有效——与 `logout` 同一取舍，列表是"在哪登录过"的权威视图。
- 被踢客户端若继续用旧 refresh token 刷新，只会得到该会话的 `401 AUTH_003`，**不会**连带吊销该账号其它会话。

## 数据导出（v0.83.0）—— 产出物面向**插件端导入**，前端只负责触发与下载

| 项 | 值 |
|---|---|
| HTTP 方法 | `GET` |
| 路径 | `/api/v1/users/me/export?timezoneOffset=<分钟>` |
| 鉴权 | **Bearer JWT**（也接受带 `READ` scope 的 API key） |
| 限流 | 3/小时（超出 `429 RATE_LIMIT_001`） |
| 响应（成功） | `200 OK` + `application/json;charset=UTF-8`，`Content-Disposition: attachment; filename="ctt-export-<yyyyMMdd>.json"`（流式下载，非 `RestApiResponse` 外壳） |
| 响应（错误） | `400 COMMON_003`（`timezoneOffset` 缺失或超出 ±720）、`403 AUTH_020`（API key 缺 READ）、`403 AUTH_022`（账号非活跃）、`429` |

### 前端必读：`timezoneOffset` 是**必填**参数

```ts
// 与统计端点同一取法（UTC+8 → 480）
const offset = -new Date().getTimezoneOffset()
const res = await fetch(`/api/v1/users/me/export?timezoneOffset=${offset}`, {
  headers: { Authorization: `Bearer ${accessToken}` },
})
const blob = await res.blob()          // 直接存盘即可，不要 JSON.parse 后再序列化
```

- **为什么必填**：文件里的时间戳是**无时区的本地时间**（插件端按本地时间解析，带 `Z` 会整体导入失败）。服务端只能按你给的偏移把 UTC 转成本地时间，所以**不能省略**——省略即全局 400，避免生成"时间看起来对、导入后整体位移"的坏文件。
- **下载不要重编码**：`blob` 原样保存为 `.json`；前端若先 `JSON.parse` 再 `JSON.stringify` 会重新格式化（无功能影响但无必要），若丢弃 `sessions` 则文件将**不可导入**。

### 文件结构（`sessions[]` 是插件端消费的部分）

| 键 | 说明 |
|---|---|
| `exportVersion` | 恒为 `"1.0"`（插件端硬校验，其它值直接拒绝） |
| `exportTime` | 生成本地时间 |
| `totalSessions` | `sessions` 条数 |
| `sessions[]` | `sessionUuid` / `userId` / `projectName` / `language` / `platform` / `ideName` / `startTime` / `endTime` / `lastModified` —— **字段名与插件端导出一致**；`platform`/`ideName` 取自设备注册表，缺失时为 `""`（绝不为 `null`） |
| `serverExport` | 服务端附加信息：`account` / `devices` / `dailyStats` / `achievements` / `achievementProgress` / `oauthAccounts` / `loginSessions` / `apiKeys`（**无任何凭据**：无密码、无令牌、无 key hash） |

### 语义要点

- **不含软删会话**：插件端导入一律按 `is_deleted = 0` 落库，导出软删会"复活"用户已删的记录。
- **不导出凭据**：API key 只给元数据（名称/scope/时间），OAuth 只给绑定关系，refresh token 只给会话元信息。
- **导入路径（当前）**：用户在插件端点 `Import Data` 选择该文件即可（按 `sessionUuid` 去重、已存在的跳过）；重复导入同一文件是安全的。
- 审计：每次导出记 `DATA_EXPORTED`（含会话条数与时区偏移），可在安全审计中追溯。
