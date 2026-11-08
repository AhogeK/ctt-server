# Active Context

- [2026-11-08] - P0.6 审计载荷开关：评估后删除(v0.93.7)
 - 核查:`ctt.security.audit.log-payloads` / `masked-fields` 自 2026-03-18 引入后无生产读取方(仅绑定测试);`AuditDetails` 只有 reason / errorCode / attemptCount / stateBefore / stateAfter / ext
 - 决策:不是「无消费者就删」,而是先问「要不要这个能力」→ **不引入请求体审计**。依据:审计行 `ON DELETE SET NULL` 会永久保留用户内容,与注销承诺冲突;生产脱敏是文本正则,不是 JSON 掩码(`pwd` / `otp` / `apiKey` 不匹配,空值/对象值会被破坏);`SyncPushRequest` 单次最多 1000 会话;ASVS 16.3.2 要求访问审计不记录敏感数据,GDPR 5(1)(c)(e)/17(1)(a) 要求最小化与可删除。完整分析见 `docs/decisions/ADR-0001-audit-payload-boundary.md`
 - 交付:删除 `AuditProperties` 与 base/prod 两份受版本控制的 YAML 的 audit 块(`application-local.yaml` 被 gitignore,仅本地改动),修正 5 处测试构造器与 `SecurityPropertiesTest`;`security-architecture` 增 Audit Storage Boundary;`audit-boundary-spec` 增「不存请求体」规则;AGENTS R9 改为「先追溯意图再决定接线或删除」
 - 验证:compile + 定向测试全绿;实跑 local profile 的 `POST /auth/login`(body 含 `password`),审计行只有 `{"reason":"Invalid email or password","errorCode":"AUTH_001"}`,全库检索该密码串 = 0;全量 1691 tests / 0 failures(P0.6 批次;v0.93.8 删除 `MaskSerializerTest` 后为 1687 tests / 0 failures / 1 skipped)
 - 后续(P0.6b):边界尚未完全落地——`PROFILE_UPDATED` 写旧/新显示名,passkey 写标签,`MAIL_IDEMPOTENT_SKIP` 的 resource_id 是原始邮箱,邮件审计写 `recipientMasked` 与可能含地址的 `lastError`,`TokenRefresh` 重复写 IP/UA;这些会随审计行在注销后保留。计划改为只写 action/资源 id/布尔/枚举/计数,并另定 IP/UA 的保留期
 - 复审(双轴):ADR 证据曾用 `Matcher.replaceAll` 冒充 `MaskingMessageConverter.mask()`——已修正为真实行为(`newPassword` 正确掩码;空值/对象值才破坏文本);无生产使用的 `MaskSerializer` 已在 v0.93.8 删除,文档改为两层;计划 P0.6b 的状态、顺序与生产量级验收已补齐
- [2026-11-08] - P0.5 密钥改 Compose secrets（v0.93.6）
    - 交付：`.sops.yaml`（只放 age 公钥）+ `deploy/secrets/secrets.enc.yaml`（6 个键，仅密文）+ `render.sh`（解密成 0400 文件，`--mode` 可改，输出目录有护栏，重跑会先清旧文件）+ `frp-token.sh`（接入机渲染 600 文件）+ `run/` 加入 gitignore；`up.sh` 与 `Jenkinsfile` 起栈前调用渲染
    - compose：应用走 `SPRING_CONFIG_IMPORT=optional:configtree:/run/secrets/`（配置里的 `${...}` 引用不变），PostgreSQL 用 `POSTGRES_PASSWORD_FILE`，Redis 写 tmpfs 配置后 `setpriv` 降到 redis（口令不进进程表），Grafana 用 `GF_SECURITY_ADMIN_PASSWORD__FILE`；环境变量里的密钥全部移除
    - 验证：运行中的 6 个容器 `.Config.Env` 逐值比对无密钥；`/run/secrets` 文件 0400 且容器用户可读；缺渲染文件时 Compose 报 bind source 不存在并拒绝创建；缺 age 身份时 `render.sh` 退出 1；redis `/proc/1/cmdline` 只有 `redis-server *:6379`
    - 文档与规则：新增 `docs/deployment-secrets.md`（初始化/日常/轮换/frp token/Linux 可读性/验证），README 快速开始与配置段、文档索引、`.env.example` 抬头同步；`AGENTS.md` R9 增「容器密钥只走 Compose secrets」
    - 复审修复（双轴）：`.dockerignore` 补 `deploy/secrets`（此前解密文件会进构建上下文）；`render.sh` 重跑不再因 0400 写入失败、并清理已移除的键；README 快速开始补渲染步骤；Jenkinsfile 补渲染；Redis 口令改为配置文件 + `setpriv`；Grafana 口令 secret 化
    - 复审二期修复（Standards 轴）：`render.sh` 改为**先解密到临时目录、成功后才替换**（旧版先删后解密，密钥错误会清掉上一份可用文件），并把解密结果写到独立目录避免把合并明文带进 `run/`；新机器文档不再教 `age-keygen` 后直接 `updatekeys`（没有已授权身份无法解开数据密钥）；计划/AGENTS 的 Redis 描述改为 wrapper + `setpriv`、补 Grafana 消费者
    - 未闭环项**不再单设清单**，按阶段归入阶段表：P0.17（`FRP_TOKEN` 值 + Linux 上 0400 可读性实测）、P0.16（生产密钥从开发值换生产值）、P3.1（CI 节点须持有 age 身份）；`docs/deployment-secrets.md` 同步指向这些条目。教训：待办只能有一处真相，散落的「滚动清单」会让状态无法快速聚焦
