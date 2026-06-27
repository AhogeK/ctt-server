# GitHub OAuth 前端接入指南

本文档面向前端开发者，说明如何在客户端实现 GitHub OAuth 登录/注册功能。

## 流程总览

```
用户点击「GitHub 登录」
        │
        ▼
前端调用 GET /api/v1/auth/oauth/github/authorize
        │
        ▼
后端返回 GitHub 授权地址 (authUrl)
        │
        ▼
前端将用户重定向到 authUrl
        │
        ▼
用户在 GitHub 页面授权
        │
        ▼
GitHub 回调到后端 /api/v1/auth/oauth/github/callback
        │
        ▼
后端完成 state 校验 → code 换 token → 获取用户信息 → 登录或注册
        │
        ▼
后端 302 重定向到前端回调页，携带 token 参数
        │
        ▼
前端回调页读取 token，存入本地，跳转主应用
```

关键点：前端**永远不会**直接调用 `/callback` 接口。这个端点由 GitHub 触发，后端处理完后重定向到前端。

## 步骤一：获取授权地址

### 请求

```
GET /api/v1/auth/oauth/github/authorize
```

- 公开接口，无需认证
- 限流：同一 IP 每小时 30 次

### 响应

```json
{
  "code": 200,
  "message": "OK",
  "data": {
    "authUrl": "https://github.com/login/oauth/authorize?client_id=xxx&scope=read:user%2Cuser:email&state=yyy"
  },
  "timestamp": "2026-04-22T10:00:00Z"
}
```

`data.authUrl` 就是用户需要访问的 GitHub 授权页面。`state` 参数由后端自动生成，用于 CSRF 防护，有效期 10 分钟。

### 前端怎么做

拿到 `authUrl` 后，直接 `window.location.href = authUrl` 跳转即可。不需要前端额外处理 state。

```javascript
async function loginWithGitHub() {
  const response = await fetch('/api/v1/auth/oauth/github/authorize');
  const { data } = await response.json();
  window.location.href = data.authUrl;
}
```

## 步骤二：处理回调重定向

用户在 GitHub 授权后，GitHub 会重定向到后端的 callback 地址。后端处理完毕后，会 302 重定向到前端的回调页面。

### 成功时的重定向地址

```
{FRONTEND_BASE_URL}/oauth/callback?accessToken=xxx&refreshToken=yyy&termsExpired=false
```

参数说明：

| 参数 | 类型 | 说明 |
|------|------|------|
| `accessToken` | string | JWT 访问令牌，用于后续 API 调用 |
| `refreshToken` | string | 刷新令牌，用于续期 accessToken |
| `termsExpired` | boolean | `true` 表示用户需要重新接受服务条款 |

### 失败时的重定向地址

```
{FRONTEND_BASE_URL}/oauth/error?code=ERROR_CODE
```

### 前端需要做的事

在前端路由中注册 `/oauth/callback` 页面，读取 URL 查询参数：

```javascript
function handleOAuthCallback() {
  const params = new URLSearchParams(window.location.search);

  const accessToken = params.get('accessToken');
  const refreshToken = params.get('refreshToken');
  const termsExpired = params.get('termsExpired');
  const error = params.get('code');  // 错误时走 /oauth/error，这里不会同时出现

  if (accessToken && refreshToken) {
    // 存储 token
    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);

    if (termsExpired === 'true') {
      // 跳转到条款接受页面
      window.location.href = '/terms';
    } else {
      // 跳转到主页
      window.location.href = '/dashboard';
    }
  }
}
```

同时注册 `/oauth/error` 页面处理错误：

```javascript
function handleOAuthError() {
  const params = new URLSearchParams(window.location.search);
  const errorCode = params.get('code');
  // 根据 errorCode 展示错误信息（见下方错误码表）
}
```

## 步骤三：使用 Token

OAuth 回调返回的 token 和普通登录返回的 token 完全一样。后续 API 调用方式相同：

```
Authorization: Bearer {accessToken}
```

Token 刷新也走同一个接口：

```
POST /api/v1/auth/refresh
Body: { "refreshToken": "xxx" }
```

## 前端需要实现的页面/组件

### 1. 登录页：「GitHub 登录」按钮

放在登录表单旁边，点击后调用上面的 `loginWithGitHub()`。

```
┌─────────────────────────────┐
│  邮箱: [____________]       │
│  密码: [____________]       │
│  [ 登录 ]                   │
│  ─────── 或 ───────         │
│  [  GitHub 登录  ]          │  ← 这个按钮
└─────────────────────────────┘
```

