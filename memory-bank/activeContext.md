# Active Context

- [2026-10-28] - 会话列表 500 修复（v0.87.3，待授权提交）
    - 根因: `LoginSessionService.listSessions` 以 `devices.get(row.getDeviceId())` 查设备；浏览器会话的 `device_id` 为 null，而无任何设备的账号会让 `devicesById` 返回 `Map.of()`，`ImmutableCollections.MapN.get(null)` 抛 NPE ⇒ 500 `SYSTEM_001`。修复: `deviceOf()` 先判空再查；回归用例用非 UUID 的 deviceId 登录（浏览器形态），断言列表 200 且 `deviceName`/`ideName` 为 null

- [2026-10-27] - 依赖升级（v0.87.2）
    - Boot 4.1.2 / springdoc 3.2.0 / greenmail 2.1.15 / Flyway 13.11.1 / pg 42.7.14 / ben-manes 0.66.0 / spotless 8.10.4 / JaCoCo 0.8.16 / Gradle 9.8.2；google-java-format 1.37.0 在 Spotless 下崩溃，保留 1.36.1；分层 clean build 各 1567 tests / 0 failures

- [2026-10-27] - README 重设计（文档，已提交推送）
    - 785 → 226 行：Hero SVG + 徽章、机制图（mermaid）、快速开始、API 总览、数据模型、测试、文档索引；API 细节零丢失迁至 `docs/api-reference.md`（仅标题由 `## API Endpoints` 改为 `## Endpoints`）；新增 `assets/readme/hero.svg`；底部 Support 用 `<div align="center">` + 单行 flat-square 徽章（源码换行会变竖排）

- [2026-10-26] - 会话 IDE 复核补齐（v0.87.1，已提交推送）
    - 前端复核两项属实: pull 的 `SyncChangeDto` 未带 IDE ⇒ 跨设备本地副本回退成拉取方 IDE；`applyIncomingFields` 无条件写 ⇒ 缺少字段的更新把已有 IDE 清成 null
    - 修复: `SyncChangeDto` 加 `ideName`/`ideVersion`，`toChangeDto` 的正常分支与 `session == null` 分支都显式传值；IDE 只在传入非空时写入（旧客户端不清空、升级客户端可补值）
    - 验证: 单测覆盖 pull 映射、缺会话为 null、更新不清空、升级补值；集成覆盖跨设备保留；全量 1567 tests / 0 failures

- [2026-10-26] - 会话级 IDE 归属（v0.87.0，已提交推送）
    - 需求核实: 报告属实 —— deviceId 是安装级（同机所有 IDE 共享），`devices.ide_name` 只在注册时写，`coding_sessions` 不存 IDE，统计按设备归属 ⇒ 多 IDE 被合并、IDE 升级不更新；插件端 `SyncSessionMapper` 已按会话发送 IDE
    - 契约: 迁移 `V20261026120000` 加 `coding_sessions.ide_name/ide_version`（可空、不回填）+ 部分索引 `(user_id, ide_name) WHERE is_deleted = FALSE`；`SyncSessionDto` 可选 `ideName`/`ideVersion`（≤100/≤50，空白归一 null）；批量 INSERT 16 → 18 列
    - 统计与设备: 过滤 = 会话 `ide_name` ∪ 旧行回退 origin 设备注册值；`IDES` 桶会话优先、回退设备、再 `Unknown IDE`；`ide-filters` = 两者并集；404 仅当两者都无；设备响应改「最近使用」（`DISTINCT ON` 单查询、无 N+1、回退注册值）；每日汇总表不参与 IDE 筛选
    - 导出与不做: 会话行写会话级 IDE（旧行回退设备）；不建 `device_ides`、不拆 device id、不回填旧会话、IDE 不进 `ConflictResolver.sameContent`
    - 验证: 单测 + 集成（同设备双 IDE → 两桶、筛选各自命中、设备一行取最近 IDE、旧客户端无字段仍 200）

- [2026-10-26] - 设备最后活跃时间停摆修复（v0.86.1，已提交推送）
    - 前端报告核实: **属实**。`setLastSeenAt` 在生产代码中只有 `DeviceService.registerDevice` 一处；`SyncPullService`/`SyncPushService` 只校验归属与吊销，不写回 ⇒ 每天同步的设备 `last_seen_at` 停在注册日，前端 7 天阈值判成 Inactive
    - 修复: `DeviceRepository.touchLastSeen(userId, deviceId, seenAt)` —— 单条 JPQL update（`lastSeenAt` + `updatedAt`，谓词含 `revokedAt is null`，`@Modifying(clearAutomatically, flushAutomatically)`）；pull 与 push 在归属/吊销校验通过后各调一次。用整行 update 而非实体 save 的原因：`Device` 有 `@Version`，同一安装的多插件并发同步会撞乐观锁，而心跳没有业务含义；调用点在 `@Transactional` 内，失败随事务回滚
    - 验证: 单测 4 例 + 集成「13 天前的 last_seen_at → pull → 一分钟内」；已推送（6a83f7f → master 77cc619）

- [2026-10-25] - 排行榜排除 0 分成员（v0.86.0，已提交推送）
    - 前端需求核实: `leaderboard:total`/`streak`/`active_days` 各 9 成员其中 7 个 0 分，`night_owl`/`early_bird` 各 8 个 0 分；7 个 AutoTest 账号 `coding_sessions` 为 0 条 ⇒ **不是精度问题，是写入规则**
    - 根因: `writeScore` 无条件 `ZADD`，`recomputeAndWriteAll` 对每个维度×周期都写 ⇒ 无活动账号以 0 分入榜，并计入 `totalParticipants` 与并列名次
    - 修复: `LeaderboardDimension.ranksOnlyPositiveScores()`（除 `GROWTH` 外全部为真）；`writeScore` 在 `score <= 0` 时 `ZREM` 并返回（只查层过滤不行：`totalParticipants`、分页、名次仍会带上 0 分成员）；`LANGUAGE` 的榜索引与 `user:languages` 记录只在正分时维护，零时长语言不建榜；回填代次 3 → 4 以清理存量 0 分成员；`GROWTH` 保留 0/负分（度量而非缺失）
    - 测试: 零会话用户只移除不写入（仅 `growth` 键可写 0）、负增长与零增长仍写入、零时长语言不建榜、TTL 只在写分时设置；集成用例「无活动用户不在榜上且 `totalParticipants` = 1」
    - 状态: 实施完成，待授权提交

