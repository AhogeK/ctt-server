# OAuth 手动测试文档

## 目标

验证 GitHub OAuth 登录/注册流程的完整性和正确性。包括正常流程、异常处理、安全防护等场景。

## 前置条件

- 服务器已启动（`docker compose up -d --build` 或 `./gradlew bootRun`）
- Redis 正常运行
- PostgreSQL 正常运行
- `.env` 中已配置 `GITHUB_CLIENT_ID`、`GITHUB_CLIENT_SECRET`、`FRONTEND_BASE_URL`
- 有一个可用的 GitHub 账号用于测试
- 浏览器已登录 GitHub（可选，方便测试）

## 测试环境

| 项目 | 值 |
|------|-----|
| 服务器地址 | `http://localhost:8080/ctt-server` |
| 前端回调地址 | `http://localhost:5173/oauth/callback` |
| 前端错误页面 | `http://localhost:5173/oauth/error` |
| GitHub OAuth 回调 | `http://localhost:8080/ctt-server/api/v1/auth/oauth/github/callback` |

## 测试工具

- 浏览器（Chrome/Firefox）
- curl 或 Postman
- Redis CLI（用于检查/清理 state）

---

## 测试用例

### 1. OAuth 授权发起

#### TC-OAUTH-001: 正常获取授权地址

**场景**: 用户点击"GitHub 登录"按钮

**步骤**:
```bash
curl -v http://localhost:8080/ctt-server/api/v1/auth/oauth/github/authorize
```

**预期结果**:
- HTTP 200
- 响应体包含 `authUrl`，格式为 `https://github.com/login/oauth/authorize?client_id=...&scope=...&state=...`
- `state` 参数为 UUID 格式
- Redis 中存在 `oauth:state:{state}` 键，TTL 为 10 分钟

**验证方式**:
```bash
# 检查 Redis 中的 state
redis-cli GET "oauth:state:{替换为实际state值}"
```

---

#### TC-OAUTH-002: 不支持的 OAuth 提供商

**场景**: 请求不存在的 provider

**步骤**:
```bash
curl -v http://localhost:8080/ctt-server/api/v1/auth/oauth/google/authorize
```

**预期结果**:
- HTTP 400
- 错误码 `COMMON_001`
- 消息包含 "Unsupported OAuth provider"

---

### 2. OAuth 回调处理

#### TC-OAUTH-003: 完整登录流程（新用户注册）

**场景**: 首次使用 GitHub 登录，系统自动注册新用户

**步骤**:
1. 获取授权地址: `curl http://localhost:8080/ctt-server/api/v1/auth/oauth/github/authorize`
2. 复制 `authUrl` 到浏览器
3. 在 GitHub 授权页面点击"Authorize"
4. GitHub 重定向到回调地址
5. 观察浏览器地址栏变化

**预期结果**:
- 浏览器重定向到 `{frontendUrl}/oauth/callback?accessToken=...&refreshToken=...&termsExpired=false`
- 数据库 `users` 表新增一条记录，`status` 为 `ACTIVE`，`password_hash` 为 `null`
- 数据库 `user_oauth_accounts` 表新增一条记录，`provider` 为 `GITHUB`
- `access_token_encrypted` 字段已加密存储（AES-256-GCM）

**验证方式**:
```sql
-- 检查用户表
SELECT id, email, display_name, status, password_hash, terms_version 
FROM users ORDER BY created_at DESC LIMIT 1;

-- 检查 OAuth 绑定表
SELECT id, user_id, provider, provider_user_id, provider_login, provider_email 
FROM user_oauth_accounts ORDER BY created_at DESC LIMIT 1;
```

---

#### TC-OAUTH-004: 已绑定用户登录

**场景**: 用户已通过 GitHub 注册，再次使用 GitHub 登录

**步骤**:
1. 确保 `user_oauth_accounts` 表中已有该 GitHub 账号的绑定记录
2. 重复 TC-OAUTH-003 的授权流程

**预期结果**:
- 浏览器重定向到 `{frontendUrl}/oauth/callback?accessToken=...&refreshToken=...&termsExpired=...`
- `users` 表记录数不变
- `user_oauth_accounts` 表中对应记录的 `updated_at` 更新
- 不创建新用户

---

#### TC-OAUTH-005: 邮箱匹配自动合并

**场景**: 用户通过密码注册，后使用相同邮箱的 GitHub 账号登录

**步骤**:
1. 先通过 `POST /api/v1/auth/register` 注册一个用户（使用 GitHub 账号的邮箱）
2. 通过 TC-OAUTH-003 的授权流程登录

