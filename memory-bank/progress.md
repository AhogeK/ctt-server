# 项目进度

两种问题分开回答：**当前版本是什么、什么已经交付**（本文件）；**最近具体改了什么**（`activeContext.md`，
30 天热窗口，逐条详情）。逐次变更的历史不在这里堆积。

## 当前状态

| | |
| --- | --- |
| 版本 | `0.112.0`（`gradle/libs.versions.toml` 的 `appVersion`） |
| 领域知识 | 见 [`domains/README.md`](domains/README.md)：`stats-aggregation` / `leaderboard` / `sync-protocol` / `api-contract` / `auth-lifecycle` |
| 已交付能力 | 同步引擎（LWW + 游标 + 分页）、统计聚合、排行榜、成就系统、认证（JWT/API Key/OAuth/两步验证/通行密钥）、审计与限流（含 180 天保留策略，ADR-0002）、liveness/readiness health 探针、Prometheus 指标出口（回环管理端口）、六个业务指标（同步、限流、邮件、排行榜、审计）、W3C 追踪与 OTLP 导出、部署机观测栈（Prometheus/Alertmanager/Grafana/Loki/Tempo/Alloy + 四类 exporter，30 天保留）、日志用户维度（`userId` 进 MDC，请求结束清理）、告警规则（12 条：p95 300ms/5xx 0.5%/429/DB 池/磁盘/outbox 最老待发/排行榜陈旧/审计掉行/公网单点/隧道中断/证书 14 天/Telegram 投递失败）、Grafana 面板 as code（请求/后台/边缘与宿主机三份，随栈自动出现，每块画告警阈值的契约线）、值班路径（规则按 blast radius 定级，critical/warning 经 Alertmanager 原生 Telegram 集成投递到操作者的私聊与工作群，文件凭证、零新增依赖、已在生产通道实测送达，info 只看面板；两条抑制；投递失败本身有告警；runbook 首章 `Alerts and on-call`）、CI 质量门与供应链证据（GitHub Actions 三 job 九门：格式、全量测试与覆盖率、SOPS 渲染与 0440 断言、gitleaks 全历史按指纹允许、Trivy 文件系统、单次构建的 OCI 镜像 + SLSA v1 来源证明 + 镜像扫描 + CycloneDX SBOM；Jenkins 改为文件凭证 + `render.sh`，不再落明文 `.env`） |
| 镜像发布与签名 | `publish.yml` 在 master 与 `v*` 上单次构建推 GHCR，用 keyless OIDC 证书签 **digest**，并把 BuildKit 的证明从 build metadata 取出、用 `cosign attest` 重新绑到被签的 digest（注册表不把 buildx 的内联证明暴露给 cosign）；`deploy/verify-image.sh` 钉 workflow 身份与 issuer、拒绝 tag 引用；**首次发布与部署机验证均已通过**；Jenkins 以 `--no-build` 按 digest 部署，部署前先登录注册表（缺凭证时提示包需可公开拉取）；镜像 tag 语义按注册表实测校正——`latest` 只跟发布走（分支构建不动它）、`sha-<short>` 会被后续构建覆盖、镜像 tag 名是 `X.Y.Z`（无 `v`），部署与回滚一律用 digest |
| 边缘转发 | 两跳头契约：`deploy/edge/nginx/ctt-server.conf`（80 只放 ACME 并 301；443 追加 `X-Forwarded-For`、声明 `X-Forwarded-Proto https`、`client_max_body_size 4194304`（4 MiB）、`limit_req 50r/s burst=100`、上游故障收敛为 503）与 `deploy/local/Caddyfile`（`trusted_proxies`、`request_body max_size 4MiB`、`handle_errors` 只接 502/503/504）。探针实测钉住三件事：真实客户端进链条、超限 413、上游停 503。**配置尚未上机**——接入机 sshd 无 banner，公网 443 未验证 |

## 版本里程碑

由 `gradle/libs.versions.toml` 与提交历史推导，非手工维护；列取该版本交付的主线变更
（`chore: bump version to X.Y.Z` 提交之前、优先级最高的 feat/fix）。