- [2026-10-25] - 窗口化 STREAK 回填缺失修复（v0.85.1，待授权提交）
    - 前端报告核实: **五项断言全部属实**。Redis 实测（192.168.5.178:6379）：`leaderboard:streak`=2，`month/quarter/half-year/year` 四个窗口各 1；`leaderboard:recompute:g2:vocab2` 存在；代码 `SCORE_RULE_GENERATION` 仍为 2
    - 根因: 标记键只有「代次 + 词表版本」，0.85.0 放开 `STREAK` 全部周期既没提升代次、标记也不含矩阵信息 ⇒ 旧标记命中，`backfillScores()` 首次判断即 return，未推送过的用户没有窗口键；查不存在的 ZSet 返回 200 + 空列表，前端显示空态而非错误
    - 修复: 代次 2 → 3；标记改为 `g{n}:cov{8位}:vocab{v}`，其中 `cov` 由 `LeaderboardDimension.values() × supports()` 排序后 SHA-256 现算 ⇒ **新增维度或放开周期自动失效**；改公式仍需人工提升代次。新标记 `leaderboard:recompute:g3:covbdc93f7f:vocab2`（41 个合法组合）
    - 测试: 断言不再复用 `g2:vocab2`、矩阵增删即改变指纹、枚举顺序无关，排行榜相关 49 例全绿；**刻意未做**：不在真实 Redis 手工回填（共享数据），部署后由 `ctt.leaderboard.backfill-initial-delay-ms`（60s）触发

- [2026-10-23] - `.omp` 全量分类归档（用户要求：时间只做文件夹，不进文件名；一个需求一个文件）
    - 结构: `.omp/<category>/<YYYY-MM-DD>/<kebab-topic>.md`；类别 = reports / acceptance / requirements / research / plans / notes；顶层不再有散文件
    - 迁移 35 个 .md（顶层 30 + plans/ 5 + reports/ 2，去重 2 份；旧 `reports/2026-10-2X-*.md` 命名已并入日期文件夹），`skill-graph-zh.json` 移入 `data/`；同步修正 `AGENTS.md` R26 两处过时行并合并「每轮新建」与「分类归档」表述（✅ 已完成，随该批 AI 提交）

- [2026-10-23] - 数据库连不上诊断（**第二次复发，非代码问题**）
    - 现象: IDEA 内启动应用 ⇒ `flywayInitializer` 取连接失败，根因 `java.net.NoRouteToHostException`（EHOSTUNREACH，SQLState 08001）
    - 证据: 本批未改任何数据源配置；**系统 shell** 到 `192.168.5.178:5432` 的 `ping` 0% 丢包、`nc -z` 成功；系统日志反复出现 `LocalNetwork: found bundle id com.jetbrains.intellij`
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

