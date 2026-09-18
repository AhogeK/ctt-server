# ctt-server 开发计划

> **本文件的分工**（避免日后混用）：
>
> - `.plans/` —— 规划、阶段计划、交付记录、归档；**人机共读，中文**（本文件）
> - `docs/` —— 面向使用者的项目文档，**英文**
> - `memory-bank/` —— AI 自用的记忆与领域知识
>
> 迁移自 Notion《🖥️ ctt-server 开发计划》（2026-09-01 快照），此后以本文件为准。

## 🗺️ 计划总览

```plantuml
@startmindmap
* ctt-server 开发计划
** 基础建设 ✅
*** 工程骨架与统一规范
*** 请求上下文/日志/追踪
*** 审计与安全事件模型
*** 校验/状态机/标准化
*** 安全上下文与接口治理
*** 配置/测试基线/开发手册
** 认证闭环 ✅
*** 注册 + 邮箱验证
*** 登录 + 双 Token 签发
*** Refresh 轮换 + 登出
*** 忘记密码重置
*** 账号锁定策略
*** 端到端集成测试

left side
** 邮件基础设施 ✅
*** Resend 接入
*** Outbox 持久层
*** 模板渲染器
*** 入队幂等
*** 轮询投递 + 退避重试
** 外部身份与凭据 ✅
*** GitHub OAuth 接入
*** 账号绑定/解绑
*** Token 加密与轮换
*** API Key 生命周期
*** Scopes 权限系统
** 数据通路 ✅
*** 设备管理
*** 编码会话双向同步
*** 统计聚合
*** 排行榜与成就
** 运维与可观测性 ⬜
*** 统计接口开放调用
*** 个人统计 Card
*** GitHub 提交信息联动
@endmindmap
```

## 🎯 项目定位

ctt-server 是 code-time-tracker 的后端服务，负责用户认证、编码会话同步、数据统计等核心能力。本文档分为「基础建设」与「后续功能大纲」两部分。

## 🧱 第一阶段：基础建设✅

### 目标与边界

这一阶段的交付物不是「用户能注册登录」，而是一套后续所有认证能力都能复用的底层平台。

**本阶段明确不交付：**

- 邮箱密码注册/登录闭环
- 邮件发送链路实现
- JWT / Refresh Token 签发与轮换
- GitHub OAuth
- API Key 生成与吊销

```dot
digraph ctt_server {
    rankdir=TB;
    node [shape=box, style=rounded, fontname="Helvetica"];
    edge [color="#94a3b8"];

    subgraph cluster_core {
        label="公共底座";
        color="#cbd5e1";
        common [label="common\n响应/异常/错误码/上下文"];
        audit  [label="audit\n审计事件与落库"];
    }
    subgraph cluster_identity {
        label="身份与凭据";
        color="#cbd5e1";
        auth   [label="auth\nJWT/刷新/登出/锁定"];
        user   [label="user\n账号/资料/密码"];
        oauth  [label="auth/oauth\nGitHub 绑定"];
        apikey [label="auth/apikey\nAPI Key 与 scope"];
    }
    subgraph cluster_data {
        label="数据与派生";
        color="#cbd5e1";
        sync_   [label="sync\n会话同步 (Pull/Push)"];
        device [label="device\n设备注册"];
        stats  [label="stats\n统计聚合"];
        leader [label="leaderboard\n排行 (Redis ZSet)"];
        mail   [label="mail\nOutbox 邮件"];
    }

    auth -> common; user -> common; auth -> user;
    oauth -> auth; oauth -> user; apikey -> auth; apikey -> device;
    sync_ -> apikey; sync_ -> device; stats -> sync_;
    leader -> sync_; leader -> stats; mail -> audit;
    stats -> audit; sync_ -> audit;
}
```

### 1：工程骨架与统一规范

**目标：** 把项目从「能写代码」升级成「能长期写代码」

- [x] 重构包结构，拆出 `common`、`auth`、`user`、`device`、`audit`、`mail` 边界模块
- [x] 定义统一响应模型：`ApiResponse<T>`、`ErrorResponse`、分页响应、空响应
- [x] 定义统一错误码体系，按 `COMMON`、`AUTH`、`USER`、`MAIL`、`RATE_LIMIT`、`SECURITY`、`SYSTEM` 分组
- [x] 定义统一异常体系：`BusinessException`、`ValidationException`、`UnauthorizedException`、`ForbiddenException`、`ConflictException`、`TooManyRequestsException`
- [x] 定义接口返回约束：成功结构、失败结构、字段命名、时间格式、trace 字段位置
- [x] 输出《接口风格约定》文档
**交付物：** 统一 API 外壳 / 全局异常处理器 / 错误码枚举与分类文档 / 包结构文档

**验收：** 新建任意测试接口自动返回统一结构；参数校验失败、业务异常、系统异常均能统一出参

### 2：请求上下文、日志、追踪体系

**目标：** 系统如何看见自己

- [x] 实现 `traceId` 生成与透传机制
- [x] 建立请求上下文对象：`traceId`、`clientIp`、`userAgent`、`requestUri`、`method`、`deviceId`
- [x] 接入 MDC，统一日志输出字段
- [x] 设计请求日志、业务日志、错误日志三层日志规范
- [x] 设计脱敏规则：邮箱、token、密码、authorization header、cookie 不允许原样落日志
- [x] 统一异常日志策略：`warn` / `error` / 只做审计不打堆栈
**交付物：** `TraceIdFilter` / `RequestContextHolder` / 结构化日志规范 / 脱敏日志规范 / 错误日志分级策略

**验收：** 任意一次请求都能通过 `traceId` 串起全链路日志；敏感数据不进入普通应用日志

### 3：审计模型与安全事件模型

**目标：** 系统如何留下可追责记录，基于已有的 `audit_logs` 表补完语言体系

- [x] 设计审计事件枚举，先把模型定住
- [x] 定义 `resource_type` 规范取值：`USER` / `EMAIL_VERIFICATION` / `PASSWORD_RESET` / `REFRESH_TOKEN` / `API_KEY`
- [x] 定义 `action` 命名规范，如 `REGISTER_REQUESTED`、`LOGIN_FAILED`、`EMAIL_VERIFICATION_SENT`
- [x] 定义 `details` 的 JSON 结构规范，避免后面写得五花八门
- [x] 实现 `AuditLogService`，让业务只传对象，不直接拼 JSON
- [x] 明确「什么必须审计、什么只记业务日志」
**交付物：** 审计事件字典 / `AuditLogService` / 审计落库统一模型 / 审计字段规范文档

**验收：** 后续任何认证行为都能以统一格式写入 `audit_logs`；审计日志能区分「谁 / 在何时 / 从哪里 / 对什么资源 / 做了什么动作 / 结果如何」

```plantuml
@startuml
!pragma layout smetana
hide empty description
[*] --> PENDING_VERIFICATION : 注册
PENDING_VERIFICATION --> ACTIVE : 邮箱验证通过
PENDING_VERIFICATION --> SUSPENDED : 管理员处置
PENDING_VERIFICATION --> DELETED : 注销
ACTIVE --> LOCKED : 连续失败达阈值
ACTIVE --> SUSPENDED : 管理员处置
ACTIVE --> DELETED : 注销
LOCKED --> ACTIVE : 锁定期满自动解锁
LOCKED --> SUSPENDED : 管理员处置
LOCKED --> DELETED : 注销
SUSPENDED --> ACTIVE : 恢复
SUSPENDED --> DELETED : 注销
DELETED --> [*]
note right of DELETED
  终态：不可再迁移回任何状态（无复活路径）
end note
note bottom of LOCKED
  登录响应在 details 附 retryAfter
end note
@enduml
```

### 4：参数校验、领域规则、状态机基础

**目标：** 系统如何拒绝非法输入与非法状态

- [x] 建 DTO 校验基线：邮箱格式、显示名长度、密码强度、UUID、分页参数等通用规则
- [x] 提炼领域规则校验器，不把所有规则写进 Controller
- [x] 设计用户状态机：`PENDING_VERIFICATION → ACTIVE → LOCKED / SUSPENDED / DELETED` 迁移图
- [x] 设计 Token 状态判定模型：有效、过期、已消费、已吊销、不可用
- [x] 设计统一时间策略：全部使用 `Instant` / `OffsetDateTime`，明确时区处理规范
- [x] 设计统一大小写规范：邮箱入库前标准化，查询统一 `lower(email)`
**交付物：** 通用参数校验组件 / 领域状态机设计文档 / Token 状态判定器 / 时间与标准化规则文档

**验收：** 后续写注册/登录接口时，不需要在业务逻辑里临时发明状态规则；用户状态变更、token 判定、邮箱标准化都有统一入口

### 5：安全上下文、接口治理、限流与幂等框架

**目标：** 接口如何被安全地访问

- [x] 设计 `CurrentUserProvider`，统一读取当前登录用户，不让业务层直接碰 Security 细节
- [x] 设计公开接口与受保护接口的分类模型
- [x] 实现限流框架，支持按 IP、按用户、按邮箱、按接口名四种 key 维度的扩展点
- [x] 实现轻量幂等框架，支持「同一业务操作在短时间内不重复执行」
- [x] 设计客户端身份提取规则，为未来 `devices`、`refresh_tokens`、`api_keys` 接入预留统一上下文
- [x] 定义安全 Header 与请求元信息规范
**交付物：** `CurrentUserProvider` / 限流框架骨架 / 幂等框架骨架 / 接口访问分类清单 / 客户端上下文提取器

**验收：** 新增任意接口时，都能快速声明认证要求、限流要求、幂等要求；未来接入 API Key 与设备时不需重新发明客户端身份模型

### 6：配置规范、测试基线、开发手册固化

**目标：** 把前五周基础层「收口」，让后续开发不靠记忆靠规范

- [x] 建立配置分层：`application.yml`、`application-local.yml`、`application-dev.yml`，敏感配置走环境变量
- [x] 规范安全配置项命名：JWT、邮件、限流、审计、密码策略、锁定策略等提前预留配置位
- [x] 建立测试基线：Controller Slice Test、Service Test、Repository Test、集成测试脚手架
- [x] 接入 Testcontainers，统一 PostgreSQL 测试环境
- [x] 设计 Fixture / Builder 工具，方便后续写用户、token、审计测试数据
- [x] 输出开发手册：如何新增错误码、审计事件、公共异常、受保护接口
**交付物：** 配置规范 / 测试脚手架 / Testcontainers 基线 / 开发手册 / 基础层完成清单

**验收：** 新人接手能根据手册继续开发；新功能开发前不需重新搭建测试环境；认证业务开始时工程环境已稳定

### 基础建设总交付清单

| 交付项 | 结果 |
|---|---|
| 工程分层 | common / auth / user / device / audit / mail 边界清晰 |
| API 规范 | 统一响应、统一错误码、统一异常出口 |
| 可观测性 | traceId、结构化日志、脱敏日志、错误分级 |
| 审计体系 | 审计事件字典、统一审计落库服务 |
| 规则体系 | DTO 校验、领域状态机、标准化规则 |
| 接口治理 | 安全上下文、限流骨架、幂等骨架 |
| 工程保障 | 配置规范、测试基线、开发手册 |

## 🗺️ 第二阶段以后：功能开发大纲

> 这部分是基础建设完成后要继续开展的功能方向，目前不展开细节。

### 🔐 认证闭环 — 细化开发计划✅

> **前置状态：** 邮件基础设施（A–G 全部 ✅）已就绪，`MailOutboxService.enqueueVerificationEmail` / `enqueuePasswordResetEmail` 可直接调用。本阶段复用所有已有基础：`AuditLogService`、限流框架、幂等框架、`UserStateMachine`、`TokenStatusJudge`、`Testcontainers` 基线。

#### H：用户注册 + 邮箱验证✅

**目标：** 新用户提交邮箱密码，系统发出验证邮件，用户点击链接后状态变为 `ACTIVE`

**数据层前置确认：**

- [x] 确认 `users` 表字段完整：`id`、`email`（唯一索引）、`display_name`、`password_hash`、`status`（`PENDING_VERIFICATION` / `ACTIVE` / `LOCKED` / `SUSPENDED` / `DELETED`）、`created_at`、`updated_at`
- [x] 确认 `email_verification_tokens` 表字段（⚠️ 表名是 `email_verification_tokens`，不是 `email_verifications`）：
  - ✅ 核心字段：`id`、`user_id`（FK）、`token_hash`（SHA-256）、`expires_at`、`created_at`
  - ✅ 状态相关：**无 `status` 列**，使用 `consumed_at`（已使用）、`revoked_at`（已撤销）时间戳动态推导状态
  - ✅ 其他字段：`email`、`purpose`（`REGISTER_VERIFY` / `CHANGE_EMAIL`）、`sent_at`、`request_ip`、`user_agent`

- [x] 索引确认：
  - ✅ `token_hash` 唯一索引：`uk_email_verification_token_hash`
  - ✅ 联合索引：`idx_email_verification_lookup (user_id, purpose, expires_at, consumed_at)`（⚠️ 不是 `user_id + status + expires_at`）

**业务实现：**

**1. DTO 层（已有，无需修改）**

- [x] `UserRegisterRequest` DTO 已存在：
  - ✅ `email`：`@Email` 校验，构造函数已做 lowercase 标准化
  - ✅ `password`：`@StrongPassword` 校验（8-72 字符、含大小写、数字）
  - ✅ `displayName`：`@Pattern` 校验（2-50 字符）

**2. 注册服务（需扩展 `UserService`）**

- [x] 扩展 `UserService.registerUser()`：
  - [x] 邮箱唯一性检查（`existsByEmailIgnoreCase`，冲突抛 `ConflictException(ErrorCode.AUTH_002)`）
  - [x] 密码 BCrypt 哈希（`BCryptPasswordEncoder`，`strength=12`）
  - [x] 创建 `PENDING_VERIFICATION` 状态用户，写入 `users`
  - [x] 落审计事件 `REGISTER_REQUESTED`
  - [x] 新增：生成 64 字节随机 token（`SecureRandom`），SHA-256 哈希后存入 `email_verification_tokens`
  - [x] 新增：设置 `expires_at = now() + 24h`（⚠️ 建议统一为 24 小时；`MailOutboxService` 当前默认 15 分钟，需对齐配置）
  - [x] 新增：调用 `MailOutboxService.enqueueVerificationEmail(userId, displayName, email, rawToken)`（⚠️ 注意参数顺序）
  - [x] 整个流程在同一事务内，邮件入队失败则回滚注册

**3. 注册接口（已有，需补充限流注解）**

- [x] `POST /api/v1/auth/register` 已存在（`AuthController.register()`）
- [x] 新增：添加 `@RateLimit(type = RateLimitType.IP, limit = 60, windowSeconds = 3600)`（60 次/小时）
**4. 邮箱验证服务（需新建 `EmailVerificationService`）**

- [x] 创建 `EmailVerificationService.verify(String rawToken)`：
  - [x] `sha256(rawToken)` 查 `email_verification_tokens`（通过 `EmailVerificationTokenRepository.findByTokenHash()`）
  - [x] 不存在 → `UnauthorizedException(ErrorCode.AUTH_005)`
  - [x] 已消费（`consumed_at != null`）→ `UnauthorizedException(ErrorCode.AUTH_005)`
  - [x] 已撤销（`revoked_at != null`）→ `UnauthorizedException(ErrorCode.AUTH_005)`
  - [x] 已过期（`now > expires_at`）→ `UnauthorizedException(ErrorCode.AUTH_006)`
  - [x] 调用 `token.consume()`（⚠️ 不是直接更新 `status = CONSUMED`）
  - [x] 调用 `User.verifyEmail()`（⚠️ 不是直接更新 `users.status = ACTIVE`）
  - [x] 落审计事件 `EMAIL_VERIFICATION_SUCCESS`

**5. 验证接口（需新建）**

- [x] 创建 `EmailVerificationController.verifyEmail(String token)`：
  - [x] `GET /api/v1/auth/verify-email?token=...`（公开接口 `@PublicApi`）
  - [x] 委托 `EmailVerificationService.verify(token)`
  - [x] 成功返回 `200 OK` + `ApiResponse<EmptyResponse>`

**6. 重新发送验证邮件（需扩展）**

- [x] 创建 `EmailVerificationService.resendVerificationEmail(String email)`：
  - [x] 用户必须处于 `PENDING_VERIFICATION` 状态（`UserValidator.assertCanVerifyEmail()`）
  - [x] 吊销旧 token：查询未过期的 token，调用 `token.revoke()`（⚠️ 不是更新 `status = REVOKED`）
  - [x] 生成新 token，调用 `MailOutboxService.enqueueVerificationEmail()`
  - [x] 限流：复用现有 `MailOutboxService` 的限流逻辑（**3 次/1 分钟**，⚠️ 不是 10 分钟 3 次）
  - [x] 落审计事件 `EMAIL_VERIFICATION_SENT`

**7. Repository 层（需新建）**

- [x] 创建 `EmailVerificationTokenRepository`：
  - [x] `Optional<EmailVerificationToken> findByTokenHash(String tokenHash)`
  - [x] `List<EmailVerificationToken> findByUserIdAndPurpose(UUID userId, String purpose)`
  - [x] `boolean existsByUserIdAndPurposeAndConsumedAtIsNull(UUID userId, String purpose)`

**交付物：**

| 类型 | 文件 | 状态 |
|---|---|---|
| Service | `UserService`（扩展） | 已有，需添加 token 生成逻辑 |
| Service | `EmailVerificationService`（新建） | 需创建 |
| Controller | `EmailVerificationController`（新建） | 需创建 |
| Repository | `EmailVerificationTokenRepository`（新建） | 需创建 |
| DTO | `UserRegisterRequest` | ✅ 已有 |
| Entity | `EmailVerificationToken` | ✅ 已有（含 `revoke()` 方法） |
| Migration | `V20260322120000__add_revoked_at_to_email_verification_tokens.sql` | ✅ 已有 |