- [2026-11-08] - P0.4 compose 加固（v0.93.5）
    - 交付：两份 compose 共用 `x-hardening` 锚点（`restart: unless-stopped`、`stop_grace_period: 45s`、`read_only`、`cap_drop: [ALL]`、`no-new-privileges`、json-file 10m×3），逐服务补 `mem_limit` / `cpus` / tmpfs；postgres 加回 CHOWN/DAC_OVERRIDE/FOWNER/SETGID/SETUID，redis 再加 SETPCAP
    - 实测：9 个容器 `docker inspect` 逐项命中；edge / blue / green / Prometheus / Grafana / Mailpit 六处 200，两条外部探测结果为 1；演练后停掉其余容器，只保留 Mailpit
    - 坑：`read_only` 下 `redis:latest` 没有声明卷、工作目录在根文件系统，entrypoint 的 chown 直接报只读；`cap_drop: ALL` 又让 setpriv 无法收窄 bounding set（缺 CAP_SETPCAP）⇒ 给 redis 加 tmpfs `/data` 并补 SETPCAP
    - 双轴复审后修正：三处能力清单文档（AGENTS / 计划 / systemPatterns）改为精确列表（redis 无 FOWNER）；README 的「tmpfs 即卷」与漏 `restart` 改准；生产 `Dockerfile` 的 ENTRYPOINT 补 `-XX:MaxRAMPercentage=45 -XX:InitialRAMPercentage=20`（原先只有本地镜像带，900m 上限下堆只有约 225 MB）；blue/green 的重复 `mem_limit` 删除，统一继承 base `app`；inspect 记录落在 `.omp/notes/2026-11-08/compose-hardening.md`
- [2026-11-08] - P0.3 构建上下文收口（v0.93.4）
    - 交付：新增 `.dockerignore`，排除 `.git` / `build` / `.env*` / `.plans` / `memory-bank` / `docs` / `dev-docs` 与 AI 工作区、编辑器状态；保留生产构建所需的 `gradle/`、`gradlew`、构建脚本、`src/`，以及本地镜像暂存的 `build/local-image/app.jar`
    - 实测：复制进镜像的上下文 **249.44 MB → 4.06 MB**（不含暂存 jar）、92.16 MB（含 88 MB jar）；生产前置文件校验通过；本地镜像 `deploy/local/Dockerfile` 构建成功
    - 坑：BuildKit 的 `transferring context` 受内容寻址缓存影响（第二次读数只剩 71 KB），不能当体积证据；改用 `COPY . /ctx` + `du -sb`，已写入 `AGENTS.md` R17
- [2026-11-08] - 误关用户常驻 Mailpit（已恢复，待授权提交）
    - 事故：P0.2 演练收尾用 `docker compose … down`，把用户长期使用的 `ctt-mailpit`（:8025）一起删除；`mailpit_data` 卷未受影响，历史邮件（`mailpit.db`）完好
    - 恢复：`docker compose -f docker-compose.yaml up -d mailpit` → 容器 healthy、UI 200、SMTP `220 … ready`，数据卷复用
    - 防复发：mailpit 加 `restart: unless-stopped`；README 写明 `down` 会带走它并给出恢复命令；`AGENTS.md` R19 增边界「用户常驻服务不得被清理波及」；`.plans` 的踩坑表补一行（演练收尾不得带走别人的常驻服务）
    - 版本：PATCH 0.93.3（compose 变更）
- [2026-11-08] - P0.2 镜像可追溯与升级演练（v0.93.2）
    - 交付：`deploy/local/digests.sh` 从两份 compose 解析 7 个镜像、输出 Markdown 表，`--out` 追加到部署记录；`ctt-server-app` 主机构建，按镜像 id 记录；README 补用法与演练四步
    - 演练：记录 → `up.sh --profile obs`（edge / blue / Prometheus / Grafana 各 200，两条外部探测结果均为 1）→ `clean build --rerun-tasks`（**1695 tests / 0 failures / 1 skipped**）→ 再记录：7 个镜像摘要完全一致，只有 app 镜像 id 随重建变化（预期）
    - 注意：首次 `clean build` 1 秒完成是任务缓存命中（`:check UP-TO-DATE`），按 R12 用 `--rerun-tasks` 重跑才是真验证
    - 部署记录：`.omp/notes/2026-11-08/image-digests.md`（已 gitignore，含两次带时间戳的表）；演练后 `docker compose … down`，18080/9090/3000 均已释放
- [2026-11-08] - 收口：README 纠错、规则与记忆维护（随本批提交）
    - README：修正「no dashboard is provisioned yet」与上文「已有预置面板」的自相矛盾（改为「面板展示的是外部探测结果，应用自身指标随 P1.1 到来」）；安全段补「prod JSON 日志掩码文本与结构化字段」
    - `AGENTS.md` R9 新增「日志脱敏必须覆盖输出通道」；`techContext` 包结构改为实际目录（补 user/device/mail/audit/language，删不存在的 apikey/）、核对基线刷到 v0.93.1；`progress` 补 0.93.1 里程碑并修正 0.92.x 与 0.93.0 的排序
    - R13：10-18/10-19 六条（R1–R3 与阶段六计划，70 行）完整移入 `archive/activeContext-2026-10.md`，热文件降到 136 行、只留结论
    - 死链修复：README 与 `docs/security-architecture.md` 都链到 `docs/terms-acceptance-frontend-guide.md`，而该文件按内容判定属对接方指南、master 上不存在（`0be68a1` 曾刻意移除）⇒ 生产分支两个死链。已删两处引用，指南保留在 develop
- [2026-11-08] - 计划文件完成总结与纠偏（纯文档，不 bump 版本）
    - 补：顶部加「怎么读」与能力索引（11 个能力面 → 节 → 状态 → 最近变更）；思维导图补「账号安全」与 P0.1；统计与排行新增 S6（v0.58–v0.79 演进：7 维 × 6 周期 = 41 组合 / 35 带 TTL、词表 v2 842/489/76、排行 `ZCOUNT` 语义）；文末补「prod 日志脱敏（v0.93.1）」与「账号注销（v0.77.0）」两条交付记录
    - 纠偏（以代码为准）：锁定 / OAuth / API Key 三处的枚举别名与 HTTP 码（`AUTH_004/013/015/016/017/018`；`AUTH_012` 是 403）、OAuth 账号端点 `/auth/oauth/accounts`、导出契约（单 JSON + `ctt-export-<yyyy-MM-dd>.json` + `exportVersion 1.0` + `serverExport` 分块）、JUnit 5 → 6、H 章「需创建」→ 已交付、章节标题状态标记、中英混排条目
    - 校验：表格列数一致、代码围栏配平、无重复 H2、无 TODO/占位、两个子计划链接可达；未改代码与版本号
