# 系统模式

## 架构风格: Package-by-Feature

按功能分包而非按层分包，高内聚、可拆分微服务、清晰边界。

## 同步策略: LWW + 软删除

多设备双向同步采用 LWW (Last-Write-Wins) + 软删除。**优先级链、幂等规则、游标语义、分页契约见
[`domains/sync-protocol/principles.md`](domains/sync-protocol/principles.md)**（领域文件是权威，此处不重复）。

## 环境数据隔离

开发使用 `ctt_server`；生产使用 `ctt_server_prod`，owner 与登录角色均为 `ctt_app`。生产角色没有 SUPERUSER/CREATEDB/CREATEROLE，且不能连接开发库。Redis 按环境划分逻辑库：本地/开发 db0，生产 db2，库号由 `REDIS_DATABASE` 覆盖；db1 已被其他服务的 `check-approver-sort:*` 占用，禁止清理或混用。生产库首次启动由 Flyway 建表，不复制开发数据。

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

`audit_logs.details` 只承载动作、资源、原因、状态与计数；请求/响应体与姓名、邮箱、passkey 标签、原始 SMTP 错误永不入库（ADR-0001，P0.6b 已收口）。历史行的保留清理由 `AuditRetentionJob` 按 `ctt.audit.retention.days` 执行（基础 profile 默认 0=保留，每批独立事务）；保留策略为 180 天整行删除（ADR-0002），生产 profile 携带 `${AUDIT_RETENTION_DAYS:180}`、`0` 为回滚，生产量级实测见 P3.8。无生产消费者的配置键先追溯引入意图，再决定接线或删除；不保留假开关。

## 健康探针：liveness 与 readiness 分离

liveness 只包含 `livenessState`，不能纳入数据库/Redis；readiness 包含 `readinessState`、`db`、`redis`。readiness 依赖失败或状态未知时由专用 `HealthEndpointGroupsPostProcessor` 将 `DOWN`/`UNKNOWN` 映射为 `OUT_OF_SERVICE`（HTTP 503），只包装 readiness，不改变 liveness 或根健康。当前上下文未自动注册 Redis contributor，需显式提供条件式 `DataRedisHealthIndicator`；`AdditionalPathsMapper` 必须继续委托。compose 与 blackbox 使用 readiness；停 PostgreSQL 演练实测 liveness `UP`/200、readiness `OUT_OF_SERVICE`/503。

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

## 密钥通道：SOPS → 0440 组可读文件 → Compose secrets

仓库只放 SOPS 密文（dev `deploy/secrets/secrets.enc.yaml`、prod `deploy/secrets/secrets.prod.enc.yaml`），`.sops.yaml` 只含 age 公钥；`deploy/secrets/render.sh --env dev|prod` 用本机身份解成「一个键一个文件、权限 0440、组为 `SECRET_GID`」，写入 `deploy/secrets/run/`（gitignore）。Compose 以文件形式挂成 secrets，并以 `group_add` 把同一 GID 加入应用与 Grafana：应用用 `SPRING_CONFIG_IMPORT=optional:configtree:/run/secrets/` 读取（属性名等于文件名，现有 `${DB_PASSWORD}` / `${JWT_SECRET_KEY}` 占位符不用改），PostgreSQL 用 `POSTGRES_PASSWORD_FILE`，Redis 由 entrypoint 与健康检查 `cat` 文件，Grafana 用 `GF_SECURITY_ADMIN_PASSWORD__FILE`。结果：`docker inspect` 的 `.Config.Env` 不含任何密钥；缺 age 身份、缺渲染文件、空值都在启动前失败。`.env` 仅保留给 `bootRun`，容器栈不再读它。

**宿主级密钥按文件注入，身份只在操作机（frp token）**：隧道这类由同一主机上多个栈共享的凭据不属于某一个 Compose 栈，也不该按环境各存一份——`FRP_TOKEN` 只存在开发载荷里，`deploy/secrets/frp-token.sh [--remote HOST] [TARGET]` 把它渲染成 `600` 文件（本机或经 SSH；在目标旁 `mktemp` 建 `600` 临时文件再改名覆盖，写完回读模式与字节数，不符就失败；SSH 加 `BatchMode=yes`，否则交互提示会吞掉管道里的 token）。它不是容器密钥，`render.sh` 显式跳过该键，避免在 `deploy/secrets/run/` 另生一份 `0440` 副本。两端 frp 用 `auth.tokenSource.file.path` 读同一文件（路径按进程权限区分），读取时由 frp 自行 `TrimSpace`，所以尾随换行安全；解密只在持有 age 身份的操作机上进行，frp 主机既无仓库检出也无身份文件。代价是轮换需要两端同时重渲染并重启，这一条写进 `docs/deployment-secrets.md`。