- [2026-10-19] - R3 数据导出（**产物面向插件端无缝导入**，v0.83.0，已交付）
    - 触发: 用户提出硬约束 —— "要是做导出就必须同步插件端的导入导出，web 导出的插件端一定要能无缝导入，不然这个功能就没有意义"
    - **只读核实插件端真实导入器后推翻计划的 NDJSON 定案**：插件用 Gson 反序列化为固定模型（`exportVersion` 非 `"1.0"` 直接拒绝；`sessions[]` 字段名固定；时间用 `ISO_LOCAL_DATE_TIME` 解析 ⇒ **带 `Z` 会整体导入失败**；按 `sessionUuid` 去重；`INSERT OR IGNORE` 且一律 `is_deleted=0`；模型字段非空）⇒ 后端改为**单一 JSON 对象**、字段名与插件一致、时间按 `timezoneOffset`（**必填**、±720、超范围 400 `COMMON_003`）转成**无时区本地时间**、扩展数据收进 `serverExport`（Gson 忽略未知字段 ⇒ 扩展与可导入并存）、**不导出软删会话**（否则插件端"复活"已删记录）、无法提供的值写 `""` 绝不 `null`
    - 交付: `UserExportService`（`JsonGenerator` 手写流式：先 `count` 再按 1000/页遍历 `findByUserIdAndIsDeletedFalse(Pageable)`，不整表装配）+ `GET /api/v1/users/me/export`（`@RequiresApiKeyScope(READ)` + 限流 3/h + `Content-Disposition: attachment; filename="ctt-export-<yyyy-MM-dd>.json"`）+ 审计 `DATA_EXPORTED`（details: 会话数 + 偏移）+ `CodingSessionRepository` 增分页读
    - 导出范围: `sessions[]`（插件消费）+ `serverExport`（account / devices / dailyStats / achievements / achievementProgress / oauthAccounts / loginSessions / apiKeys —— **零凭据**：无密码、无令牌、无 key hash，已断言）
    - 测试: `UserExportIntegrationTest` 3 例，**真实 HTTP 流式**（`StreamingResponseBody` 在 MockMvc 下不可观测）：用**插件端同构模型**反序列化 + 每条时间戳 `LocalDateTime.parse(ISO_LOCAL_DATE_TIME)` + 时区换算（00:30Z → 08:30 @+480）+ `platform`/`ideName` 取自设备注册表（未注册设备为 `""`）+ 软删排除 + 无凭据字符串 + 400/401
    - 全量运行抓到测试自身一处**顺序假设** ✗：导出按 id 排序而 UUID 随机 ⇒ 断言不得取"第一条"，已改为按 `projectName` 定位（单独跑绿、全量跑红 = 典型隔离缺陷）
    - 文档: README（端点行 + 导出语义段含"为什么必须是这个格式"）；`dev-docs/user/frontend-integration.md`（前端契约：**`timezoneOffset` 必填**、blob 原样存盘不要重编码、文件结构、导入路径）；`docs/developer-handbook.md`（`DATA_EXPORTED`）；`.plans` R3 段补「交付修订」对照表
    - 已知缺口（诚实记录）: 插件端没有"从 URL 导入"，用户需先下载文件再经 `ImportDataAction` 选文件（插件端既有交互，无需其改动）；web 端尚无导出 UI，本批为其备好契约
    - 验证: 全量 **1538 tests / 0 failures / 0 errors**（1 skipped 为既有）；新增 7 个验收用例全部通过（`LoginSessionEndpointsIntegrationTest` 9/9、`UserExportIntegrationTest` 5/5、`SessionIdentityIntegrationTest` 5/5、`TokenRefreshService*` 12/12）；`build` PASS（含 spotless 与覆盖率自动检查）
    - **交付形态评估（用户质疑"单 JSON 会不会是严重等待的接口"）**: 服务端**不是**先组装再发（`JsonGenerator` 逐页写、8KB 缓冲自动 flush，首字节约 = 首次查询）；但客户端必须等整份（插件导入器要完整文件 ⇒ "逐行"对该消费端零收益）。**实测生产库：2 用户 / 3641 行 / 707 kB**，最重真实用户 3639 行 ≈ 707 kB ⇒ 亚秒级、4 次分页。结论：**维持同步流式**（形态 A）；形态 B（一次性票据 + 原生下载 URL，解决内存/进度/重试，成本小）与 C（异步任务 + 产物存储，需项目当前没有的对象存储）都留待触发；并把计划的触发条件从"p99>30s 或 >100MB"**下调为"单用户 >10 MB 或 p95 >5 s"**（原线差两个数量级＝永不触发）。关键区分沉淀到 `systemPatterns`：**格式由消费端决定，通道才是可选的**
    - **补测（v0.83.1）**: 计划里的验收项全部落实——新增 **7 例**（分页 2500 行跨 3 页 / 导出第 4 次 `429` / `logout-all` 后列表为空 / 会话列表未认证 401 / User-Agent 512 截断 / 轮换后 UA 沿用 / **并发锁串行化**）；仅"堆峰值"一项保留为决策（不可稳定测量，且分页已由用例证明）
    - 状态: ✅ 已交付（v0.83.0，验收补测于 v0.83.1）
    - 交付: `GET /api/v1/users/me/sessions`（按 `session_id` 归并一次登录一条；`current` 取自 access token 的 `sid`；`limit` 默认 20 / 上限 100；限流 60/h）+ `DELETE /api/v1/users/me/sessions/{sessionId}`（204；该会话全部未吊销行置 `revoked_at`；非本人或不存在统一 404 `COMMON_002`；限流 10/h；审计 `SESSION_REVOKED`，resource = 会话 id、details 带 `kind`）+ DTO `LoginSessionResponse` 九字段 + `RefreshTokenRepository` 增三条（归并查询 / 会话锁 / 按会话吊销）
    - **并发保证（本批核心）**: 刷新与吊销**都先取会话级排它锁**（`pg_advisory_xact_lock(hashtext(session_id))`，事务级自动释放）⇒ "踢了就一定失效"。**补测时发现并修复一个真缺陷**：锁原先在"状态判定之后"才取，竞态下刷新读到的是吊销前的旧状态 ⇒ 仍会轮换成功（用例实测 200 而非 401）；现改为**先取锁 → `entityManager.refresh` 重读 → 再判定**，用例转为 401 `AUTH_003`。教训：并发保证必须"锁→重读→判定"，只加锁不复读等于没加
    - **连带缺陷（R2 引入后才会暴露，已修）**: 吊销会话后该客户端仍会拿旧 refresh token 刷新 —— 原逻辑按**令牌重用**判（`AUTH_009`）并 `revokeAllUserTokens` ⇒ "踢一个会话"会变成"全端登出" ✗。改为先看该会话是否仍有活跃行：无 → 整个会话已被吊销 ⇒ `401 AUTH_003`（只影响该会话）；有 → 重放的是轮换后的旧令牌 ⇒ 才判重用
    - **查询正确性（用例抓到）**: 会话跨度（`createdAt`=MIN、`lastUsedAt`=MAX、`expiresAt`=MAX）必须跨**该会话全部行**（轮换会吊销上一行），而"哪条会话在用、取谁作活跃行属性"只看**未吊销行** ⇒ 拆成 `session_span` + `live_session` 两个 CTE；只聚合活跃行会把刚刷新的会话报成"从未使用" ✗
    - 契约细节: DTO 加 `@JsonInclude(ALWAYS)` —— 可空键（`deviceName`/`ideName`/`userAgent`/`lastUsedAt`）**始终出现**（R9 规则），断言必须按键做（本次被 `path(k).isNull()` 对**缺失键**也为 true 坑过一次 ⇒ 改用 `has(k)` + `get(k).isNull()`）
    - 复用与补漏: 转换器 `sid → CurrentUser.sessionId` 此前**无任何断言**（R2-a 缺口，列表用例把空 sid 暴露出来）→ `JwtToCurrentUserConverterTest` 补"有 sid"/"无 sid"两例
    - 测试: `LoginSessionEndpointsIntegrationTest` 4 例（一次登录一条目 + PLUGIN 带设备名 + 按最近活动排序 / 只停被踢会话且其余仍可刷新 / 允许吊销当前会话 / 不泄露他人会话且其会话不受影响）
    - 文档: README（User Profile 表两行 + 会话语义段）；`dev-docs/user/frontend-integration.md` 增完整契约（字段表 + 可空键语义 + 吊销语义）；`docs/developer-handbook.md` 增 `SESSION_REVOKED` 审计动作
    - **既有测试按新语义更新（5 个类）**: 全局吊销（logout / logout-all / 密码重置）后的刷新期望由 `403 AUTH_009` 改为 **`401 AUTH_003`**；`TokenRefreshServiceTest` 新增「会话仍有活跃行 ⇒ 才判重用（`AUTH_009` + `revokeAllUserTokens`）」与「会话已无活跃行 ⇒ `AUTH_003` 且**不**做全局吊销」两条并列用例；`UserControllerMockMvcTest` 补 `LoginSessionService` mock（构造器新增依赖）。未删测试、未削弱断言
    - 验证: 全量 **1528 tests / 0 failures / 0 errors**（1 skipped 为既有；1521 + 7 = 4 端点用例 + 2 转换器 sid 用例 + 1 重用/吊销区分用例）；`spotlessApply` 后 `build` PASS。一次 `clean build` 以 `:test FAILED` 且**零结果**中止（无诊断输出），随后单独 `test` 与 `build` 均通过——与依赖升级批次同形的瞬时失败，非代码问题
    - 状态: ✅ 已交付（v0.83.0，与 R3 同批）