- [2026-11-08] - P0.1 prod 日志脱敏生效（v0.93.1，已提交 `7749ac7`；版本 `709b098`）
    - 根因（两处，评审核实）：① prod 用 `JsonEncoder`，而 `MaskingMessageConverter` 只挂在 console 的 `%maskedMsg` 上 ⇒ 生产 JSON 绕过 pattern 级脱敏；② JSON 把 `arguments` 与 `kvpList` 当**独立字段**输出——`RequestLoggingFilter` 用 `addKeyValue("query_string", …)` 记整条查询串，`/verify-email?token=…` 的凭据在正常流程上就进了日志
    - 交付：`MaskingMessageConverter.mask()` 抽成静态共享（用 `(?!\{\})` 前置断言保住模板里的 `{}`，而不是把 `{` 排除出值字符类——那会让 `cookie={sessionId=…}` 完全不再匹配）；新增 `MaskingJsonEncoder`——掩码 formatted 文本并将其作为 `message` 输出、掩码 `kvpList` 字符串值、一旦文本命中就丢弃 `arguments`，未命中则原样交给 `JsonEncoder`；`logback-spring.xml` 的 prod encoder 切换
    - 验证：`MaskingJsonEncoderTest` 4 例（含 kvp/query_string 与占位符两例）+ `MaskingMessageConverterTest` 11 例全绿；`clean build` 通过；prod profile 实跑——请求 `/api/v1/auth/verify-email?token=leakcheck123` 后，访问日志里是 `"query_string":"token=******"`，全行无 token 原文
    - 文档：`docs/security-architecture.md` 三层防线表与说明改准（写明命中事件的 `arguments` 会被省略、MDC 与 throwable 未覆盖）；`systemPatterns` 的「结构化日志不能沿用 pattern 级脱敏」补上 `arguments`/`kvpList` 两条独立通道
- [2026-11-07] - 运维与可观测性升级计划（draft，已按要求重写）：`.plans/ctt-server-development-plan.md` §「🔍 运维与可观测性升级」—— 选型全部定案，阶段 P0–P7：P0 修既有状态（隧道改 systemd、端口收口、生产库隔离）、P1 指标与追踪、P2 告警面板、P3 发布链、P4 备份与 Resend、P5 压测演练、P6 文档、P7 对外接入（`ctt.ahogek.com` 阿里云 A 记录 + certbot、GitHub OAuth、Passkey/hCaptcha/CORS）。补「本地先行」一节。**仓库内不写真实 IP 与主机名**（角色名 + `.omp/notes/2026-11-07/deployment-endpoints.md`，`.omp/` 已 gitignore）。deep research 后修正六处：GitHub 单 App 可存 10 个回调但省略 `redirect_uri` 只用第一个；Jenkins 实为 nginx 443 + ufw 默认拒绝（非 8080 公网）；Resend 无 SMTP 服务端日志；可信代理须含 edge 那一跳且 edge 要用 host 网络（否则 IP 限流与审计退化）；`management.server.port` 还需 `management.server.address`；**部署机 ufw 装了但 `ENABLED=no`，PG/Redis 对整段 LAN 全开**（P0.13 改为启用防火墙，实施需你在场输 sudo 密码）
- [2026-11-07] - 依赖升级（v0.92.2）与计划收口
    - 升级：flyway 13.11.1→13.12.1（core 与 database-postgresql 同版本）、commons-codec 1.22.0→1.23.0、spotless 8.10.4→8.11.0、ben-manes 0.66.0→0.67.0、Gradle wrapper 9.8.2→9.9.0；Spring Boot 4.1.2 / springdoc 3.2.0 / greenmail 2.1.15 / pg 42.7.14 / junit-platform 6.1.3 / otp-java 2.2.0 / webauthn 2.10.1 已是各自最新（4.2.0-RC2 属 RC，按策略跳过）
    - 验证：分层 clean build —— 库依赖 3m23s、插件 17s、wrapper 全量 `--rerun-tasks` 3m49s；**1689 tests / 0 failures / 1 skipped**；`spotlessCheck` 通过；升级后 `dependencyUpdates` 报 0 项、Gradle 无更新
    - 事实：ben-manes 报告仍只列 4 项（Spring Boot 等漏报），版本一律以 Maven Central / Plugin Portal 元数据为准；spotless **8.11.0 + google-java-format 1.36.1 组合实测可用**（1.37.0 仍不用）
    - 计划：`### 🔍 运维与可观测性升级` 完成态处理（P0.8/P0.10/P0.14 ✅、P0.9 🚧，新增 P0.15/P0.16、P1.5、P3.7、P6.4），补「工程与生产化缺口复查」「踩坑表」；修正总览图归类、Resend 额度断言、`.sisyphus` 死链、拓扑图 frpc 标签
    - 仍缺（计划条目，未实施）：覆盖率校验未挂 `check`、prod 未关 springdoc、CORS 占位域、OAuth 与验证码缺键无 fail-fast
    - 规则（R14 记录）：`AGENTS.md` R12 补两条实测事实——报告漏报需查元数据（2026-11-07 复核仍只列 4 项）；Spotless 8.11.0 + google-java-format 1.36.1 为当前可用组合；新增「wrapper 与依赖同批、用 `--rerun-tasks` 验证」
