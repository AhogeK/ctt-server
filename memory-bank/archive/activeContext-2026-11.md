# Active Context — 2026-11 archive

> 冷数据归档（R13）。仅供回溯，不再更新。

- [2026-11-10] - P0.16 生产配置收口（v0.98.0，代码+配置+密钥+文档）
    - 交付：prod 关闭 springdoc（两路径 404）；CORS 取 `CORS_ALLOWED_ORIGINS`（回退 `FRONTEND_BASE_URL`），基础配置删占位域；OAuth 与前端 URL 生产必填；`ProductionConfigurationGuard` 覆盖四类违规并一次列出；`secrets.prod.enc.yaml` + `render.sh --env prod`；app 的 `DB_PASSWORD` 改源自 `db_password`；`PROD_DB_PASSWORD` 从 `.env` 移除
    - 缺陷：未知路径原被 `Exception` 兜底成 500（关闭文档后也不满足 404）⇒ `NoResourceFoundException → 404 COMMON_002` + 单测
    - 验证：prod 实测 api-docs/swagger-ui/swagger-index 均 404；缺 GitHub secret、占位 CORS、开启 api-docs、短 DB 口令四个分支 exit 1；CORS 回退正常启动；补 `ApiDocsDisabledIntegrationTest`（真实 MVC 派发）；`docker compose config`、两种 `--env` 渲染、全量 `./gradlew build`（含该集成测试）均通过
    - 误判：guard 消息里属性名后的空格会让日志掩码把下一个词当值（`client-secret must` → `******`）；消息改为反引号包属性名
    - 边界：生产编排 P3；GitHub OAuth App P7.2；生产库未初始化；Redis 口令因共享实例（db1 属另一服务、仅 default 用户）未轮换，约束与重访触发见 `docs/production-configuration.md`
    - 文档：新增 `docs/production-configuration.md`；`docs/deployment-secrets.md` 改双载荷；README 与 `.env.example` 同步；验收记录 `.omp/acceptance/2026-11-10/p0-16-prod-config.md`
- [2026-11-10] - P0.15 时钟与日志上限（完成，纯主机配置/验证，不 bump 版本）
    - 两机：chrony 替换 systemd-timesyncd（接入机 4.5 / 部署机 4.8，enabled+active）；offset 0.00004 s / −0.0376 s（< 1 s）
    - 接入机：journald 200M/1G/64M（16M）；nginx `maxsize 20M + rotate 14`、btmp `maxsize 10M + rotate 3`、rsyslog `maxsize 50M`
    - 部署机：journald 500M/2G/64M（2.8G → 490.1M，`--vacuum-size`）；同一套轮转上限；`logrotate.service` Result=success
    - 坑与误判：备份留在 `/etc/logrotate.d/` 会 duplicate log entry ⇒ 移到 `/root/`；首次 `chronyc tracking` 的 `Not synchronised` 是启动瞬时态（约 10s 后同步）；`du` 的 Permission denied 与 grep 命中的 `error_log` 文件名不是故障
    - 边界与记录：部署机手工 nginx master（单元 inactive、无 80/443）未处理，归 P0.16/运维；未删除任何审计记录；记录：`.omp/acceptance/2026-11-10/p0-15-time-and-log-caps.md`
- [2026-11-10] - P0.13c 外部负向验证（完成，纯验证/文档，不 bump 版本）
    - 方法：以接入机（公网，独立于家庭 LAN）为外部视角；目标地址取 SSH 与 frpc 在接入机上的对端（两条独立流一致）；部署机自报 HTTP 出口与 SSH/frpc 不同，不可用
    - 结果：`tcp/5432`、`tcp/6379` 两次均耗尽 5s 超时（丢弃非 `refused`）；`ping` 100% 丢包；`tracepath` 至中国电信骨干后无回应；同时段 frpc 隧道在线。LAN 内两端口可用，Redis 返回 `NOAUTH`
    - 边界与记录：若为 CGNAT/共享地址，「无端口转发」与「无可寻址入口」无法从外部区分，需路由器侧确认；第二外部视角 SSH banner 超时未作证据；未创建任何临时路径；记录：`.omp/acceptance/2026-11-10/p0-13c-external-negative-verification.md`；ADR-0003 与 `production-data-isolation` 的「无入站转发」已改为已测量