- [2026-10-19] - R2-a 收尾：双轴独立审查与审查修复（并入 v0.81.0，已交付）
    - 双轴独立审查（标准轴 / 规格轴，另加机械自检：spotlessCheck、jacoco 门禁、注释语言与全限定名扫描、diff 范围）
    - **两轴独立命中同一个真 bug**: terms 接受路径 `issueTokens` 以 `deviceId=null` 重建行 ⇒ 插件会话在**同一 `session_id`** 下变成 WEB/30 天并丢设备绑定——既违背刚写入领域文件的"续期沿用受众/设备"承诺，也会让 R2-b 的会话列表把插件会话显示成浏览器会话（验收"PLUGIN 才显示设备名"会失效）
    - TDD 修复: 新增集成用例 `shouldKeepPluginAudience_whenAcceptingTerms`（断言 `issued_for=PLUGIN` + `device_id` + 13~15 天 TTL）→ RED（5 用例 1 失败）→ `issueTokens` 改为**按会话查活动行并沿用受众/设备/UA/相应 TTL**（`RefreshTokenRepository.findFirstByUserIdAndSessionIdAndRevokedAtIsNullOrderByCreatedAtDesc`）→ GREEN（5/5）
    - 清理（两轴共同指出）: 受众字面量统一到 `RefreshToken.AUDIENCE_*`（删 `UserLoginService` 与 `OAuthLoginOrRegisterService` 两处死常量）、`isPluginSession()` 真正被 `TokenRefreshService` 使用、删除零调用的 2 参 `createRefreshToken`、迁移脚本中文注释改英文、4 个测试方法名规范化为 `shouldX_whenY`、测试文件全限定名 `java.util.UUID` 改短名 + 补 import
    - 判为不改的 3 项（理由入账）: `TokenUtils.createRefreshToken` 7 参（单一静态工厂、3 调用点各一行；引入 Spec 类型收益不抵）｜`issueTokens` 在无 sid 时自造会话（`issueTokens` 从不传 null，NOT NULL 列下属防御分支，令牌 claim 与响应一致）｜R2-b 两端点（下一批）
    - 领域知识同步: `domains/auth-lifecycle/practices.md` 增「轮换与一切后续签发沿用会话属性」条款 + 记录该回归与其用例名
    - 验证: `clean build` **1521 tests / 0 failures / 0 errors**（1 skipped 为既有）；`SessionIdentityIntegrationTest` 5/5、`AuthControllerPasswordResetConfirmIntegrationTest` 13/13；spotless + 覆盖率门禁通过
    - 状态: ✅ 修复完成，已交付 v0.81.0（2026-10-19）

- [2026-10-19] - R2-a 会话身份与插件会话缺陷修复（v0.81.0，已交付）
    - 交付: 迁移 `V20261019120000__add_session_identity_to_refresh_tokens.sql`（`session_id` NOT NULL + 回填=id + 默认 + `(user_id, session_id)` 索引；`user_agent` 可空）+ `RefreshToken` 增两列与 `AUDIENCE_WEB/AUDIENCE_PLUGIN` 常量 + `isPluginSession()`；`TokenUtils.createRefreshToken` 增会话/UA 参数（UA 写入前截断 512）；**四条签发路径全部带会话身份**（登录/轮换/OAuth/terms 接受）；access token 增 `sid` claim（无会话则省略）+ `CurrentUser` 增可空 `sessionId`（**5 参便捷构造器 ⇒ 37 处既有构造点零改动**）+ 转换器读取 `sid`
    - **修掉一个存在已久的缺陷**: 全仓**从没有任何地方写 `PLUGIN`**（`ISSUED_FOR_WEB` 是唯一常量、`refreshTokenTtlPlugin` 是死配置）⇒ 插件会话从**登录**起就被记成 WEB/30 天，刷新后依旧
    - **Ruling 修正（证据驱动，非猜测）**: 判别信号原定"带 deviceId 即插件" ✗ —— 被 `LoginRequest.deviceId` 的 **`@NotBlank`** 证伪（所有登录都必须带 ✓），且实测**两侧客户端都不发**平台/IDE 头 ✓ ⇒ 改为 **deviceId 已在 `devices` 注册 ⇒ PLUGIN**（注册表是服务端可核实的"这是插件客户端"，也正是该表存在的意义）。代价：插件**首次**登录（尚未注册）仍记 WEB、刷新沿用旧受众，已在用例中按"注册后"验证
    - TDD: 4 个集成用例先红（`session_id` 列不存在 / 插件仍 WEB-30d / `sid` 缺失）→ 实现 → **4/4 绿**；全量见批次报告
    - 顺带修一处我自己的早期误判: `TokenRefreshService` 的受众"硬编码 WEB"只是表象 —— 真相是从未被写过 PLUGIN，登录路径同样如此