**验收标准：**

1. ✅ 注册 → 收邮件 → 点链接 → `users.status` 变 `ACTIVE`
2. ✅ 同一邮箱二次注册返回 409（`ConflictException`）
3. ✅ token 过期/伪造/已消费/已撤销返回 401（`UnauthorizedException`）
4. ✅ 1 分钟内重发超 3 次被限流（`TooManyRequestsException`）
5. ✅ **状态推导正确**：`consumed_at != null` → CONSUMED，`revoked_at != null` → REVOKED

#### I：邮箱密码登录 + JWT / Refresh Token 签发✅

**目标：** `ACTIVE` 用户能凭邮箱密码换取 `Access Token + Refresh Token`，双 Token 机制落地

**数据层前置确认：**

- [x] 确认 `refresh_tokens` 表字段（已存在于 `V20260303210000` 迁移脚本）
  - ✅ **无 `status` 列**，Entity 使用 `determineStatus()` 从 `revoked_at + expires_at` 动态推导状态
  - ✅ 实际为 `device_id`（UUID，FK to `devices`）
  - ✅ 实际字段：`id`、`user_id`、`token_hash`、`issued_for`（`WEB` / `PLUGIN`）、`device_id`、`expires_at`、`revoked_at`、`last_used_at`、`created_at`

- [x] 索引已就位：
  - ✅ `uk_refresh_tokens_token_hash`（唯一索引）
  - ✅ `idx_refresh_tokens_user_id`
  - ✅ `idx_refresh_tokens_active`（部分索引）

**配置项落位：**

- [x] `ctt.security.jwt.secret-key`（已存在，走环境变量 `JWT_SECRET_KEY`）
- [x] `ctt.security.jwt.access-token-ttl`（已存在，默认 `15m`，`Duration` 类型）
- [x] `ctt.security.jwt.refresh-token-ttl-web`（已存在，默认 `30d`）
- [x] `ctt.security.jwt.issuer`（已存在，值为 `ctt-identity-provider`）
**业务实现：**

**1. 依赖与基础设施**

- [x] 添加依赖：`implementation("org.springframework.security:spring-security-oauth2-jose")`
- [x] 创建 `RefreshTokenRepository`（继承 `JpaRepository<RefreshToken, UUID>`）
- [x] 扩展 `TokenUtils` 支持 Refresh Token 生成：
  - [x] 复用现有 `generateRawToken()`
  - [x] 复用现有 `hashToken()`

**2. JWT Bean 注册**

- [x] 在 `SecurityConfig` 或独立 `JwtConfig` 中注册 `JwtEncoder` Bean
- [x] 注册 `JwtDecoder` Bean
**3. JWT 提供器**

- [x] 实现 `JwtTokenProvider`：
  - [x] 注入 `JwtEncoder` + `SecurityProperties.JwtProperties`
  - [x] `generateAccessToken(User user)` → 使用 `JwtClaimsSet.builder()` 构建 Claims（`iss`、`sub=userId`、`email`、`iat`、`exp`），通过 `JwtEncoder.encode()` 签发返回 token 字符串

**4. 登录服务**

- [x] 实现 `UserLoginService.login(LoginRequest request, String ip, String userAgent)`：
  - [x] 查用户（`findByEmailIgnoreCase`），不存在 → `UnauthorizedException(ErrorCode.AUTH_001)`（防枚举）
  - [x] 检查 `users.status`：
    - [x] `PENDING_VERIFICATION` → `ForbiddenException(ErrorCode.AUTH_006)`
    - [x] `LOCKED` → `ForbiddenException(ErrorCode.AUTH_004)`
    - [x] `SUSPENDED` / `DELETED` → `ForbiddenException(ErrorCode.AUTH_005)`
  - [x] 调用 `UserValidator.assertLoginAttemptsNotExceeded(user)` 检查锁定
  - [x] BCrypt 校验密码：
    - [x] 失败 → `user.recordFailedLogin(maxAttempts)` + 审计 `LOGIN_FAILED` + `UnauthorizedException(ErrorCode.AUTH_001)`
    - [x] 成功 → `user.recordSuccessfulLogin()` + 调用 `JwtTokenProvider.generateAccessToken()` + 生成 Refresh Token 写入 `refresh_tokens` + 审计 `LOGIN_SUCCESS`

**5. 登录接口**

- [x] 实现 `POST /api/v1/auth/login`：
  - [x] 公开接口：`@PublicApi`
  - [x] 限流：`@RateLimit(type = IP, limit = 30, windowSeconds = 3600)`
  - [x] 返回 `AuthTokenResponse`

**6. Security Filter Chain 配置**

- [x] 在 `SecurityConfig` 的 `SecurityFilterChain` 中开启 JWT 资源服务器支持
- [x] 实现自定义 `AuthenticationEntryPoint`，将 token 无效/过期统一映射到 `ErrorCode.AUTH_003` 的响应格式
**7. DTO**

- [x] 创建 `LoginRequest` DTO（`email`、`password`）
- [x] 创建 `AuthTokenResponse` record：
  - [x] `accessToken`
  - [x] `refreshToken`
  - [x] `accessTokenExpiresIn`
  - [x] `tokenType = "Bearer"`

**交付物清单：**

| 组件 |
|---|
| `RefreshToken` Entity |
| `TokenStatus` Enum |
| `TokenUtils` |
| `User.recordFailedLogin()` |
| `UserValidator` |
| `SecurityProperties.JwtProperties` |
| `ErrorCode (AUTH_001~012)` |
| `AuditAction.LOGIN_SUCCESS / FAILED` |
| `spring-security-oauth2-jose` 依赖 |
| `JwtEncoder` / `JwtDecoder` Bean |
| `RefreshTokenRepository` |
| `JwtTokenProvider` |
| `UserLoginService` |
| `LoginRequest` DTO |
| `AuthTokenResponse` DTO |
| `POST /login` Endpoint |
| `SecurityConfig` oauth2ResourceServer 配置 |
| 自定义 `AuthenticationEntryPoint` |

**验收标准：**

- [x] 正确凭据返回双 Token（`access + refresh`）
- [x] 密码错误返回 `401 (AUTH_001)`，不泄露用户是否存在
- [x] 未验证用户返回 `403 (AUTH_006)`
- [x] 锁定用户返回 `403 (AUTH_004)`
- [x] 用 JWT 访问受保护接口返回 `200`
- [x] 伪造 / 过期 JWT 返回 `401 (AUTH_003)`
- [x] `refresh_tokens` 表写入正确
- [x] 审计日志记录 `LOGIN_SUCCESS / LOGIN_FAILED`

#### J：Refresh Token 轮换 + 主动登出✅

**目标：** Access Token 过期后能无感刷新；用户主动登出后 Refresh Token 立即失效；检测到 Refresh Token 复用时全设备下线

**业务实现：**

- [x] 实现 `TokenRefreshService.refresh(String rawRefreshToken)`：
  - [x] sha256(rawRefreshToken) 查 `refresh_tokens`
  - [x] 不存在 → `AUTH_TOKEN_INVALID`
  - [x] `status = REVOKED` → **检测到复用攻击**：吊销该用户全部 `ACTIVE` Refresh Token，落审计事件 `REFRESH_TOKEN_REUSE_DETECTED`，返回 `AUTH_TOKEN_REUSE_DETECTED`（迫使全量重新登录）
  - [x] `status = EXPIRED` 或 `expires_at < now()` → `AUTH_TOKEN_EXPIRED`
  - [x] 正常：**原 token 置 `REVOKED`**，生成新 Refresh Token 写库（轮换机制），签发新 Access Token
  - [x] 更新 `last_used_at = now()`，落审计事件 `REFRESH_TOKEN_ROTATED`

- [x] 实现 `POST /api/v1/auth/refresh`（公开接口，按 IP 限流：120次/小时）
- [x] 实现 `LogoutService.logout(Long userId, String rawRefreshToken)`：
  - [x] 吊销当前设备 Refresh Token（`status = REVOKED`）
  - [x] 落审计事件 `LOGOUT`

- [x] 实现 `POST /api/v1/auth/logout`（受保护接口，需要有效 JWT）
- [x] 实现 `POST /api/v1/auth/logout-all`（受保护接口）：吊销该用户全部 `ACTIVE` Refresh Token，落 `LOGOUT_ALL_DEVICES`
**交付物：** `TokenRefreshService` / `LogoutService` / `POST /refresh` + `POST /logout` + `POST /logout-all`

**验收：** 正常刷新返回新双 Token，旧 Refresh Token 不可再用；复用旧 Refresh Token 触发全设备下线且审计记录含 `REFRESH_TOKEN_REUSE_DETECTED`；登出后旧 Refresh Token 刷新返回 401

#### K：忘记密码 → 邮件重置链路✅

**目标：** 未登录用户能通过邮件重置密码，链路防枚举、防重放

**数据层前置确认：**

- [x] 确认 `password_resets` 表字段：`id`、`user_id`（FK）、`token_hash`（sha256）、`status`（`PENDING` / `CONSUMED` / `EXPIRED` / `REVOKED`）、`expires_at`、`created_at`
**业务实现：**

- [x] 实现 `PasswordResetService.requestReset(String email)`：
  - [x] 查用户：**无论邮箱是否存在，均返回 200 + 相同响应体**（防枚举）
  - [x] 仅当用户存在且 `status = ACTIVE` 时：吊销旧 `PENDING` 重置 token，生成新 token（64字节，sha256），`expires_at = now() + 1h`，调 `MailOutboxService.enqueuePasswordResetEmail`
  - [x] 落审计事件 `PASSWORD_RESET_REQUESTED`（用户不存在时落 `PASSWORD_RESET_EMAIL_NOT_FOUND`，但响应不区分）
  - [x] 按邮箱限流：10 分钟内最多 3 次

- [x] 实现 `POST /api/v1/auth/password-reset/request`（公开，邮箱限流 3 次/10 分钟）
- [x] 实现 `PasswordResetService.resetPassword(ResetPasswordRequest)`：
  - [x] sha256(token) 查 `password_resets`，校验状态与过期时间
  - [x] 校验新密码不能与当前密码相同（BCrypt 校验）
  - [x] 更新 `users.password_hash`，置 token `status = CONSUMED`
  - [x] **吊销该用户全部 `ACTIVE` Refresh Token**（密码变更后强制重新登录）
  - [x] 落审计事件 `PASSWORD_RESET_COMPLETED`

- [x] 实现 `POST /api/v1/auth/password-reset/confirm`（公开，IP 限流 15 次/10 分钟）
**交付物：** `PasswordResetService` / `PasswordResetTokenRepository` + Integration Tests / `POST /password-reset/request` + `POST /password-reset/confirm`

**验收：** 邮件中链接 1 小时内有效；token 使用后不可复用；重置成功后旧 Refresh Token 全部失效；邮箱不存在时响应与存在时相同（防枚举验证）

#### L：账号锁定策略✅

**目标：** 防暴力破解，失败计数超阈值自动临时锁定，锁定到期自动解锁

**配置项落位：**

- [x] `auth.lockout.max-attempts`（默认 5）
- [x] `auth.lockout.window-seconds`（失败计数滑动窗口，默认 900，即 15 分钟）
- [x] `auth.lockout.lockout-duration-seconds`（默认 1800，即 30 分钟）
- [x] `auth.lockout.storage`（`db` / `redis`，默认 `db`，预留 Redis 快速切换）
**业务实现：**

- [x] 实现 `LoginAttemptService`（解耦失败计数逻辑，与登录主流程松耦合）：
  - [x] `recordFailure(String email, String ip)` → 写 `login_attempts` 表（或 Redis Hash），在滑动窗口内计数
  - [x] `isLocked(String email)` → 检查是否超阈值且仍在锁定窗口内
  - [x] `clearAttempts(String email)` → 登录成功后清零

- [x] `login_attempts` 表字段（DB 方案）：`id`、`email_hash`（sha256，不存原文）、`ip_hash`、`attempt_at`；按 `(email_hash, attempt_at)` 清理过期记录
- [x] 在 `UserLoginService.login` 中集成：登录前先查 `isLocked`，失败后调 `recordFailure`，超阈值时更新 `users.status = LOCKED`，设置 `locked_until` 字段
- [x] 实现定时任务 `AccountUnlockScheduler`（或在查询时懒解锁）：`locked_until < now()` 的 `LOCKED` 用户自动恢复 `ACTIVE`
- [x] 锁定时落审计事件 `ACCOUNT_LOCKED`；解锁时落 `ACCOUNT_UNLOCKED`
- [x] `POST /api/v1/auth/login` 响应被锁定用户时，在 `ErrorResponse.details` 中附 `retryAfter` 时间戳
**交付物：** `LoginAttemptService` / `login_attempts` 表 DDL / `AccountUnlockScheduler` / 锁定配置项 / Service Test（覆盖第 5 次失败触发锁定、锁定窗口到期自动解锁）

**验收：** 连续 5 次密码错误后 `users.status` 变 `LOCKED`；锁定期间任何密码均返回 `AUTH_ACCOUNT_LOCKED` + `retryAfter`；30 分钟后可正常登录；审计记录可追溯完整失败序列

#### M：认证闭环集成测试✅

**目标：** 完整注册 → 登录 → 刷新 → 登出链路有 E2E 自动化保障

- [x] `RegistrationAndVerificationIntegrationTest`：
  - [x] 完整注册 → GreenMail 收邮件 → 提取 token → 验证 → `users.status = ACTIVE`
  - [x] 重复注册同邮箱 → 409
  - [x] 验证 token 过期 → 401；重发验证邮件 → 旧 token 失效，新 token 可用

- [x] `LoginAndTokenIntegrationTest`：
  - [x] 正常登录 → 返回双 Token → 用 Access Token 访问受保护接口 200
  - [x] 错误密码 5 次 → 第 6 次返回 `AUTH_ACCOUNT_LOCKED`
  - [x] 刷新：旧 Refresh Token 换新双 Token → 再用旧 Refresh Token → 触发 `REFRESH_TOKEN_REUSE_DETECTED` → 全设备下线

- [x] `PasswordResetIntegrationTest`：
  - [x] 忘记密码 → GreenMail 收邮件 → 重置 → 旧 Refresh Token 全部失效 → 新密码可登录
  - [x] 1 小时后 token 过期 → 重置返回 401

- [x] `LogoutIntegrationTest`：单设备登出 / 全设备登出，Refresh Token 状态核查
**交付物：** 四组集成测试 / `AuthFixtures`（含注册用户、已激活用户、已锁定用户、活跃 Refresh Token 预设）

**验收：** `./gradlew test` 全绿；所有集成测试可在 CI 中重复执行（Testcontainers 隔离）

#### 认证闭环交付清单

| 交付项 | 核心产出 |
|---|---|
| H：注册 + 邮箱验证 | `UserRegistrationService` / `EmailVerificationService` / 3个接口 |
| I：登录 + Token 签发 | `UserLoginService` / `AuthTokenService` / `JwtAuthenticationFilter` / `/login` |
| J：Token 轮换 + 登出 | `TokenRefreshService` / `LogoutService` / `/refresh` / `/logout` / `/logout-all` |
| K：忘记密码链路 | `PasswordResetService` / `/forgot-password` / `/reset-password` |
| L：账号锁定策略 | `LoginAttemptService` / `AccountUnlockScheduler` / 锁定配置 |
| M：集成测试 | 四组 E2E 测试 + `AuthFixtures` |

### 📧 邮件基础设施 ✅

> **发送服务商：** Resend（已注册）
> SMTP Host: `smtp.resend.com`，Port: `465`，Username: `resend`，Password: API Key（走环境变量）
> ⚠️ 免费额度限制：**100 封/天**，3000 封/月 — 入队幂等保护必须落实，不能浪费额度

#### A：Resend 接入与环境配置✅

**目标：** 打通真实发送链路，本地/测试不污染额度，生产走 Resend

- [x] `application-local.yml` / `application-dev.yml` 接入 **Mailpit**（本地 SMTP 沙箱，Docker Compose 新增服务）
- [x] `application-test.yml` 使用 `GreenMail`（`@SpringBootTest` 集成测试内嵌 SMTP，不真实发送）
- [x] `application-prod.yml` 配置 Resend SMTP：`spring.mail.*` 全走环境变量，API Key 不入代码
- [x] 配置项命名规范落位 `application.yml`：
  - [x] `mail.from.address` / `mail.from.name`
  - [x] `mail.outbox.poll-interval-ms` / `mail.outbox.batch-size` / `mail.outbox.zombie-timeout-seconds`
  - [x] `mail.retry.base-delay-seconds` / `mail.retry.multiplier` / `mail.retry.max-delay-seconds` / `mail.retry.max-attempts`

- [x] Docker Compose 补充 Mailpit 服务配置，本地一条命令启动即可收信
**交付物：** 三环境 SMTP 配置方案 / Mailpit Docker Compose 片段 / 环境变量命名清单

**验收：** 本地启动后向任意地址发送测试邮件，Mailpit Web UI（`localhost:8025`）能收到；`application-prod.yml` 中无任何明文凭据

#### B：Mail Outbox 数据模型与持久层✅

**目标：** 邮件不再是「调用即发」，而是先落库再异步投递，发送链路可观测、可重试

- [x] 确认 `mail_outbox` 表字段完整性：`id`、`recipient`、`subject`、`body_html`、`body_text`、`status`（`PENDING` / `SENDING` / `SENT` / `FAILED` / `CANCELLED`）、`retry_count`、`max_retries`、`next_retry_at`、`sent_at`、`last_error`、`trace_id`、`created_at`、`updated_at`
- [x] 创建 `MailOutbox` JPA Entity，字段映射与枚举 `MailStatus` 对齐
- [x] 实现 `MailOutboxRepository`，添加查询方法：
  - [x] `findPendingJobs(Pageable)` — 查 `status=PENDING AND next_retry_at <= now()`
  - [x] `findByTraceId(String traceId)` — 按 traceId 查投递记录
  - [x] `countByRecipientAndStatusAndCreatedAtAfter(...)` — 防重复投递频率校验