**预期结果**:
- 浏览器重定向到 `{frontendUrl}/oauth/callback?accessToken=...&refreshToken=...`
- `users` 表记录数不变（不创建新用户）
- `user_oauth_accounts` 表新增一条记录，`user_id` 指向原用户
- 原用户的 `password_hash` 保留（仍可用密码登录）

**验证方式**:
```sql
-- 检查 OAuth 绑定是否关联到原用户
SELECT u.id, u.email, u.password_hash, o.provider, o.provider_login
FROM users u
JOIN user_oauth_accounts o ON u.id = o.user_id
WHERE u.email = 'your-github-email@example.com';
```

---

### 3. 状态验证（CSRF 防护）

#### TC-OAUTH-006: State 缺失

**场景**: 回调请求缺少 state 参数

**步骤**:
```bash
curl -v "http://localhost:8080/ctt-server/api/v1/auth/oauth/github/callback?code=test_code"
```

**预期结果**:
- HTTP 302 重定向到 `{frontendUrl}/oauth/error?code=MISSING_OAUTH_PARAMS`

---

#### TC-OAUTH-007: State 无效（伪造）

**场景**: 使用随机生成的 state 值

**步骤**:
```bash
curl -v "http://localhost:8080/ctt-server/api/v1/auth/oauth/github/callback?code=test_code&state=00000000-0000-0000-0000-000000000000"
```

**预期结果**:
- HTTP 302 重定向到 `{frontendUrl}/oauth/error?code=AUTH_013`
- Redis 中不存在该 state 键

---

#### TC-OAUTH-008: State 已过期

**场景**: State 超过 10 分钟 TTL

**步骤**:
1. 获取授权地址，记录 state 值
2. 等待 10 分钟（或手动删除 Redis 中的 state 键）
3. 使用该 state 发起回调

**快速测试方式**:
```bash
# 获取 state
curl http://localhost:8080/ctt-server/api/v1/auth/oauth/github/authorize
# 记录返回的 state 值

# 手动删除 Redis 中的 state
redis-cli DEL "oauth:state:{替换为实际state值}"

# 使用已删除的 state 发起回调
curl -v "http://localhost:8080/ctt-server/api/v1/auth/oauth/github/callback?code=test_code&state={替换为实际state值}"
```

**预期结果**:
- HTTP 302 重定向到 `{frontendUrl}/oauth/error?code=AUTH_013`

---

#### TC-OAUTH-009: State 重放攻击

**场景**: 同一个 state 使用两次

**步骤**:
1. 获取授权地址，记录 state 值
2. 正常完成一次 OAuth 回调（第一次使用 state）
3. 再次使用相同的 state 发起回调

**预期结果**:
- 第一次回调成功（302 重定向带 token）
- 第二次回调失败，302 重定向到 `{frontendUrl}/oauth/error?code=AUTH_013`
- Redis 中该 state 键已被删除（GETDEL 原子操作）

---

#### TC-OAUTH-010: Code 缺失

**场景**: 回调请求缺少 code 参数

**步骤**:
```bash
curl -v "http://localhost:8080/ctt-server/api/v1/auth/oauth/github/callback?state=test_state"
```

**预期结果**:
- HTTP 302 重定向到 `{frontendUrl}/oauth/error?code=MISSING_OAUTH_PARAMS`

---

### 4. GitHub API 交互

#### TC-OAUTH-011: 无效的 Authorization Code

**场景**: 使用伪造的 code 值

**步骤**:
1. 获取授权地址，记录 state 值
2. 使用伪造的 code 发起回调

```bash
curl -v "http://localhost:8080/ctt-server/api/v1/auth/oauth/github/callback?code=invalid_code_123&state={替换为实际state值}"
```

**预期结果**:
- HTTP 302 重定向到 `{frontendUrl}/oauth/error?code=AUTH_015`
- 服务器日志包含 "GitHub OAuth code exchange failed"

---

#### TC-OAUTH-012: GitHub 返回错误

**场景**: GitHub 授权页面用户点击"Cancel"

**步骤**:
1. 获取授权地址
2. 在浏览器打开授权地址
3. 在 GitHub 授权页面点击"Cancel"或"Deny"

**预期结果**:
- GitHub 重定向到回调地址，带 `error=access_denied` 参数
- 服务器重定向到 `{frontendUrl}/oauth/error?code=OAUTH_PROVIDER_ERROR`

---

#### TC-OAUTH-013: GitHub 用户邮箱为私有

**场景**: GitHub 用户设置了邮箱为私有