- [2026-10-18] - R1 修改显示名（`PATCH /api/v1/users/me`，v0.80.0 → v0.80.1）
    - 交付: `UpdateProfileRequest`（校验复用注册同一常量 `REGEX_DISPLAY_NAME`）+ `UserService.updateDisplayName` + `UserController` PATCH 路由（`getActiveUserRequired` + `@RequiresApiKeyScope(WRITE)` + `@RateLimit(USER,10,3600)`）+ 新审计 `PROFILE_UPDATED`（IAM 分组、`ResourceType.USER`）+ README / `dev-docs` 同步
    - **Ruling（偏离计划初稿）**: 写方法落 `UserService` 而**不**落 `UserProfileService` —— 后者类注释明文声明只读（"This service is read-only"），塞进写等于削弱一条已声明的约束；`UserService` 本就是"校验 + 持久化 + 审计 + 事务"的应用服务（与 `registerUser` 同形）。代价：若日后判定归 profile 域，移动一个方法
    - Ruling: 同名提交 = 幂等成功（不写库、不审计），审计只在真实变更时产生；`AuditDetails` 带 `field/oldValue/newValue`
    - Ruling: 响应由控制器在写后调 `UserProfileService.getCurrentUserProfile` 取 —— 资料投影（含 `emailChangePending`）只应有一个来源；代价：多一次读
    - 前置核实: `audit_logs.action` **无 CHECK 约束**（只有 `resource_type`/`severity`）⇒ 新审计动作**零迁移** ✓
    - TDD: 先写 10 个用例 → `compileTestJava` 亲眼看它失败（缺 `updateDisplayName`×4 / `PROFILE_UPDATED`×1，失败原因正确）→ 实现 → 目标类全绿 → **全量 1513 / 0 failures / 0 errors**（上批 1503，+10 与本轮用例数一致）；`spotlessCheck` PASS
    - 顺带修一处漂移（R25 回源）: `domains/api-contract/references.md` 的 AUTH 错误码计数写 24、**实测 25**（v0.77.0 新增的 `AUTH_025` 未回写），已改并把核对基线刷到 v0.80.0
    - **顺带修复的既有缺口（用户授权"发现就解决"）**: `DELETE /me` **无限流** → 加 `@RateLimit(USER,5,3600)` —— 该端点每次请求跑 bcrypt（strength 12）校验密码，无限流即 CPU 耗尽面（DoS），与其它凭据端点不一致已一年；TDD 固化（新集成测试，后因扩面更名为 `UserMeRateLimitsIntegrationTest`：第 6 次/小时 → 429，RED→GREEN 各实跑）。`GET /me` **判定不加**：前端 AppHeader 每次渲染都会调用（其对接文档明示）、实现零 bcrypt、一次主键查 —— 加了会破坏已确认用法且收益不成比例。已成领域实践：`api-contract/practices.md`「Rate limits are a property of the controller」
    - **两条边界已补测（同日复核——用户点出 R1 仍有未打勾项）**: ① `ActiveAccountGateIntegrationTest` 新增「**已删除账号改显示名 → `403 AUTH_022`**」（门存在的理由：删除后旧令牌仍在 15 分钟窗口内有效）；② `UserMeRateLimitsIntegrationTest`（原 `AccountDeletionRateLimitIntegrationTest` 更名扩面）新增「**第 11 次改显示名 → 429**」⇒ 两类 6/6 与 2/2 通过。**Ruling 修正**：初版把这两条判为"可以不建例"被推翻——门与限流同属安全面，钉成用例比省一类测试重要；并修正计划写窄处（门的返回码随令牌状态：`LOCKED`/`PENDING`/`SUSPENDED`/`DELETED` → `AUTH_004`/`006`/`005`/`022`）
    - 状态: ✅ **已交付 v0.80.0 → v0.80.1**（develop：`28daa85` 功能+测试+README+api-governance、`69e4af3` 版本、`79bba22` 计划与 dev-docs、`f635b93`/`4379be4` 记忆；补测批 `0aa808c` 测试、`05f00b2` 版本、`a5d5a5b` 计划、`d23250a` 记忆 → **全部推送 ✓**；master 逐条 cherry-pick `649c484`+`35544fe`、`1155ec3`+`3a658bc`，纯净检查 0 命中、构建通过 ✓）