- [x] `MailOutboxRepository` Repository Test 覆盖以上三个查询方法
**交付物：** `mail_outbox` DDL / `MailOutbox` Entity / `MailStatus` 枚举 / `MailOutboxRepository` + 对应测试

**验收：** Repository Test 全绿；`findPendingJobs` 能正确按 `next_retry_at` 筛出到期任务；频率校验查询能区分 10 分钟内已入队与未入队的场景

#### C：邮件模板渲染器✅

**目标：** 邮件内容有统一的 HTML/纯文本双版本，变量注入类型安全

- [x] 选型确认：使用 Thymeleaf（`spring-boot-starter-thymeleaf`），模板放 `resources/mail-templates/`
- [x] 建立模板目录结构：
  - [x] `mail-templates/email-verification.html` — 注册验证邮件
  - [x] `mail-templates/password-reset.html` — 重置密码邮件
  - [x] `mail-templates/layout/base.html` — 公共 Layout（页眉、页脚、品牌色）

- [x] 实现 `MailTemplateRenderer` 接口及 Thymeleaf 实现类：`render(templateName, variables)` / `renderText(templateName, variables)`
- [x] 为每个模板定义强类型 DTO（替代裸 Map）：
  - [x] `EmailVerificationTemplateData(String username, String verificationLink, Duration expiresIn)`
  - [x] `PasswordResetTemplateData(String username, String resetLink, Duration expiresIn)`

- [x] `MailTemplateRenderer` 单元测试：变量正确注入、关键 HTML 结构存在、链接格式正确
**交付物：** 两套 HTML 邮件模板 + 纯文本备用版本 / `MailTemplateRenderer` 接口及实现 / 强类型模板 DTO / 单元测试

**验收：** 单元测试全绿；用 Mailpit 手动触发渲染结果，验证邮件在主流邮件客户端（移动端/桌面端）视觉正常；链接、用户名、过期时间均正确填充

#### D：Mail Outbox Service（写入侧）✅

**目标：** 业务层只需调用「入队」，不关心发送细节；同一操作在短时间内不重复入队

- [x] 实现 `MailOutboxService` 接口，仅负责**写入** Outbox，不触发发送：
  - [x] `enqueueVerificationEmail(Long userId, String email, String token)`
  - [x] `enqueuePasswordResetEmail(Long userId, String email, String token)`
  - [x] 内部调用 `MailTemplateRenderer` 渲染，组装 `MailOutbox` 实体写入

- [x] 入队时自动填充 `trace_id`（从 `RequestContextHolder` 取当前 traceId）
- [x] 入队时设置 `status=PENDING`、`retry_count=0`、`next_retry_at=now()`
- [x] 入队幂等保护：同一 `(recipient, template_type, reference_id)` 在 10 分钟内已有 `PENDING/SENT` 记录则跳过，发布 `MAIL_IDEMPOTENT_SKIP` 审计事件
- [x] `MailOutboxService` Service Test：正常入队、幂等跳过、模板渲染失败回滚
**交付物：** `MailOutboxService` 接口与实现 / 幂等逻辑 / Service Test

**验收：** Service Test 全绿；同一用户在 10 分钟内重复请求发送验证邮件，数据库只存在一条 `PENDING` 记录，第二次调用产生 `MAIL_IDEMPOTENT_SKIP` 审计事件

#### E：Mail Dispatcher + 重试策略（投递侧）✅

**目标：** Outbox 中的 `PENDING` 记录能被自动捞起发送，失败后按指数退避重试，多实例下不重复投递

- [x] 实现 `MailDispatcher`（`@Component`），持有 `JavaMailSender` 执行真实发送
- [x] 实现 `MailOutboxPoller`（`@Scheduled`），轮询并驱动投递：
  - [x] 每 `${mail.outbox.poll-interval-ms:5000}` ms 执行一次
  - [x] 每次最多取 `${mail.outbox.batch-size:20}` 条
  - [x] 取出后立即将 `status` 置为 `SENDING`（乐观锁）
  - [x] 成功：`status=SENT`、`sent_at=now()`，发布 `MAIL_SENT`
  - [x] 失败：进入重试逻辑

- [x] 多实例并发安全：`SENDING` 超时补偿（`updated_at < now()-2min` 的 `SENDING` 记录重置为 `PENDING`）
- [x] 实现 `ExponentialBackoffRetryStrategy`：
  - [x] 基础延迟 `base-delay-seconds`，乘数 `multiplier`，上限 `max-delay-seconds`
  - [x] 抖动 ±10% 防止集群雷同重试
  - [x] 达到 `max-attempts`（默认 5）后 `status=FAILED`，发布 `MAIL_PERMANENTLY_FAILED`

- [x] `ExponentialBackoffRetryStrategy` 单元测试：覆盖 0/1/3/5 次重试的时间窗口边界
**交付物：** `MailDispatcher` / `MailOutboxPoller` / `ExponentialBackoffRetryStrategy` + 单元测试 / 超时补偿逻辑

**验收：** 单元测试全绿；本地启动后写入一条 `PENDING` 记录，5 秒内 Mailpit 收到邮件且数据库状态变为 `SENT`；手动将 SMTP 配置改错，触发发送失败，观察 `retry_count` 递增、`next_retry_at` 符合指数退避规律

#### F：邮件投递审计✅

**目标：** 每一封邮件的完整生命周期都有审计记录，可追责、可排查

- [x] 在 `AuditEvent` 枚举追加：`MAIL_ENQUEUED` / `MAIL_SENT` / `MAIL_RETRY_SCHEDULED` / `MAIL_PERMANENTLY_FAILED` / `MAIL_IDEMPOTENT_SKIP`
- [x] 审计 `details` JSON 规范：
  - [x] 必含：`recipientMasked`（邮箱脱敏）、`templateName`、`mailOutboxId`、`retryCount`
  - [x] 失败场景额外含：`lastError`（截断至 500 字符）

- [x] `AuditFixtures` 补充对应预设数据
**交付物：** 更新后的 `AuditEvent` 枚举 / `details` JSON Schema 描述 / `AuditFixtures` 补充

**验收：** 手动触发入队、发送成功、幂等跳过三个场景，`audit_logs` 表中能查到对应事件且 `details` 字段格式正确；邮箱地址在审计记录中已脱敏

#### G：集成测试✅

**目标：** 邮件链路关键路径有自动化保障，不依赖手动验证

- [x] `MailOutboxPollerIntegrationTest`：
  - [x] 写入 `PENDING` → 触发轮询 → 验证 `status=SENT` 且 GreenMail 收到邮件
  - [x] 模拟 SMTP 不可用 → 验证重试次数递增、`next_retry_at` 符合退避
  - [x] 5 次失败后 → 验证 `status=FAILED` 且 `MAIL_PERMANENTLY_FAILED` 审计落库
  - [x] 并发安全：两线程同时轮询，同一封邮件只发送一次

- [x] `MailTemplateRendererIntegrationTest`：渲染两种模板，断言变量注入正确、链接格式合法
- [x] `MailOutboxServiceIntegrationTest`：正常入队落库 / 幂等入队被跳过且审计事件正确
**交付物：** `MailOutboxPollerIntegrationTest` / `MailTemplateRendererIntegrationTest` / `MailOutboxServiceIntegrationTest`

**验收：** `./gradlew test` 全绿；并发安全测试在重复运行 10 次中无重复投递

### 🔗 OAuth 接入

> **前置依赖：** 认证闭环（H–M）全部 ✅，`UserStateMachine`、`AuditLogService`、`CurrentUserProvider`、`Testcontainers` 基线全部就绪。本阶段复用所有已有基础设施，不重新发明认证框架。
> **设计原则：** OAuth 是「另一种凭据来源」，核心用户实体（`users` 表）保持不变，OAuth 身份通过独立的 `user_oauth_accounts` 表关联。新用户走 OAuth 直接激活（跳过邮箱验证），老用户可自助绑定/解绑 OAuth 提供方。
> ⚠️ 当前仅接入 GitHub，不抽象多 Provider 框架。

#### N：数据模型与基础设施准备✅

**目标：** 确认已有表结构，补充缺失基础设施，为 GitHub OAuth 提供数据底座

**数据层前置确认（`user_oauth_accounts` 表已在 init 迁移中创建）：**

- [x] 表名：`user_oauth_accounts`（⚠️ 不是 `oauth_accounts`）
- [x] 实际字段：`id`（UUID PK）、`user_id`（UUID FK → `users.id` ON DELETE CASCADE）、`provider`（VARCHAR 30）、`provider_user_id`（VARCHAR 255）、`provider_login`（VARCHAR 255）、`provider_email`（VARCHAR 255）、`access_token_encrypted`（TEXT）、`refresh_token_encrypted`（TEXT，可为 NULL）、`token_expires_at`（TIMESTAMPTZ，可为 NULL）、`created_at`（TIMESTAMPTZ）、`updated_at`（TIMESTAMPTZ）
- [x] ⚠️ **无** `scope` 字段、**无** `raw_profile` 字段
- [x] 唯一约束：`uk_user_oauth_provider_uid (provider, provider_user_id)`
- [x] 唯一约束：`uk_user_oauth_user_provider (user_id, provider)`
- [x] 索引：`idx_user_oauth_user_id (user_id)`
- [x] CHECK 约束：`chk_oauth_provider` 当前允许 `GITHUB` / `GOOGLE` / `GITLAB` / `APPLE`
**Flyway 迁移（已在 init 迁移中完成）：**

- [x] ⚠️ **无独立 OAuth 迁移脚本**：`user_oauth_accounts` 表、`chk_oauth_provider` CHECK 约束、`audit_logs` CHECK 约束扩展均在 `V20260303210000__init_base_schema.sql` 中定义
- [x] `audit_logs.chk_audit_resource_type` 已包含 `OAUTH_ACCOUNT` 值（无需 ALTER）
**OAuth State 存储（Redis 方案）：**

- [x] 复用项目现有 Redis 基础设施（`StringRedisTemplate`），不建额外表
- [x] Key 格式：`oauth:state:{state_uuid}`，Value：JSON 序列化的 `OAuthStatePayload`（含 `redirect_uri`、可选 `userId`）
- [x] TTL：10 分钟（`SETEX` 自动过期，零清理成本）
**加密基础设施：**

- [x] 实现 `OAuthTokenEncryptor` 接口（`encrypt(plaintext: String): String` / `decrypt(ciphertext: String): String`）
- [x] 默认实现 `AesGcmTokenEncryptor`：AES-256-GCM，密钥走环境变量 `OAUTH_TOKEN_ENCRYPTION_KEY`（Base64 编码的 32 字节密钥），IV 随机生成并拼接在密文头部
- [x] 在 `SecurityProperties` 中追加 `oauth.token-encryption-key` 配置项
- [x] `AesGcmTokenEncryptor` 单元测试：加密后不可逆读、解密还原正确、不同明文密文不同（IV 随机性）
**Domain 对象：**

- [x] 创建 `UserOAuthAccount` JPA Entity，映射 `user_oauth_accounts` 表（⚠️ 类名与表名一致，不是 `OAuthAccount`）
- [x] 创建 `OAuthProvider` 枚举：**仅保留 `GITHUB`**（⚠️ 移除 `GOOGLE`/`GITLAB`/`APPLE`，当前只做 GitHub 接入，避免噪音）
- [x] 创建 `UserOAuthAccountRepository`：
  - [x] `findByProviderAndProviderUserId(provider, providerUserId): Optional<UserOAuthAccount>`
  - [x] `findByUserIdAndProvider(userId, provider): Optional<UserOAuthAccount>`
  - [x] `findAllByUserId(userId): List<UserOAuthAccount>`
  - [x] `existsByUserIdAndProvider(userId, provider): boolean`

- [x] 创建 `OAuthTokenConverter`（JPA AttributeConverter）：自动加密/解密 `access_token_encrypted` 和 `refresh_token_encrypted` 字段，使用 `AesGcmTokenEncryptor`
- [x] 创建 `OAuthStatePayload` record（`action` / `redirectUri` / `userId`）+ `OAuthStatePayload.Action` 枚举（`LOGIN` / `BIND`）
**审计事件扩展：**

- [x] 在 `AuditAction` 枚举追加：`OAUTH_LOGIN_SUCCESS` / `OAUTH_LOGIN_FAILED` / `OAUTH_ACCOUNT_LINKED` / `OAUTH_ACCOUNT_UNLINKED`
- [x] ⚠️ `OAUTH_TOKEN_REFRESHED` 暂不添加（GitHub token 不过期，Q 阶段不需要刷新逻辑）
**错误码扩展：**

- [x] 在 `ErrorCode` 枚举的 `AUTH` 分组追加 5 个错误码（AUTH_013-018，AUTH_014 已删除）：
  - [x] `AUTH_013` / `OAUTH_PROVIDER_ERROR`（HTTP 502 BAD_GATEWAY）：Provider 返回异常（网络/授权失败）
  - [x] ⚠️ `AUTH_014` 已删除（原 HTTP 401 → 设计错误，decrypt 失败应由 InternalServerErrorException 统一返回 500）
  - [x] `AUTH_015` / `OAUTH_ACCOUNT_ALREADY_LINKED`（HTTP 409 CONFLICT）：该 Provider 账号已绑定其他用户
  - [x] `AUTH_016` / `OAUTH_ACCOUNT_NOT_LINKED`（HTTP 404 NOT_FOUND）：用户尚未绑定该 Provider
  - [x] `AUTH_017` / `OAUTH_CANNOT_UNLINK_LAST_CREDENTIAL`（HTTP 422 UNPROCESSABLE_ENTITY）：用户无密码且只剩一个 OAuth，拒绝解绑
  - [x] `AUTH_018` / `OAUTH_STATE_INVALID`（HTTP 400 BAD_REQUEST）：CSRF state 校验失败

**交付物：** 无独立 Flyway 迁移（init 迁移已覆盖）/ `OAuthTokenEncryptor` + `AesGcmTokenEncryptor` / `OAuthTokenConverter`（JPA Converter）/ `UserOAuthAccount` Entity / `OAuthProvider` 枚举（仅 GITHUB）/ `UserOAuthAccountRepository` / `OAuthStatePayload` record + Action 枚举 / 审计事件 & 错误码扩展

**验收：**

- [x] `./gradlew flywayMigrate` 在 Testcontainers PostgreSQL 上执行成功，`user_oauth_accounts` 表存在，CHECK 约束正确
- [x] `AesGcmTokenEncryptor` 单元测试全绿（6 个测试方法）
- [x] `UserOAuthAccountRepository` Repository Test 全绿（findByProviderAndProviderUserId、唯一约束冲突场景）
- [x] ❌ **无单元测试**：`OAuthTokenConverter`（JPA Converter，源文件存在）、`OAuthStatePayload`（record，源文件存在）

#### O：GitHub OAuth 核心流程实现 ✅

**目标：** 实现完整的 GitHub OAuth 2.0 授权码流程（登录 + 新用户注册），不使用 Spring Security OAuth2 Client 的 Session 机制，改为无状态 JWT 流

**流程设计（无状态方案）：**

```javascript
客户端 → GET /api/v1/auth/oauth/github/authorize
  → 服务端生成 state（UUID，TTL=10min，存 Redis）
  → 返回 JSON {authUrl: "https://github.com/..."}

GitHub → GET /api/v1/auth/oauth/github/callback?code=...&state=...
  → 服务端校验 state（防 CSRF）
  → 用 code 换 GitHub access_token
  → 调用 GitHub API 获取用户信息（id、email、login、name）
  → 走 OAuthLoginOrRegisterService（登录 or 注册）
  → 签发 ctt Access Token + Refresh Token
  → 重定向到前端回调 URL（带 token 参数）
```

**State 管理（防 CSRF）：**

- [x] 实现 `OAuthStateService`（✅ 已实现，N Stage 完成）
  - [x] `generateAndSaveState(payload: OAuthStatePayload): String`：生成 UUID state，序列化 payload 后以 `oauth:state:{uuid}` 为 Key 存入 Redis（TTL=10min）
  - [x] `consumeState(stateId: String): OAuthStatePayload`：原子 GETDEL 读取并删除（防重放），无效则抛 `ForbiddenException(AUTH_013)`
  - [x] `OAuthStatePayload`（record + Action enum LOGIN/BIND）已在 N Stage 实现

- [x] 复用项目现有 `StringRedisTemplate` + `ObjectMapper`（已在 N Stage 完成）
- [x] **验收测试**：Mockito 单元测试覆盖 state 生成、消费、过期、重放、异常、兼容场景（10 个测试用例）✅
**GitHub API 客户端：**

- [x] 实现 `GitHubOAuthClient`（基于 Spring `RestClient` / `WebClient`）：
  - [x] `exchangeCodeForToken(code, state): GitHubTokenResponse`：POST `https://github.com/login/oauth/access_token`
  - [x] `getUserInfo(accessToken): GitHubUserInfo`：GET `https://api.github.com/user`，解析 `id`、`login`、`name`、`avatar_url`、`email`（⚠️ email 可能为 null，需额外调 `/user/emails`）
  - [x] `getUserEmails(accessToken): List<GitHubEmail>`：GET `https://api.github.com/user/emails`，取 `primary=true && verified=true` 的邮箱

- [x] 创建 `GitHubOAuthProperties`：`client-id`、`client-secret`（走环境变量 `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET`）、`scope`（`read:user,user:email`）
- [x] `GitHubOAuthClient` 单元测试（MockServer / WireMock）：正常换 token、用户邮箱为 null 时的回退逻辑、GitHub API 不可用时的异常映射
**登录/注册核心服务：**

