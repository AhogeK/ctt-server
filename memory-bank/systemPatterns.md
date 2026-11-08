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

声明式注解，支持 IP/USER/EMAIL 三维度，Redis Lua 原子脚本；另有一层每实例的 `GlobalRequestGuard` 全局上限（`rate-limit.global-max-requests-per-second`），在维度桶之前执行。端点级桶必须带租户维度——「只按端点」的桶衡量的是部署规模，不是滥用。

## 接口安全: Secure by Default

`@PublicApi` 注解显式标记公开接口，`PublicApiEndpointRegistry` 动态白名单。

## 客户端上下文: ClientIdentity

X-Device-ID/X-Platform/X-IDE-Name Header 标准化，业务层不接触 HttpServletRequest。

## 传输安全: OWASP Headers

X-Content-Type-Options, X-XSS-Protection, X-Frame-Options, HSTS, CSP。

## 结构化日志格式不能沿用 pattern 级脱敏

Logback 的 `conversionRule`（如 `%maskedMsg`）只对 `PatternLayout` 生效。切到 JSON encoder 后事件字段被直接序列化，基于 pattern 的脱敏不会执行——2026-11-08 查实：prod 的 `JsonEncoder` 让 pattern 级脱敏静默失效，而 console 一直是好的。同一批复核还发现 JSON 会把 `arguments` 与 `kvpList` 作为**独立字段**输出，pattern 永远看不见它们：`RequestLoggingFilter` 用 `addKeyValue("query_string", …)` 记整条查询串，`/verify-email?token=…` 的凭据就是这样进的日志（比 message 里的明文更严重，因为它发生在正常流程上）。

做法：脱敏规则抽成纯函数共享；自定义 encoder 在序列化前包一层事件——把已掩码的 formatted 文本作为 `message` 输出、掩码 `kvpList` 的字符串值，**一旦发生文本掩码就丢弃 `arguments`**（参数无法逐个判断是否敏感，而渲染文本已包含它们），再委托官方 encoder。代价是每个事件一次正则与可能的克隆；收益是未命中事件 schema 完全不变，命中的事件里凭据不再以任何字段形态出现。MDC 与 throwable 文本仍未掩码，是这条防线的已知残余。

## 审计边界：请求体与个人数据不进审计

`audit_logs.details` 只承载动作、资源、原因、状态与计数；请求/响应体与姓名、邮箱、passkey 标签、原始 SMTP 错误永不入库（ADR-0001，P0.6b 已收口）。历史行的保留清理由 `AuditRetentionJob` 按 `ctt.audit.retention.days` 执行（默认 0=保留，每批独立事务）；保留策略已定 180 天整行删除（ADR-0002），生产启用按部署清单，量级见 P0.16b。无生产消费者的配置键先追溯引入意图，再决定接线或删除；不保留假开关。

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

- **标记键同时编码规则代号、分数矩阵指纹与词表版本**（`...:backfill:g{n}:cov{hash}:vocab{v}`），而不是把「记得 bump」写在注释里——忘记 bump 导致跳过必要重算这件事因此结构性不可能。**矩阵指纹由枚举现算**（`values() × supports()` 的排序后 SHA-256 前 8 位）：新增维度或放开周期会自己改键；只有「改公式」仍需人工提升代次。2026-10-25 的现实教训：0.85.0 放开 `STREAK` 全部周期却没提代次，旧标记 `g2:vocab2` 命中，未再推送的用户窗口榜全空——补指纹正是为了让这类「加维度/放周期」不再依赖人记
- 执行成功才写标记；失败不写 → 下次启动自动重试（自愈，无需人工介入）
- 重算复用在线写路径（同一把用户锁 + 单事务），与并发推送安全共存

**测试约束**：写数据的后台任务在测试中必须被推离（`application-test.yaml` 把初始延迟设为 1 天）。理由与禁用限流一致——它会在断言进行时改写共享 Redis/DB，且每个 Spring 上下文拿到的是全新 Redis，标记挡不住。**任何新增的 `@Scheduled` 写数据任务照此处理。**

## 导出/数据可携带: 格式由消费端决定，通道才是可选的

导出内容的**格式**不是服务端可选的：它由唯一消费端（插件端导入器）的解析方式决定——`DataExportImportService` 用 Gson 读固定模型（`exportVersion` 硬校验、字段名固定、时间按 `ISO_LOCAL_DATE_TIME` 解析），故 NDJSON 与带 `Z` 的时间都会让**整份导入失败**（不是跳过一行）⇒ 后端产出必须是单 JSON 对象 + 无时区本地时间（`timezoneOffset` 必填）。

有取舍的是**通道**（同步流式 / 一次性票据 + 原生下载 / 异步任务 + 产物存储），取舍依据是**实测规模**而非直觉：2026-10-19 实测全库会话负载 707 kB，故维持同步流式；升级触发条件 = **单用户 >10 MB 或导出 p95 >5 s**。详见 `.plans/ctt-server-development-plan.md` 的 R3 段。

## 安全边界：机器凭据 vs 浏览器凭据

API key 只代表机器同步能力，不代表可交互的账号控制。改密码、改邮箱、账号删除等凭据/身份操作必须经过 `WebSessionGuard`；仅调用 `getActiveUserRequired()` 不足以证明调用者是浏览器会话。

## 代理头与客户端身份