- [2026-10-18] - 阶段六《个人设置》后端计划（R1–R3；R1 已交付，R2/R3 未开工）
    - 触发: ctt-web 阶段六定稿（改显示名 / 登录会话列表与吊销 / 数据导出），要求逐条确认或反提案 + 排期回传
    - **事实核实（只读全仓）**: 三项**全部不存在**（`@PatchMapping` / `/sessions` / `/export` 各 0 命中）；可复用底座齐备（`REGEX_DISPLAY_NAME`、`UserProfileResponse`、`AuditAction`/`ResourceType`、`@RateLimit`、`@RequiresApiKeyScope`）；**零新增错误码**
    - **R1 裁决（接受）**: `PATCH /api/v1/users/me`；**不引入显示名唯一性**（列无唯一约束，且本产品无按显示名查找/登录路径，昵称非身份标识）；同名提交=幂等成功（不写库不审计）；`getActiveUserRequired` + `@RequiresApiKeyScope(WRITE)` + `USER 10/3600` + 新审计 `PROFILE_UPDATED`
    - **R2 裁决（接受 + 反提案）**: 必须新增 `session_id`（独立迁移；轮换携带 ⇒ 一次登录 = 一条会话链）+ access token 增 `sid` claim（`CurrentUser` 加可空 `sessionId`）——否则"会话"在数据层不存在（轮换 = 旧行 revoked + 新行 INSERT），`current` 也无可判定（JWT 无会话标识）
    - **R3 裁决（接受 + 反提案）**: `GET /users/me/export` 用 **NDJSON 流式**（`StreamingResponseBody` + 项目注入的 `ObjectMapper`；编码会话按 1000 行分页读取，不整表装配）；范围含编码会话本体与每日物化统计；绝不含密码/密钥/令牌/IP；审计 `DATA_EXPORTED`
    - **顺带发现的真实缺陷（R2 前置修复）**: `TokenRefreshService` 轮换时硬编码 `issued_for = "WEB"` 且恒用 `refreshTokenTtlWeb()`（30d）——`refreshTokenTtlPlugin`（14d）**从未被使用** ⇒ 插件会话每刷新一次即被降级并续成 30 天
    - **计划文件自身的记录漂移（按 R4 校正）**: OAuth 段 N 的错误码清单语义整体错位（AUTH_013–018 与现码不符 ✗）；P 段的枚举名 `OAUTH_CANNOT_UNLINK_LAST_CREDENTIAL` 不存在（实为 `AUTH_018`）→ 两处已改 + 附校正表
    - 计划落地: `.plans/ctt-server-development-plan.md` 新增「👤 个人设置（账号自助与数据可携带）」节（事实基线 / 裁决摘要 / 三个子项含契约·决策台账·交付物·验收 / 排期与升级条件 / 反向要求 / 明确不做 / 校正记录）+ 3 张 plantuml 图；顺带把原有 `dot` 模块依赖图换成 plantuml（docu.md 渲染管线不认 dot ✗）
    - 渲染验证: `npx @markdown-viewer/documd …--assets` → **7 diagrams / 0 失败**（退出码 0），并肉眼复核类图与活动图
    - 状态: 计划定稿（2026-10-18 提交并推送 develop：`62bd6f0` 计划 + `63223fa` 记忆；master 无内容）；**R1 已按本计划实施交付**（见上一条），R2 / R3 未开工
    - **前端答复（2026-10-18）**: 无契约冲突、顺序无需重排、8 字段够用；**另提一条可选增强并已采纳** —— 会话列表增可空 `userAgent`（原文存储、服务端不解析、写入截断 512 字符、轮换沿用旧值），供前端派生 "Chrome · macOS" 类显示名。回源核实: 刷新与重置密码路径早已从请求头取 UA，**登录路径没有**（`UserLoginService.login(LoginRequest)` 单参 ✗）→ R2 一并补；`RequestInfo` record 已带 `userAgent` 可直接复用。同一迁移脚本加列 ⇒ 排期不变；R3 导出相应增加 `loginSession` 行类型（不含令牌哈希）
    - 已同步更新: 计划 R2（迁移 / 取法 / 读取 / 验收）+ R3 行类型、`dev-docs` 对接文档、前端报告（文首同步清单 6 → 7 条 + 第七节改为"你们已答复"记录）
    - **补充同步（同日复核后）**: 会话列表**排序 = `lastUsedAt` 倒序**、API key 调用时 `current` 恒为 false；**"踢会话"与"正在刷新"并发**的洞（刷新插入的新行躲过批量吊销 ⇒ 会话诈尸 ✗）→ 刷新与吊销都先按 `session_id` 取 `pg_advisory_xact_lock` 串行化（该竞态在既有 `logout` 上同样存在，一并覆盖，已加验收项）；给前端的报告新增**第八节五条接口细节**（导出无 JSON 外壳 / 三个端点的 scope 要求 / 插件会话也记 UA / 会话行不返回 `deviceId` / 导出含已删会话）
    - **文件组织（用户裁定）**: 新反馈**单独成文**，不并进旧文件 ⇒ `.omp/settings-phase6-frontend-report.md` = **对接契约（滚动更新）**，`.omp/settings-phase6-frontend-report-2.md` = 第二轮通知（对前端答复的三问确认 + `userAgent` 采纳 + 六条细节 + 并发说明 + 无变化部分）；第二轮内容已从契约文件移出，只留索引行
    - **第二轮回执（2026-10-18 同日）**: 前端"全部确认、**无冲突、无新增需求**" ⇒ 该轮往来闭环；`userAgent` 的显示名由前端派生（其设置页工作项，纯函数 + 单测，识别失败回退原文/未知）；前端按 **R1 → R2 → R3** 等待"端点已可用"通知（首个通知要等 R1 实施完成）

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

- [2026-10-12] - 账号状态门控接线 + 生产侧 FQCN 清理（v0.78.1）
    - **先纠正我自己报错的数字**: 上批收尾我说"仓库还剩 9 处全限定类名 / 8 个文件"，实测是**生产 4 处 + 测试 91 处**（前后两次 grep 过滤条件不一致 ✗）。教训：规模必须用同一条命令复算，不能凭中间结果拼凑
    - **裁决 1（门控）= 把已实现却从未接线的 `getActiveUserRequired()` 接上，而不是改文档就范**: 接口 Javadoc 明写"适合要求账号完全激活的业务操作"并按状态映射错误码（`AUTH_006`/`004`/`005`/`022`）、`SpringSecurityCurrentUserProviderTest` 已逐状态钉死、`docs/api-governance.md` Tier 3 以它作为 Gate —— 三者齐备 ⇒ 属"**意图在、接线缺**"的**缺陷**（R25：文档记录的是意图而代码违反时，改文档就范是错的 ✗）
    - 接线 **7 处**（只挑会改**凭证 / 身份 / 账号本身**的）: password set/change、email change-request/resend、`DELETE /users/me`、`POST /auth/api-keys`、OAuth unbind；**故意放行** logout / logout-all / 取消待改邮箱 / 读自己资料 / 撤销与清除 API key（这些是锁定账号的**自救通道**，设门会把封禁变成陷阱 ✗）
    - **效力边界（不夸大）**: 状态取自**令牌 claim** 而非查库 ⇒ 它拦的是"令牌签发/刷新时账号已非激活"，**并不缩短**"签发时激活、之后状态变化"的那个 15 分钟窗口（缩短要每请求查库，是本领域早已裁决拒绝的取舍）
    - 测试: 新增 `ActiveAccountGateIntegrationTest`（**真实 provider + 真实安全链** 5 例，含 **LOCKED 仍可 `logout-all` → 200** 的放行证明）；连带修 **20 个既有切片测试** —— 每文件加**一个转发桩**（切片代表 ACTIVE 用户；拒绝路径由新集成测试覆盖），而非逐处改 20 次
    - **裁决 2（FQCN）**: 生产 4 处修掉（`Pattern`/`Locale`/`Optional`/`ScanOptions`，均核实无 import 冲突）；**测试侧 91 处留给独立的一次机械清理**（与安全修复混批会污染提交边界 ✗）→ 记为待办；AGENTS.md R9 的禁令保持通用、**不缩窄**（缩窄正好放过我上次翻车的那类位置 ✗）
    - 领域沉淀: `auth-lifecycle/principles.md` §10（门控原则 / 效力边界 / 防御性例外）+ `scenarios.md` 新增触发行（新增加凭证类端点该用哪个 provider 方法）
    - 验证: `spotlessCheck` + 全量 **1494 tests / 0 failures / 0 errors** + 覆盖率 **95.25% / 84.60%**（阈值 80/70 通过）+ 生产代码 FQCN 终扫 **0** ✓
    - 状态: ✅ 已交付 v0.78.1（代码 + 测试 + 文档 + 领域知识；AI 记忆独立提交）

