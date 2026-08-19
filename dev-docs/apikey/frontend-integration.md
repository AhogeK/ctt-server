# API Key 前端接入指南

本文档面向前端开发者，说明如何在客户端实现 API Key 的管理功能。

## 流程总览

API Key 管理是用户个人设置的一部分，用户可以在「设置 → API Keys」页面中创建、查看和吊销自己的 API Key。

```
用户打开设置页面
        │
        ▼
前端调用 GET /api/v1/auth/api-keys（获取列表）
        │
        ▼
列表为空时显示「暂无 API Key，请创建」
        │
        ▼
用户点击「创建 API Key」
        │
        ▼
前端弹出表单（名称 + 权限选择）
        │
        ▼
用户提交 → POST /api/v1/auth/api-keys
        │
        ▼
后端返回 rawKey（仅此一次）
        │
        ▼
前端弹窗显示 rawKey，提示用户立即保存
        │
        ▼
刷新列表，新 Key 出现在列表中
```

关键约束：**rawKey 只在创建时返回一次**，之后无法再获取。前端必须在创建成功后立即展示给用户并提示保存。

## 认证

所有 API Key 管理端点都需要 JWT 认证（用户登录后的 Bearer Token）：

```
Authorization: Bearer <jwt_access_token>
```

## 错误处理

### 通用错误格式

```json
{
  "success": false,
  "code": "AUTH_010",
  "message": "API key invalid",
  "details": [],
  "traceId": "abc-123",
  "httpStatus": 401,
  "timestamp": "2026-07-09T10:30:00Z"
}
```

### 错误码映射

| HTTP | Code | 场景 | 前端处理 |
|------|------|------|---------|
| 401 | AUTH_002 | JWT 过期或无效 | 跳转登录页 |
| 409 | AUTH_024 | 已创建 20 个 Key，达到上限 | 显示「已达到上限，请先吊销一个 Key」 |
| 400 | COMMON_003 | 表单验证失败（name 空/scopes 空） | 校验未通过，显示对应字段错误 |
| 401 | AUTH_010 | BOLA 防护（不可访问其他用户的 Key） | 刷新列表，该 Key 可能已被删除 |
| 409 | AUTH_023 | 删除仍为 ACTIVE 状态的 Key（仅永久删除场景） | 刷新列表兜底 |
| 429 | RATE_LIMIT_001 | 创建频率超过每小时 10 次限制 | 显示「操作过于频繁，请稍后重试」，根据 `Retry-After` header 显示倒计时 |

## 端点说明

### 1. 创建 API Key

```
POST /api/v1/auth/api-keys
```

限流：同一用户每小时 10 次。

#### 请求体