- [x] 实现 `OAuthLoginOrRegisterService.process(provider, accessToken, providerUserInfo): LoginResponse`：
  1. [x] 查 `user_oauth_accounts` by `(provider, provider_user_id)`
  2. [x] **命中**（已有绑定）：
    - [x] 加载关联 `users` 记录，校验 `status ∈ {ACTIVE}`（`LOCKED/SUSPENDED/DELETED` 拒绝登录）
    - [x] 更新 `user_oauth_accounts.access_token_encrypted`（AES-GCM 加密）、`provider_login`、`provider_email`、`token_expires_at`
    - [x] ⚠️ 不更新 `raw_profile`（字段不存在）
    - [x] 落审计事件 `OAUTH_LOGIN_SUCCESS`
    - [x] 签发 ctt Access Token + Refresh Token，返回 `LoginResponse`
  3. [x] **未命中**（新用户）：
    - [x] 检查 Provider 返回的 `email` 是否已有本地账号（查 `users` by email）：
      - [x] 若有 → **自动合并**：在 `user_oauth_accounts` 插入绑定记录，落审计事件 `OAUTH_ACCOUNT_LINKED`，然后走上面「已有绑定」逻辑
      - [x] 若无 → **新建用户**：`users.status = ACTIVE`（OAuth 用户跳过邮箱验证），`password_hash = null`（纯 OAuth 用户无密码），`display_name` 取 Provider 的 `name/login`，插入 `user_oauth_accounts`，落审计事件 `OAUTH_LOGIN_SUCCESS`
    - [x] 整个新建流程在同一事务内

**Callback Controller：**

- [x] 实现 `OAuthCallbackController`：
  - [x] `GET /api/v1/auth/oauth/{provider}/authorize`（`@PublicApi`）：生成 state，返回 GitHub 授权 URL（302 或 JSON `{authUrl: "..."}` 供前端跳转）
  - [x] `GET /api/v1/auth/oauth/{provider}/callback`（`@PublicApi`）：校验 state → 换 token → 取用户信息 → 调 `OAuthLoginOrRegisterService` → 重定向前端（`{frontendUrl}/oauth/callback?accessToken=...&refreshToken=...`）或直接返回 JSON
  - [x] 失败时重定向前端错误页（`{frontendUrl}/oauth/error?code=...`）
  - [x] 限流：`@RateLimit(type = IP, limit = 30, windowSeconds = 3600)`

**配置项：**

- [x] `oauth.github.callback-url`：✅ 等效实现（回调 URL 在 Controller 中由请求 URL 动态构造）
- [x] `oauth.frontend-callback-url`：✅ 等效实现（配置名为 `ctt.security.oauth.frontend-url`）
- [x] `oauth.state.ttl-seconds`：✅ 符合（TTL 在 OAuthStateService 中硬编码 `Duration.ofMinutes(10)`）
**交付物：** `OAuthStateService` / `GitHubOAuthClient` / `OAuthLoginOrRegisterService` / `OAuthCallbackController` / `GitHubOAuthProperties` / 相关 DTO（`GitHubTokenResponse`、`GitHubUserInfo`、`GitHubEmail`）

**验收：**

- [x] 全新 GitHub 账号首次授权 → 系统自动创建用户（`status=ACTIVE`，`password_hash=null`），`user_oauth_accounts` 写入正确，返回有效双 Token ✅
- [x] 已有 GitHub 绑定的用户再次授权 → 正常登录，`access_token_encrypted` 更新，审计事件 `OAUTH_LOGIN_SUCCESS` 记录正确 ✅
- [x] GitHub 邮箱与已有本地账号一致 → 自动合并绑定，不创建重复用户 ✅
- [x] state 过期/无效 → 返回 `AUTH_013 ForbiddenException` ✅
- [x] state 重放攻击防护（原子 GETDEL） ✅
- [x] 锁定用户 OAuth 登录 → 返回 403（`AUTH_004`） ✅
- [x] `GitHubOAuthClient` 单元测试全绿 ✅
- [x] `OAuthStateService` 单元测试（10 个测试用例） ✅
- [x] `access_token` AES-256-GCM 透明加密存储（`OAuthTokenConverter` + `AesGcmTokenEncryptor`） ✅
**验收决议：✅ 核心业务完整通过验收（commit 0c0b10f），无遗留待确认项。**

**实现路径差异（不构成阻塞项）：**

1. State 无 HMAC 签名：采用纯 UUID + Redis TTL 方案，Redis 作为 server-side store，防 CSRF 核心语义已满足
2. `GitHubOAuthProperties` 内嵌在 `SecurityProperties` 中：符合 Spring Boot 单一配置树最佳实践

#### P：已有账号绑定 / 解绑 OAuth 提供方 ✅（已完成）

> **实现状态：** ✅ **全部完成**（v0.28.0 BIND + v0.29.0 UNBIND）— 通过 `?action=bind` + `OAuthStatePayload.Action.BIND` + `attachToExistingUser/unbindFromExistingUser` + `OAuthAccountController` 实现。
> 纯 OAuth 用户（无密码）解绑唯一 Provider → 返回 `OAUTH_CANNOT_UNLINK_LAST_CREDENTIAL`
验收报告：`.sisyphus/oauth-binding-acceptance-report.md`
  **目标：** 已登录用户能在账号设置中主动绑定或解绑 OAuth Provider，安全边界清晰
  **绑定流程：**
  - [x] 实现 `OAuthLinkService.initiateLinking(userId, provider): String`：
    - [x] 校验用户已存在且 `status=ACTIVE`
    - [x] 校验该用户尚未绑定该 Provider（已绑定则抛 `OAUTH_ACCOUNT_ALREADY_LINKED`）
    - [x] 生成 state 时在 payload 中携带 `userId`（区分「登录流」与「绑定流」）
    - [x] 返回授权 URL
  - [x] 在 `OAuthCallbackController.callback` 中根据 state payload 判断流程类型：
    - [x] `userId` 存在 → 绑定流：调 `OAuthLinkService.completeLinking(userId, provider, providerUserInfo)`
      - [x] 确认该 `(provider, provider_user_id)` 未被其他用户占用（已占用抛 `OAUTH_ACCOUNT_ALREADY_LINKED`）
      - [x] 插入 `user_oauth_accounts` 记录
      - [x] 落审计事件 `OAUTH_ACCOUNT_LINKED`
      - [x] 重定向前端「绑定成功」页
    - [x] `userId` 不存在 → 登录/注册流（走 O 阶段逻辑）
  **解绑：**
  - [x] 实现 `OAuthLinkService.unlink(userId, provider)`：
    - [x] 查询 `user_oauth_accounts` 确认绑定存在（不存在抛 `OAUTH_ACCOUNT_NOT_LINKED`）
    - [x] **安全校验（最后凭据保护）**：若用户 `password_hash = null`（纯 OAuth 用户）且 `user_oauth_accounts` 只剩此一条 → 拒绝解绑，抛 `OAUTH_CANNOT_UNLINK_LAST_CREDENTIAL`（防止用户将自己锁在系统外）
    - [x] 删除 `user_oauth_accounts` 记录
    - [x] 落审计事件 `OAUTH_ACCOUNT_UNLINKED`
  - [x] 实现 `DELETE /api/v1/auth/oauth/{provider}/link`（受保护接口，需有效 JWT）
  - [x] 实现 `GET /api/v1/users/me/oauth-connections`（受保护接口）：返回当前用户已绑定的所有 Provider 列表（脱敏：只返回 `provider`、`providerUserId` 后四位脱敏、`createdAt`，不返回 token）
  **交付物：** `OAuthLinkService` / `DELETE /api/v1/auth/oauth/{provider}/link` / `GET /api/v1/users/me/oauth-connections` / 「最后凭据保护」逻辑 ✅

实际实现：`OAuthLoginOrRegisterService.attachToExistingUser/unbindFromExistingUser`（合并到登录注册服务） + `DELETE /api/v1/auth/oauth/accounts/{provider}` + `GET /api/v1/auth/oauth/accounts` + 最后凭据保护逻辑已实现
  **验收：**
  - [x] 已登录用户发起绑定 GitHub → 授权后 `user_oauth_accounts` 新增记录，审计事件 `OAUTH_ACCOUNT_LINKED` 正确
  - [x] 同一 GitHub 账号被两个不同 ctt 用户尝试绑定 → 第二次返回 `OAUTH_ACCOUNT_ALREADY_LINKED`
  - [x] 纯 OAuth 用户（无密码）解绑唯一 Provider → 返回 `OAUTH_CANNOT_UNLINK_LAST_CREDENTIAL`
  - [x] 有密码用户解绑 OAuth → 成功，`user_oauth_accounts` 记录删除，审计事件正确
  - [x] `GET /api/v1/users/me/oauth-connections` 返回数据不含任何 token 明文

#### Q：OAuth Token 生命周期管理 ✅（已完成）

**实现状态：** ✅ **全部完成** — Token 加密基础设施、账号生命周期联动、密钥轮换服务均已实现（v0.35.0）。

具体来说：用户 `status → DELETED` 后 `user_oauth_accounts` 记录被 FK 级联删除（验证 FK 行为符合预期）

**验收报告：** `.sisyphus/oauth-token-lifecycle-acceptance-report.md`
  **目标：** Provider access_token 安全存储，账号生命周期联动
  **Token 刷新（简化）：**
  - [x] GitHub access_token **不过期**（除非用户手动撤权），`token_expires_at` 始终为 `null`
  - [x] ⚠️ `OAuthTokenRefreshService` **本阶段不需要实现**，仅保留接口预留。待后续接入第二个 Provider（如 Google）时再实现刷新逻辑
  **账号生命周期联动：**
  - [x] 用户注销账号（`DELETE /api/v1/users/me`，预留接口）或 `status → DELETED` 时：
    - [x] 级联删除 `user_oauth_accounts` 记录（由 FK `ON DELETE CASCADE` 自动处理，无需 Service 层额外清理）✅
  - [x] 管理员暂停用户（`status → SUSPENDED`）时：
    - [x] `user_oauth_accounts` 记录**保留**（不删除），用户再次 OAuth 登录时在 `OAuthLoginOrRegisterService` 中被状态机拦截返回 403 ✅
  **加密密钥轮换（方案设计，不要求本阶段实现，但要留文档）：**
  - [x] 文档化密钥轮换策略（`docs/security-architecture.md:997`）✅
  - [x] 后台任务批量解密再加密（`TokenKeyRotationService`，v0.35.0 新增）✅
  **交付物：**
  - [x] 账号生命周期联动逻辑（FK 级联已覆盖）✅
  - [x] 密钥轮换方案文档 + `TokenKeyRotationService` 实现 + `TokenKeyRotationServiceTest` 测试 ✅
  **验收：**
  - [x] 用户 `status → DELETED` 后 `user_oauth_accounts` 记录被 FK 级联删除 ✅
  - [x] 用户 `status → SUSPENDED` 后 OAuth 登录被 403 拦截，`user_oauth_accounts` 记录未被误删 ✅
  - [x] `TokenKeyRotationService` 单元测试 5/5 通过 ✅

#### ~~R：Provider 扩展适配器框架~~（已移除）

> **移除原因：** 当前只接入 GitHub，抽象 `OAuthProviderAdapter` 接口 + Registry 属于过度设计。待后续真正需要接入第二个 Provider 时再提取。

#### S：OAuth 集成测试 ✅

**实现状态：** ✅ **已完成** — 使用 MockMvc + MockRestServiceServer + Mockito 替代 WireMock（更轻量、Spring 原生）。11 个 OAuth 测试文件，80+ 测试方法，覆盖全部 10 个计划场景。

**目标：** OAuth 核心路径有自动化保障，Mock 替换真实 GitHub API，不依赖网络

**测试工具：**

- [x] 使用 MockRestServiceServer + MockMvc（Spring 内置）替代 WireMock，达到相同目标：OAuth 核心路径有自动化保障，Mock 替换真实 GitHub API，不依赖网络
- [x] 测试数据通过 `@BeforeEach` + `@Nested` 内联创建，覆盖「已有 GitHub 绑定用户」「纯 OAuth 用户（无密码）」「普通密码用户（无 OAuth 绑定）」三类用户
**`OAuthLoginIntegrationTest`（等效实现）：**

- [x] MockRestServiceServer 桩：GitHub `/login/oauth/access_token` 返回 mock token，`/user` 和 `/user/emails` 返回 mock 用户数据（`GitHubOAuthClientTest`）
- [x] 场景 1：全新 GitHub 账号 → 系统创建用户（`status=ACTIVE`，`password_hash=null`），`user_oauth_accounts` 写入，返回有效双 Token（`OAuthLoginOrRegisterServiceTest`）
- [x] 场景 2：已有绑定账号 → 正常登录，`access_token_encrypted` 更新（`OAuthLoginOrRegisterServiceTest`）
- [x] 场景 3：GitHub 邮箱与已有本地账号一致 → 自动合并，`user_oauth_accounts` 插入，不创建新 `users` 行（`OAuthLoginOrRegisterServiceTest`）
- [x] 场景 4：state 过期 → 返回 `OAUTH_STATE_INVALID`（400）（`OAuthStateServiceTest` + `OAuthCallbackControllerMockMvcTest`）
- [x] 场景 5：GitHub API 返回 401（access_token 失效）→ 映射为 `OAUTH_PROVIDER_ERROR`（502）（`GitHubOAuthClientTest` + `OAuthCallbackControllerMockMvcTest`）
- [x] 场景 6：锁定用户 OAuth 登录 → 403（`AUTH_ACCOUNT_LOCKED`）（`OAuthLoginOrRegisterServiceTest`）
**`OAuthLinkingIntegrationTest`（等效实现）：**

- [x] 场景 1：已登录用户绑定 GitHub → `user_oauth_accounts` 新增，审计事件 `OAUTH_ACCOUNT_LINKED`（`OAuthAccountControllerMockMvcTest`）
- [x] 场景 2：同一 GitHub 账号绑定第二个 ctt 用户 → `OAUTH_ACCOUNT_ALREADY_LINKED`（409）（`OAuthAccountControllerMockMvcTest`）
- [x] 场景 3：纯 OAuth 用户解绑唯一 Provider → `OAUTH_CANNOT_UNLINK_LAST_CREDENTIAL`（422）（`OAuthAccountControllerMockMvcTest`）
- [x] 场景 4：有密码用户解绑 OAuth → 成功（`OAuthAccountControllerMockMvcTest`）
**交付物：** `OAuthCallbackControllerMockMvcTest`（14+ tests）/ `OAuthLoginOrRegisterServiceTest`（13+ tests）/ `OAuthAccountControllerMockMvcTest`（11+ tests）/ `OAuthStateServiceTest`（10+ tests）/ `GitHubOAuthClientTest`（15+ tests）/ 其他 6 个测试文件

**验收：**

- [x] `./gradlew test` 全绿，所有 OAuth 测试可在 CI 中重复执行（无真实网络依赖，MockRestServiceServer 完全接管）
- [x] 测试覆盖率：`oauth` 包 80+ 测试方法覆盖全部核心业务逻辑

#### OAuth 接入总交付清单

| 阶段 | 交付项 | 核心产出 | 状态 |
|---|---|---|---|
| N | 数据模型 & 基础设施 | 无独立 Flyway 迁移 / `OAuthTokenEncryptor`  • `AesGcmTokenEncryptor` / `OAuthTokenConverter` / `UserOAuthAccount` Entity & Repository / `OAuthStatePayload` / 审计事件 & 错误码扩展 / Redis State 存储 | ✅ 已完成 |
| O | GitHub OAuth 核心流程 | `OAuthStateService`（✅） / `GitHubOAuthClient`（✅） / `OAuthLoginOrRegisterService`（✅） / `OAuthCallbackController`（✅） | ✅ 已完成（已验证） |
| P | 账号绑定 / 解绑 | `OAuthLinkService` / 绑定接口 / 解绑接口 / 查询已绑定列表接口 / 最后凭据保护 | ✅ 已完成 |
| Q | Token 生命周期管理 | Token 加密基础设施（已实现）/ 账号生命周期联动（已实现）/ 密钥轮换服务（已实现） | ✅ 已完成 |
| ~~R~~ | ~~Provider 扩展框架~~ | ~~已移除~~（仅 GitHub，过度设计。待第二 Provider 时再提取） | ~~已移除~~ |
| S | 集成测试 | 11 个 OAuth 测试文件，80+ 测试方法，MockMvc + MockRestServiceServer 替代 WireMock | ✅ 已完成 |

### ✅ API Key 管理

> **实施状态：** ✅ **全部完成**（v0.36.0–v0.40.1，约 2 周）
> **交付范围：** API Key 完整生命周期（创建 / 哈希存储 / 认证 / 吊销 / 审计 / 集成测试 / 文档）。JetBrains 插件可通过 `Authorization: Bearer cttak_xxx_xxx` 认证同步数据。
> **设计原则：** API Key 是独立凭据，`users` 表保持不变，`api_keys` 表关联用户。Key 永不存明文、SHA-256 哈希入库、raw key 仅创建时返回一次。
> **前置依赖：** OAuth 接入全部完成，认证闭环、审计体系、Redis State 存储、错误码体系已就绪。
> ⚠️ 仅作为 JetBrains 插件认证凭证，不替代 OAuth、不做多租户密钥分发。

#### N：API Key 核心生命周期（CRUD + 端点）✅

**实现状态：** ✅ **全部完成**（v0.36.0）— 68 个测试全部通过，Code Review 修复已完成

**目标：** 实现生产级 API Key 管理，用于 JetBrains 插件认证，镜像 OAuth 接入模块的结构和严谨性