### 2. 用户设置页：「绑定 GitHub」按钮

已有账号的用户可以通过 OAuth 绑定 GitHub。当前后端仅支持登录/注册场景，绑定功能需要后续扩展 authorize 端点的 `action` 参数。暂时可以预留按钮位置，调用同一个 authorize 接口。

### 3. 回调页：`/oauth/callback`

纯逻辑页面，不需要 UI。加载时读取 URL 参数，存储 token，跳转。

建议加一个 loading 状态：在 token 存储和跳转完成前，显示「正在登录...」之类的提示，避免用户看到空白页。

### 4. 错误页：`/oauth/error`

显示错误信息，提供「重试」和「返回登录」按钮。

## 错误码对照表

后端在回调失败时会重定向到 `/oauth/error?code=xxx`。以下是所有可能出现的错误码：

| 错误码 | 含义 | 前端处理建议 |
|--------|------|-------------|
| `AUTH_013` | OAuth state 校验失败（过期或不存在） | 提示用户重新登录，state 有效期 10 分钟 |
| `AUTH_015` | GitHub API 调用失败（code 换 token 失败、获取用户信息失败等） | 提示「GitHub 授权失败，请重试」 |
| `AUTH_016` | 该 GitHub 账号已绑定其他用户 | 提示「该 GitHub 账号已被其他账号绑定，请使用已有账号登录」 |
| `AUTH_017` | GitHub 账号未绑定（仅在解绑场景出现） | 提示「该 GitHub 账号未绑定任何用户」 |
| `AUTH_018` | 无法解绑最后一个凭证 | 提示「无法解绑，至少需要保留一种登录方式」 |
| `AUTH_004` | 账号被锁定（多次登录失败） | 提示「账号已锁定，请稍后重试」 |
| `AUTH_005` | 账号被停用/删除 | 提示「账号已停用，请联系客服」 |
| `AUTH_006` | 邮箱未验证 | 提示「请先验证邮箱」 |
| `AUTH_019` | 服务条款版本过期 | 跳转到条款接受页面 |
| `OAUTH_PROVIDER_ERROR` | GitHub 返回了错误（用户拒绝授权等） | 提示「GitHub 授权被取消或失败」 |
| `MISSING_OAUTH_PARAMS` | 回调缺少必要参数 | 提示「授权请求异常，请重试」 |
| `INVALID_STATE_ACTION` | state 中的 action 不匹配 | 提示「授权请求异常，请重试」 |
| `OAUTH_INTERNAL_ERROR` | 后端内部错误 | 提示「服务异常，请稍后重试」 |

## 接口速查

| 接口 | 方法 | 说明 | 前端是否调用 |
|------|------|------|-------------|
| `/api/v1/auth/oauth/github/authorize` | GET | 获取 GitHub 授权地址 | 是 |
| `/api/v1/auth/oauth/github/callback` | GET | 处理 GitHub 回调 | 否（GitHub → 后端 → 前端重定向） |
| `/api/v1/auth/oauth/accounts` | GET | 查询当前用户已绑定的 OAuth 账号列表 | 是 |
| `/api/v1/auth/refresh` | POST | 刷新 access token | 是（token 过期时） |

## 注意事项

1. **不要缓存 authUrl**。每次点击登录按钮都应该重新请求 authorize 接口，因为 state 是一次性的。

2. **回调页面要处理 URL 参数残留**。处理完 token 后，用 `history.replaceState` 清掉 URL 中的查询参数，避免 token 泄露到浏览器历史记录。

3. **HTTPS**。生产环境下，`FRONTEND_BASE_URL` 必须是 HTTPS 地址。token 通过 URL 参数传递，明文传输有安全风险。

4. **CORS**。如果前端和后端不在同一个域名下，authorize 接口需要配置 CORS 允许前端域名访问。

5. **移动端 WebView**。如果在 WebView 中使用 OAuth，需要确保 GitHub 授权页面能正常加载，且回调重定向能被 WebView 正确处理。

## 本地开发配置

后端 `.env` 中的前端回调地址默认为：

```
FRONTEND_BASE_URL=http://localhost:5173
```

本地开发时，前端 dev server 启动在 `localhost:5173` 即可自动接收回调。

## 相关文档

- [开发者手册](../../docs/developer-handbook.md) - 错误码、审计事件、异常处理完整参考
- [API 治理](../../docs/api-governance.md) - API 安全等级、限流、幂等性规范