- [2026-11-10] - P0.13b Redis 来源限制决策（纯文档/决策，不 bump 版本）
    - 决策：不安装来源白名单；保留 `bind 127.0.0.1 + LAN`、口令、`protected-mode` 与「路由器不转发入站」边界。Redis ACL 只限命令/键/通道，没有客户端地址谓词；`bind` 只选监听地址 ⇒ 服务内无法表达「只允许本子网」
    - 证据（部署机 2026-11-10）：`ss -lnt` 仅 `127.0.0.1:6379` 与 LAN 地址 `:6379`；`CONFIG GET bind` = 回环 + LAN，`protected-mode yes`，`requirepass` 已设；`ACL LIST` 只有 `default` 用户 `~* &* +@all`；`ip -brief addr` 仅一个 IPv4 接口，`ip -6 addr show scope global` 为空；数据面现存连接只来自开发机；其余容器走 bridge 自有发布端口，不使用数据面
    - 触发（6 条，命中即先装主机 nftables 白名单再放行）：端口转发/新增路由/全局 IPv6、Redis 迁到容器发布端口或他机、口令扩散、出现异常来源访问
    - 边界与交付：外部负向验证仍归 P0.13c；未启用 UFW；Redis ACL 用户加固属独立决策（ADR 已列为替代方案）；交付 `docs/decisions/ADR-0003-redis-source-restriction.md`、`docs/production-data-isolation.md`、README 文档索引、`systemPatterns` 与计划 P0.13b 状态同步，`AGENTS.md` R8 增「平台能力要按机制核实」（Redis 无来源地址 ACL；共享主机加防火墙前先证明能拒绝真实可达来源）

- [2026-11-07] - 运维与可观测性升级计划（draft，已按要求重写）：`.plans/ctt-server-development-plan.md` §「🔍 运维与可观测性升级」—— 选型全部定案，阶段 P0–P7：P0 修既有状态（隧道改 systemd、端口收口、生产库隔离）、P1 指标与追踪、P2 告警面板、P3 发布链、P4 备份与 Resend、P5 压测演练、P6 文档、P7 对外接入（`ctt.ahogek.com` 阿里云 A 记录 + certbot、GitHub OAuth、Passkey/hCaptcha/CORS）。补「本地先行」一节。**仓库内不写真实 IP 与主机名**（角色名 + `.omp/notes/2026-11-07/deployment-endpoints.md`，`.omp/` 已 gitignore）。deep research 后修正六处：GitHub 单 App 可存 10 个回调但省略 `redirect_uri` 只用第一个；Jenkins 实为 nginx 443 + ufw 默认拒绝（非 8080 公网）；Resend 无 SMTP 服务端日志；可信代理须含 edge 那一跳且 edge 要用 host 网络（否则 IP 限流与审计退化）；`management.server.port` 还需 `management.server.address`；**部署机 ufw 装了但 `ENABLED=no`，PG/Redis 对整段 LAN 全开**（P0.13 改为启用防火墙，实施需你在场输 sudo 密码）
- [2026-11-07] - 依赖升级（v0.92.2）与计划收口
    - 升级：flyway 13.11.1→13.12.1（core 与 database-postgresql 同版本）、commons-codec 1.22.0→1.23.0、spotless 8.10.4→8.11.0、ben-manes 0.66.0→0.67.0、Gradle wrapper 9.8.2→9.9.0；Spring Boot 4.1.2 / springdoc 3.2.0 / greenmail 2.1.15 / pg 42.7.14 / junit-platform 6.1.3 / otp-java 2.2.0 / webauthn 2.10.1 已是各自最新（4.2.0-RC2 属 RC，按策略跳过）
    - 验证与事实：分层 clean build —— 库依赖 3m23s、插件 17s、wrapper 全量 `--rerun-tasks` 3m49s；**1689 tests / 0 failures / 1 skipped**；`spotlessCheck` 通过；升级后 `dependencyUpdates` 报 0 项、Gradle 无更新；ben-manes 报告仍只列 4 项（Spring Boot 等漏报），版本一律以 Maven Central / Plugin Portal 元数据为准；spotless **8.11.0 + google-java-format 1.36.1 组合实测可用**（1.37.0 仍不用）
    - 计划与仍缺：`### 🔍 运维与可观测性升级` 完成态处理（P0.8/P0.10/P0.14 ✅、P0.9 🚧（后已完成），新增 P0.15/P0.16、P1.5、P3.7、P6.4），补「工程与生产化缺口复查」「踩坑表」，修正总览图归类、Resend 额度断言、`.sisyphus` 死链、拓扑图 frpc 标签；未实施项：覆盖率校验未挂 `check`、prod 未关 springdoc、CORS 占位域、OAuth 与验证码缺键无 fail-fast
    - 规则（R14 记录）：`AGENTS.md` R12 补两条实测事实——报告漏报需查元数据（2026-11-07 复核仍只列 4 项）；Spotless 8.11.0 + google-java-format 1.36.1 为当前可用组合；新增「wrapper 与依赖同批、用 `--rerun-tasks` 验证」