```json
{
  "name": "MacBook Pro — IntelliJ IDEA",
  "scopes": ["READ", "SYNC"],
  "expiresAt": "2027-01-01T00:00:00+09:00"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 1-100 字符，建议用设备名+IDE 名 |
| `scopes` | array | 是 | 至少一个。可选值：`READ`, `WRITE`, `SYNC`, `ADMIN` |
| `expiresAt` | ISO8601 | 否 | 过期时间，不传则永不过期。若填写必须为未来时间 |

#### 响应（201 Created）

```json
{
  "success": true,
  "data": {
    "rawKey": "cttak_a1b2c3d4_z9y8x7w6v5u4t3s2r1q0p9o8n7m6l5k4j3i2h1g0f9e8d7c6b5a4",
    "apiKey": {
      "id": "770e8400-e29b-41d4-a716-446655440002",
      "name": "MacBook Pro — IntelliJ IDEA",
      "keyPrefix": "cttak_a1b2c3d4",
      "scopes": ["READ", "SYNC"],
      "lastUsedAt": null,
      "expiresAt": "2027-01-01T00:00:00+09:00",
      "revokedAt": null,
      "createdAt": "2026-07-09T10:30:00Z",
      "status": "ACTIVE"
    }
  },
  "timestamp": "2026-07-09T10:30:00Z"
}
```

**`rawKey` 仅在此响应中出现一次**。前端必须：

1. 立即在 UI 中弹窗显示 rawKey（全选 → 复制按钮）
2. 提示用户「请立即复制，关闭后将无法再次获取」
3. 使用醒目的视觉样式（如密码框样式的展示）
4. 提供「已复制，关闭」按钮

### 2. 获取 API Key 列表

```
GET /api/v1/auth/api-keys
```

#### 响应（200 OK）

```json
{
  "success": true,
  "data": {
    "apiKeys": [
      {
        "id": "770e8400-e29b-41d4-a716-446655440002",
        "name": "MacBook Pro — IntelliJ IDEA",
        "keyPrefix": "cttak_a1b2c3d4",
        "scopes": ["READ", "SYNC"],
        "lastUsedAt": "2026-07-09T10:30:00Z",
        "expiresAt": null,
        "revokedAt": null,
        "createdAt": "2026-07-09T10:30:00Z",
        "status": "ACTIVE"
      }
    ]
  },
  "timestamp": "2026-07-09T10:30:00Z"
}
```

列表包含所有 Key（ACTIVE / EXPIRED / REVOKED），不含 rawKey。

### 3. 获取单个 API Key

```
GET /api/v1/auth/api-keys/{id}
```

#### 响应（200 OK）

返回单个 Key 元数据，结构和列表项相同。

#### 错误处理

- 401 AUTH_010：Key 不存在或不属于当前用户（BOLA 防护，同一错误码）

### 4. 吊销 API Key

```
DELETE /api/v1/auth/api-keys/{id}
```

#### 响应（204 No Content）

无响应体。幂等：吊销已吊销的 Key 同样返回 204。

#### 前端交互

吊销是**不可逆操作**。建议：

1. 点击「吊销」按钮后弹出确认对话框：
   - 标题：「吊销 API Key」
   - 内容：「吊销后，使用此 Key 的设备将无法继续同步数据。Key 前缀：{keyPrefix}」
   - 确认按钮：红色「确认吊销」
   - 取消按钮：「取消」
2. 确认后调 DELETE 接口
3. 成功后从列表中移除该 Key（或标记为「已吊销」状态）

### 5. 永久删除 API Key（v0.41.0+）

```
DELETE /api/v1/auth/api-keys/{id}/delete
```

#### 响应（204 No Content）

无响应体。**物理删除**：密钥从数据库彻底移除，列表不再出现，不可恢复。

#### 业务规则

- **仅 ACTIVE 状态不可删除**（须先吊销）：EXPIRED / REVOKED 可直接删除，409 时密钥保持原状
- **BOLA 防护**：删除他人密钥 / 不存在的密钥 → 401 AUTH_010（与吊销完全一致，防枚举）
- **幂等语义**：删除已删除的密钥 → 401 AUTH_010（等同不存在）
- **审计**：删除操作记录 `API_KEY_DELETED` 审计事件（含 keyId）

#### 前端交互

1. **EXPIRED / REVOKED 状态行显示「删除」按钮**（仅 ACTIVE 不显示，与吊销按钮互斥）
2. 点击后弹出确认对话框（复用吊销确认模式）：
   - 标题：「永久删除 API Key」
   - 内容：「此操作不可恢复！删除后该 Key 将从列表中彻底消失。Key 前缀：{keyPrefix}」
   - 确认按钮：红色「永久删除」
   - 取消按钮：「取消」
3. 确认后调 DELETE 接口，成功后从列表中**移除该行**（invalidate `api-keys` 查询）

#### 错误码

| HTTP | Code | 场景 | 前端处理 |
|------|------|------|---------|
| 409 | AUTH_023 | 密钥仍为 ACTIVE 状态（前端一般不会触发，因按钮仅 EXPIRED/REVOKED 显示） | 刷新列表兜底 |
| 401 | AUTH_010 | 密钥不存在 / 非本人 / 已删除 | 刷新列表，该行已消失 |

## 状态显示
`status` 字段取值：

| 值 | UI 建议 |
|----|---------|
| `ACTIVE` | 绿色标签，显示「活跃」 |
| `EXPIRED` | 灰色标签，显示「已过期」 |
| `REVOKED` | 红色标签，显示「已吊销」 |

## 列表表格建议

| 列名 | 内容 | 说明 |
|------|------|------|
| 名称 | `name` | 用户自定义名称 |
| 前缀 | `keyPrefix` | 8 字符可见标识符 |
| 权限 | `scopes` | 逗号分隔 |
| 状态 | 颜色标签 | 见上 |
| 最后使用 | `lastUsedAt` | 相对时间或「从未」 |
| 创建时间 | `createdAt` | 日期 |
| 过期时间 | `expiresAt` | 日期或「永不过期」 |
| 操作 | 吊销按钮 | 仅 ACTIVE 状态可操作 |

## 安全注意事项

- **rawKey 只在创建时返回一次**，后端不存储原始 key。关闭弹窗后无法再次获取
- 吊销的 Key 保留在数据库（审计用途），但认证时会被拒绝（403 AUTH_012）
- 每个用户最多 20 个活跃 Key（超过返回 409 AUTH_024）
- Key 的 `scope` 创建后不可修改，需吊销后重新创建

## 相关文档

- [开发者手册](../../docs/developer-handbook.md) — 错误码注册表、审计事件、API Key 认证流程
- [API 治理](../../docs/api-governance.md) — API Key 安全层级、限流策略