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

## 详细文档

- [时间策略](../docs/time-strategy.md)
- [大小写规范](../docs/case-normalization.md)
- [接口治理](../docs/api-governance.md)