**架构 & 技术栈：** `auth/apikey/` 包，结构与 `auth/oauth/` 一致；Java 25 / Spring Boot 4 / Spring Security 7 / Spring Data JPA / Flyway / JUnit 5 + Testcontainers

**实施计划：** `.sisyphus/plans/2026-07-07-api-key-management.md`（630 行详细步骤）

**验收报告：** `.sisyphus/api-key-phase-n-acceptance-report.md`

**错误码扩展：**

- [x] `AUTH_010` (401) — API Key 无效（格式错误、哈希不匹配）
- [x] `AUTH_011` (401) — API Key 已过期（`expires_at < now()`）— 预留给 Phase O
- [x] `AUTH_012` (401) — API Key 已吊销（`revoked_at IS NOT NULL`）— 预留给 Phase O
- [x] `AUTH_020` (403) — API Key 缺少必要 scope — 预留给 Phase P
- [x] `AUTH_021` (401) — API Key header 格式错误 — 预留给 Phase O
**数据层前置确认：**

- [x] `api_keys` 表已存在（V20260303210000__init_base_schema.sql）
- [x] `devices` 表已存在（设备绑定可选）
- [x] `users` 表已存在（外键关联）
**Domain 对象：**

- [x] **Task N.1: ApiKeyScope enum** — `READ`, `WRITE`, `SYNC`, `ADMIN`，每个带 `String authority`（`ROLE_API_KEY_<SCOPE>` 格式）
- [x] **Task N.2: ApiKeyStatus enum** — `ACTIVE`, `REVOKED`, `EXPIRED`，含 `derive(ApiKey, Instant)` 静态方法，优先级：已吊销 > 已过期 > 活跃
- [x] **Task N.3: ApiKey entity** — JPA 实体，映射 `api_keys` 表，11 列，含生命周期方法 `isActive()`/`revoke(Instant)`/`touchLastUsed(Instant)`，`revoke()` 幂等（保留原始 `revokedAt`）
- [x] **Task N.4: ApiKeyScopeConverter** — JPA 转换器，JSONB 存储，null/blank 安全，防御性 `ensureObjectMapperInitialized` 检查（Code Review H-2 修复空集反序列化 bug）
- [x] **Task N.5: ApiKeyRepository** — Spring Data JPA 仓库，4 个查询方法：`findByKeyHash`（认证查找）、`findAllByUserId`（列表）、`findByIdAndUserId`（BOLA）、`countByUserIdAndRevokedAtIsNull`（限额）
**加密基础设施：**

- [x] **Task N.6: ApiKeyHasher** — SHA-256 哈希 + SecureRandom 生成
  - 复用 `TokenUtils.hashToken`（项目已有 SHA-256 工具）
  - `generateRawKey()` 使用 `SecureRandom` 输出 `cttak_xxx_xxx` 格式（8 字符前缀 + 32 字符密钥，URL-safe Base64）
  - 关键不变量：raw key 永不进 `api_keys` 表，仅 `key_hash` + `key_prefix` 入库
  - `KEY_PREFIX_MARKER = "cttak_"` 设为 `public`（Code Review L-3 修复，供 Service 层引用）

**DTO 层：**

- [x] **Task N.7: CreateApiKeyRequest/Response** — 请求 DTO（`@NotBlank @Size(max=100)` name、`@NotEmpty` scopes、`@Future` expiresAt）/ 响应 DTO（rawKey 仅返回一次 + ApiKeyResponse 快照）
- [x] **Task N.8: ApiKeyResponse/ApiKeysResponse** — 列表查询 DTO，`status` 字段使用 `ApiKeyStatus` 枚举类型（Code Review H-4 修复，原为 String），无 `keyHash` 泄露
- [x] **~~Task N.9: RevokeApiKeyRequest~~** — **已删除**（Code Review M-2 修复：死代码，Controller 从未读取 Body；`revokeApiKey` 接口签名简化为 `revokeApiKey(UUID userId, UUID id)`，与 OAuth UNBIND 流程对称）
**服务层：**

- [x] **Task N.10: ApiKeyService + ApiKeyServiceImpl** — 核心业务逻辑：`createApiKey`（限额 20/用户、生成 key、保存实体、审计 `API_KEY_CREATED`）、`revokeApiKey`（BOLA 检查、幂等吊销、审计 `API_KEY_REVOKED`）
- [x] **Task N.11: ApiKeyQueryService + ApiKeyQueryServiceImpl** — 只读查询服务：`listApiKeys`（按 `createdAt` 降序）、`getApiKey`（BOLA 保护）
**控制层：**

- [x] **Task N.12: ApiKeyController** — REST 端点（`/api/v1/auth/api-keys`）：POST（201 + rawKey）、GET（200 列表）、GET /{id}（200 详情）、DELETE /{id}（204 幂等吊销）。`@RateLimit(USER, 10, 3600)` 限制创建。完整 Swagger `@ApiResponses` + `@ExampleObject`。
**测试（实际 8 个测试文件，68 个测试用例）：**

- [x] **Task N.13: ApiKeyHasherTest** — 9 个测试：格式匹配、确定性、熵验证、已知 SHA-256 向量
- [x] **Task N.14: ApiKeyScopeConverterTest** — 8 个测试：往返转换、null/blank/empty 处理、ObjectMapper 未初始化防御
- [x] **Task N.15: ApiKeyServiceImplTest** — 7 个测试：create 成功/上限/用户不存在、revoke 成功/幂等/不存在/BOLA
- [x] **Task N.16: ApiKeyQueryServiceImplTest** — 5 个测试：空列表、按 createdAt 降序、keyHash 不暴露、get 成功/不存在
- [x] **Task N.17: ApiKeyControllerMockMvcTest** — 20 个测试覆盖 4 个端点组 + BOLA + 校验 + 401 路径
- [x] **Code Review 补充测试（H-3）**：ApiKeyStatusTest（6）、ApiKeyTest（9）、ApiKeyResponseTest（4）
**交付物：** 11 个源文件 + 8 个测试文件（含 Code Review 新增 3 个测试文件）

**Code Review 修复记录（16 项）：**

- C-1: `README.md` + `developer-handbook.md` + `api-governance.md` 文档同步
- H-1: ApiKeyController Javadoc 状态码 "(404)" → "(401)"
- H-2: ApiKeyScopeConverter 空集反序列化 bug 修复
- H-3: 新增 3 个单元测试文件
- H-4: ApiKeyResponse.status 从 String 改为 ApiKeyStatus enum
- M-1: extractPrefix 改用 KEY_PREFIX_MARKER 长度切片
- M-2: revokeApiKey 接口移除 reason 参数 + 删除 RevokeApiKeyRequest
- M-4: ApiKeyScopeConverter 增加 objectMapper null 防御性检查
- L-1: ApiKey entity 移除 @Schema 注解（与 User/Device 一致）
- L-3: ApiKeyHasher.KEY_PREFIX_MARKER 改为 public
**验收：**

- [x] 创建 API Key → 返回 raw key + key_prefix + scopes
- [x] 同一用户创建 20 个 key 后，第 21 个返回 409
- [x] 列出当前用户的所有 API Key（不含 raw key）
- [x] 查询单个 API Key（不含 raw key）
- [x] 吊销 API Key → revoked_at 设置，审计事件记录
- [x] 二次吊销同一 key → 返回 204（幂等）
- [x] 尝试吊销其他用户的 key → 返回 401（BOLA 防护，AUTH_010）
- [x] `./gradlew test --tests "*ApiKey*"` — 68/68 全部通过
- [x] `./gradlew spotlessCheck` — PASS

#### O：API Key 认证管线 ✅

**目标：** 允许 JetBrains 插件使用 `Authorization: Bearer cttak_xxx_xxx` 认证，访问 `/api/v1/sync/**` 端点

**安全模型：**

- [x] **Task O.1: ApiKeyPrincipal** — Security principal record（`model/ApiKeyPrincipal.java`，Java 25 record，含 userId/keyId/scopes）
- [x] **Task O.2: ApiKeyAuthenticationException** — 合理偏离：复用现有 `NotFoundException`/`UnauthorizedException`/`ForbiddenException` + `ErrorCode` 体系，与 JWT 路径一致
- [x] **Task O.3: ApiKeyProperties** — 配置属性（`SecurityProperties.ApiKeyProperties` 嵌套 record，`application.yaml` 同步）
**过滤器：**

- [x] **Task O.4: ApiKeyAuthenticationFilter** — Spring Security 过滤器（`client/ApiKeyAuthenticationFilter.java`，3 层快速跳过，`ex.errorCode()` 非硬编码）
- [x] **Task O.5: ApiKeySecurityConfig** — 配置类（`config/ApiKeySecurityConfig.java`，`@Configuration` + `@Bean`）
**集成：**

- [x] **Task O.6: SecurityConfig integration** — 注入过滤器到 SecurityFilterChain（`addFilterBefore`，CSRF bypass 使用配置属性）
- [x] **Task O.7: 新增审计动作** — `API_KEY_USED`（成功）+ `API_KEY_AUTH_FAILED`（失败），过滤器中实现
**测试：**

- [x] **Task O.8: ApiKeyAuthenticationFilterTest** — 单元测试（8 个用例：透传 x2、认证成功、403 inactive、401 x4）
- [x] **Task O.9: Sync endpoint compatibility check** — 全量 1023 测试通过，0 回归
**交付物：** 4 个源文件 + 1 个测试文件（实际交付）

**验收：**

- [x] 使用有效 API Key 访问 `/api/v1/sync/**` → SecurityContext 包含 ApiKeyPrincipal
- [x] 使用过期 API Key → 返回 401 AUTH_011
- [x] 使用已吊销 API Key → 返回 403 AUTH_012
- [x] 使用无效格式 header → 透传到 JWT 链
- [x] API Key 认证成功 → last_used_at 同步更新
- [x] API Key 认证成功 → SecurityContext 包含 ApiKeyPrincipal（userId/keyId/scopes）
- [x] 使用 JWT 访问 `/api/v1/sync/**` → 仍然正常工作（双轨认证）
- [x] `./gradlew test --tests "*ApiKeyAuthenticationFilter*"` — 8/8 通过
**代码审查：** 5 轮审查，修复 20+ 项（含 AUTH_009→AUTH_022 语义冲突、过滤器错误码屏蔽、CSRF 硬编码、审计日志缺失）

**版本：** 0.36.0 → 0.37.0（MINOR）

#### P：Scopes 权限系统（v0.39.0）✅

**目标：** 在受保护端点强制执行基于 scope 的授权

**实现状态：** ✅ 全部完成

---

**权限映射（Phase O 已实现）：**

- [x] **Task P.1: ApiKeyPrincipal authority mapping** — `ROLE_API_KEY_<SCOPE>` 权限字符串
  - `ApiKeyScope` 枚举定义 4 个 scope：READ、WRITE、SYNC、ADMIN
  - `ApiKeyAuthenticationFilter` 在认证时将 scope 转换为 `SimpleGrantedAuthority`

**注解：**

- [x] **Task P.2: @RequiresApiKeyScope 注解** — 方法级别 scope 校验
  - 新增 `@RequiresApiKeyScope` 自定义注解 (`auth/apikey/security/`)
  - 新增 `ApiKeyScopeAspect` AOP 切面（参数绑定，非硬编码 FQN）
  - JWT 用户自动绕过，API Key 用户检查 scope，ADMIN scope 超越所有

**端点应用：**

- [x] **Task P.3: Apply scope enforcement on endpoints** — 在 8 个端点应用 scope 校验
  - `ApiKeyController`: POST/DELETE=WRITE, GET=READ (4 个端点)
  - `DeviceController`: GET=READ, DELETE=WRITE (2 个端点)
  - `SyncController`: POST pull/push=SYNC (2 个端点，最小实现)

**DTO 验证（Phase N 已实现）：**

- [x] **Task P.4: ApiKeyScope usage in DTOs** — `CreateApiKeyRequest` 已有 `@NotEmpty` scope 验证

---

**交付物：**

| 文件 | 类型 | 说明 |
|---|---|---|
| `RequiresApiKeyScope.java` | 新增 | 自定义注解 |
| `ApiKeyScopeAspect.java` | 新增 | AOP 切面（JWT 绕过 + scope 检查 + 审计） |
| `SyncController.java` | 新增 | 同步端点最小实现（SYNC scope） |
| `SecurityConfig.java` | 修改 | 启用 `@EnableMethodSecurity(prePostEnabled=true)` |
| `AuditAction.java` | 修改 | 新增 `API_KEY_SCOPE_DENIED` 审计事件 |
| `ApiKeyScope.java` | 修改 | ADMIN 超越行为 Javadoc |
| `ApiKeyController.java` | 修改 | 4 个端点添加 scope + 403 OpenAPI |
| `DeviceController.java` | 修改 | 2 个端点添加 scope + 403 OpenAPI |
| `README.md` | 修改 | 同步端点文档 |
| `developer-handbook.md` | 修改 | scope 执行 + 同步端点说明 |

---

**测试覆盖：**

| 测试文件 | 测试数 | 覆盖场景 |
|---|---|---|
| `ApiKeyScopeAspectTest` | 6 | scope 匹配、ADMIN 超越、scope 拒绝、多 scope、JWT 绕过、无认证 |
| `SyncControllerMockMvcTest` | 4 | JWT 访问、未认证 401 |
| `ApiKeyScopeIntegrationTest` | 8 | SYNC→200、READ-only→403、ADMIN→200、JWT 绕过→200 |

---

**验收：**

- [x] Scope enforcement live; 403 when key lacks required scope
- [x] Audit log records scope denials (`API_KEY_SCOPE_DENIED`)
- [x] No regression: WEB JWT users bypass scope check
- [x] 403 response has OpenAPI documentation (AUTH_020 example)
- [x] 集成测试验证完整请求管道（认证过滤器 → scope 切面 → 控制器）

---

**Code Review：** 3 轮审查，修复 8 项（含 OpenAPI 缺失、FQN 类名、@TestPropertySource、测试命名）

**版本：** 0.37.1 → 0.39.0（MINOR）

**验收报告：** `.sisyphus/api-key-phase-p-acceptance-report.md`

#### Q：审计 + 安全事件（v0.40.0）✅

**目标：** 完整的 API Key 生命周期审计追踪，加上限流和防暴破保护

**实现状态：** ✅ 全部完成（Phase O/N/Q）

**审计事件矩阵：**

- [x] **Task Q.1: Audit event coverage matrix** — 覆盖所有生命周期事件（Phase O/N 已实现）

| 事件 | AuditAction | Severity | Resource | Details |
|---|---|---|---|---|
| 创建 | `API_KEY_CREATED` | INFO | `API_KEY` | `keyId, keyPrefix, scopeCount, expiresAt?` |
| 认证成功 | `API_KEY_USED` | INFO | `API_KEY` | `keyId, ipAddress, traceId` |
| 认证失败（吊销） | `API_KEY_AUTH_FAILED` | WARNING | `API_KEY` | `keyId, reason="revoked"` |
| 认证失败（过期） | `API_KEY_AUTH_FAILED` | WARNING | `API_KEY` | `keyId, reason="expired"` |
| 认证失败（格式错误） | `API_KEY_AUTH_FAILED` | WARNING | `API_KEY` | `reason="malformed"` |
| 认证失败（scope不足） | `API_KEY_AUTH_FAILED` | WARNING | `API_KEY` | `keyId, reason="insufficient_scope"` |
| 吊销 | `API_KEY_REVOKED` | INFO | `API_KEY` | `keyId, keyPrefix, reason?` |

**异步审计：**

- [x] **Task Q.2: Async audit emission** — 不阻塞认证路径（Phase O 已实现：AuditEventListener @Async）
**限流：**

- [x] **Task Q.3: Per-IP rate limit on auth path** — 10 次失败/60 秒（Phase Q 已实现：RedisRateLimiter + 修复竞态条件）
**防暴破：**

- [x] **Task Q.4: Brute-force lockout** — per IP，Redis TTL-based（Phase Q 已实现：与 Q.3 合并）
**异常处理：**

- [x] **Task Q.5: GlobalExceptionHandler integration** — 映射异常 → HTTP 状态码（Phase O 已实现）
**交付物：** 8 个文件修改（RedisRateLimiter + ApiKeyAuthenticationFilter + ApiKeySecurityConfig + SecurityProperties + application.yaml + `developer-handbook.md` + 4 个测试文件 + ApiKeyAuthenticationFilterTest 新增 429 测试）

**验收：**

- [x] 所有审计事件按正确的 severity 和 details 发出
- [x] 防暴破保护：10 次失败/60 秒 per IP 触发 429
- [x] `lastUsedAt` 写入异步（AuditEventListener @Async），不阻塞认证路径
- [x] 无 raw key 出现在任何审计行（仅 keyId/keyPrefix）
- [x] RedisRateLimiter 竞态条件已修复（先增后查）
- [x] 集成测试：`./gradlew test --tests "*ApiKeyAuthenticationFilterTest"` — 9/9 PASS

#### R：集成测试（API Key）

**目标：** 完整生命周期覆盖：create → use → revoke，使用真实 Spring 上下文和 Testcontainers PostgreSQL

**测试场景：**

- [x] **Task R.1: ApiKeyIntegrationTest** — 6 个 E2E 场景

| 场景 | 描述 |
|---|---|
| happy_path | create → authenticate → use → revoke 全流程 |
| revoke | 吊销后使用 → 401 AUTH_012 |
| expire | 创建 key with expiresAt = now + 1s → 时间旅行 → 401 AUTH_011 |
| scope_deny | 创建 key with only READ scope → 访问 SYNC 端点 → 403 AUTH_020 |
| BOLA | User A 创建 key，User B 尝试吊销 → 404 |
| rate_limit | 11 次失败认证/60s → 第 11 次返回 429 |

**覆盖率：**