- [2026-11-07] - P0.0 本地一键全栈（v0.92.3，待授权提交）
    - 交付：`docker-compose.local.yaml` 叠加层——基础 `app` 用 `profiles: !override ["standalone"]` 让位给两色；`app-blue`（18081）与 `app-green`（18082，profile `green`）走 `extends` 复用基础定义；`edge`（Caddy `:18080` → `UPSTREAM`）；`obs`（Prometheus/Grafana/blackbox）；入口脚本 `deploy/local/up.sh`
    - 阻断与取舍：容器内 Gradle 构建**无法**访问 Maven Central（`Remote host terminated the handshake`，本机代理所致），而 `build.network: host` 被 buildkit 拒绝（需 `--allow=network.host`）⇒ 改为宿主机构建 `bootJar` + `deploy/local/Dockerfile` 只打包 jar，`up.sh` 把三步串成一条命令；生产镜像仍走多阶段 `Dockerfile`
    - 验证：经 edge `18080/ctt-server/actuator/health` = **200**（`groups: liveness/readiness`），`app-blue` 直连亦 200；`--profile obs` 后 Prometheus `/-/ready` 就绪、`prometheus` 与两条 `blackbox-http` target 全 `up`、Grafana `13.3.0` `api/health` = `database: ok`
    - 边界（未做）：应用自身 `/actuator/prometheus` 未暴露（P1.1），Loki/Tempo/Alertmanager 属 P1.4/P2，蓝绿切换属 P3.4；README 开发段已补用法
    - 双轴审查后修正（2 个 reviewer 并行）；**容器镜像一律 `latest`（用户 2026-11-07 决策：始终最新、含大版本）**，原先钉 tag 的做法已回退，可追溯性改由部署时记录 digest：①profile 统一为 `bluegreen`（原 P0.0 行与 2170 行不一致，compose 的 `green` 会静默忽略）②Grafana 首启约 **3 分钟**（13 版逐个安装 app-platform API 后才绑定 3000），此前 120 s 探测窗口误判为「起不来/内存不足」；实测 `api/health=200`、预置面板 `CTT local stack` 可见、`probe_success=1`（两 target）、占用 281 MiB ⇒ 仍在 `obs` profile 内 ③obs 镜像一律 `latest`（用户决策：不含固定 tag，可追溯性靠部署记录 digest；构建镜像 `eclipse-temurin:25-*` 例外——用户 2026-11-07 明确「JDK 跟着项目走」，不跟 latest）④compose 头注释改指 `./deploy/local/up.sh`（裸 compose 在干净检出会因缺 jar 失败）⑤README 说明 edge 实际代理 `app-blue:8080`、`:18081` 只是宿主机端口 ⑥版本按 R15 从 PATCH 改 MINOR（0.93.0）⑦`up.sh` 的 `ls|grep|head` 改 `sed -n '1p'` 防 pipefail
    - 内存调节（实测）：Docker VM 仅 1.9 GB，最初 `-XX:MaxRAMPercentage=60` + 700m 上限时第二色被 OOM 杀（exit 137, `oom=true`）；改 `mem_limit: 900m` + 45% 后 blue+green+Prometheus/blackbox 同起正常（约 1.34 GB）；**加入 Grafana 后不稳**：稳态接近 1.7 GB，启动期 blue 被 OOM 杀（`oom=true`, exit 137）⇒ 两色+Grafana 需 VM ≥ 3 GB，推荐 4 GB。**已调至 4 GB 并复验**：3.81 GiB 可用下全 profile 同起约 2.06 GB，五处 200、`oom=false`，Grafana 上限 768m。诊断路径：容器内 `wget 18080` 得 503 → `getent hosts app-blue` 解析失败 → `docker inspect` 见 `oom=true`（DNS 失败只是容器已死的下游症状，别先查网络）
- [2026-11-06] - 同步容量研究与修复（v0.92.0 → v0.92.1；实测表见 `domains/sync-protocol/references.md`，研究全文 `.omp/research/2026-11-05/`）
    - 插件 0.22.0：每轮 pull 用本机游标作 `replayFromChangeId`、每页应用后持久化游标；老服务端 400 时回退为普通请求并按小时重探；所有请求发 `Accept-Encoding: gzip` 并自行解压
    - 服务端修真实缺陷：`nextCursor` 曾被抬到 watermark，会让落后超过一页的客户端跳过未收到的变更；改为「实际下发页尾」，单调性交给 upsert 的 `GREATEST`
    - 轮询写收敛（v0.92.1）：游标未推进不再 upsert（空轮询不写库）、设备心跳 15 分钟内不重写。审计表实测 29,749 行 / 11 MB、89% 是已停写的数据面行为；收口后增量按活跃 key 计（每 key ≤24 行/天）⇒ **不做清理或分区**，只纠正「20 年后 2.7 GB」的过时推算并留复查阈值（>1 GB 或查询变慢）
- [2026-11-04] - 安全加固与刷新 Cookie（v0.91.3 已推送 develop / master；v0.91.4 OAuth 恢复修复随 2026-11-06 批次提交）
    - API key 仅浏览器可改密码/邮箱；密码变更撤销其他 session 与 API keys；JWT/OAuth key 无公开 fallback；actuator 仅 health/info；XFF 仅可信代理；OAuth state cookie + token fragment（可信 TLS 代理下 Secure）；audit trace/IP 校验；mail outbox cascade/retention、显式 user_id、失败日志脱敏；MDC IP 脱敏；sync 批量上限；HSTS 代理；`/auth/refresh` Cookie 优先、body 可省略（无令牌 400，service 空令牌 401）；旧 OAuth 密钥密文按缺失处理，避免账户列表 500
    - 全量 **1671 tests / 0 failures / 1 skipped**，build/coverage 通过；前端影响见 `.omp/reports/2026-11-04/security-hardening-frontend-report.md` 与 `.omp/reports/2026-11-04/refresh-cookie-only-frontend-report.md`