**步骤**:
1. 在 GitHub 设置中将邮箱设为私有
2. 完成 OAuth 登录流程

**预期结果**:
- 登录成功
- 服务器通过 `/user/emails` API 获取主邮箱
- `user_oauth_accounts.provider_email` 存储了正确的邮箱
- `users.email` 使用获取到的邮箱

---

### 5. 用户状态验证

#### TC-OAUTH-014: 被锁定的用户尝试 OAuth 登录

**场景**: 用户账号被锁定后尝试 OAuth 登录

**步骤**:
1. 手动将用户状态设为 `LOCKED`:
```sql
UPDATE users SET status = 'LOCKED' WHERE email = 'test@example.com';
```
2. 使用该用户的 GitHub 账号完成 OAuth 登录

**预期结果**:
- HTTP 302 重定向到 `{frontendUrl}/oauth/error?code=AUTH_004`

---

#### TC-OAUTH-015: 被停用的用户尝试 OAuth 登录

**场景**: 用户账号被停用后尝试 OAuth 登录

**步骤**:
1. 手动将用户状态设为 `SUSPENDED`:
```sql
UPDATE users SET status = 'SUSPENDED' WHERE email = 'test@example.com';
```
2. 使用该用户的 GitHub 账号完成 OAuth 登录

**预期结果**:
- HTTP 302 重定向到 `{frontendUrl}/oauth/error?code=AUTH_005`

---

#### TC-OAUTH-016: 邮箱未验证的用户尝试 OAuth 登录

**场景**: 用户邮箱未验证时尝试 OAuth 登录

**步骤**:
1. 手动将用户状态设为 `PENDING_VERIFICATION`:
```sql
UPDATE users SET status = 'PENDING_VERIFICATION' WHERE email = 'test@example.com';
```
2. 使用该用户的 GitHub 账号完成 OAuth 登录

**预期结果**:
- HTTP 302 重定向到 `{frontendUrl}/oauth/error?code=AUTH_006`

---

### 6. 限流测试

#### TC-OAUTH-017: 授权接口限流

**场景**: 1 小时内同一 IP 请求超过 30 次

**步骤**:
```bash
# 快速发送 31 次请求
for i in $(seq 1 31); do
  echo "Request $i:"
  curl -s -o /dev/null -w "%{http_code}" \
    http://localhost:8080/ctt-server/api/v1/auth/oauth/github/authorize
  echo
done
```

**预期结果**:
- 前 30 次返回 HTTP 200
- 第 31 次返回 HTTP 429
- 响应体包含 `RATE_LIMIT_001` 错误码

---

#### TC-OAUTH-018: 回调接口限流

**场景**: 1 小时内同一 IP 请求超过 60 次

**步骤**:
```bash
# 快速发送 61 次请求
for i in $(seq 1 61); do
  echo "Request $i:"
  curl -s -o /dev/null -w "%{http_code}" \
    "http://localhost:8080/ctt-server/api/v1/auth/oauth/github/callback?code=test&state=test"
  echo
done
```

**预期结果**:
- 前 60 次返回 HTTP 302（因为 code/state 无效，但请求被处理）
- 第 61 次返回 HTTP 429
- 响应体包含 `RATE_LIMIT_001` 错误码

---

### 7. Token 加密验证

#### TC-OAUTH-019: Token 加密存储

**场景**: 验证 OAuth access token 在数据库中已加密

**步骤**:
1. 完成一次 OAuth 登录
2. 查询数据库中的 token

```sql
SELECT access_token_encrypted FROM user_oauth_accounts 
ORDER BY created_at DESC LIMIT 1;
```

**预期结果**:
- `access_token_encrypted` 字段值为 Base64 编码的字符串
- 不是明文的 GitHub access token
- 长度大于 50 字符（包含 IV + 密文 + GCM tag）

---

### 8. 错误处理

#### TC-OAUTH-020: Redis 不可用

**场景**: Redis 服务停止或连接失败

**步骤**:
1. 停止 Redis: `docker compose stop redis`
2. 请求授权地址

```bash
curl -v http://localhost:8080/ctt-server/api/v1/auth/oauth/github/authorize
```

**预期结果**:
- HTTP 500 或 503
- 错误信息包含 Redis 连接失败相关描述

**恢复**:
```bash
docker compose start redis
```

---

#### TC-OAUTH-021: GitHub API 不可达

**场景**: GitHub API 服务器故障或网络不通

**步骤**:
1. 在防火墙中阻止对 `github.com` 的访问（或使用网络代理模拟）
2. 完成 OAuth 授权流程