- [x] **Task R.2: Coverage verification ✓ INSTRUCTION 93.5% / BRANCH 83.5%** — instruction ≥ 85%, branch ≥ 80%
**基线：**

- [x] **Task R.3: Test baseline ✓** — `./gradlew test --tests "*TestBaselineSmokeTest"` — green
**交付物：** 1 个集成测试文件

**验收：**

- [x] `./gradlew test --tests "*ApiKeyIntegration*" ✓ 7/7 PASS` — 全部通过
- [x] E2E 场景：create → authenticate → use → revoke 全流程 ✓
- [x] 测试覆盖率：INSTRUCTION 93.5% / BRANCH 83.5%（远超阈值）✓

---

**完成总结（v0.40.1）**

**核心交付物**

- 1 个集成测试文件：`src/test/java/com/ahogek/cttserver/auth/apikey/ApiKeyIntegrationTest.java`（454 行，6 个 E2E 场景 + 7 个测试方法）
- 5 个原子化提交（fix / test / docs / chore: bump version / docs(memory-bank)）
- 4 个非 AI commit 已 cherry-pick 到 master（零冲突）；1 个 AI memory-bank commit 留在 develop
- 全量测试：1041 tests / 0 failed；coverage INSTRUCTION 93.5% / BRANCH 83.5%；spotlessCheck PASS；build PASS
**顺手修复的 4 个 Phase N/O 隐藏 bug**（执行集成测试时暴露）

| # | Bug | 根因 | 修复 |
|---|---|---|---|
| 1 | jsonb converter | `@Convert(String) + columnDefinition="jsonb"` 在 Hibernate 7 下触发 PSQLException | `@JdbcTypeCode(SqlTypes.JSON)`（与 AuditLog/MailOutbox 一致） |
| 2 | filter order | ApiKeyAuthenticationFilter 实际放在 BearerTokenAuthenticationFilter 之后 | `addFilterBefore(apiKeyFilter, BearerTokenAuthenticationFilter.class)` |
| 3 | double JWT parse | JWT 过滤器对 cttak_\* token 仍尝试解析 | 新增 `ApiKeyAwareBearerTokenResolver`（注入 SecurityProperties + import static KEY_PREFIX_MARKER） |
| 4 | CurrentUserProvider | 不识别 ApiKeyPrincipal | ApiKeyPrincipal 重构为嵌入 CurrentUser + from() factory |

**项目一致性决策**

- BOLA：任务描述 404 → 实际 401 AUTH_010（BOLA 防护语义，防止 UUID 枚举）
- Revoke：任务描述 401 → 实际 403 AUTH_012（ErrorCode 定义）
- 删除 ApiKeyScopeConverter 死代码（无 @Convert 引用）
**Code Review**：3 CRITICAL + 3 MAJOR 修复；scope blast 验证 0 个同类 latent bug

**版本**：0.40.0 → 0.40.1（PATCH）

**远端**：develop 5 commits / master 4 commits（R17 验证 clean）

**状态**：✅ Phase R 完成 + 4 bug 修复 + review 修复 + 文档同步 + 远端推送

#### S：文档 + UI 集成

**目标：** 端到端文档化新模块；与 `ctt-web` 协调 "API Keys" 设置页面

**开发者文档：**

- [x] **Task S.1: ✓ 已完成** — `developer-handbook.md` — 错误码注册表 + 审计事件 + API Key 认证章节
- [x] **Task S.2: ✓ 已完成** — `api-governance.md` — API Key 认证安全层级 + 限流策略
**用户文档：**

- [x] **Task S.3: ✓ 已完成** — `README.md` — API 端点表 + 技术栈确认
**前端集成：**

- [x] **Task S.4: ✓ 已完成** — `dev-docs/apikey/frontend-integration.md` — 前端集成指南
**OpenAPI：**

- [x] **Task S.5: ✓ 已完成OpenAPI examples** — 完整的 Swagger 示例审查
**交付物：** 4 个文档更新 + 1 个新文档

**验收：**

- [x] `docs/developer-handbook.md` 包含新审计动作和错误码 ✓
- [x] `docs/api-governance.md` 记录 API Key 认证和限流策略 ✓
- [x] `README.md` API 端点表包含 API Key 管理端点 ✓
- [x] 所有 DTO 的 `@Schema(description, example)` 注解完整 ✓
- [x] Raw key 不出现在任何日志/审计/DTO 中（除创建时）✓

---

**完成总结**

S.1~S.3/S.5 已在 Phase N/O/P/Q/R 累积完成，无需额外修改。

- `developer-handbook.md`：29 个错误码引用、32 处 API Key 文档、认证章节、审计事件、限流、scope 强制、filter order 均在 Phase N/O/P/Q/R 累积完成
- `api-governance.md`：Tier 2/4 已包含 API Key 端点分类和限流策略
- `README.md`：端点表、认证流程（含 Resolver + Principal）、Error Codes 表、技术栈均已更新
- OpenAPI 示例：Controller 39 个 @Schema/@ApiResponse 注解，Review 阶段已通过
S.4 为唯一新增交付物：

- `dev-docs/apikey/frontend-integration.md`（230 行）
- 创建/列表/吊销流程、错误码映射、状态显示指南、安全注意事项
- 审查后修复 7 项问题（缺失 timestamp、缺 429 错误码、expiresAt 不一致、@Future 约束说明、错别字、相关文档脚注）
版本：0.40.1（不变，dev-doc 非生产代码）

状态：✅ S.1~S.5 全部完成 + 已提交（develop + master cherry-pick）✓

#### API Key 管理总交付清单

| 阶段 | 交付项 | 核心产出 | 说明 | 状态 |
|---|---|---|---|---|
| N | API Key 核心生命周期 | `ApiKeyScope` / `ApiKeyStatus` / `ApiKey` Entity / `ApiKeyRepository` / `ApiKeyHasher` / DTOs（5 records）/ `ApiKeyService` / `ApiKeyQueryService` / `ApiKeyController` | 建立 API Key 完整 CRUD 能力：用户可创建、列出、查询、吊销 API Key；raw key 仅在创建时返回一次，此后只保留 SHA-256 哈希，杜绝明文泄露 | ✅ 已完成（v0.36.0） |
| O | API Key 认证管线 | `ApiKeyPrincipal`（嵌入 CurrentUser）/ `ApiKeyProperties` / `ApiKeyAwareBearerTokenResolver` / `ApiKeyAuthenticationFilter` / `ApiKeySecurityConfig` / `SpringSecurityCurrentUserProvider`（扩展 ApiKeyPrincipal 分支） | 让 JetBrains 插件能以 `Authorization: Bearer cttak_xxx_xxx` 方式同步数据；认证成功后将 `ApiKeyPrincipal` 注入 SecurityContext；`lastUsedAt` 同步写入；addFilterBefore(BearerTokenAuthenticationFilter) 确保 API Key 在 JWT 之前处理；`ApiKeyAwareBearerTokenResolver` 对 `cttak_*` 返回 null 避免 JWT 解析；当集成测试时暴露 4 个隐藏 bug（jsonb converter、filter order、double JWT parse、CurrentUserProvider 识别） | ✅ 已完成 |
| P | Scopes 权限系统 | `@RequiresApiKeyScope` 注解 + `ApiKeyScopeAspect` AOP 切面 / `@JdbcTypeCode(SqlTypes.JSON)`（替代 `ApiKeyScopeConverter`）/ sync 端点 scope 应用 / JWT 用户自动绕过 | 在受保护端点强制执行基于 scope 的细粒度授权；创建 key 时声明 scopes，认证时校验 scopes，缺少必要权限返回 `403 AUTH_020`；`ApiKeyScopeConverter` 已删除（死代码，`@JdbcTypeCode(SqlTypes.JSON)` 替代） | ✅ 已完成 |
| Q | 审计 + 安全事件 | `AuditAction` 扩展（`API_KEY_CREATED/USED/AUTH_FAILED/REVOKED`）/ 异步审计发射 / per-IP 限流（10 次失败/60 秒）/ 防暴破 Redis TTL 方案 | 覆盖 API Key 完整生命周期的审计追踪；认证失败分 revoked / expired / malformed / insufficient_scope 四类原因落库；per-IP 暴力破解防护，超阈值返回 429；raw key 永不出现在任何审计行 | ✅ 已完成 |
| R | 集成测试 | `ApiKeyIntegrationTest`（E2E 6 个场景）/ 覆盖率验证（instruction ≥ 85%，branch ≥ 80%）/ TestBaselineSmokeTest | 完整 E2E 链路：create → authenticate → use → revoke；BOLA 越权返回 401（非 404，BOLA 防护语义防 UUID 枚举）、吊销后返回 403（非 401，ErrorCode 定义）、过期 401、scope 不足 403、限流 429 共 6 个关键边界场景；使用 Testcontainers PostgreSQL，1041 tests / 0 failed，coverage 93.5% / 83.5% | ✅ 已完成 |
| S | 文档 + UI 集成 | `developer-handbook.md` 更新 / `api-governance.md` 更新 / `README.md` 端点表更新 / `dev-docs/apikey/frontend-integration.md` 新建 / 所有 DTO `@Schema` 注解 | 端到端文档化新模块：`developer-handbook.md` 补充错误码/审计事件/认证章节/限流/scope 强制/filter order；`README.md` 补充认证流程/Error Codes表；新建前端集成指南（230行）；审查后修复7项问题 | ✅ 已完成 |

**关键决策：**

- 异步 lastUsedAt 写入（\< 5ms 延迟预算）
- 双轨认证（JWT 或 API Key）
- per-user 20 个 key 上限
- 复用 `TokenUtils.hashToken` (SHA-256)
- 复用 `ErrorCode.AUTH_010/011/012`
**新增 ErrorCode：**

- `AUTH_020` (403 scope 不足)
- `AUTH_021` (401 header 格式错)
**新增 AuditAction：**

- `API_KEY_USED`
- `API_KEY_AUTH_FAILED`

### 📱 设备管理

> **前置依赖：** 认证闭环（H–M）、API Key 管理 。设备模型已在 `devices` 表中预留，本阶段已激活并与同步模块联动（插件需求驱动，v0.48.0 完成）。

#### D1：设备模型激活 ✅

**实现状态：** ✅ **全部完成**（v0.48.0）— devices 表激活（此前生产路径零写入点），全量 1185 tests / 0 failed，双轴 Code Review 通过

**目标：** 激活 `devices` 表（init 迁移已预置），建立可支撑设备注册与同步校验的领域模型。

**架构 & 技术栈：** `device/` 包（entity/repository/service/dto/controller）+ `auth/apikey`（ApiKey 补全 device 关联）

**子任务：**

- D1.1：`Device` Entity — 移除 `@GeneratedValue`（id 是客户端分配的 deviceId，非 DB 生成）+ `@Version`（null 初始化，Spring Data 判新建走 persist）；修复 save() 对客户端 id 走 merge 的 StaleObjectStateException
- D1.2：`DeviceRepository` — `findById` / `findByIdAndUserId`（BOLA）/ `findByUserIdOrderByLastSeenAtDesc`
- D1.3：`ApiKey.device` 关联补全 — 实体已有 `@ManyToOne Device` + `api_keys.device_id` 列（半成品），补 getter/setter 使业务层可用
- D1.4：迁移回填 — `devices.version` 列直接融入 init 迁移（开发阶段回填策略，无独立迁移）
**关键设计决策：**

- 客户端分配 ID 的 JPA 模式：无 @Version 时 save() 对非 null id 走 merge（Hibernate 对 DB 无行的 detached 实体抛 StaleObjectStateException）；@GeneratedValue + 非 null id 又触发"uninitialized version"拒绝 → 正解 = 移除 @GeneratedValue + @Version(null)
- 与登录解耦：devices 记录不再依赖登录流程（refresh_tokens.device_id 仅是跟踪字段）
**验收标准：**

- [x] Flyway validate 通过（devices.version 列与实体一致）
- [x] 客户端 id 设备可正常持久化（persist 路径）
**验证：** 全量 1185 tests / 0 failed；jacoco PASS；spotless PASS；LSP 0 errors

#### D2：设备注册端点（POST /devices）✅

**实现状态：** ✅ **全部完成**（v0.48.0）— 插件需求落地（方案 B 显式注册），全量 1185 tests / 0 failed

**目标：** 提供设备注册能力——插件绑定 SYNC key 后注册本设备，消除首次同步 COMMON_002 失败。

**子任务：**

- D2.1：`RegisterDeviceRequest` — deviceId(必填 UUID)/deviceName(255)/platform(50)/ideName(100)/ideVersion(50)/appVersion(50)，@Size 对齐列宽
- D2.2：`DeviceService.registerDevice` — upsert 语义（未注册创建/已注册更新元数据+lastSeenAt+lastIp）；归属冲突 409 DEVICE_001；API key 认证时绑定 key↔device（api_keys.device_id 更新到最新）；审计 DEVICE_LINKED
- D2.3：`DeviceController POST /devices` — SYNC scope（JWT 绕过）+ @RateLimit(USER, 10/3600) + Swagger 完整（400/401/403/409/429 独立示例）
- D2.4：方案裁决 — 需求报告方案 A（key 创建带 deviceId）判定不可行（创建 key 端点需 JWT/WRITE，Web 控制台无插件 deviceId）；采纳方案 B
**关键设计决策：**

- key↔device 绑定补全半成品结构：api_keys.device_id 列已存在但业务层从未使用，注册时写入实现"绑定 key 即绑定设备"
- 归属冲突新增 DEVICE_001（409）：现有码无匹配（COMMON_002=404 语义不符）
- 审计复用闲置的 DEVICE_LINKED + 新增 ResourceType.DEVICE（枚举遗漏补全）
**验收标准：**

- [x] 插件绑定 SYNC key 注册后，GET /devices 可见该设备
- [x] 以该 deviceId 执行 pull/push 不再 COMMON_002
- [x] 归属冲突 → 409 DEVICE_001
**验证：** 全量 1185 tests / 0 failed（DeviceServiceTest 6 + DeviceControllerMockMvcTest 8 + DeviceRegistrationIntegrationTest 7）

#### D3：设备查询与管理（GET/DELETE）✅

**实现状态：** ✅ **全部完成**（v0.48.0）— 查询 scope 语义修正 + 吊销保留，全量 1185 tests / 0 failed

**目标：** 提供设备列表查询（插件设备状态检查）与吊销（管理操作）。

**子任务：**

- D3.1：`GET /devices` — scope 从 READ 改为 READ 或 SYNC（@RequiresApiKeyScope 多值任一语义），插件 SYNC key 可查、READ key 保留读能力
- D3.2：`@RequiresApiKeyScope` 扩展 — 单值 → 多值"任一"匹配；Aspect 审计参数多值 join；现有单值使用点全兼容
- D3.3：`DELETE /devices/{deviceId}` — 保持 WRITE scope，吊销设备（撤销 refresh tokens，记录保留审计）
**关键设计决策：**

- 读操作不剥夺 READ 语义：GET 支持 READ 或 SYNC（避免 READ key 能力退化 + 插件 SYNC 可用）
- DeviceController 三方法统一走 CurrentUserProvider（兼容 ApiKeyPrincipal，替代 @AuthenticationPrincipal）
**验收标准：**

- [x] SYNC key 与 READ key 均可查询设备列表
- [x] 设备吊销仅 WRITE scope（插件不具备）
**验证：** 全量 1185 tests / 0 failed

#### 设备管理总交付清单

| 阶段 | 交付项 | 核心产出 | 说明 | 状态 |
|---|---|---|---|---|
| D1 | 设备模型激活 | `Device` Entity（@Version + 客户端 id）/ `DeviceRepository` / `ApiKey.device` 关联补全 / 迁移回填 | 激活 devices 表（此前生产路径零写入点）；修复客户端分配 ID 实体的 JPA 持久化（merge 陷阱）；补全 api_keys.device_id 半成品 | ✅ 已完成（v0.48.0） |
| D2 | 设备注册端点 | `POST /devices` / `RegisterDeviceRequest` / `DeviceService.registerDevice`（upsert+key 绑定）/ `DEVICE_001` | 插件绑定 SYNC key 后注册本设备；key↔device 绑定实现"绑定即注册"；归属冲突 409；审计 DEVICE_LINKED | ✅ 已完成（v0.48.0） |
| D3 | 设备查询与管理 | `GET /devices`（READ 或 SYNC）/ `DELETE /devices/{id}`（WRITE）/ `@RequiresApiKeyScope` 多值任一 | 插件设备状态检查（SYNC key 可查）+ Web 管理吊销；scope 语义扩展不破坏既有单值使用 | ✅ 已完成（v0.48.0） |

**关键决策：**

- 方案 B（显式 POST /devices 注册）取代报告方案 A（key 创建带 deviceId，Web 端无插件 deviceId 不可行）
- 设备 id = 客户端分配（插件 UserManager.getUserId()），非 DB 生成
- key↔device 绑定（api_keys.device_id）补全半成品结构
- GET 查询同时接受 READ 或 SYNC（读操作保留 READ 语义）
**新增 ErrorCode：**

- `DEVICE_001`（409 设备已注册到其他用户）
**新增/复用 Audit：**

- 复用 `DEVICE_LINKED`（此前闲置）
- 新增 `ResourceType.DEVICE`