- [2026-11-03] - 通行密钥基础 + 第二步（v0.91.0，已交付：6432bc1/2f99161/cc1995e/c1ac658）
    - 新增 `POST /auth/2fa/passkey/options|assert`；options 用 `peek` 校验而不消费 2FA `challengeToken`，WebAuthn challenge 存 `SECOND_FACTOR` 类型与 token 的 SHA-256 绑定；`allowCredentials` 由账号 `userHandle` 生成
    - 失败只消费 WebAuthn challenge，保留 TOTP/恢复码回退；成功才 `GETDEL` 消费 2FA challenge；无 TOTP 的账号不进入第二步；新 session 天然 recent
    - `LoginChallengeResponse` 增加 primitive `passkeyCount`；OAuth 回跳 fragment 同步携带；密码与 OAuth 共用同一端点
    - 新增发现型登录 `POST /auth/passkey/options|assert`（不进入 TOTP）与 `/users/me/passkey/*` 注册/列表/重命名/删除；注册与删除要求近期认证，API key 一律拒绝
    - 依赖 Yubico `webauthn-server-core` 2.10.1；Redis 操作绑定挑战（5 分钟、`GETDEL`）、RP ID/origin、UV/UP、计数器 0 语义、备份标志展示、无 attestation、邮件通知（`REQUIRES_NEW`）；密码/TOTP/恢复码/OAuth 全保留；删除最后凭据仅在有密码或 OAuth 时允许（`AUTH_018`）；新增 `AUTH_032`–`AUTH_035`
    - 测试/文档: 密码 2FA + passkey 成功、错误签名后 TOTP 仍可用、无 passkey 账号 `passkeyCount=0`、跨账号凭据拒绝；全量 **1645 tests / 0 failures / 1 skipped**，`build` 与 `jacocoTestCoverageVerification` 通过。README、`docs/api-reference.md` / `api-governance.md` / `security-architecture.md` / `developer-handbook.md`、`auth-lifecycle` 领域与前端报告 `.omp/reports/2026-11-03/passkey-second-factor-frontend-report.md` 已同步
    - 文档: README、`docs/api-reference.md` / `api-governance.md` / `security-architecture.md` / `developer-handbook.md`、`auth-lifecycle` 与 `api-contract` 已同步；验证: passkey 目标用例通过，全量 **1645 tests / 0 failures / 1 skipped**，build 通过；RestClient 429 自动重试导致假卡死的排障见 `JdkClientHttpRequestFactory` 修复
- [2026-10-30] - 两步验证（TOTP + 恢复码）（v0.89.0，已交付）
    - 新增 `/api/v1/users/me/2fa/*`（状态/start/confirm/disable/regenerate）与 `POST /auth/login/2fa`；启用后密码登录返回 202 + 挑战，OAuth 回调以 `#challengeToken` fragment 返回且不写 token/Cookie；新增 `AUTH_026`–`AUTH_031`
    - 安全: TOTP secret AES-256-GCM（`TOTP_ENCRYPTION_KEY`）、恢复码仅存 SHA-256、`totp_last_used_step` 防重放、Redis 一次性挑战（5 次失败 429）、启用/关闭撤销其他会话、API key 创建要求 WEB_SESSION + 2FA 复核
    - **审查修复（10-31）**: ①启动阻断——`TOTP_ENCRYPTION_KEY` 默认空串 + `@NotBlank` 使三条启动路径全挂（曾靠 `@TestPropertySource` 掩盖）；改为本地占位键、prod 无回退、compose 不再传空值；`completeLogin` 现重校验账号状态 ②审计阻断——`LoginTwoFactorService.complete` 的 `@Transactional` 让 `TOTP_CHALLENGE_FAILED`（AFTER_COMMIT）随回滚丢弃并把已消费凭据一并回滚；移除该事务，`completeLogin` 补显式 `save` ③新增 `SecondFactorVerifier` 收敛三处重复判定（位数从配置推导），删除 `UserLoginService.login` 死方法，OAuth 两条 `process*` 合并 ④`markTotpStepUsed` 去掉仓库层 `@Transactional`、`UserController` 改用 `WebSessionGuard`、去掉失效的 `@RequiresApiKeyScope`、10 个实体通配导入展开 ⑤补 7 例测试（OAuth email-merge+2FA、`AUTH_031` 新旧会话、`AUTH_029`、双凭据歧义、端点级 429、`TOTP_CHALLENGE_FAILED` 落库，末者 RED 可复现；另加 2FA 完成后账号被 SUSPENDED 的 403 用例）
    - 验证: `spotlessCheck` + `jacocoTestCoverageVerification` + 全量 `build` 1618/0/1 skipped；版本 0.89.0；`AGENTS.md` R8 补配置空值与二次认证完成阶段两条边界。**2026-11-02 修复（v0.89.1）**: iOS Google Authenticator 拒扫 52 字符密钥。根因不是版本漂移，而是 `SecretGenerator.generate(160)` 已返回 Base32 字节，`TotpService` 又编码一次（20 字节 → 32 字符 → 52 字符 + `====`）；改为 20 个随机字节只编码一次，返回 32 字符无填充。已启用旧账号需 `disable → start → confirm` 重设；新增密钥长度、start 端点与旧 56 字符 secret 兼容性测试；全量 1620/0/1 skipped
- [2026-10-29] - 会话批量撤销（v0.88.0，已提交推送）
    - 新增 `POST /api/v1/users/me/sessions/revoke`（body `{sessionIds}`，去重后 1..100）：按 UUID 升序取会话锁，逐条撤销并写 `SESSION_REVOKED`；不存在的/已撤销的/他人的进 `skipped`，整体 200 且重放幂等；限流 10 次/时；当前会话可撤销（refresh 立即 401，access token 15 分钟内仍有效）；审查后补 401/403/429 独立示例、拆分 `MAX_BATCH_REVOCATIONS`、抽取 `findLiveTokenUnderLock`、429 用例独立成类、补 null 元素用例；前端反馈后补「按端点分桶 + `Retry-After`/`retryAfter`」文档说明（行为不变、纯文档不 bump 版本）
- [2026-10-28] - 会话列表 500 修复（v0.87.3，待授权提交）
    - 根因: `LoginSessionService.listSessions` 以 `devices.get(row.getDeviceId())` 查设备；浏览器会话的 `device_id` 为 null，而无任何设备的账号会让 `devicesById` 返回 `Map.of()`，`ImmutableCollections.MapN.get(null)` 抛 NPE ⇒ 500 `SYSTEM_001`。修复: `deviceOf()` 先判空再查；回归用例用非 UUID 的 deviceId 登录（浏览器形态），断言列表 200 且 `deviceName`/`ideName` 为 null