- [2026-10-12] - summary 涨跌对比（对比上一完整周期，v0.78.0，已交付）
    - 触发: 前端交来需求报告（自称已回源核对）。逐条复核其 **6 项断言: 5 属实、1 不实** —— 报告称"沿用 60s 缓存"，实际 `src/main` 全无 `@Cacheable`/`Cache-Control`/`CacheManager`，60s 只是**限速窗口**；端点本就无缓存，稳定性来自纯计算（故其验收 7「重复请求稳定」天然成立）
    - **报告未覆盖、却决定实现的三个事实**: ①全局 `spring.jackson.default-property-inclusion: non_null`（`application.yaml:29`）会**整键丢弃 null**，与需求「五个键恒在、`changePercent` 用 null 表达」**直接冲突** → 组件级 `@JsonInclude(ALWAYS)` 覆盖（项目已有先例: `StatsIntegrationTest` 注释「字段是 primitive 才能在 non_null 下存活」）②两路径对**未来日期**上界本就不同（物化开放 / 实时截到本周期末）—— 既有行为，不特判（基期窗口都是有界历史区间，不受影响）③`StatsCalculator.Summary` 仅 `liveSummary` 与测试使用、测试只读访问器 → 加字段安全
    - 口径决策（报告缺口处由我定）: 首日 `dailyAverage.previousSeconds` 取 **0**（首日无"昨天"；实践上 `total == today` 与报告公式同值，仅在未来日期数据时更诚实，且自然落入「previous == 0 → null」免特例）；四舍五入用 `BigDecimal` **HALF_UP（远离零）**（中文"四舍五入"语义；JSON 里 double 写 `-100.0`，解析后与 `-100.00` 同值）；**现有周期口径一律不动**（当前值是回归红线）
    - 实现: `StatsCalculator.Summary` 加 `previous`（`PreviousPeriods`）+ 4 次 `mergedDurationSeconds(prevStart, prevEnd)`（同一份已加载区间，**无新增查询**）+ 基线 `daysSinceFirst > 1 ? (total − today)/(D − 1) : 0`；物化路径按 `[start, end)` 有界区间求和（当前周期保持原有开放下界语义），两条路径共用 `toResponse(Summary)` 装配；percent/null 规则收口在 `StatsComparison.of`
    - **实跑（真 HTTP + 真库，端口 18080）**: 推 A(昨 1h) + B(今 1h) + C(昨日 15:30–16:30Z，对 UTC+8 恰跨本地午夜) → UTC 物化 `today=3600 / prev=7200 / −50.0%`，UTC+8 实时 `today=5400 / prev=5400 / 0.0%`（C 被正确切成 1800+1800）—— **同一份数据在两个时区各自正确归属** ✓；基期为 0 时响应中 `changePercent` **键存在且为 null** ✓；`/v3/api-docs` 已含五键与字段文档 ✓
    - 测试: **1486 tests / 0 failures**（+16: DTO 6 + 计算器 3 + 服务 5 + 集成 2）。集成用例用 `asMap().containsEntry(k, null)` 断言键存在 —— `extractingPath(...).isNull()` **对缺失键同样通过** ✗ 分不清"键不存在"与"值为 null"
    - 我自己的三处算错（跑出来才发现，非逻辑错）: ①"上一周"用例当前周也有值 → 应为 +20.0 ②`dailyAverage` 基线**不受基期窗口影响**（800 天历史时上一日均值非 0，断言不能是 null）③实跑脚本按 30 分钟写 C 却推了 1 小时 → 期望应为 −50% 而非 −33.33%（**系统对、我的期望过期**）
    - 领域沉淀: `stats-aggregation/principles.md` §10（口径闭环 + 基期必须与主值同源 + 三条易错点）；`stats-aggregation/references.md` 响应字段表；`api-contract/practices.md`「null 字段默认缺失，契约要求键在时须 opt out」+ 断言陷阱
    - 顺带: 实跑也走了销号（`DELETE /users/me` 200 → 再登录 401、旧令牌 `/users/me` 404）；**旧令牌 `GET /stats/summary` 仍 200** 属上一批已裁决的 15 分钟窗口（无害空读），非回归。8080 上另有一个**你 9/30 启动的实例**（非我产生，未动）
    - 状态: 实施 + 验证完成；**接口已与前端逐条确认通过**（前端按源码复核属实；首日 `dailyAverage.previousSeconds = 0` 与"物化/实时 ≤1s/天"差异**两项均获接受**；§7 三条事实修正已被其文档吸收并据以改正自身注释），已交付 v0.78.0

