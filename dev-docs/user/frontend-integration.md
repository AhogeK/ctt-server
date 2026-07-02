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
