# 系统模式

## 架构风格: Package-by-Feature

按功能分包而非按层分包，高内聚、可拆分微服务、清晰边界。

## 同步策略: LWW + 软删除

多设备双向同步采用基于时间戳的 LWW (Last-Write-Wins) 策略，软删除防止数据丢失。

## 认证方式: JWT + API Key 双轨制

Web 用户用 JWT，插件设备用 API Key，设备管理支持单独撤销。

## 时间策略: UTC-First

全链路 UTC 绝对时间，强制 `Instant`，禁止 `LocalDateTime`。

## 邮箱规范: 防御性归一化

入库前强制转小写，DTO 归一化 → 实体钩子 → Repository IgnoreCase 三层防御。

## 安全底座: CurrentUserProvider 防腐层

解耦业务逻辑与 Spring Security，`CurrentUser` record 统一身份模型。

## 代码复用: SpEL 表达式解析器

`SpelExpressionResolver` 共享组件供限流和幂等框架复用。

## 接口治理: @RateLimit + @Idempotent

声明式注解，支持 IP/USER/EMAIL/API 四维度，Redis Lua 原子脚本。

## 接口安全: Secure by Default

`@PublicApi` 注解显式标记公开接口，`PublicApiEndpointRegistry` 动态白名单。

## 客户端上下文: ClientIdentity

X-Device-ID/X-Platform/X-IDE-Name Header 标准化，业务层不接触 HttpServletRequest。

## 传输安全: OWASP Headers

X-Content-Type-Options, X-XSS-Protection, X-Frame-Options, HSTS, CSP。

## 测试风格: 轻量级 BDD

采用 `// Given // When // Then` 注释结构组织测试代码，不引入 Cucumber 等重量级 BDD 框架。

**决策依据**：
- 个人/小团队项目，无需跨团队协作
- 开发者为主要读者，注释结构已足够清晰
- 遵循 AGENTS.md R12（禁止擅自添加依赖）
- AssertJ `then()` 与 `assertThat()` 功能相同，无实际改进价值

## 登录元数据设置模式

登录成功后必须同步更新 User 实体的 `lastLoginAt` 和 `lastLoginIp` 字段：

| 登录流程 | lastLoginAt | lastLoginIp | 位置 |
|---|---|---|---|
| 邮箱密码登录 | `Instant.now()` | `RequestContext.current().map(RequestInfo::clientIp)` | `UserLoginService.login()` |
| OAuth 已有绑定登录 | `Instant.now()` | `clientIp`（从 state payload 取得，authorize 时捕获） | `OAuthLoginOrRegisterService.handleExistingBinding()` |
| OAuth 新用户注册 | `Instant.now()` | `clientIp`（同上） | `OAuthLoginOrRegisterService.registerNewUser()` |
| BIND 流程 | 不设置（不是登录） | 不设置 | — |
| UNBIND 流程 | 不设置（不是登录） | 不设置 | — |

**防复发检查清单**：实现新登录/注册流程时，必须检查 User 实体的时间戳字段是否需要同步更新。

## 详细文档

- [时间策略](../docs/time-strategy.md)
- [大小写规范](../docs/case-normalization.md)
- [接口治理](../docs/api-governance.md)

## 客户端分配 ID 实体模式（Device）

当实体主键由客户端提供（非 DB 生成）时：
1. **移除 `@GeneratedValue`**——否则 Hibernate 视非 null id 为 detached 实体，校验 version 或拒绝
2. **加 `@Version` 且初始化为 null**——Spring Data 的 `isNew()` 以 version==null 判新建 → `save()` 走 persist（INSERT）；从 DB 加载后 version=0 → 走 merge/dirty-checking（UPDATE）
3. 对比 User 等 DB 生成 id 的实体：id=null 天然判新建，无需此模式

**教训**: 无 @Version 时 `save()` 对非 null id 走 merge，Hibernate 对 DB 无行的 detached 实体抛 `StaleObjectStateException`（"Row was already updated or deleted"）；有 @GeneratedValue + 非 null id 又抛 "uninitialized version"——两者都要求上述组合。