- [2026-10-27] - 依赖升级（v0.87.2）
    - Boot 4.1.2 / springdoc 3.2.0 / greenmail 2.1.15 / Flyway 13.11.1 / pg 42.7.14 / ben-manes 0.66.0 / spotless 8.10.4 / JaCoCo 0.8.16 / Gradle 9.8.2；google-java-format 1.37.0 在 Spotless 下崩溃，保留 1.36.1；分层 clean build 各 1567 tests / 0 failures

- [2026-10-27] - README 重设计（文档，已提交推送）
    - 785 → 226 行：Hero SVG + 徽章、机制图（mermaid）、快速开始、API 总览、数据模型、测试、文档索引；API 细节零丢失迁至 `docs/api-reference.md`（仅标题由 `## API Endpoints` 改为 `## Endpoints`）；新增 `assets/readme/hero.svg`；底部 Support 用 `<div align="center">` + 单行 flat-square 徽章（源码换行会变竖排）

- [2026-10-26] - 会话级 IDE 归属与复核补齐（v0.87.0 / v0.87.1，已提交推送）
    - 前端复核两项属实: pull 的 `SyncChangeDto` 未带 IDE ⇒ 跨设备本地副本回退成拉取方 IDE；`applyIncomingFields` 无条件写 ⇒ 缺少字段的更新把已有 IDE 清成 null
    - 修复: `SyncChangeDto` 加 `ideName`/`ideVersion`，`toChangeDto` 的正常分支与 `session == null` 分支都显式传值；IDE 只在传入非空时写入（旧客户端不清空、升级可补值）；单测/集成覆盖两端，全量 1567 tests / 0 failures
    - 需求核实: 报告属实 —— deviceId 是安装级（同机所有 IDE 共享），`devices.ide_name` 只在注册时写，`coding_sessions` 不存 IDE，统计按设备归属 ⇒ 多 IDE 被合并、IDE 升级不更新；插件端 `SyncSessionMapper` 已按会话发送 IDE
    - 契约: 迁移 `V20261026120000` 加 `coding_sessions.ide_name/ide_version`（可空、不回填）+ 部分索引 `(user_id, ide_name) WHERE is_deleted = FALSE`；`SyncSessionDto` 可选 `ideName`/`ideVersion`（≤100/≤50，空白归一 null）；批量 INSERT 16 → 18 列
    - 统计与设备: 过滤 = 会话 `ide_name` ∪ 旧行回退 origin 设备注册值；`IDES` 桶会话优先、回退设备、再 `Unknown IDE`；`ide-filters` = 两者并集；404 仅当两者都无；设备响应改「最近使用」（`DISTINCT ON` 单查询、无 N+1、回退注册值）；每日汇总表不参与 IDE 筛选
    - 导出与不做: 会话行写会话级 IDE（旧行回退设备）；不建 `device_ides`、不拆 device id、不回填旧会话、IDE 不进 `ConflictResolver.sameContent`；单测 + 集成覆盖同设备双 IDE、筛选、设备取最近 IDE、旧客户端无字段

- [2026-10-26] - 设备最后活跃时间停摆修复（v0.86.1，已提交推送）
    - 前端报告核实: **属实**。`setLastSeenAt` 在生产代码中只有 `DeviceService.registerDevice` 一处；`SyncPullService`/`SyncPushService` 只校验归属与吊销，不写回 ⇒ 每天同步的设备 `last_seen_at` 停在注册日，前端 7 天阈值判成 Inactive
    - 修复: `DeviceRepository.touchLastSeen(userId, deviceId, seenAt)` —— 单条 JPQL update（`lastSeenAt` + `updatedAt`，谓词含 `revokedAt is null`，`@Modifying(clearAutomatically, flushAutomatically)`）；pull 与 push 在归属/吊销校验通过后各调一次。用整行 update 而非实体 save 的原因：`Device` 有 `@Version`，同一安装的多插件并发同步会撞乐观锁，而心跳没有业务含义；调用点在 `@Transactional` 内，失败随事务回滚；单测 4 例 + 集成「13 天前的 last_seen_at → pull → 一分钟内」；已推送（6a83f7f → master 77cc619）

- [2026-10-25] - 排行榜排除 0 分成员（v0.86.0，已提交推送）
    - 前端需求核实: `leaderboard:total`/`streak`/`active_days` 各 9 成员其中 7 个 0 分，`night_owl`/`early_bird` 各 8 个 0 分；7 个 AutoTest 账号 `coding_sessions` 为 0 条 ⇒ **不是精度问题，是写入规则**
    - 根因: `writeScore` 无条件 `ZADD`，`recomputeAndWriteAll` 对每个维度×周期都写 ⇒ 无活动账号以 0 分入榜，并计入 `totalParticipants` 与并列名次
    - 修复: `LeaderboardDimension.ranksOnlyPositiveScores()`（除 `GROWTH` 外全部为真）；`writeScore` 在 `score <= 0` 时 `ZREM` 并返回（只查层过滤不行：`totalParticipants`、分页、名次仍会带上 0 分成员）；`LANGUAGE` 的榜索引与 `user:languages` 记录只在正分时维护，零时长语言不建榜；回填代次 3 → 4 以清理存量 0 分成员；`GROWTH` 保留 0/负分（度量而非缺失）
    - 测试: 零会话用户只移除不写入（仅 `growth` 键可写 0）、负增长与零增长仍写入、零时长语言不建榜、TTL 只在写分时设置；集成用例「无活动用户不在榜上且 `totalParticipants` = 1」；状态: 实施完成，待授权提交

- [2026-10-25] - 窗口化 STREAK 回填缺失修复（v0.85.1，已提交推送）
    - 前端报告核实: **五项断言全部属实**。Redis 实测（部署机，地址不入库）：`leaderboard:streak`=2，`month/quarter/half-year/year` 四个窗口各 1；`leaderboard:recompute:g2:vocab2` 存在；代码 `SCORE_RULE_GENERATION` 仍为 2
    - 根因: 标记键只有「代次 + 词表版本」，0.85.0 放开 `STREAK` 全部周期既没提升代次、标记也不含矩阵信息 ⇒ 旧标记命中，`backfillScores()` 首次判断即 return，未推送过的用户没有窗口键；查不存在的 ZSet 返回 200 + 空列表，前端显示空态而非错误
    - 修复: 代次 2 → 3；标记改为 `g{n}:cov{8位}:vocab{v}`，其中 `cov` 由 `LeaderboardDimension.values() × supports()` 排序后 SHA-256 现算 ⇒ **新增维度或放开周期自动失效**；改公式仍需人工提升代次。新标记 `leaderboard:recompute:g3:covbdc93f7f:vocab2`（41 个合法组合）
    - 测试: 断言不再复用 `g2:vocab2`、矩阵增删即改变指纹、枚举顺序无关，排行榜相关 49 例全绿；**刻意未做**：不在真实 Redis 手工回填（共享数据），部署后由 `ctt.leaderboard.backfill-initial-delay-ms`（60s）触发

