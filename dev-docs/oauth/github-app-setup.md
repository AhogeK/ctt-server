# GitHub OAuth 应用配置指南

本指南将帮助你创建 GitHub OAuth 应用，并正确配置 CTT Server 后端，实现 GitHub 第三方登录功能。

## 目录

- [概述](#概述)
- [前置条件](#前置条件)
- [第一步：创建 GitHub OAuth 应用](#第一步创建-github-oauth-应用)
- [第二步：配置环境变量](#第二步配置环境变量)
- [第三步：重启服务](#第三步重启服务)
- [配置属性详解](#配置属性详解)
- [OAuth 认证流程](#oauth-认证流程)
- [故障排查](#故障排查)
- [安全建议](#安全建议)

## 概述

CTT Server 使用 GitHub OAuth 2.0 协议实现第三方登录。用户可以通过 GitHub 账号授权登录 Code Time Tracker，无需单独注册账号。

整个流程涉及三个角色：

| 角色 | 说明 |
|------|------|
| **前端应用** | Vue.js Web 界面（`http://localhost:5173`） |
| **CTT Server** | Spring Boot 后端服务（`http://localhost:8080`） |
| **GitHub** | OAuth 认证提供方 |

## 前置条件

- 拥有 GitHub 账号
- CTT Server 后端服务已启动并运行
- Redis 服务正常运行（用于存储 OAuth state，防止 CSRF 攻击）

## 第一步：创建 GitHub OAuth 应用

### 1.1 进入 GitHub 开发者设置

打开浏览器，访问 GitHub 开发者设置页面：

```
https://github.com/settings/developers
```

> **提示**：你也可以通过 GitHub 右上角头像 → Settings → 左侧栏底部 Developer settings → OAuth Apps 进入。

### 1.2 创建新的 OAuth 应用

点击页面中的 **"New OAuth App"** 按钮。

> **截图说明**：页面右上角有一个绿色的 "New OAuth App" 按钮，点击后进入注册表单。

### 1.3 填写应用信息

在注册表单中填写以下信息：

| 字段 | 值 | 说明 |
|------|-----|------|
| **Application name** | `Code Time Tracker` | 应用名称，用户授权时会显示此名称 |
| **Homepage URL** | `http://localhost:5173` | 应用主页地址，生产环境请填写实际域名 |
| **Application description** | `Track your coding time and productivity` | 应用描述（可选） |
| **Authorization callback URL** | `http://localhost:8080/ctt-server/api/v1/auth/oauth/github/callback` | **关键配置**，GitHub 授权后的回调地址 |

> **截图说明**：表单包含四个输入框，分别对应上述四个字段。底部有 "Register application" 绿色按钮。

#### 关于回调 URL 的重要说明

回调 URL（Authorization callback URL）**必须精确匹配** CTT Server 的 OAuth 回调端点。该 URL 的构成规则如下：

```
http://{服务器地址}:{端口}/{context-path}/api/v1/auth/oauth/{provider}/callback
```

各部分含义：

| 部分 | 默认值 | 来源 |
|------|--------|------|
| 服务器地址 | `localhost` | 部署环境决定 |
| 端口 | `8080` | `SERVER_PORT` 环境变量或 `application.yaml` 中 `server.port` |
| context-path | `/ctt-server` | `application.yaml` 中 `server.servlet.context-path` |
| provider | `github` | 路径变量，当前仅支持 `github` |

**为什么必须是这个 URL？**

GitHub 授权完成后，会将用户重定向到此 URL，并附带 `code` 和 `state` 参数。CTT Server 的 `OAuthCallbackController` 会在此端点接收这些参数，完成 token 交换和用户登录/注册流程。如果 URL 不匹配，GitHub 会拒绝重定向并报错。

### 1.4 注册应用

点击 **"Register application"** 按钮完成注册。

### 1.5 获取凭据

注册成功后，页面会显示应用详情：

- **Client ID**：自动生成，直接复制即可
- **Client secrets**：点击 **"Generate a new client secret"** 按钮生成，**立即复制保存**，页面刷新后将无法再次查看

> **截图说明**：应用详情页显示 Client ID（可见）和 Client secrets（需要点击生成按钮）。请妥善保管这两个值。

## 第二步：配置环境变量

打开项目根目录下的 `.env` 文件，更新以下配置：

```bash
# GitHub OAuth 配置
GITHUB_CLIENT_ID=YOUR_GITHUB_CLIENT_ID
GITHUB_CLIENT_SECRET=YOUR_GITHUB_CLIENT_SECRET

# 前端地址（OAuth 成功后的重定向地址）
FRONTEND_BASE_URL=http://localhost:5173
```

将 `YOUR_GITHUB_CLIENT_ID` 和 `YOUR_GITHUB_CLIENT_SECRET` 替换为上一步获取的实际值。

> **注意**：`.env` 文件已被 `.gitignore` 忽略，不会提交到 Git 仓库。请勿将真实凭据提交到版本控制系统。

## 第三步：重启服务

配置完成后，重启 CTT Server 使配置生效：

```bash
# 如果使用 Docker Compose
docker compose down && docker compose up -d --build

# 如果直接运行
./gradlew bootRun
```

重启后，前端应用的登录页面应该会出现 "GitHub 登录" 按钮。

## 配置属性详解

以下是 `application.yaml` 中与 OAuth 相关的所有配置属性：

### ctt.security.oauth.frontend-url

| 项目 | 说明 |
|------|------|
| **类型** | `String` |
| **默认值** | `http://localhost:5173` |
| **环境变量** | `FRONTEND_BASE_URL` |
| **用途** | OAuth 成功/失败后的前端重定向地址 |

OAuth 流程完成后，CTT Server 会将用户重定向到此地址。成功时路径为 `/oauth/callback`，失败时路径为 `/oauth/error`。

完整重定向 URL 示例：
- 成功：`http://localhost:5173/oauth/callback?accessToken=xxx&refreshToken=xxx`
- 失败：`http://localhost:5173/oauth/error?code=AUTH_013`

### ctt.security.oauth.token-encryption-key

| 项目 | 说明 |
|------|------|
| **类型** | `String` |
| **默认值** | 开发环境有默认值 |
| **环境变量** | `OAUTH_TOKEN_ENCRYPTION_KEY` |
| **用途** | OAuth token 加密密钥（AES-GCM） |

用于加密存储在数据库中的 OAuth access token。生产环境必须使用独立的强密钥。

### ctt.security.oauth.github.client-id

| 项目 | 说明 |
|------|------|
| **类型** | `String` |
| **默认值** | 空 |
| **环境变量** | `GITHUB_CLIENT_ID` |
| **用途** | GitHub OAuth 应用的 Client ID |

在 GitHub OAuth 应用详情页获取。用于构建 GitHub 授权 URL。

### ctt.security.oauth.github.client-secret

| 项目 | 说明 |
|------|------|
| **类型** | `String` |
| **默认值** | 空 |
| **环境变量** | `GITHUB_CLIENT_SECRET` |
| **用途** | GitHub OAuth 应用的 Client Secret |

在 GitHub OAuth 应用详情页生成。用于向 GitHub 交换 access token。**必须保密**。

### ctt.security.oauth.github.token-uri

| 项目 | 说明 |
|------|------|
| **类型** | `String` |
| **默认值** | `https://github.com/login/oauth/access_token` |
| **用途** | GitHub token 交换端点 |

用于将授权码（authorization code）交换为 access token。通常无需修改。

### ctt.security.oauth.github.user-info-uri

| 项目 | 说明 |
|------|------|
| **类型** | `String` |
| **默认值** | `https://api.github.com/user` |
| **用途** | GitHub 用户信息 API |

用于获取授权用户的基本信息（用户名、头像等）。通常无需修改。

### ctt.security.oauth.github.user-emails-uri

| 项目 | 说明 |
|------|------|
| **类型** | `String` |
| **默认值** | `https://api.github.com/user/emails` |
| **用途** | GitHub 用户邮箱 API |

用于获取用户的邮箱地址。如果用户未公开邮箱，需要此 API 获取。通常无需修改。

### ctt.security.oauth.github.scope

| 项目 | 说明 |
|------|------|
| **类型** | `String` |
| **默认值** | `read:user,user:email` |
| **用途** | 请求的 GitHub 权限范围 |

| Scope | 说明 |
|-------|------|
| `read:user` | 读取用户基本信息（用户名、头像等） |
| `user:email` | 读取用户邮箱地址 |

如需访问其他 GitHub 资源，可修改此配置。修改后需要用户重新授权。

## OAuth 认证流程

以下是完整的 GitHub OAuth 认证流程：

```
┌─────────────┐         ┌─────────────┐         ┌─────────────┐
│   前端应用    │         │  CTT Server  │         │   GitHub    │
└──────┬──────┘         └──────┬──────┘         └──────┬──────┘
       │                       │                       │
       │  1. 点击"GitHub登录"   │                       │
       │──────────────────────>│                       │
       │                       │                       │
       │  2. 返回授权URL        │                       │
       │<──────────────────────│                       │
       │                       │                       │
       │  3. 跳转到GitHub授权页  │                       │
       │──────────────────────────────────────────────>│
       │                       │                       │
       │  4. 用户授权并确认      │                       │
       │──────────────────────────────────────────────>│
       │                       │                       │
       │  5. GitHub重定向到回调URL│                       │
       │<──────────────────────│───────────────────────│
       │                       │  (带code和state参数)   │
       │                       │                       │
       │                       │  6. 用code换取token    │
       │                       │──────────────────────>│
       │                       │                       │
       │                       │  7. 返回access_token   │
       │                       │<──────────────────────│
       │                       │                       │
       │                       │  8. 获取用户信息        │
       │                       │──────────────────────>│
       │                       │                       │
       │                       │  9. 返回用户信息        │
       │                       │<──────────────────────│
       │                       │                       │
       │  10. 重定向到前端       │                       │
       │  (带accessToken和      │                       │
       │   refreshToken)       │                       │
       │<──────────────────────│                       │
       │                       │                       │
```

### 流程说明

1. **发起授权**：前端调用 `GET /api/v1/auth/oauth/github/authorize`，服务端生成 CSRF state 并返回 GitHub 授权 URL
2. **用户授权**：用户被重定向到 GitHub，登录并授权应用
3. **回调处理**：GitHub 将用户重定向回 CTT Server 的回调 URL，附带 `code` 和 `state` 参数
4. **Token 交换**：服务端使用 `code` 向 GitHub 请求 access token
5. **获取用户信息**：使用 access token 调用 GitHub API 获取用户信息
6. **登录/注册**：根据 GitHub 用户信息自动登录或创建新账号
7. **重定向前端**：将生成的 JWT token 通过 URL 参数重定向到前端

## 故障排查

### "Invalid redirect_uri" 错误

**原因**：GitHub OAuth 应用配置的回调 URL 与实际请求的 URL 不匹配。

**解决方案**：

1. 检查 GitHub OAuth 应用设置中的 "Authorization callback URL"
2. 确保与 CTT Server 实际使用的回调 URL 完全一致（包括协议、域名、端口、路径）
3. 注意检查是否有尾部斜杠差异（`/callback` vs `/callback/`）

```
✅ 正确：http://localhost:8080/ctt-server/api/v1/auth/oauth/github/callback
❌ 错误：http://localhost:8080/ctt-server/api/v1/auth/oauth/github/callback/  (多了尾部斜杠)
❌ 错误：http://localhost:8080/api/v1/auth/oauth/github/callback              (缺少 context-path)
❌ 错误：https://localhost:8080/ctt-server/api/v1/auth/oauth/github/callback (协议错误)
```

### "Bad verification code" 错误

**原因**：授权码（code）无效或已过期。

**可能原因**：

- `client_secret` 配置错误
- 授权码已过期（GitHub 授权码有效期为 10 分钟）
- 授权码已被使用（每个授权码只能使用一次）

**解决方案**：

1. 检查 `.env` 中的 `GITHUB_CLIENT_SECRET` 是否正确
2. 重新发起 OAuth 登录流程，获取新的授权码
3. 确保服务器时间正确（时间偏差可能导致 token 交换失败）

### "The state does not match" 错误

**原因**：OAuth state 验证失败，可能是 CSRF 攻击或 state 已过期。

**可能原因**：

- Redis 服务未运行或连接失败
- state 已过期（state 有效期为 10 分钟，超时后自动失效）
- 用户在多个标签页同时发起 OAuth 登录

**解决方案**：

1. 确认 Redis 服务正常运行：`redis-cli ping` 应返回 `PONG`
2. 检查 Redis 连接配置（`REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`）
3. 重新发起 OAuth 登录流程

### GitHub API 速率限制

**原因**：GitHub API 有请求频率限制。

**限制说明**：

| API | 未认证限制 | OAuth 认证后限制 |
|-----|-----------|----------------|
| `/user` | 60 次/小时 | 5000 次/小时 |
| `/user/emails` | 60 次/小时 | 5000 次/小时 |

**解决方案**：

- 正常使用情况下不会触发限制
- 如果开发调试时频繁测试，可能触发限制
- 等待限制重置（通常为 1 小时）或使用不同的 GitHub 账号测试

### 回调 URL 尾部斜杠问题

**原因**：URL 路径的尾部斜杠不匹配。

**解决方案**：

确保 GitHub OAuth 应用配置的回调 URL 与代码中的路径完全一致：

```
# application.yaml 中的 context-path
server:
  servlet:
    context-path: /ctt-server  # 注意：没有尾部斜杠

# OAuthCallbackController 的路径映射
@RequestMapping("/api/v1/auth/oauth")
@GetMapping("/{provider}/callback")
```

最终 URL：`http://localhost:8080/ctt-server/api/v1/auth/oauth/github/callback`

### 环境变量未生效

**原因**：`.env` 文件修改后未重启服务。

**解决方案**：

Spring Boot 在启动时加载 `.env` 文件。修改后必须重启服务：

```bash
# Docker Compose
docker compose down && docker compose up -d --build

# 直接运行
./gradlew bootRun
```

## 安全建议

1. **保管好 Client Secret**：不要将 Client Secret 提交到版本控制系统，不要在前端代码中使用
2. **使用 HTTPS**：生产环境必须使用 HTTPS，确保 OAuth 流程中的敏感数据不被窃取
3. **限制 Scope**：只申请必要的权限范围，避免过度授权
4. **定期轮换密钥**：定期更换 Client Secret 和 OAuth token 加密密钥
5. **监控异常登录**：关注审计日志中的 OAuth 相关事件，及时发现异常登录行为
6. **生产环境配置**：生产环境使用独立的 GitHub OAuth 应用，不要与开发环境共用

## 相关文档

- [GitHub OAuth 文档](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps)
- [CTT Server README](../../README.md)
- [CTT Server 开发者手册](../../docs/developer-handbook.md)
