# 系统模式

## 架构风格: Package-by-Feature

按功能分包而非按层分包，高内聚、可拆分微服务、清晰边界。

## 同步策略: LWW + 软删除

多设备双向同步采用 LWW (Last-Write-Wins) + 软删除。**优先级链、幂等规则、游标语义、分页契约见
[`domains/sync-protocol/principles.md`](domains/sync-protocol/principles.md)**（领域文件是权威，此处不重复）。

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

## 登录元数据（见领域文件）

登录成功必须同步 `lastLoginAt` / `lastLoginIp`（各流程逐一对照表 + 防复发清单在
[`domains/auth-lifecycle/practices.md`](domains/auth-lifecycle/practices.md)）。

## 详细文档

- [领域知识图谱](domains/README.md) — 领域级不变量/判断/做法（R25 治理）
- [时间策略](../docs/time-strategy.md)
- [大小写规范](../docs/case-normalization.md)
- [接口治理](../docs/api-governance.md)

## 客户端分配 ID 实体模式（Device）

当实体主键由客户端提供（非 DB 生成）时：
1. **移除 `@GeneratedValue`**——否则 Hibernate 视非 null id 为 detached 实体，校验 version 或拒绝
2. **加 `@Version` 且初始化为 null**——Spring Data 的 `isNew()` 以 version==null 判新建 → `save()` 走 persist（INSERT）；从 DB 加载后 version=0 → 走 merge/dirty-checking（UPDATE）
3. 对比 User 等 DB 生成 id 的实体：id=null 天然判新建，无需此模式

**教训**: 无 @Version 时 `save()` 对非 null id 走 merge，Hibernate 对 DB 无行的 detached 实体抛 `StaleObjectStateException`（"Row was already updated or deleted"）；有 @GeneratedValue + 非 null id 又抛 "uninitialized version"——两者都要求上述组合。

## 派生数据重算：版本化标记的一次性回填

当派生值（Redis ZSet 分数、物化行）依赖的是「计算规则」本身——排序公式、规范名映射——而非仅原始数据时，规则一改已有数据不会自动跟进，需要一次性全量重算。

`LeaderboardScoreBackfill` 是该模式的实现：启动后延迟执行一次，遍历全部用户重算分数并写回。

- **标记键同时编码规则代号与词表版本**（`...:backfill:{rule}:{vocabularyVersion}`），而不是把「记得 bump」写在注释里——忘记 bump 导致跳过必要重算这件事因此结构性不可能
- 执行成功才写标记；失败不写 → 下次启动自动重试（自愈，无需人工介入）
- 重算复用在线写路径（同一把用户锁 + 单事务），与并发推送安全共存

**测试约束**：写数据的后台任务在测试中必须被推离（`application-test.yaml` 把初始延迟设为 1 天）。理由与禁用限流一致——它会在断言进行时改写共享 Redis/DB，且每个 Spring 上下文拿到的是全新 Redis，标记挡不住。**任何新增的 `@Scheduled` 写数据任务照此处理。**