`X-Forwarded-For` / `X-Real-IP` 默认不可信。只有 socket peer 命中 `ctt.security.trusted-proxy-addresses` 时才解析代理链，并取右侧第一个非可信 hop；否则使用 `getRemoteAddr()`。IP 限流、审计 IP 和 last-login IP 共用这一来源。

## OAuth 浏览器绑定

OAuth state 必须与 HttpOnly、SameSite Lax 的状态 Cookie 对齐；缺失/不匹配的 callback 不消耗登录。成功 token 只放 fragment，不能放 query（query 会进入历史、代理和访问日志）。

## 限流桶必须有租户维度

限流键若只由「端点」拼成（`RateLimitType.API` → `rate_limit:api:{类名.方法名}`），它就是**全部署共享的一个桶**：一个租户（或一次新设备排空）能把所有人饿死。端点级限流必须带用户/设备维度，否则它衡量的不是「滥用」而是「部署规模上限」。2026-11-05 实测：同步轮询 0.28–0.375 次/分钟/设备时，120/分钟的全局桶只支撑约 320–430 台设备。**同日已修复**：`RateLimitType.API` 枚举删除，同步端点改 `USER` 维度（pull 600/min、push 120/min，账户 = API key 属主），另以 `GlobalRequestGuard` 承担每实例全局限流（`global-max-requests-per-second`，默认 200/s，在维度桶之前）。

## 派生状态不得在写路径上按全量历史重算

写路径上的派生值（排行榜分数、成就缓存）若每次写入都重算该用户的**全部**历史，成本就与用户生涯数据量成正比，而不是与本次写入量成正比。数据看起来很小的用户（10 万行 ≈ 100 MB）在第 10 年会让每次写入付出数百毫秒与上百 MB 堆内存。重算要么改为**增量/按天聚合**，要么**移出写路径**（定时任务 + 允许短暂陈旧）。排行榜分数选的是后者：push 只写一次脏标记（`afterCommit` 之后，避免排空读到未提交快照），由 `LeaderboardRecomputeQueue` 定时 `SPOP` 批量重算——同一用户在一个排空周期内的多次 push 合并成一次重算，代价是分数最多陈旧一个排空周期。

## 空轮询不该产生持久化写入

「什么都没有发生」的轮询若每次都写审计行 + 心跳 + 游标 upsert，写放大会按**轮询次数**而非业务量增长，并与业务数据无关地吃掉存储（收口前实测每设备每天 ~1000 行审计）。无结果的读请求应尽量不落库，审计对空结果应聚合或降级。**2026-11-05 起已按此收口**：成功同步只进应用日志（失败才进审计）、`API_KEY_USED` 每 key 每小时至多 1 行、`last_used_at` 每小时至多 UPDATE 1 次；**2026-11-06 起**空轮询不再产生游标行与心跳行版本。审计增量因此从「按轮询次数」变成「按活跃 key 数」——每 key 至多 24 行/天，旧的「20 年后比会话数据大 26 倍」推算随之失效。

## 容器加固基线（compose 锚点）

本地与生产 compose 共用一套加固：`restart: unless-stopped`、`stop_grace_period: 45s`、`read_only: true`、`cap_drop: [ALL]`、`no-new-privileges:true`、json-file 日志轮转（10m × 3），再由每个服务声明 `mem_limit` / `cpus` 与所需 tmpfs。写成 `x-hardening` 锚点，避免十几处重复漂移。

能力按 entrypoint 精确加回，不做「先全开再收紧」：postgres 需要 `CHOWN/DAC_OVERRIDE/FOWNER/SETGID/SETUID`；redis 需要 `CHOWN/DAC_OVERRIDE/SETGID/SETPCAP/SETUID`（没有 FOWNER，SETPCAP 用于 setpriv 收窄 bounding set）；应用、Caddy、Prometheus、Grafana、blackbox、Mailpit 不需要任何附加能力。只读根意味着所有写入点必须落在命名卷或 tmpfs（应用与 Mailpit 用 `/tmp`，postgres 另加 `/var/run/postgresql`，redis 的 `/data` 是缓存、用 tmpfs 即可）。验收方式是 `docker inspect` 逐字段核对（restart、StopTimeout、Memory、NanoCpus、LogConfig、ReadonlyRootfs、Tmpfs、CapDrop/CapAdd、SecurityOpt），而不是看 compose 文件。

## 密钥通道：SOPS → 0400 文件 → Compose secrets

仓库只放 SOPS 密文（`deploy/secrets/secrets.enc.yaml`），`.sops.yaml` 只含 age 公钥；`deploy/secrets/render.sh` 用本机身份解成「一个键一个文件、权限 0400」，写入 `deploy/secrets/run/`（gitignore）。Compose 以文件形式挂成 secrets：应用用 `SPRING_CONFIG_IMPORT=optional:configtree:/run/secrets/` 读取（属性名等于文件名，现有 `${DB_PASSWORD}` / `${JWT_SECRET_KEY}` 占位符不用改），PostgreSQL 用 `POSTGRES_PASSWORD_FILE`，Redis 由 entrypoint 与健康检查 `cat` 文件。结果：`docker inspect` 的 `.Config.Env` 不含任何密钥；缺 age 身份、缺渲染文件、空值都在启动前失败。`.env` 仅保留给 `bootRun`，容器栈不再读它。
