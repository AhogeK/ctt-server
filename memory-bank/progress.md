# 项目进度

两种问题分开回答：**当前版本是什么、什么已经交付**（本文件）；**最近具体改了什么**（`activeContext.md`，
30 天热窗口，逐条详情）。逐次变更的历史不在这里堆积。

## 当前状态

| | |
| --- | --- |
| 版本 | `0.77.1`（`gradle/libs.versions.toml` 的 `appVersion`） |
| 领域知识 | 见 [`domains/README.md`](domains/README.md)：`stats-aggregation` / `leaderboard` / `sync-protocol` / `api-contract` / `auth-lifecycle` |
| 已交付能力 | 同步引擎（LWW + 游标 + 分页）、统计聚合、排行榜、成就系统、认证（JWT/API Key/OAuth）、审计与限流 |

## 版本里程碑

由 `gradle/libs.versions.toml` 与提交历史推导，非手工维护；列取该版本交付的主线变更
（`chore: bump version to X.Y.Z` 提交之前、优先级最高的 feat/fix）。

| 版本 | 日期 | 主线交付 |
| --- | --- | --- |
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
| 部署脚本配套 | 仅有 `Dockerfile`，无 compose/编排 | 部分完成 |

旧 `progress.md` 中列出的「CodingSession 数据模型 / SyncCursor / SyncPullService / SyncPushService /
ConflictResolver(LWW) / Redis ZSet 排行榜」**均已交付**（v0.45–v0.73），当时清单已过期；其原文在归档中保留。
插件端条目（SQLite 表结构升级、同步调度逻辑）属只读关联项目 `../code-time-tracker` 的范围。

## 归档

> 逐条变更历史（2026-03 至 v0.49.0）见 [`archive/progress-completed.md`](archive/progress-completed.md)。