```plantuml
@startuml
autonumber
participant "JetBrains 插件" as P
participant "API Key 过滤器" as F
participant "SyncController" as C
participant "SyncPush/PullService" as S
database "PostgreSQL" as DB
participant "ConflictResolver" as R

P -> F : POST /sync/push\nBearer cttak_*  (SYNC scope)
F -> C : ApiKeyPrincipal(userId, keyId, scopes)
C -> S : push(deviceId, sessions[])
S -> DB : 校验 deviceId 归属（BOLA，非本人 → 404）
loop 每条会话
  S -> DB : 按 (user, session_uuid) 取现行（含软删）
  S -> R : resolve(existing, incoming)
  R --> S : APPLY_INCOMING / APPLY_DELETE / KEEP_EXISTING
  S -> DB : 字段应用 + bumpServerVersion + 写 session_changes
end
S -> DB : 单事务提交（部分应用永不发生）
S --> C : nextCursor = 用户最大 change_id
C --> P : 200 + 变更日志水位

P -> F : POST /sync/pull\n{deviceId, lastPulledChangeId}
F -> C : ApiKeyPrincipal
C -> S : pull(deviceId, cursor)
S -> DB : 有效游标 = max(持久化水印, 客户端游标)
S -> DB : WHERE change_id > 游标 ORDER BY change_id ASC
S -> DB : advancePullWatermark（原子 upsert，单调不回卷）
S --> P : 增量变更 + 胜出会话快照（无新增 → 空列表，幂等）
@enduml
```

### 🔄 编码会话同步

> **前置依赖：** API Key 管理 、设备管理 。JetBrains 插件通过 API Key 认证，定期将本地编码会话上报到服务端。

#### T：数据模型与持久层 ✅

**实现状态：** ✅ **全部完成**（v0.45.0）— 全量 1126 tests / 0 failed，双轴 Code Review 通过（Standards PASS + Spec COMPLIANT）

**目标：** 激活 `coding_sessions` / `session_changes` / `sync_cursors` 三张已在 init 迁移中预置的表，建立可支撑双向同步的领域模型。

**架构 & 技术栈：** `sync/` 包，含 `entity/` / `enums/` / `repository/`；Java 25 / Spring Boot 4 / Spring Data JPA / Flyway / JUnit 5 + Testcontainers

**子任务：**

- T1：`CodingSession` Entity — 映射 `coding_sessions` 表（`session_uuid` / `user_id` / `project_name` / `language` / `start_time` / `end_time` / `client_modified_at` / `client_version` / `server_version` / `updated_by_device_id` / `is_deleted` / `deleted_at`），LWW 冲突解析所需字段齐备；域方法 `softDelete`（幂等保留 deleted_at）/ `restore` / `bumpServerVersion`；唯一约束 `uk_coding_sessions_user_session_uuid (user_id, session_uuid)`
- T2：`SessionChange` Entity + `ChangeOp` 枚举 — 映射 `session_changes` 表（`change_id` BIGSERIAL / `user_id` / `device_id` 可空 / `session_id` FK→coding_`sessions.id` / `op` / `server_version` / `happened_at`）；`op` 枚举值严格对齐 `chk_session_change_op` CHECK（UPSERT / DELETE）
- T3：`SyncCursor` Entity + `SyncCursorId` 复合主键 — 映射 `sync_cursors` 表（复合 PK `(user_id, device_id)` / `last_pulled_change_id` / `last_push_at` / `updated_at`），每设备一条；`advancePullWatermark` 单调守护（永不回退）
- T4：三个 Repository（`CodingSessionRepository` / `SessionChangeRepository` / `SyncCursorRepository`）— 按 user/device 维度查询、按 change_id 增量拉取（`WHERE change_id > :cursor` + per-user 隔离 + 升序）、软删除过滤；`SyncCursorRepository.advancePullWatermark` 以 `@Modifying` 原子语句实现并发安全单调水印（`lastPulledChangeId < :watermark` 谓词），bulk JPQL 显式设 updated_at
- ~~T5：DTOs~~ — **推迟至 V 阶段**（pull/push 协议语义未定，提前定义属 Speculative Generality；V 阶段与协议一起定稿）
**关键设计决策：**

- 以实际 DDL 为准（R8.5）：无 `file_path` / `duration_seconds` / `device_id` 列（计划稿字段与真实表不符，不映射）；设备溯源用 `updated_by_device_id`；`session_changes.session_id` 是 FK 而非 `session_uuid`
- 软删除 `is_deleted` 在所有查询默认过滤，sync 数据永不硬删（change-log 传播收敛）
- `change_id`（BIGSERIAL）作为单调水印，增量拉取按 user 隔离
- 标量 UUID 引用（无 JPA 关联）保持 sync 模块依赖轻量
- Repository Javadoc 逐方法标注索引支撑，诚实标注无索引路径（`updated_by_device_id` 待未来加 partial index）
**验收标准：**

- [x] 三张表均有 JPA 实体映射，Flyway validate 通过（`@BaseIntegrationTest` 上下文加载验证）
- [x] 软删除（`is_deleted`）在所有查询中默认过滤，硬删除绝不用于 sync 数据
- [x] 增量查询（`WHERE change_id > :cursor`）按 user+device 隔离
- [x] 单测覆盖 Entity 状态机与 Repository 查询（含软删除/水标边界）— CodingSessionTest / SessionChangeTest / SyncCursorTest / SyncCursorIdTest / ChangeOpTest + 三 RepositoryTest
**验证：** 全量 1126 tests / 0 failed；jacoco PASS；spotless PASS；LSP 0 errors

#### U：LWW 冲突解析引擎 ✅

**实现状态：** ✅ **全部完成**（v0.46.0）— `ConflictResolver` 纯领域组件 + `Decision` 枚举，全量 1142 tests / 0 failed，双轴 Code Review 通过（Standards PASS + Spec PASS）

**目标：** 实现基于 server_version 的 Last-Write-Wins 冲突解析，保证多设备并发上报时不丢数据、不乱序。

**架构 & 技术栈：** `sync/service/` 包；纯领域组件（无 Spring/DB 依赖、不改变体）；Java 25 / JUnit 5

**子任务：**

- U1：`ConflictResolver` — 对同一 session 的并发变更，按优先级判定胜者：①删除胜（最强终态）②server_version 高者胜（双方均携带服务端版本时）③client_version 高者胜（服务端版本相等或一方无版本时）④clientModifiedAt 最新胜（同版本兜底）；完全相同状态 → 保留服务端（幂等 no-op，重提未变状态不产生冗余写入）
- U2：`server_version` 单调递增策略 — 实体 `bumpServerVersion()` 每次落库 +1，作为全序时钟；`ConflictResolver` 消费该水印（>0 才参与比较），递增动作归属未来推送服务（本阶段不改变体，职责边界清晰）
- U3：软删除合并规则 — 一端删除、一端更新时删除胜出（`APPLY_DELETE`）；双向判定（服务端已删 vs 客户端更新 → `KEEP_EXISTING`）；双方均删除落入版本规则（更近的删除仍是删除）
**关键设计决策：**

- 领域裁决：需求"同版本取 happened_at 最新"在 CodingSession 领域落地为 `clientModifiedAt`（实体无 happenedAt 字段）
- 判定输出 `Decision` 枚举：`APPLY_INCOMING`（采纳客户端 + 记 UPSERT）/ `KEEP_EXISTING`（保留服务端行）/ `APPLY_DELETE`（软删 + 记 DELETE）
- 纯函数设计：静态方法、不改变体（副作用与 bumpServerVersion 归属未来推送服务）
- 幂等 tie-break：完全相同状态解析为 KEEP_EXISTING，避免重复写入与冗余变更日志
**验收标准：**

- [x] 并发写入同一 session，最终只剩一条胜出记录，无重复（resolver 确定性单一决策；全量收敛依赖推送服务，属下一阶段）
- [x] 删除与更新竞争，删除胜出（双向覆盖）
- [x] `ConflictResolverTest` 覆盖同版本/不同版本/删除竞争三组边界（16 用例：不同版本 8 + 同版本 2 + 删除竞争 5 + 纯函数不变性 1）
**验证：** 全量 1142 tests / 0 failed；jacoco PASS；spotless PASS；LSP 0 errors

#### V：双向同步协议（Pull / Push） ✅

**实现状态：** ✅ **全部完成**（v0.47.0）— Pull/Push 协议落地替换占位实现，全量 1163 tests / 0 failed，双轴 Code Review 通过（Standards PASS + Spec PARTIAL 2 项已修复）

**目标：** 实现 `POST /api/v1/sync/pull` 与 `POST /api/v1/sync/push` 的真实业务逻辑，替换当前仅演示 scope 的占位实现。

**架构 & 技术栈：** `sync/service/`（SyncPullService / SyncPushService）+ `sync/dto/`（6 个请求/响应记录）+ `SyncController`；Java 25 / Spring Boot 4 / JUnit 5

**子任务：**

- V1：Pull 语义 ✅ — 请求 `{deviceId, lastPulledChangeId}`；有效查询游标 = max(持久化水印, 客户端游标)（陈旧客户端无法回卷）；按 change_id 升序返回增量 + 胜出会话快照（含软删行，DELETE 事件可送达行身份）；设备水印经 `advancePullWatermark` 原生 SQL 原子 upsert（INSERT ... ON CONFLICT + GREATEST）单调推进；无新增 → 空列表 + 当前游标（幂等）
- V2：Push 语义 ✅ — 批量 `{deviceId, sessions[]}`；逐条查询含软删行后经 `ConflictResolver` 三路路由：`APPLY_INCOMING`（字段应用 + bumpServerVersion + UPSERT 变更）/ `APPLY_DELETE`（软删 + bump + DELETE 变更）/ `KEEP_EXISTING`（no-op）；新会话 server_version 从 1 起；客户端删除服务端不存在的会话 → 幂等 no-op；整批单事务（部分应用永不发生）；响应返回用户最大 change_id 作为 nextCursor
- V3：认证集成 ✅ — 复用 `ApiKeyAuthenticationFilter`（`Bearer cttak_*`）+ `@RequiresApiKeyScope(ApiKeyScope.SYNC)`；JWT 用户经既有 Aspect 自动绕过（与现状一致）；deviceId 归属经 `DeviceRepository.findByIdAndUserId` BOLA 校验（未持有 → 404 `COMMON_002`，复用 DeviceService 同款文案，零新增错误码）
- V4：限流 ✅ — `@RateLimit(API, limit=120, windowSeconds=60)`；429 `RATE_LIMIT_001` + retryAfter 复用既有契约
- V5：审计 ✅ — 新增 `AuditAction.SYNC_PULL/SYNC_PUSH` + `ResourceType.CODING_SESSION`（未复用 `API_KEY_USED`——独立动作语义更清晰）；成功 `logSuccess` / 失败 `logFailure`（携带错误码名）
**关键设计决策：**

- deviceId 来源：请求体显式携带（API Key 的 device 关联可空且插件端不传），服务端按 user+device 校验归属，杜绝跨用户伪造
- pull 幂等双保险：客户端游标 + 服务端持久化水印；审查发现「游标行从未创建」缺陷（`advancePullWatermark` 原为纯 UPDATE，fresh device 匹配 0 行）→ 改为原生 SQL upsert，一条语句同时解决插入/单调/并发三问题
- push 软删互操作：查询含软删行，服务端软删 + 客户端 live → `KEEP_EXISTING`（删除胜，避免撞 `uk_coding_sessions_user_session_uuid` 唯一约束）
- 错误码零新增：404 复用 `COMMON_002`；限流/scope 复用既有契约
**验收标准：**

- [x] pull/push 端到端：push 一批 → pull 能取回增量 → 再次 pull 返回空（幂等）
- [x] 无 API Key / 无 JWT → 401；scope 不足 → 403 AUTH_020（MockMvc + 集成双覆盖）
- [x] 并发 push 同一 session → LWW 收敛为一条（集成验证单行 + client_version=2）
- [x] MockMvc + 集成测试覆盖上述场景；全量测试无回归
**验证：** 全量 1163 tests / 0 failed（基线 1142 + 21）；jacoco PASS（INSTRUCTION 93.5% / BRANCH 83.5%）；spotless PASS；LSP 0 errors；额外覆盖 device BOLA → 404（服务单元 + 集成层）

#### W：插件端对接与文档 ✅

**实现状态：** ✅ **全部完成**（v0.47.0）— 契约文档落地，双轴 Code Review 通过（Standards PASS + Spec PASS，6/6 DTO 字段与 @Schema 逐项核对一致）

**目标：** 产出 sync 契约文档供 code-time-tracker 插件端并行对接，交付验收标准。

**架构 & 技术栈：** `dev-docs/sync/`（插件接入指南）+ `docs/developer-handbook.md`（审计/错误码补充）；纯文档交付，无代码变更

**子任务：**

- W1 ✅ — `dev-docs/sync/frontend-integration.md` 新建（475 行中文契约文档）：流程总览（ASCII 同步循环）→ 认证（API Key SYNC scope + JWT 绕过 + 密钥获取路径）→ Pull 接口（请求/响应完整 JSON + 逐字段表 + 游标规则）→ Push 接口（LWW 结果表 + 原子性 + 响应游标）→ 错误码映射表（8 码含插件端处理建议）→ 限流重试策略（`Retry-After` 头 delta-seconds 优先 + 响应体 `retryAfter` ISO-8601 兜底 + jitter 退避 + 禁止无限重试）→ 对接流程建议 → 附录示例
- W2 ✅ — README Sync Engine 段落已在协议实现阶段完成（真实协议描述：404 COMMON_002/限流 120/审计 SYNC_PULL/SYNC_PUSH 均已写入），核对确认无需重复修改
- W3 ✅ — `developer-handbook.md` 补充：`### Sync Audit Events` 独立审计表（SYNC_PULL/SYNC_PUSH + ResourceType.CODING_SESSION + 成功 logSuccess / 失败 logFailure 细节）+ API Key 错误码表补 COMMON_002 行（此前仅内文出现未登记）
**关键设计决策：**

- 错误码零新增：文档映射的 8 个错误码（AUTH_010/011/012/020/021、COMMON_002/003、RATE_LIMIT_001）全部为既有错误码
- 审计事件独立成表：同步审计针对 CODING_SESSION 资源，不放入携带 API_KEY 注记的 API Key 审计表，对齐项目「每模块一张审计表」惯例
- 幂等场景显式化：审查发现「客户端删除服务端不存在的会话」幂等行为未在 LWW 结果表列出，补为独立结果行（与新会话创建行对照），保证插件端仅凭文档可对接
- 契约以代码为准：全部字段名/类型/必填/示例与后端 DTO `@Schema` 逐项核对，不依赖文档想象
**验收标准：**

- [x] 插件端可仅凭文档完成对接（字段、游标、错误码、重试语义均明确）
- [x] 文档中所有 DTO 字段与后端 `@Schema` 一致
**验证：** 双轴 Code Review（规范轴：中文一致/无 em-dash/表格有效/风格对齐既有指南；契约轴：6/6 DTO 逐项核对零偏差、游标/LWW/原子性/429 双源解析与代码一致）；em-dash 零；版本不变（纯文档不 bump，历史惯例一致）

#### 编码会话同步总交付清单

| 阶段 | 交付项 | 核心产出 | 说明 | 状态 |
|---|---|---|---|---|
| T | 数据模型与持久层 | `CodingSession` / `SessionChange` / `SyncCursor` Entity + `SyncCursorId` 复合主键 / `ChangeOp` 枚举 / 三个 Repository | 激活 init 迁移预置的三张表，建立支撑双向同步的领域模型与增量查询能力；软删除默认过滤，杜绝硬删；DTOs 推迟至 V 阶段（协议语义未定，避免 Speculative Generality） | ✅ 已完成（v0.45.0） |
| U | LWW 冲突解析引擎 | `ConflictResolver`（删除优先 → server_version → client_version → clientModifiedAt 四层优先级）+ `Decision` 枚举 / 删除优先合并规则 | 多设备并发上报收敛为单一正确状态，不丢数据、不乱序；删除是终态；完全相同状态幂等保留服务端 | ✅ 已完成（v0.46.0） |
| V | 双向同步协议（Pull/Push） | `SyncPullService` / `SyncPushService` / 6 个 DTO / `SyncController` 真实逻辑 / scope+限流+审计集成 | 替换占位实现：pull 按游标取增量（原生 SQL upsert 单调推进），push 批量落库生成变更日志（单事务原子）；认证复用 API Key，scope 强制 SYNC；设备归属 BOLA 校验 | ✅ 已完成（v0.47.0） |
| W | 插件端对接与文档 | `dev-docs/sync/frontend-integration.md` 新建 / README + handbook 同步（审计表、COMMON_002） | 契约先行：文档即合同，插件端可仅凭文档完成对接；DTO 字段与后端 @Schema 逐项核对一致 | ✅ 已完成（v0.47.0） |

**关键决策：**

- server_version 作为全序时钟（LWW），不依赖客户端时钟
- 软删除默认过滤，sync 数据永不硬删
- Pull 幂等（无新增返回空 + 当前游标），客户端游标与服务端持久化水印双保险
- 复用 API Key 认证 / SYNC scope / 既有 429 retryAfter 契约
- **错误码零新增**：计划稿设想的 `SYNC_001`/`SYNC_002` 未落地，404 复用 `COMMON_002`、400 复用 `COMMON_003`、429 复用 `RATE_LIMIT_001`
**新增 AuditAction：**

- `SYNC_PULL`
- `SYNC_PUSH`
（配套新增 `ResourceType.CODING_SESSION`）

### 📊 统计与排行

> **前置依赖：** 编码会话同步 。基于 `coding_sessions` 聚合统计数据，提供个人看板与全球排行榜。统计维度对齐插件端全部能力（summary / heatmap / streaks / languages / projects / timeOfDay / hourly / weekday / recent），排行覆盖总时长 / 连续天数 / 周期 / 时段偏好等维度，并扩展成就系统。

#### S1：统计聚合基础（个人看板核心维度）✅