- [2026-10-23] - `.omp` 全量分类归档（用户要求：时间只做文件夹，不进文件名；一个需求一个文件）
    - 结构: `.omp/<category>/<YYYY-MM-DD>/<kebab-topic>.md`；类别 = reports / acceptance / requirements / research / plans / notes；顶层不再有散文件
    - 迁移 35 个 .md（顶层 30 + plans/ 5 + reports/ 2，去重 2 份；旧 `reports/2026-10-2X-*.md` 命名已并入日期文件夹），`skill-graph-zh.json` 移入 `data/`；同步修正 `AGENTS.md` R26 两处过时行并合并「每轮新建」与「分类归档」表述（✅ 已完成，随该批 AI 提交）

- [2026-10-23] - 数据库连不上诊断（**第二次复发，非代码问题**）
    - 现象: IDEA 内启动应用 ⇒ `flywayInitializer` 取连接失败，根因 `java.net.NoRouteToHostException`（EHOSTUNREACH，SQLState 08001）
    - 证据: 本批未改任何数据源配置；**系统 shell** 到部署机 PostgreSQL（地址不入库）的 `ping` 0% 丢包、`nc -z` 成功；系统日志反复出现 `LocalNetwork: found bundle id com.jetbrains.intellij`
    - 结论: macOS「本地网络」权限挡住 **IDEA 进程**（同一地址外部 Terminal 可连）；处置 = 关开该权限 + `Cmd+Q` 完全重启 IDEA，或在系统 Terminal `./gradlew bootRun`
    - 沉淀: `AGENTS.md` R8 新增「环境类故障先对照再怀疑代码」（异常类型对照表 + 用系统 shell 对照）；详细报告 `.omp/reports/2026-10-23/db-unreachable-diagnosis.md`

- [2026-10-23] - 窗口化 STREAK 启用（v0.85.0）
    - 需求: ctt-web「启用窗口化的连续天数排行」——上一轮已定义口径（窗口内最长 run），本轮接线
    - 交付: `LeaderboardService.longestRunInside`（从 `secondsByDay` 取窗口内日期，按 `prev+1` 计连续段、取最大）；`STREAK` 分支按 `period == ALL` 分流（终身走 `StatsCalculator.streaks`，窗口走新 reducer）；`supports()` 放行 `STREAK` 全部周期 ⇒ 现在**唯一**非法组合是 `GROWTH + ALL`
    - 测试: 新增用例「连续 5 天跨月/周边界」——`ALL`=5、`WEEK`=3、`MONTH`=3、`QUARTER`=5（证明两端切断）；两处"非法组合"样例由 `STREAK+WEEK` 改为 `GROWTH+ALL`（枚举矩阵仍由枚举驱动）
    - **事故记录**: 我用 `open(p,'w').write(open(p).read().replace(...))` 把 `gradle/libs.versions.toml` 写空（先截断再读）——已从 HEAD 恢复并用 `sed` 重做；教训：读写同一文件必须先读完再写，禁止一行式截断写入
    - 前端: 新报告 `.omp/leaderboard-streak-window-frontend-report.md`（选择器补五项、删"待接入"说明）
    - 状态: ✅ 已交付 v0.85.0（2026-10-23；全量 1543 tests / 0 failures）

- [2026-10-20] - 排行榜新增季度/半年周期（v0.84.0）
    - 需求来源: ctt-web「longer leaderboard periods」——周/月榜在用户增长后失去区分度，季度与半年是恢复区分度的最小增量
    - 交付: `LeaderboardPeriod` 增 `QUARTER`（1/4/7/10 月首日）与 `HALF_YEAR`（1/7 月首日），`start`/`endExclusive`/`keySuffix`（`:quarter:` / `:half-year:`）与 `shift`/TTL（93 / 185 天）同步；`supports()` 对 TOTAL/NIGHT_OWL/EARLY_BIRD/ACTIVE_DAYS/LANGUAGE 全周期，GROWTH 自动获得；不支持组合仍 `400 COMMON_003`
    - **窗口化 STREAK：已定义、未启用** —— 分数 = **完全落在窗口内的最长连续编码天数**（两端切断 run）；否决"窗口结束时的连续天数"（当前周期未结束时无定义、且会随窗口回溯变化）。Redis 每成员单值 ⇒ 启用须先把该 reducer 接到写分路径，本轮不做；`STREAK` + 周期继续 400，避免把终身分数塞进窗口 key
    - 顺带修两处**脆断言**: `LeaderboardServiceTest` 的「20 个 key / 15 个 TTL」硬编码改为按枚举推导——这类枚举清单式断言每次扩展都会误报，已改成一条公式
    - 验证: `LeaderboardPeriodTest` 4 例；排行榜测试 49 例全绿（集成矩阵由枚举驱动，自动覆盖 6×7 组合与 400 分支）
    - 状态: ✅ 已交付 v0.84.0（2026-10-20）——其中"窗口化 STREAK 未启用"已被 v0.85.0 取代

- [2026-10-19] - 对接报告约定固化：每轮新建、旧文件不改
    - 用户指示: "每次新报告都要新起文件避免旧内容的混淆"。此前我**原地更新**了 `settings-phase6-frontend-report.md`（10-18 的"滚动契约"）✗ —— `.omp/` 无版本控制，原文无法还原；现已保留该文件并加顶部横幅标注为**历史文件**，最新口径以 `-3.md` 为准
    - 落地: `AGENTS.md` R26 增「对接/变更报告：每轮新建文件、旧文件不改，新文件自述取代范围」；`-3.md` 增「口径唯一性」说明（与旧文件冲突处一律以最新轮次为准）
    - 状态: ✅ 约定已固化（AGENTS + 记忆），随下批 AI 提交