**预期结果**:
- HTTP 302 重定向到 `{frontendUrl}/oauth/error?code=AUTH_015`
- 服务器日志包含 "GitHub OAuth server error" 或连接超时信息

---

### 9. 并发与安全

#### TC-OAUTH-022: 同一 GitHub 账号并发登录

**场景**: 同一用户在多个浏览器同时发起 OAuth 登录

**步骤**:
1. 在浏览器 A 获取授权地址
2. 在浏览器 B 获取授权地址
3. 在浏览器 A 完成授权
4. 在浏览器 B 完成授权（使用相同的 GitHub 账号）

**预期结果**:
- 两次登录都成功
- 只创建一个用户（第一次登录时创建）
- 两次都获得有效的 JWT token
- `user_oauth_accounts` 表只有一条记录

---

#### TC-OAUTH-023: State 参数注入

**场景**: 尝试在 state 参数中注入恶意内容

**步骤**:
```bash
curl -v "http://localhost:8080/ctt-server/api/v1/auth/oauth/github/callback?code=test&state=<script>alert(1)</script>"
```

**预期结果**:
- HTTP 302 重定向到错误页面
- 不执行任何 JavaScript
- 服务器不崩溃

---

### 10. 数据一致性

#### TC-OAUTH-024: 事务回滚验证

**场景**: 用户注册过程中发生异常

**步骤**:
1. 临时将 `users` 表的 `email` 字段设为 UNIQUE（如果尚未设置）
2. 在数据库中插入一条测试记录: `INSERT INTO users (email, ...) VALUES ('conflict@test.com', ...)`
3. 将 GitHub 账号邮箱设为 `conflict@test.com`
4. 尝试 OAuth 登录（新用户场景）

**预期结果**:
- 如果邮箱已存在，触发邮箱匹配合并逻辑（TC-OAUTH-005）
- 如果合并失败，事务回滚，数据库无脏数据
- `users` 表和 `user_oauth_accounts` 表保持一致

---

#### TC-OAUTH-025: OAuth 绑定唯一性约束

**场景**: 尝试将同一 GitHub 账号绑定到多个用户

**步骤**:
1. 用户 A 通过 GitHub 登录（创建绑定）
2. 手动在数据库中删除该绑定
3. 用户 B 通过 GitHub 登录（创建新绑定）
4. 尝试手动恢复用户 A 的绑定

```sql
-- 尝试插入重复的 provider + provider_user_id
INSERT INTO user_oauth_accounts (user_id, provider, provider_user_id, ...)
VALUES ('{user_a_id}', 'GITHUB', '{github_user_id}', ...);
```

**预期结果**:
- 数据库拒绝插入，违反 `uk_user_oauth_provider_uid` 唯一约束
- 返回唯一键冲突错误

---

## 测试结果记录表

| 用例 ID | 场景 | 预期结果 | 实际结果 | 通过 | 备注 |
|---------|------|----------|----------|------|------|
| TC-OAUTH-001 | 正常获取授权地址 | 200 + authUrl | | | |
| TC-OAUTH-002 | 不支持的提供商 | 400 COMMON_001 | | | |
| TC-OAUTH-003 | 新用户注册 | 302 + token | | | |
| TC-OAUTH-004 | 已绑定用户登录 | 302 + token | | | |
| TC-OAUTH-005 | 邮箱匹配合并 | 302 + token | | | |
| TC-OAUTH-006 | State 缺失 | 302 MISSING_OAUTH_PARAMS | | | |
| TC-OAUTH-007 | State 无效 | 302 AUTH_013 | | | |
| TC-OAUTH-008 | State 过期 | 302 AUTH_013 | | | |
| TC-OAUTH-009 | State 重放 | 302 AUTH_013 | | | |
| TC-OAUTH-010 | Code 缺失 | 302 MISSING_OAUTH_PARAMS | | | |
| TC-OAUTH-011 | 无效 Code | 302 AUTH_015 | | | |
| TC-OAUTH-012 | GitHub 返回错误 | 302 OAUTH_PROVIDER_ERROR | | | |
| TC-OAUTH-013 | 私有邮箱 | 登录成功 | | | |
| TC-OAUTH-014 | 锁定用户 | 302 AUTH_004 | | | |
| TC-OAUTH-015 | 停用用户 | 302 AUTH_005 | | | |
| TC-OAUTH-016 | 未验证邮箱 | 302 AUTH_006 | | | |
| TC-OAUTH-017 | 授权限流 | 429 | | | |
| TC-OAUTH-018 | 回调限流 | 429 | | | |
| TC-OAUTH-019 | Token 加密 | Base64 密文 | | | |
| TC-OAUTH-020 | Redis 不可用 | 500/503 | | | |
| TC-OAUTH-021 | GitHub 不可达 | 302 AUTH_015 | | | |
| TC-OAUTH-022 | 并发登录 | 两次都成功 | | | |
| TC-OAUTH-023 | State 注入 | 302 错误页 | | | |
| TC-OAUTH-024 | 事务回滚 | 数据一致 | | | |
| TC-OAUTH-025 | 唯一约束 | 数据库拒绝 | | | |