**实现状态：** ✅ **全部完成**（v0.53.1，设备/IDE 维度扩展 v0.59.0，IDE 过滤 v0.60.0）— stats/ 包 9 维度聚合 + 6 端点落地，全量测试通过（含时区边界 / 跨天会话 / 空数据 / 非法区间），双轴 Code Review 通过，与插件端 StatisticsView 同数据集联调一致；插件端 heatmap 按天不合并 bug 已反馈修复。v0.59.0 补齐设备 / IDE 维度：origin_device_id 创建时盖章（跨设备更新不漂移）+ 全端点可选 deviceId 过滤（他人设备 404）+ distribution 新增 type=DEVICES / type=IDES（IDE 桶派生自 origin 设备注册的 ide_name；deviceId 为安装级、同机多 IDE 共享，协议/插件零改动）。v0.60.0 补齐 IDE 过滤：6 端点可选 ideName 参数（与 deviceId 互斥 400、未匹配任何设备 404）+ GET /ide-filters 筛选选项端点（distinct 非空 IDE 名）。v0.61.0 新增 GET /heatmap-years（有有效会话的年份倒序，热力图年下拉数据源；数据源=coding_sessions 非物化表）

**目标：** 建立 stats/ 包，基于 coding_sessions 提供个人统计聚合，维度对齐插件端 StatisticsView 全部能力。
  **维度：**
  - summary — today / dailyAverage / thisWeek / thisMonth / thisYear / total（对齐插件端 SummaryData）
  - heatmap — 指定范围每日编码时长（年视图热力图）
  - streaks — 当前 / 最长连续编码天数
  - languages — 语言编码时长分布
  - projects — 项目编码时长分布
  - timeOfDay — Morning / Daytime / Evening / Night 时段分布
  - hourly — 24 小时平均编码时长（含活跃天数）
  - weekday — 周几分布
  - recent — 最近活动会话列表
  **端点：** GET /api/v1/stats/summary、/heatmap、/streaks、/distribution（type=languages/projects/timeOfDay/hourly/weekday/devices/ides）、/recent?limit=、/ide-filters、/heatmap-years
  **关键设计决策：**
  - UTC-First 存储，统计聚合按请求时区偏移（timezoneOffset 参数）换算每日 / 时段边界
  - 跨午夜会话按日切分（对齐插件端 splitSessionByDay），时段归属按时区
  - 只统计未删除会话（is_deleted=false）
  - 数据源：CodingSessionRepository 聚合查询（按 user 隔离），复用 sync 实体
  **验收标准：**
  - [x] 每个维度与插件端同数据集计算结果一致（联调验证）
  - [x] 单测 + 集成测试覆盖（含时区边界、跨天会话、空数据）
  **验证：** 全量 tests / 0 failed；jacoco PASS；spotless PASS；LSP 0 errors；与插件端 SQLite 同数据集独立复现一致（summary 全维度 / streaks.max / heatmap 活跃天数 / 四种分布自洽）

#### S2：排行基础（Redis ZSet 全球排行）✅

**实现状态：** ✅ **全部完成**（v0.54.0）— leaderboard/ 包 + Redis ZSet 全球排行落地（total/streak 双维度），全量测试通过，双轴 Code Review 通过，真实服务联调验证（push → 排行即时更新 + 多用户排名正确）

**目标：** leaderboard/ 包，Redis ZSet 实现用户间编码时长排行（全球维度）。

**能力：**

- total — 用户总编码时长排行（ZSet score = 合并重叠总秒数，对齐 stats 口径）
- streak — 连续编码天数排行（最长连续 max，UTC 统一时区）
**端点：** GET /api/v1/leaderboard?dimension=total/streak&limit=&offset=（响应含当前用户排名，并列同 rank）

**关键设计决策：**

- 更新策略：push 成功后从 DB 重算当前用户 score 用 ZADD 覆盖（非累加——软删/更新会导致漂移，streak 无法增量），仅影响受动用户、ZSet 自动重排；读-算-写以用户级 Redis 锁串行化，防并发同用户 push 覆盖过期 score；Redis 失败容忍记日志不传播，下次 push 自愈
- 连续天数排行需要独立键（streak 值由 stats 计算后写入）
- 响应含当前用户排名（zrevrank）
- 排行键按维度隔离（leaderboard:total / leaderboard:streak 全局键）：键数量固定=维度数、member 是有限用户数，无需 TTL；S3 周期键才需滚动 TTL
**验收标准：**

- [x] push 后排行即时更新，多用户排名正确
- [x] 单测 + 集成测试（含并发 push、排名并列）
**验证：** 全量 tests / 0 failed；jacoco PASS；spotless PASS；单测 7（合并 total / 最长 streak / Redis 失败容忍+锁释放 / 空用户 / 条目映射+竞赛排名 / 未排名 / 分页）+ 集成 7（多用户 total/streak 排名 / 并列同 rank / 并发更新 / 真实 push E2E / 401 / 400 非法 dimension）；真实服务联调验证 push → leaderboard 即时更新 + 多用户排名正确；`.sisyphus/get-token.sh` 可复用 token 引导脚本（注册→Mailpit→验证→登录）

#### S3：排行进阶（周期与多维度）✅

**实现状态：** ✅ **全部完成**（v0.55.0）— LeaderboardPeriod 周期（ALL/WEEK/MONTH/YEAR）+ 趣味维度（nightOwl/earlyBird/growth）落地，全量测试通过，双轴 Code Review 通过（含跨午夜窗口漏计修复），真实服务接口验证 8 组合全 PASS。

**目标：** 排行支持周期与更多趣味维度。

**能力：**

- period — 本周 / 本月 / 本年排行（周期键隔离，TTL 滚动自清理；TOTAL 支持全周期）
- nightOwl — 夜猫子排行（UTC 22:00-05:00 窗口合并时长）
- earlyBird — 早起鸟排行（UTC 06:00-09:00 窗口合并时长）
- growth — 本周增长最快排行（本周 − 上周净增长秒，固定 WEEK）
- streak — 保持全局 ALL（累积指标，周期内语义弱）
- languages / projects 维度排行（计划稿标注可选，未实施）
**端点：** GET /api/v1/leaderboard?dimension=TOTAL/STREAK/NIGHT_OWL/EARLY_BIRD/GROWTH&period=ALL/WEEK/MONTH/YEAR&limit=&offset=（period 默认 ALL，GROWTH 默认 WEEK；响应含当前用户排名，并列同 rank；非法维度×周期组合 400 COMMON_003）

**关键设计决策：**

- 维度-周期合法组合显式校验：TOTAL×{ALL,WEEK,MONTH,YEAR} / STREAK×ALL / NIGHT_OWL,EARLY_BIRD×ALL / GROWTH×WEEK（非法组合 400 COMMON_003，与参数校验错误码一致）
- 周期键滚动：键按周期起始分桶（周=ISO 周一、月=1 号、年=1 月 1 日），写入刷新 TTL（WEEK 8d/MONTH 32d/YEAR 370d）自清理旧键；ALL 键无 TTL，S2 键名不变向后兼容
- 时段维度复用 stats 聚合语义：mergedDurationInDailyWindow（每日窗口合并时长，支持跨午夜；凌晨会话归前一天窗口——审查修复的漏计缺陷）
- 增长排行按周键对比：score = 本周合并时长 − 上周合并时长（DB 重算，无需物化表；净增长秒避免涨幅比例除零）
- 更新策略延续 S2：push 后重算覆盖，updateUserScores 一次锁 + 一次 DB 查询 + 批量写 8 键（Redis 失败容忍自愈）
- 周期/时段边界统一 UTC（全球排行单一时区）
**验收标准：**

- [x] 周期排行（本周/本月/本年）与趣味维度（夜猫子/早起鸟/增长）分数正确
- [x] 非法维度×周期组合返回 400（COMMON_003）
- [x] 单测 + 集成测试（周期分数 / 窗口聚合 / 跨午夜 / 净增长 / TTL / 非法组合）
- [x] 真实服务接口验证 8 组合全 PASS + 400 边界
**验证：** 全量 tests / 0 failed；jacoco PASS；spotless PASS；LSP 0 errors；StatsCalculator +7 窗口用例 + LeaderboardService 14 用例 + LeaderboardIntegrationTest +5；真实服务验证（push 本周 3h + 上周 1h → TOTAL ALL/WEEK/MONTH/YEAR=14400/10800/14400/14400、STREAK=1、NIGHT_OWL=3600、EARLY_BIRD=3600、GROWTH=7200）；`.sisyphus/get-token.sh` token 引导脚本复用

#### S4：成就系统 ✅

**实现状态：** ✅ **全部完成**（v0.56.0）— 15 个成就（连续 / 累计 / 语言 / 时段 / 爆发 / 全勤）惰性判定落地，幂等解锁 + ACHIEVEMENT_UNLOCKED 审计，全量测试通过，双轴 Code Review 通过（含 unlockedAt 时钟源漂移修复），真实服务验证全 PASS。

**目标：** 趣味成就 / 徽章，多维度触发，驱动用户活跃。

**成就维度（15 个）：**

- 连续 — 首次连续 3 / 7 / 30 天编码（最长连续 max）
- 累计 — 总时长 10 / 100 / 500 小时（合并重叠总秒数，对齐 stats 口径）
- 语言 — 使用过 3 / 5 / 10 种语言（distinct 语言数）
- 时段 — 早起鸟（UTC 06:00-09:00 窗口活跃累计 10 / 30 天）、夜猫子（UTC 22:00-05:00 窗口活跃累计 10 / 30 天）
- 爆发 — 单日编码超 8 小时（单日合并时长 max）
- 全勤 — 某自然月每天都有编码（进行中月份不会误判）
**端点：** GET /api/v1/stats/achievements?timezoneOffset=（返回全部 15 个成就：code / displayName / description / unlocked / unlockedAt / progress / target / unit；未解锁时 unlockedAt 为 null；READ scope + 60 req/min 限流）

**关键设计决策：**

- 成就定义配置化：Achievement 枚举内聚 15 成就定义（code / 展示名 / 描述 / 类型 / 阈值 / 单位），窗口小时定义在枚举字段（早 6-9 / 夜 22-5）
- 解锁记录幂等：user_achievements 表 + (user_id, achievement_code) 唯一约束 + INSERT ... ON CONFLICT DO NOTHING 原生 SQL（返回 1 新 / 0 已存在，原子防并发双解锁双审计）
- 解锁时机：统计查询时惰性判定（避免常驻任务），达标即解锁 + 审计，已解锁不重复解锁
- 解锁时间戳权威源：unlocked_at 由数据库 CURRENT_TIMESTAMP 生成，服务端解锁后重读 DB 值返回——修复了服务时钟与远程 DB 时钟偏差导致的首次 / 后续返回值漂移（实机自测发现）
- 进度计算复用 stats 聚合：streaks.max / summary 的 total / 语言 distinct 数 / activeDaysInDailyWindow（新增，共享窗口日归属逻辑）/ maxDailySeconds（新增）/ hasPerfectMonth（新增）
- 审计：解锁记 ACHIEVEMENT_UNLOCKED + ResourceType.ACHIEVEMENT（约束修改走独立迁移重建 chk_audit_resource_type——init 迁移已应用禁止修改，已固化到 AGENTS 文档 R22）
- 时段 / 全勤成就按请求时区（timezoneOffset，对齐个人统计端点；区别于全球排行的 UTC）
- 数据库变更走独立迁移：V20260831230000（user_achievements 表）/ V20260831230001（审计约束重建），init 迁零修改
**验收标准：**

- [x] 15 成就进度计算正确（连续 / 累计 / 语言 / 窗口活跃天 / 爆发 / 全勤月）
- [x] 惰性解锁幂等：并发请求不双解锁、不双审计；已解锁保留原始 unlockedAt
- [x] 每次新解锁记一条 ACHIEVEMENT_UNLOCKED 审计（ACHIEVEMENT 资源类型）
- [x] 单测 + 集成测试（进度 / 幂等 / 审计 / 窗口 / 全勤 / 空数据 / 401）
- [x] 真实服务验证：push 连续 3 天 → STREAK_3 解锁 + unlockedAt 三次查询完全一致 + 空用户全锁定 + 401/400 边界
**验证：** 全量 tests / 0 failed；jacoco PASS；spotless PASS；LSP 0 errors；StatsCalculator +9 窗口 / 单日 / 全勤用例 + AchievementServiceTest 8 用例 + StatsIntegrationTest +3（HTTP 解锁持久化幂等 / 空数据 / 边界）+ ResourceTypeTest 同步（hasSize 11 + 约束覆盖改读独立迁移）

#### S5：性能与物化 ✅

**实现状态：** ✅ **全部完成**（v0.57.0）— daily_stats 按日物化表（独立迁移）+ 增量物化 + 惰性自举 + achievements Redis 缓存（TTL 60s + push 失效）落地，全量测试通过，双轴 Code Review 通过（修复存量用户自举永久跳过、软删 push 不重算两大一致性缺陷），真实服务验证全 PASS。

**目标：** 大数据量下统计 / 排行稳定。

**能力：**

- heatmap / summary / streaks UTC 读走按日物化表（O(range) 替代全量会话加载；时区偏移请求实时回退——跨本地午夜会话归两天，UTC 单日物化无法无损服务偏移边界）
- achievements 结果 Redis 缓存（TTL 60s，push 失效；15 成就全量重算是最重读路径）
- 索引判断：既有 idx_sessions_user_time partial 索引已覆盖聚合查询，未臆测加索引；物化表主键即查询路径
**关键设计决策：**

- 增量物化：push 事务内仅重算受影响 UTC 日（touched dates 来自 push 会话起止，含软删），O(触明日数) 与历史规模无关；物化行 INSERT ON CONFLICT DO UPDATE 原子收敛
- 惰性自举：存量用户首次 UTC 读全量重建一次（bootstrapped 标记行区分「有行」与「历史已覆盖」——审查修复：仅靠 exists 判断会让存量用户永久跳过自举），用户级 Redis 锁防并发双建，REQUIRES_NEW 脱离只读事务
- 物化表独立迁移 V20260831230002，与源表解耦（init 迁移零修改，R22）
- 冷热分层落地形态：UTC 请求全量走物化（零精度损失）；时区偏移请求实时聚合（不做按日物化+偏移换算的双写）
- 失败容忍：物化 / 缓存异常记日志不传播，下次 push 重试自愈
**验收标准：**

- [x] push 后物化行增量更新，UTC summary / heatmap / streaks 读物化结果与实时一致（含跨午夜切分）
- [x] 惰性自举：存量用户首次读全量重建一次；直接 DB 插入不触发 push 路径，首次读补齐
- [x] achievements 缓存失效：push 后立即重读可见新解锁（不返回陈旧值）
- [x] 单测（物化重算 / 自举 / 锁竞争 / 失败容忍）+ 集成测试（物化一致性 / 缓存失效 E2E）
- [x] 真实服务验证：push 1h → UTC summary total=3600；push 11h 重叠会话 → total=39600（合并语义）；achievements 缓存失效后 TOTAL_10_HOURS 解锁；heatmap 稠密补零；非 UTC 回退实时
**验证：** 全量 tests / 0 failed；jacoco PASS；spotless PASS；LSP 0 errors；DailyStatsMaterializerTest 6 用例 + StatsServiceTest 5 用例（UTC 物化 / 非 UTC 实时 / bootstrap 不可用）+ StatsIntegrationTest +2（物化一致性含软删重算 / 缓存失效 E2E）；真实服务验证（push 1h → UTC total=3600；push 11h 重叠 → total=39600；缓存失效后立即解锁；heatmap 稠密 0 值补位；非 UTC 实时回退）

#### 统计与排行总交付清单

| 阶段 | 交付项 | 核心产出 | 说明 | 状态 |
|---|---|---|---|---|
| S1 | 统计聚合基础 | stats/ 包 + 9 维度聚合 + 6 端点 | 对齐插件端全部统计维度，时区感知聚合，跨天会话切分 | ✅ 已完成（v0.53.1） |
| S2 | 排行基础 | leaderboard/ 包 + Redis ZSet | 全球总时长 + 连续天数排行，push 增量更新，含当前用户排名 | ✅ 已完成（v0.54.0） |
| S3 | 排行进阶 | 周期 + 多维度排行 | 周 / 月 / 年滚动 + 夜猫子 / 早起鸟 / 增长率维度 | ✅ 已完成（v0.55.0） |
| S4 | 成就系统 | achievements 配置化规则 + 解锁记录 | 连续 / 累计 / 语言 / 时段 / 爆发 / 全勤徽章，惰性判定 | ✅ 已完成（v0.56.0） |
| S5 | 性能与物化 | 物化表 + Redis 缓存 + 索引优化 | 大数据量聚合稳定，push 增量物化 | ✅ 已完成（v0.57.0） |

**关键决策：**

- 统计维度对齐插件端全部（summary / heatmap / streaks / languages / projects / timeOfDay / hourly / weekday / recent）并扩展设备 / IDE 维度（origin_device_id 派生，独立迁移，插件零改动）
- 排行基于 Redis ZSet，push 增量更新，全球维度（无好友系统）
- UTC-First 存储 + 请求时区聚合
- 成就系统作为趣味功能驱动活跃（连续 / 累计 / 语言 / 时段 / 爆发 / 全勤）
- **迁移策略**：sync 模块（coding_sessions 等）已落地，后续统计 / 排行的数据库变更（物化表、成就表等）一律走独立迁移脚本（如 V20260830xxxx__xxx.sql），不再回填 init 迁移
**新增接口：**

- GET /api/v1/stats/summary、/heatmap、/streaks、/distribution、/recent、/achievements
- GET /api/v1/leaderboard（dimension=total/streak/nightOwl/earlyBird/growth，period=week/month/year/all）

### 🔍 运维与可观测性升级

> **面向生产的非功能性需求，可与功能开发并行推进。**

后续可能加的功能：1. 通过KEY直接调用统计接口供用户使用接口获取个人统计数据 2.提供统计Card方便用户像是Github个人页使用 2.通过绑定Github连通Github获取用户提交信息做相关的统计与展示card