- [2026-10-18 → 10-19] - 个人设置 R1–R3 与阶段六计划（v0.80.0 → v0.83.1，全部已交付）
    - R1 改名 `PATCH /users/me`（v0.80.0/0.80.1）：写方法落 `UserService`（保留 `UserProfileService` 的只读声明）、同名提交幂等、零新增错误码；顺带补 `DELETE /me` 限流与已删账号门控用例
    - R2 会话身份与列表/吊销（v0.81.0/0.82.0）：迁移加 `session_id` + `user_agent`、access token 加 `sid`；受众判别改为「deviceId 已在 `devices` 注册 ⇒ PLUGIN」（`LoginRequest.deviceId` 恒非空，带 deviceId 不能区分）；刷新与吊销都先取会话锁、`refresh` 重读再判定，否则踢掉的会话会复活；被整体吊销的会话刷新返回 `AUTH_003`，只有「会话仍有活跃行」才判重用
    - R3 导出（v0.83.0/0.83.1）：格式由插件导入器决定——单 JSON 对象、`ISO_LOCAL_DATE_TIME`（带 `Z` 会整份导入失败）、扩展收进 `serverExport`、软删不导出、空值写 `""`；`timezoneOffset` 必填；`JsonGenerator` 每页 1000 流式写出
    - 完整过程、验收与提交记录见 `archive/activeContext-2026-10.md`；计划里的 R1–R3 节同步保留了约定与校正

- [2026-10-15] - hourly-days 端点（按日的每小时分布，v0.79.0，已交付）
    - 需求: 前端要在「Average hourly coding duration」面板里把**每天的曲线**叠画在平均曲线背后，并在含今天的窗口标出"当下时刻"
    - **回源核出的实质问题（前端报告未覆盖）**: `hourlyDistribution` 的 `activeDays` 是「**会话起始日**去重」而非领域文档定义的「**有编码的天数**」→ 跨本地午夜的会话只贡献 **1** 天分母，把平均值**抬高**；前端整套叠画假设（分母 = 有编码的天数、逐日数组之和 = 平均曲线的分子）因此不成立。裁决: **修缺陷**（对齐 `meta.md` 定义 + 前端语义 + 新端点需要同源），不做静默修改
    - 结构决策（防止两套口径漂移）: 抽出 `hourlyByDay(..., maxDays)` 单一累加器（`TreeMap<LocalDate, long[24]>`），`hourlyDistribution` 改为**由同一份日桶派生**（小时和 = 逐日相加，分母 = 日桶数）；31 天上限只施加在 `hourlySecondsByDay` 边缘 → 「逐日之和 ÷ 分母 = 平均曲线」由**构造**保证而非约定。集成测试断言该不变量（`average×N ≤ sum < (average+1)×N`，因平均值按整数向下取整）
    - 其他裁决: 原始累加（重叠计两次，与 `hourly` 一致，不合并）；只出活跃日、升序；`activeDays` 取窗口真实值、**不受 31 天截断影响**（与 `hourly` 恒等）；上限 31 防未设窗口的全历史请求放大响应
    - 实现: `StatsCalculator`（`DayHourly` record + `hourlySecondsByDay` + 私有 `hourlyByDay`，重写 `hourlyDistribution`）、DTO `HourlyDayPoint`/`HourlyDaysResponse`、`StatsService.hourlyDays`（`end<start` → COMMON_003，与 hourly 同）、`GET /api/v1/stats/hourly-days`（READ + 60/60，参数与 hourly 完全一致）
    - 测试: 计算器 +7（跨午夜切成两天 / 重叠计两次 / **与 hourly 逐小时一致 + 分母修正** / 31 天取最新 / 丢弃零日并升序 / 空）、集成 +2（逐日数组重算平均曲线 + `activeDays` 与 hourly 相等 / 400 区间非法）。**我自己写错一处期望**（第 11 小时只含第二段 1800，我错记成 5400）—— 跑出来才发现
    - 领域沉淀: `stats-aggregation/principles.md` §11（分母定义 + 单一累加器 + 不变量）、`references.md`（端点表 + 31 天上限）、`practices.md`（"第二份切片会漂移"陷阱）
    - **前端复核回执（2026-10-15）**: 分母修正与结构（唯一累加源、上限只在边界、`activeDays` 与截断无关）两项复核通过；raw 口径 / 31 上限 / 无服务端缓存 / 无"今天未结束"字段全部接受。三项顺手项**全部采纳**（均零行为变化）: ① `StatsCalculator` 类注释的时长语义清单校正（time-of-day 实为**合并**、两清单都缺的 week-hour 补入）+ README 合并清单补 week-hour ② `weekHourDistribution` 重复两遍的合并注释删一段 ③ 去掉 `hourlyDays` 的第二遍全量累加 —— 新增 `DayHourlyWindow` record（`days` 截断 / `activeDays` 全量），一次 `hourlyByDay` 同时产出两者
    - 顺带核实: `devices`/`IDEs` 分布经 `aggregateByLabel(sessions, …)` 逐会话聚合 = **raw**；已把「哪个面合并、哪个面原始」列为 `references.md` 新表（前端正是被同类清单误导过）
    - 验证: 计算器 **7/7**、集成 **22/22** 绿；全量 **1503 tests / 0 failures / 0 errors**（上批 1494，+9 = 计算器 7 + 集成 2）；jacoco INSTRUCTION **95%** / BRANCH **84%**（阈值 80/70）；`spotlessCheck` PASS
    - 状态: ✅ **已交付 v0.79.0**（develop `a98f662` / master `6a33c6a`，2026-10-15 提交并推送）

---

> **归档**：更早的条目已按月归档至 `memory-bank/archive/activeContext-YYYY-MM.md`（R13 冷数据），本文件只保留最近 30 天热条目。