- [2026-09-19] - 依赖更新分析与升级（v0.77.1）
    - 策略: 一律直接升到最新稳定版（含主版本），兼容性由编译 + 全量测试验证，不做逐项风险评估
    - **ben-manes 报告不可尽信（旧坑复现）**: `dependencyUpdates` 只列出 5 项，**缺 Spring Boot / spotless / postgresql 等关键坐标**；按既有教训改用 **Maven Central 元数据 + Gradle Plugin Portal 逐个核实**，才发现 Spring Boot 4.1.1 与 Gradle 9.7.1 **本已是最新稳定**（插件报的 4.2.0-M1 / 9.8.0-rc-3 是里程碑与 RC ✗ 按策略跳过）
    - 升级: greenmail 2.1.13→**2.1.14**、springdoc-openapi 3.1.0→**3.1.1**、flyway 13.5.0→**13.7.0**、ben-manes 插件 **坐标迁移** `com.github.ben-manes.versions`→`io.github.ben-manes.versions` 且 0.61.0→**0.64.0**（两个 id 都已有 0.64.0，按新坐标走 ✓）
    - **顺手修掉一处会漂移的重复来源**: `flyway-core` 的 `version { strictly("13.5.0") }` 是硬编码字面量，与目录里的 `flyway` 版本各自维护 → 改为 `strictly(libs.versions.flyway.get())`。**关键不变量**（上一批的血泪）: flyway 的 core 与 database-postgresql 必须同版本，BOM 给的 12.4.0 由 strictly 覆盖 → 升级后实测 `flyway-core 12.4.0 -> 13.7.0`、`flyway-database-postgresql -> 13.7.0` **同版本** ✓
    - **techContext 技术栈表严重过期，已一并修正**: 原表写 Spring Boot 4.0.5 / Flyway 11.4.0 / springdoc 2.8.5 / Testcontainers 1.20.6 / JUnit 5 / JaCoCo 0.8.14，实际为 **4.1.1 / 13.7.0 / 3.1.1 / 2.0.5 / 6.1.3 / 0.8.15**；且表里还留着**已不再使用**的 JJWT（现用 `spring-security-oauth2-jose`），并把 spotless **插件版本**（8.10.2）与 google-java-format（1.36.1）混为一谈。修正后加了一句"**本表是快照，`gradle/libs.versions.toml` 才是唯一来源**"以防再漂
    - 未动（附依据）: Spring Boot 4.1.1（最新稳定）、Gradle 9.7.1（最新稳定，9.8.0-rc-3 是 RC）、postgresql 42.7.13 / junit-platform-launcher 6.1.3 / spotless 8.10.2 / dependency-management 1.1.7（均已是 Maven Central 最新）、JDK 25.0.4（25 GA 家族最新，25.0.4.1 仅同版重建包；25→26 属大版本跳跃须先确认，未动）
    - 验证: `clean build` + 全量 **1470 tests / 0 failures** ✓（首次运行失败一次、无诊断输出即重跑通过 —— 判断为升级后首次拉取构件的瞬时失败，最终解析版本已逐一核实 ✓）

- [2026-09-19] - Notion 计划迁移入库（`.plans/`，版本不变）
    - 触发: 你决定弃用 Notion，计划类文档统一进仓库（人机共读、中文、仅 develop、不进 master）
    - 产出: `.plans/ctt-server-development-plan.md`（1782 行，迁移自《🖥️ ctt-server 开发计划》2026-09-01 快照）+ 4 张图（mindmap 阶段总览 / uml 状态机 / uml 时序 / dot 模块依赖）。转换: 42 对 `<details>` 拆壳、14 张 HTML 表转 Markdown、229 处行首 TAB 归一、14 处假链接修复、3 个 H1 收敛为 1
    - **我的转换规则漏了「块边界」，三类缺陷连环暴露**: ①"表格前补空行"缺 `not is_table(prev)` 守卫 → 给每一行前都插空行 → **106 处表格被切断、退化成段落**（你截图报的 ✗；算术印证 120 表格行 − 14 表头 = 106）②只恢复了标题/列表/表格周围，**没恢复普通段落的块边界** → 280 处独立块被并成一段 ③缩进行（列表续行）后面的非缩进行被**懒惰续行**吞进列表项 → 21 处；另有 4 处 `****` 粗体拼接伪影（Notion「粗体里嵌代码」残留）
    - **判据教训（本批最值得记）**: 我连续几轮报"结构检查 11/11 全过"，却查不出上述缺陷 —— markdownlint 只看列数/空行，**看不见"行被空行隔断成非表格"**。换判据才抓到: ①真实 GFM 解析器数元素（table=14 / thead=14 / tr=106 / h1=1 / code=5 / 字面 `****`=0）②**在你真实查看器（docu.md）里亲眼验收** —— 自建 `file:///tmp/*.html` 预览只是近似（连 `- [x]` 复选框都渲染不出 ✗），真实查看器才是标准判据（表格成真 `<table>`、4 图渲染成 PNG、复选框成 ☑）
    - 另记一处**我自己的筛查漏项**: 量化时一度把 mindmap 里的 `**`/`***` 行算作"被合并段落"（227 处）—— 筛查规则同样漏了**围栏感知** ✗，纠正后为 280 处
    - 规则同步: R26 补 `.plans/` 行（人机共读的计划/归档入库、仅 develop）—— 原 R26 只认 `.omp/`，与"计划入库"直接冲突（按 R14 已改，待你确认）
    - 提交: 计划迁移 + gitignore 在 develop；**计划文档不进 master**（面向 AI 与开发过程的内容），gitignore 单独 cherry-pick 进 master；纯文档不 bump 版本

---

> **归档**：更早的条目已按月归档至 `memory-bank/archive/activeContext-YYYY-MM.md`（R13 冷数据），本文件只保留最近 30 天热条目。