| 版本 | 日期 | 主线交付 |
| --- | --- | --- |
| `0.112.0` | 2026-11-16 | feat(ci): skip the expensive gates on documentation-only pull requests |
| `0.111.0` | 2026-11-16 | feat(edge): carry the client address and TLS through both proxy hops |
| `0.110.0` | 2026-11-15 | fix(ci): name the published tags and move the actions to the Node 24 runtime |
| `0.109.0` | 2026-11-15 | fix(ci): publish the build provenance as a signed attestation so the deployment can verify it |
| `0.108.0` | 2026-11-15 | feat(ci): publish a signed image to GHCR and deploy it by digest |
| `0.107.0` | 2026-11-15 | feat(ci): gate merges on style, tests, secrets, vulnerabilities and build provenance |
| `0.106.0` | 2026-11-15 | feat(observability): route alerts by severity into Alertmanager's native Telegram integration |
| `0.105.0` | 2026-11-15 | feat(obs): provision Grafana dashboards with the stack and draw the alert thresholds on them |
| `0.104.0` | 2026-11-14 | feat(observability): alert rules for the business and edge signals + outbox oldest-pending age gauge |
| `0.103.0` | 2026-11-14 | feat(logging): put the authenticated user identifier into the request log context |
| `0.102.0` | 2026-11-13 | feat(obs): deploy the observability stack beside the application |
| `0.101.0` | 2026-11-12 | feat(tracing): continue W3C trace context and export spans over OTLP |
| `0.100.0` | 2026-11-11 | feat(metrics): publish business metrics with a bounded tag budget |
| `0.99.0` | 2026-11-11 | feat(metrics): expose Prometheus on a loopback management server |
| `0.98.2` | 2026-11-11 | feat(deploy): render the frp token from the SOPS payload and read it from a file |
| `0.98.1` | 2026-11-11 | feat(audit): enable the production retention period and log every sweep |
| `0.98.0` | 2026-11-10 | feat(config): lock down production configuration and secrets |
| `0.96.1` | 2026-11-09 | feat(deploy): bind edge/apps to loopback host networking; fix(secret): make rendered secrets group-readable |
| `0.95.0` | 2026-11-09 | feat(actuator): split liveness and readiness health probes |
| `0.94.1` | 2026-11-09 | chore(config): align the audit retention default comment |
| `0.94.0` | 2026-11-09 | feat(audit): add configurable audit retention cleanup |
| `0.93.9` | 2026-11-08 | fix(audit): keep personal data out of audit details |
| `0.93.8` | 2026-11-08 | refactor(audit): drop the unused DTO mask serializer |
| `0.93.7` | 2026-11-08 | fix(audit): remove the never-wired payload switch |
| `0.93.6` | 2026-11-08 | feat(deploy): move credentials to SOPS-backed Compose secrets |
| `0.93.5` | 2026-11-08 | chore(compose): harden every local service (read-only, caps, limits, logs) |
| `0.93.4` | 2026-11-08 | chore(build): keep the Docker context to build inputs |
| `0.93.3` | 2026-11-08 | fix(deploy): keep the mail sandbox running across teardowns |
| `0.93.2` | 2026-11-08 | feat(deploy): record local-stack image digests for upgrade rehearsals |
| `0.93.1` | 2026-11-08 | fix(logging): mask production JSON logs and their key/value fields |
| `0.93.0` | 2026-11-07 | feat(dev): one-command local stack with edge, blue/green and observability |
| `0.92.2` | 2026-11-07 | chore(deps): upgrade flyway, commons-codec, spotless, the versions plugin and Gradle |
| `0.92.1` | 2026-11-06 | perf(sync): stop writing on idle polls (cursor upsert and device heartbeat) |
| `0.92.0` | 2026-11-06 | feat(sync): per-account rate limits, a slimmer poll write path, deferred leaderboard recompute and a replay escape hatch（插件 0.22.0 已接入） |
| `0.91.4` | 2026-11-04 | fix(auth): treat legacy OAuth ciphertext as absent after key rotation |
| `0.91.3` | 2026-11-04 | fix(security): close review gaps in proxying, PII and contracts |
| `0.91.2` | 2026-11-04 | fix(auth): allow cookie-only refresh token submission |
| `0.91.1` | 2026-11-04 | fix(security): harden credential, OAuth, proxy and audit paths |
| `0.91.0` | 2026-11-03 | feat(auth): add passkey sign-in and second-factor authentication |
| `0.89.1` | 2026-11-02 | fix(auth): generate a single-encoded 160-bit TOTP secret |
| `0.89.0` | 2026-10-31 | feat(auth): add TOTP two-factor authentication |
| `0.88.0` | 2026-10-29 | feat(user): revoke several login sessions in one call |
| `0.87.3` | 2026-10-28 | fix(user): list sessions of accounts that have no device |
| `0.87.2` | 2026-10-27 | chore(deps): upgrade Spring Boot, springdoc, Flyway and the build tooling |
| `0.87.1` | 2026-10-26 | fix(sync): carry the session IDE on pull and keep it on update |
| `0.87.0` | 2026-10-26 | feat(sync): attribute sessions to their own IDE |
| `0.86.1` | 2026-10-26 | fix(device): record device activity on every sync |
| `0.86.0` | 2026-10-25 | feat(leaderboard): rank only members with activity |
| `0.85.1` | 2026-10-25 | fix(leaderboard): backfill the windowed streak scores |
| `0.85.0` | 2026-10-23 | feat(leaderboard): rank a windowed streak |
| `0.84.0` | 2026-10-20 | feat(leaderboard): add quarter and half-year periods |
| `0.83.1` | 2026-10-19 | test(user): close the remaining session and export acceptance gaps |
| `0.83.0` | 2026-10-19 | feat(user): login sessions + account export (plugin-importable) |
| `0.81.0` | 2026-10-19 | feat(auth): give logins a session identity and fix the plugin audience |
| `0.80.1` | 2026-10-18 | test(user): cover the profile rate limit and the deleted-account gate |
| `0.80.0` | 2026-10-18 | feat(user): let users change their display name |
| `0.79.0` | 2026-10-15 | feat(stats): expose the per-day hourly distribution |
| `0.78.1` | 2026-10-12 | fix(auth): gate credential and identity changes on an active account |
| `0.78.0` | 2026-10-12 | feat(stats): compare summary metrics against the previous complete period |
| `0.77.1` | 2026-09-19 | chore(deps): upgrade dependencies to their latest stable versions |
| `0.77.0` | 2026-09-18 | feat(user): expose account deletion over HTTP |
| `0.76.1` | 2026-09-17 | fix(leaderboard): stop ranking users in languages their data no longer covers |
| `0.76.0` | 2026-09-17 | feat(leaderboard): backfill scores after a scoring change |
| `0.75.0` | 2026-09-17 | feat(leaderboard): rank inside a single language |
| `0.74.4` | 2026-09-16 | fix(sync): enforce the constraints declared on pushed sessions |
| `0.74.3` | 2026-09-16 | fix(language): filter untrusted values before reporting them |
| `0.74.2` | 2026-09-16 | test(stats): pin the language wiring so raw grouping cannot return |
| `0.74.1` | 2026-09-16 | fix(stats): normalize languages before aggregating |
| `0.74.0` | 2026-09-16 | feat(language): add the cross-IDE language vocabulary and normalizer |
| `0.73.0` | 2026-09-15 | feat(leaderboard): correct rank computation and widen dimension coverage |
| `0.72.0` | 2026-09-14 | feat(stats): report achievement period history from the session data |
| `0.71.0` | 2026-09-13 | feat(stats): add windowed achievements that reset each period |
| `0.70.0` | 2026-09-13 | feat(stats): keep achievement progress monotonic across session deletes |
| `0.69.0` | 2026-09-13 | feat(stats): back-infer the instant an achievement was earned |
| `0.68.0` | 2026-09-13 | feat(stats): expand achievement ladders and project family metadata |
| `0.67.0` | 2026-09-11 | feat(stats): add heatmap-months and make year options timezone-aware |
| `0.66.0` | 2026-09-09 | feat(stats): clip distribution to a date window and apportion bucket precision |
| `0.65.0` | 2026-09-08 | fix(stats): align TIME_OF_DAY buckets with plugin slicing semantics |
| `0.64.0` | 2026-09-05 | feat(stats): clip hourly distribution to a date range |
| `0.63.0` | 2026-09-03 | feat(stats): weekly coding activity by hour endpoint |
| `0.62.0` | 2026-09-03 | feat(sync): page pull responses with hasMore cursor continuation |
| `0.61.0` | 2026-09-02 | fix(test): anchor streak test days to real today |
| `0.60.0` | 2026-09-01 | feat(stats): IDE filter across stats endpoints and ide-filters option |
| `0.59.0` | 2026-09-01 | feat(stats): IDE distribution derived from device registry |
| `0.58.0` | 2026-09-01 | test(stats): cover device dimension filtering and attribution |
| `0.57.0` | 2026-08-31 | refactor(common): shared RedisLockService |
| `0.56.0` | 2026-08-31 | feat(achievements): badge system with lazy idempotent unlock |
| `0.55.0` | 2026-08-31 | feat(leaderboard): period and fun-dimension rankings |
| `0.54.0` | 2026-08-31 | test(leaderboard): cover ranking, ties, concurrency and push trigger |
| `0.53.0` | 2026-08-31 | test(sync): cover content-level dedup and parameterize LWW cases |
| `0.52.0` | 2026-08-30 | test: keep rate limiting off by default, enable where asserted |
| `0.51.0` | 2026-08-30 | test(stats): cover calculator aggregation and stats endpoints |
| `0.50.0` | 2026-08-30 | test(device): cover revoked device status and sync rejection |
| `0.49.0` | 2026-08-29 | test(sync): cover sessionUuid in pull response |
| `0.48.0` | 2026-08-28 | test(device): cover device registration flow |
| `0.47.0` | 2026-08-26 | feat(sync): wire pull/push endpoints |
| `0.46.0` | 2026-08-25 | feat(sync): add LWW conflict resolution engine |
| `0.45.0` | 2026-08-25 | feat(sync): add data model and persistence layer for coding session sync |
| `0.44.0` | 2026-08-23 | feat(user): add password change endpoint |
| `0.43.0` | 2026-08-21 | feat(mail): precise retryAfter for MAIL_004 rate limit |
| `0.42.0` | 2026-08-12 | feat(apikey): allow direct deletion of expired API keys |
| `0.41.0` | 2026-08-10 | docs(apikey): document permanent deletion endpoint and AUTH_023 |
| `0.40.0` | 2026-07-16 | docs(apikey): add API key authentication rate limiting documentation |
| `0.39.0` | 2026-07-13 | docs(sync): add sync endpoints documentation |
| `0.38.0` | 2026-07-12 | docs(apikey): document scope-based authorization |
| `0.36.0` | 2026-07-09 | feat(apikey): implement Phase N API Key Management CRUD + endpoints |
| `0.30.0` | 2026-07-02 | feat(user): add GET /api/v1/users/me for current user profile |
| `0.29.0` | 2026-07-01 | feat(oauth): add DELETE /api/v1/auth/oauth/accounts/{provider} for OAuth unbind |
| `0.28.0` | 2026-06-29 | feat(oauth): add BIND flow for linking OAuth account to existing user |
| `0.27.0` | 2026-06-28 | feat(oauth): add GET /api/v1/auth/oauth/accounts endpoint for binding query |
| `0.26.0` | 2026-05-26 | feat(auth): add hCaptcha backend integration for login/register/password-reset |

## 尚未落地

以下为**已核实当前仍缺失**的项（不是从旧清单照抄）：

| 项 | 核实方式 | 状态 |
| --- | --- | --- |
| 压力/负载测试 | `src/` 无 perf/load/benchmark 测试 | 未做 |
| 错误监控（Sentry 等） | 依赖与代码中无集成 | 未做 |
| CI 流水线 | 无 `.github/workflows/` | 未做 |
| 部署脚本配套 | 本地 `docker-compose.yaml` 已有；生产编排/自动化部署脚本未落地 | 部分完成 |
| 生产审计量级实测 | 计划阶段表 P3.8（P0.16b 已确认生产库为空；依赖首次生产部署与真实数据） | 待生产部署后复测 |

旧 `progress.md` 中列出的「CodingSession 数据模型 / SyncCursor / SyncPullService / SyncPushService /
ConflictResolver(LWW) / Redis ZSet 排行榜」**均已交付**（v0.45–v0.73），当时清单已过期；其原文在归档中保留。
插件端条目（SQLite 表结构升级、同步调度逻辑）属只读关联项目 `../code-time-tracker` 的范围。

## 归档

> 逐条变更历史（2026-03 至 v0.49.0）见 [`archive/progress-completed.md`](archive/progress-completed.md)。