- [2026-11-07] - P0.0 本地一键全栈（v0.92.3，待授权提交）
    - 交付与验证：`docker-compose.local.yaml` 叠加层——基础 `app` 用 `profiles: !override ["standalone"]` 让位给两色；`app-blue`（18081）与 `app-green`（18082，profile `green`）走 `extends` 复用基础定义；`edge`（Caddy `:18080` → `UPSTREAM`）；`obs`（Prometheus/Grafana/blackbox）；入口脚本 `deploy/local/up.sh`；验证经 edge `18080/ctt-server/actuator/health` = **200**（`groups: liveness/readiness`），`app-blue` 直连亦 200，`--profile obs` 后 Prometheus `/-/ready` 就绪、`prometheus` 与两条 `blackbox-http` target 全 `up`、Grafana `13.3.0` `api/health` = `database: ok`
    - 阻断与取舍：容器内 Gradle 构建**无法**访问 Maven Central（`Remote host terminated the handshake`，本机代理所致），而 `build.network: host` 被 buildkit 拒绝（需 `--allow=network.host`）⇒ 改为宿主机构建 `bootJar` + `deploy/local/Dockerfile` 只打包 jar，`up.sh` 把三步串成一条命令；生产镜像仍走多阶段 `Dockerfile`
    - 边界（未做）：应用自身 `/actuator/prometheus` 未暴露（P1.1），Loki/Tempo/Alertmanager 属 P1.4/P2，蓝绿切换属 P3.4；README 开发段已补用法
    - 双轴审查后修正（2 个 reviewer 并行）；**容器镜像一律 `latest`（用户 2026-11-07 决策：始终最新、含大版本）**，原先钉 tag 的做法已回退，可追溯性改由部署时记录 digest：①profile 统一为 `bluegreen`（原 P0.0 行与 2170 行不一致，compose 的 `green` 会静默忽略）②Grafana 首启约 **3 分钟**（13 版逐个安装 app-platform API 后才绑定 3000），此前 120 s 探测窗口误判为「起不来/内存不足」；实测 `api/health=200`、预置面板 `CTT local stack` 可见、`probe_success=1`（两 target）、占用 281 MiB ⇒ 仍在 `obs` profile 内 ③obs 镜像一律 `latest`（用户决策：不含固定 tag，可追溯性靠部署记录 digest；构建镜像 `eclipse-temurin:25-*` 例外——用户 2026-11-07 明确「JDK 跟着项目走」，不跟 latest）④compose 头注释改指 `./deploy/local/up.sh`（裸 compose 在干净检出会因缺 jar 失败）⑤README 说明 edge 实际代理 `app-blue:8080`、`:18081` 只是宿主机端口 ⑥版本按 R15 从 PATCH 改 MINOR（0.93.0）⑦`up.sh` 的 `ls|grep|head` 改 `sed -n '1p'` 防 pipefail
    - 内存调节（实测）：Docker VM 仅 1.9 GB，最初 `-XX:MaxRAMPercentage=60` + 700m 上限时第二色被 OOM 杀（exit 137, `oom=true`）；改 `mem_limit: 900m` + 45% 后 blue+green+Prometheus/blackbox 同起正常（约 1.34 GB）；**加入 Grafana 后不稳**：稳态接近 1.7 GB，启动期 blue 被 OOM 杀（`oom=true`, exit 137）⇒ 两色+Grafana 需 VM ≥ 3 GB，推荐 4 GB。**已调至 4 GB 并复验**：3.81 GiB 可用下全 profile 同起约 2.06 GB，五处 200、`oom=false`，Grafana 上限 768m。诊断路径：容器内 `wget 18080` 得 503 → `getent hosts app-blue` 解析失败 → `docker inspect` 见 `oom=true`（DNS 失败只是容器已死的下游症状，别先查网络）