## 数据面监听与网络信任边界

部署机的数据面监听、LAN 访问、UFW 决策与 DHCP/network-online 前提见 [Production Data Isolation](../docs/production-data-isolation.md#data-plane-network-isolation)；此处不重复。

PostgreSQL 用 `pg_hba.conf` 在服务内拒绝子网外客户端；Redis 没有等价能力（ACL 只限定命令/键/通道，`bind` 只选监听地址）⇒ 按 [ADR-0003](../docs/decisions/ADR-0003-redis-source-restriction.md) 不装来源白名单，靠绑定 + 口令 + 「路由器不转发入站」边界。任何新增到 `6379` 的路径（端口转发、路由、全局 IPv6、容器发布端口、迁机）都必须先装主机 nftables 白名单（非 UFW）并验证再放行——这是触发器，不是偏好。

## 生产配置门禁与双载荷密钥

`prod` profile 关闭 `springdoc.api-docs`/`swagger-ui`（`/v3/api-docs`、`/swagger-ui.html` 返回 404），CORS 只接受显式 https origin（`CORS_ALLOWED_ORIGINS`，回退 `FRONTEND_BASE_URL`），GitHub OAuth 与前端 URL 必填。`ProductionConfigurationGuard`（`@Profile("prod")`）把文档开关、CORS 占位/通配、OAuth 缺失、开发密钥四类缺项一次性列出并 exit 1；消息里的属性名用反引号，避免被日志掩码吞掉下一个词。密钥分两份载荷：`secrets.enc.yaml`（dev）与 `secrets.prod.enc.yaml`（prod），`render.sh --env` 选择；应用数据库凭据是 `DB_PASSWORD`（dev 与 `POSTGRES_PASSWORD` 同值，prod 为 `ctt_app`）。Redis 口令是实例级、与 db1 的另一服务共享，不能按环境拆分——轮换前必须协调该服务，或用 ACL 用户/独立实例（见 `docs/production-configuration.md`）。未知路径由 `NoResourceFoundException → 404 COMMON_002`，不得回落 500。

**配置默认值必须落在它声明的那一层，并有可核对的信号**：基础 profile 保持安全默认（审计保留 0 = 全留），生产 profile 显式携带已批准的周期（`${AUDIT_RETENTION_DAYS:180}`），`0` 是回滚开关；启动时 INFO/WARN 打印生效策略，每轮清理打印 cutoff/删除行数/耗时（零删除也打印），否则「已启用」只能靠猜。文档声明某项生产必须配置时，要么配置已随 profile/部署落地，要么明确写出由哪个部署产物提供——不能只写「部署时设置」而没有任何消费者。

## 管理面与业务面分端口

actuator 与 metrics 不由业务端口提供：`management.server.port`（默认 `8081`，可由 `MANAGEMENT_PORT` 覆盖）与 `management.server.address`（默认 `127.0.0.1`）另起一个回环服务器，应用端口不再提供任何管理载荷——白名单路径 404（无 handler），其余 `/actuator/**` 由安全链在分发前 401。两面结果：**暴露面**只由管理端口决定，业务端口再开放也带不出指标；代价是**探针与抓取必须跟着搬**——compose 健康检查、blackbox、Prometheus 抓取、集成测试全部按新端口寻址，漏一处就出现「容器不健康」或「target 一直 down」。管理服务器同样经过应用安全链，所以白名单必须包含要公开的端点（实测未加时返回 401 `Token invalid`），且它只在回环上可答。管理端口与 `server.port` 相同时 Boot 启动即拒绝（`Management-specific server address cannot be configured…`）；`ProductionConfigurationGuard` 另外拒绝端口缺失/为 0 与非回环地址，避免生产把管理面绑到外部接口。注册表要显式开启（`management.prometheus.metrics.export.enabled=true`）：「类路径上有注册表」不等于「端点已创建」，测试要断言端点与暴露集合，而不是依赖自动配置的直觉；测试 profile 用 `management.server.port: 0`（随机回环端口）避免缓存上下文争用，同时用一份基础 profile 的绑定测试钉住 `8081`/`127.0.0.1` 默认值，否则随机端口会把默认值漂移完全掩盖。暴露端点固定 `health`/`info`/`prometheus`，通用 `metrics` 端点保持关闭（逐条枚举 meter 会把管理端口变成发现面）；指标标签只放有界值（操作、结果、状态码类、路由模板），不放用户/设备/邮箱/原始 URL。

## 业务指标的标签预算与聚合口径

业务信号统一挂在 `cttserver.business.*`（导出为 `cttserver_business_*`），由 `common/metrics/BusinessMetrics` 一处注册，标签取值只能来自枚举——域名里的自由文本（如 `Device.platform`）不能当标签，因为取值个数不受代码控制，基数会随用户输入增长。**耗时只发布直方图分桶**（`publishPercentileHistogram()`），不发布客户端分位数：分位数由各实例各自计算，blue/green 两个 target 被抓进同一个 Prometheus 后无法合并，而 `histogram_quantile()` 能在查询期跨实例聚合；`publishPercentiles` 还会额外生成带 `phi` 标签的 `*.percentile` 计量，突破标签预算。标签预算按**业务维度**计（`le`、`phi` 属结构性标签），`BusinessMetricsTest` 与 `BusinessMetricsScrapeIntegrationTest` 分别从注册表和抓取端断言上界，改名或改标签会让构建失败而不是让告警静默失效。**待处理量用 gauge 现算**（一次走索引的 `countByStatus`、一次 Redis `SCARD`），读取失败返回 `NaN`——它是**存在但无值**的样本，`absent()` 不会为它报警、`sum()` 会把它传播出去，所以告警要用 `isnan()`；既不给旧值，也不让整次抓取因一条查询失败而失败，「没有数据」必须能与「数据是 0」区分开。

**指标的语义必须等于被观测对象自身的定义**，否则序列只是好看的数字：`CONFLICT` 只表示客户端状态与服务器不同且输了竞争——内容与删除状态都一致的重复提交走 `SKIP_UNCHANGED`，记 `SUCCESS`+`EMPTY`，否则插件的常规重放会淹没冲突率；`FAILED` 只是「已安排重试」，重试耗尽的死信是 `CANCELLED`，`SENDING` 会来回抖动故不入序列；审计指标统计「事件是否落库」，`WRITTEN` 必须在行所属事务提交之后才累加（`AuditLogWriter` 的 `REQUIRES_NEW` 让「返回即提交」成为事实），失败路径的 `payload` 记 `UNKNOWN` 而不是 `EMPTY`。埋点落在业务边界而不是控制器：同步区分成功/冲突/失败与有无载荷（`PushOutcome`），限流区分分桶拒绝与无维度总闸（`type=GLOBAL`），审计区分提交与丢弃，这样「失败在哪一环」才有答案。

## 追踪标识只有一个来源

日志 MDC 的 `traceId`、响应头 `X-Trace-Id`、导出的 span 必须是同一个 id——都取服务端 span 的 trace id，采样决定因此一次管住三者；任何一处自行生成都会让「日志里按 traceId 搜到的请求」与「追踪系统里看到的请求」对不上。**把 traceId 写回给调用方的代码一律走 `TraceIds.current()`**（错误体、审计、响应头）：评审发现异常处理器、API key 过滤器、条款过滤器与认证入口各写了一遍 `UUID.randomUUID()`，产出 36 位串，与响应头和日志都对不上——分散的复制品必然漂移。客户端传来的 `X-Trace-Id` 只在没有 span 时采信（追踪关闭或该请求未产生 span），否则会与已导出的 trace 脱钩。入站只认 W3C `traceparent`（网关按 W3C 设置），不加 B3 面：没有调用方说 B3 时多支持一种格式只是多一条未测路径。

采样器是 parent-based：上游标记 sampled 的一律记录、标记 unsampled 的一律丢弃，`management.tracing.sampling.probability` 只作用于本进程起始的 span（定时任务、无上游的请求）。所以「生产 10%」要求采样决策留在服务侧；网关若总是标记 sampled，比例永远不生效。`TracingSamplingIntegrationTest` 把比例压到 0，证明标识仍然生成——采样只管导出，不管 id 是否存在。

平台事实（Boot 4.1.2）：`OpenTelemetryPropagationConfigurations$PropagationWith*` 带 `@ConditionalOnEnabledTracingExport`，**只有导出开启时才装配 `CompositeTextMapPropagator`**，否则是 `noopTextMapPropagator`，每个请求新开 trace、入站 `traceparent` 被丢弃。所以 `TracingPropagationConfiguration` 在 `management.tracing.export.enabled=false` 时补一个 `W3CTraceContextPropagator`；删掉这个条件，本地关联会静默失效（日志看起来一切正常，只是 trace 对不上），而生产因为导出开启反而不受影响——这类「只在基座出错」的默认值最容易被忽略。过滤器顺序是第二个坑：`RequestContextInitializerFilter` 必须在 `ServerHttpObservationFilter`（`HIGHEST_PRECEDENCE + 1`）之后、安全链（-100）之前，否则拿不到 span。验证只能在真实容器上做（`MockMvc` 不经过注册的过滤器链）：`TracingIntegrationTest` 断言响应头等于传入的 `traceparent`，`OtlpTraceExportIntegrationTest` 用桩收集器断言导出的 protobuf 里带同一个 trace id——「span 出进程」才是导出功能的验收，而不是「Bean 存在」。

两个易漏的一致性点：`RequestLoggingFilter` 必须晚于 `RequestContextInitializerFilter`（当前 `HIGHEST_PRECEDENCE + 3`），否则它在 `finally` 里写 ACCESS_LOG 时 MDC 已被清掉，访问日志永远没有 traceId；出站客户端必须用应用的 `ObservationRegistry` 构建（`RestClient.builder().observationRegistry(...)`），静态 builder 既不会产生 client span 也不会注入 `traceparent`——`CaptchaService` 与 `GitHubOAuthClient` 是现有两例，新增下游时照此办理。

## 观测栈：一个后端、一处保留期、整目录挂载

应用侧只负责「产出」：指标走回环管理端口的抓取面，日志走 stdout 的 JSON，span 走 OTLP 的默认端点。接收侧必须**少一跳**：Tempo 直接听 `4317`/`4318`，`management.opentelemetry.tracing.export.otlp.endpoint` 指着它，就不需要再部署、再监控一个 OTel Collector；只有「stdout 日志 → 日志库」这类应用无法自己完成的搬运才引入采集器（Alloy），并且只收本服务的容器、只加容器名一个标签——日志里的 `traceId`/`level`/`uri` 留在正文里由查询时解析，进了索引标签就成了无界基数。

保留期写在各自会执行它的地方：Prometheus 用 `--storage.tsdb.retention.time`，Loki 用 `limits_config.retention_period` 加 `compactor.retention_enabled`，Tempo 用 `backend_worker.compaction.block_retention`。写进文档而不写进配置的保留期等于没有保留期——P0.16c 的审计保留就吃过这个亏。同理，**部署事实不进仓库**：公网探测目标（自有域名、期望状态码）由部署机的 `file_sd` 文件提供，仓库只留 `.example` 说明形状，这样换一台机器部署不需要改代码，也不会把租户域名写进公开仓库。

配置按**目录**挂载，不按单文件。单文件 bind mount 钉住的是启动时的 inode，之后在宿主上改写文件（尤其是用新 inode 的写入方式），容器仍然读旧内容——`POST /-/reload` 会返回 200 而配置毫无变化，排查方向容易被误导到「reload 不生效」。整目录挂载顺带让新增的规则文件、target 文件无需重建容器。三个镜像事实同样容易踩：`grafana/tempo:latest` 已是 Tempo 3（`compactor` 块改名 `backend_worker`，旧配置被 `-config.verify` 直接拒绝）；Alloy 的数据卷属 uid 473 而编排统一 `cap_drop: ALL`，不显式 `user` 就连 root 也建不出目录；Redis exporter 的口令文件不是纯文本而是 `map[string]string` JSON（键是规范化后的 `redis://host:port`），镜像又没有 shell 可做入口垫片，只能由渲染脚本直接产出 JSON。

验收要落在「数据真的到了」：`probe_success`（不是 `up`——`up` 只说明 blackbox 自己被抓住）对每个公网目标为 1、Tempo 能用 `GET /api/traces/<id>` 查回一条合成 span、Loki 能按容器名查到实时日志、`pg_up`/`redis_up` 为 1。应用尚未部署时，抓取目标 `up=0` 是预期状态，不是故障——把它写进边界，而不是靠「看起来没红」判断栈是否可用。