---

## 已知问题与限制

### 测试覆盖缺口

当前 `OAuthCallbackControllerTest` 仅包含注解反射测试（验证 `@PublicApi`、`@RateLimit`、`@Operation` 等注解是否存在），缺少以下 MockMvc 集成测试：

- 授权端点的 HTTP 响应验证
- 回调端点的重定向逻辑验证
- 异常处理链路验证
- 限流行为验证

### 手动测试限制

1. **GitHub 授权页面**: 无法通过 curl 模拟完整的 GitHub 授权流程，必须使用浏览器
2. **State 过期**: 等待 10 分钟不现实，建议通过删除 Redis 键模拟
3. **GitHub API 故障**: 需要网络层模拟，无法通过应用层控制
4. **并发测试**: 需要多浏览器/无痕窗口，curl 无法完全模拟

### 建议补充的自动化测试

- 使用 `@WebMvcTest` + MockMvc 测试控制器层
- 使用 Testcontainers 模拟 Redis 进行 state 管理测试
- 使用 WireMock 模拟 GitHub API 响应

---

## 错误码速查表

| 错误码 | HTTP 状态码 | 含义 | 触发场景 |
|--------|-------------|------|----------|
| `COMMON_001` | 400 | 请求参数无效 | 不支持的 OAuth 提供商 |
| `AUTH_004` | 403 | 账号已锁定 | 锁定用户尝试登录 |
| `AUTH_005` | 403 | 账号已停用 | 停用/删除用户尝试登录 |
| `AUTH_006` | 403 | 邮箱未验证 | 邮箱未验证用户尝试登录 |
| `AUTH_013` | 403 | State 验证失败 | State 无效/过期/已消费 |
| `AUTH_015` | 502 | OAuth 提供商错误 | GitHub API 返回错误/不可达 |
| `AUTH_016` | 409 | OAuth 账号已绑定 | 尝试重复绑定 |
| `AUTH_017` | 400 | OAuth 账号未绑定 | 尝试解绑不存在的绑定 |
| `RATE_LIMIT_001` | 429 | 请求过于频繁 | 超过限流阈值 |

---

## 快速测试脚本

以下脚本可用于快速验证基本流程：

```bash
#!/bin/bash
BASE_URL="http://localhost:8080/ctt-server"

echo "=== TC-OAUTH-001: 获取授权地址 ==="
RESPONSE=$(curl -s "$BASE_URL/api/v1/auth/oauth/github/authorize")
echo "$RESPONSE"
AUTH_URL=$(echo "$RESPONSE" | jq -r '.data.authUrl')
echo "Auth URL: $AUTH_URL"

echo ""
echo "=== TC-OAUTH-002: 不支持的提供商 ==="
curl -s "$BASE_URL/api/v1/auth/oauth/google/authorize" | jq .

echo ""
echo "=== TC-OAUTH-006: State 缺失 ==="
curl -s -o /dev/null -w "HTTP Status: %{http_code}\n" \
  "$BASE_URL/api/v1/auth/oauth/github/callback?code=test"

echo ""
echo "=== TC-OAUTH-007: State 无效 ==="
curl -s -o /dev/null -w "HTTP Status: %{http_code}\n" \
  "$BASE_URL/api/v1/auth/oauth/github/callback?code=test&state=00000000-0000-0000-0000-000000000000"

echo ""
echo "=== TC-OAUTH-010: Code 缺失 ==="
curl -s -o /dev/null -w "HTTP Status: %{http_code}\n" \
  "$BASE_URL/api/v1/auth/oauth/github/callback?state=test"
```

---

## 测试完成标准

- [ ] 所有 TC-OAUTH-xxx 用例执行完毕
- [ ] 测试结果记录表已填写
- [ ] 发现的 Bug 已提交到 Issue Tracker
- [ ] 关键路径（TC-OAUTH-003/004/005）100% 通过
- [ ] 安全相关用例（TC-OAUTH-006~009, 023）100% 通过
