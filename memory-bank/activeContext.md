# Active Context
- [2026-09-13] - 成就系统扩展 Batch 2（achievedAt 回推真实达成时刻，v0.69.0）
    - 需求: 文档§5——`unlockedAt` 记录的是"首次被读取时刻"而非达成时刻（`@CreationTimestamp` + 懒评估），所有"何时解锁"类功能失真
    - 关键判断: **不需要迁移**（`unlocked_at` 已是 NOT NULL，只需写入方传值）——R22 要求不改已应用迁移，此判断使本批零 schema 变更
    - 回推设计: 新增 5 个精确原语于 `StatsCalculator`（纯计算，不依赖 achievement 包，避免 `stats.service → achievement` 循环依赖）：
      · `totalSecondsAchievedAt` — 合并区间前缀和，命中区间内**精确跨阈时刻**（非区间边界）
      · `maxDailySecondsAchievedAt` — 按日切片累计，返首个单日跨阈时刻
      · `streakAchievedAt` — 按日戳首次活跃时刻，走 runs 找达标段末日
      · `languageCountAchievedAt` — 按 start 排序，第 N 个新语言首现时刻
      · `activeWindowDaysAchievedAt` — 复用 windowDays 归因，返达标窗口日 overlap start
      · `perfectMonthPercentAchievedAt` — 达标天数 = ceil(targetPercent × 月长 / 100)，与 `bestPerfectMonthPercent` 同一口径
    - 架构: `AchievementService` 引入私有 record `Measurement(progress, resolver)` —— **progress 与 achievedAt 同源同算**（单次 switch 同时产出两者，避免 Repeated Switch），resolver 以 target 为键（同家族多阶各有不同达成时刻）；实测 record 组件访问器与自定义方法同名会冲突（`achievedAt` → 改名组件为 `resolver` + 语义方法 `achievedAt(long)`）
    - 写路径: `insertIfAbsent` 增第三参 `OffsetDateTime unlockedAt`；`UserAchievement` 去 `@CreationTimestamp`（防未来 JPA persist 路径覆盖）；并发败者改读回胜者写入的时刻（原实现返回 null）
    - 防御: progress 达标但 resolver 返回 null 时**记录 warn 并回退观察时刻**——不静默丢解锁、不写错误时刻
    - README: 补 `unlockedAt` 语义说明（R4）
    - 测试: 计算器 +10（跨阈时刻在区间内/单日目标/连击断裂重开/语言第 N 个/窗口日/2 月 100%/90% 容错取整）；Service 改写 stake 为捕获真实传入值 + 新增 2 条（窗口小时接线、BURST 4h/8h 分档时刻）；集成测试补 `unlocked_at` 落库断言（08-30T10:00Z 而非查询时刻）
    - 验证: 全量 **1364 tests / 0 failures**；jacoco INSTRUCTION 95.14% + BRANCH 84.37%；spotless PASS
    - 状态: ✅ Batch 2 完成（Batch 3 = progress 高水位；Batch 4 = 周期成就）
- [2026-09-11] - 成就系统扩展 Batch 1（type/tier 投影 + 阶梯扩容 + PERFECT_MONTH 连续化）
    - 需求来源: ctt-web 六类问题文档（成就终局/阶梯太短/步长失衡/PERFECT_MONTH 二值/unlockedAt 语义/progress 可倒退）。核实后**前端文档 3 处错误 + 2 处缺口**：①DAILY_BURST progress 已是秒（MAX_DAILY_SECONDS）不需改语义 ②阶梯提案丢了 3 个现存 code（LANGUAGES_10/EARLY_BIRD_30/NIGHT_OWL_30）会孤立已解锁记录 ③tier 非"枚举已持有"需推导；缺口：周期成就需 period_key 改唯一约束（前端的"高"成本实为 schema 变更）、46 阶会退化成 46 次全量扫描
    - 评审报告: `.omp/achievement-expansion-review.md`（267 行，已 gitignore）
    - 实施: Achievement 枚举 15 → **51 阶**（STREAK 8 / TOTAL 8 / LANGUAGES 9 / EARLY_BIRD 8 / NIGHT_OWL 8 / DAILY_BURST 5 / PERFECT_MONTH 5），**保留全部 15 个既存 code**；tier 由静态 TIERS 表按 type 分组 + target 升序推导（不手写序数）；AchievementResponse 增 type/tier；AchievementService 加 **per-type EnumMap 记忆化**（51 阶 → 7 次计算，原为每常量一次）；`LocalDate.now(clock)` → `LocalDate.now(clock.withZone(zone))` 修正 UTC 与本地日不一致
    - PERFECT_MONTH 语义变更: `hasPerfectMonth(...)?1:0`（二值、零容错）→ `bestPerfectMonthPercent`（历史最佳月份的**百分比覆盖** 0-100，unit `month`→`percent`）。百分比制而非整数天数阶梯：28 天月全勤=100、31 天月 29 天=93，跨月长语义一致
    - 测试: 新增 `AchievementTest`（3 组不变量：既存 code 不可丢/每家族 ≥5 阶/target 单调且 tier 连续）；StatsCalculatorTest perfectMonth 改写为百分比（含 2 月满勤=100、96% 下取整、取最佳月）；集成测试 `hasSize(15)` → `hasSize(Achievement.values().length)`（去硬编码）；Service 测试补 type/tier 断言
    - 验证: 全量 **BUILD SUCCESSFUL** + jacoco PASS + spotless PASS（1346 → 更多用例）
    - 双轴 code review（parallel sub-agents，各自独立上下文）:
      · Standards 轴 4 项硬违规：README 仍写"15 badges"+PERFECT_MONTH 陈旧（R4/R17 违反）/ AchievementTest 缺 `_whenY` / 枚举 Javadoc「×1.6-2.2 倍率」与实际不符（实测 TOTAL 2.0-2.5、BURST 1.2-1.5、PERFECT 1.05-1.4）/ AchievementType.PERFECT_MONTH Javadoc 仍是旧二值语义
      · Spec 轴：in-scope 全部实现（51 阶 7 家族、15 个既存 code 全保留、百分比制 2 月满勤=100 已测）；越界 2 项（PERFECT_MONTH_50/_70 为满足 §2「≥5 阶」下限所必需；today 用 zone 投影属第 7 步前置修正，此处行为中性）；0 功能错误
      · 两轴**独立命中同一处 Javadoc 缺陷**——最高可信度发现
      · 额外发现（子 agent 未提，我方核查）：新 DTO 字段无 MVC/Jackson 全链路断言；缓存键未版本化 → 滚动部署 60s 内旧 JSON 反序列化得 type=null/tier=0 且服务旧阶梯（已实测 Jackson 非严格 + `default-property-inclusion: non_null` 确认可静默成功）
    - 审查后修复:
      · README 成就段重写（51 badges 七家族分布 + type/tier + PERFECT_MONTH 百分比语义 + 版本化缓存键）
      · Achievement 枚举 Javadoc 倍率声明改为"按家族分别校准"（不再声称统一几何带）
      · AchievementType.PERFECT_MONTH Javadoc 改为百分比语义
      · 缓存键 `achievements:cache:` → `achievements:cache:v2:`（格式版本化，evictCache 复用同一常量自动跟随）
      · AchievementTest 重写：`shouldGiveEveryFamily_atLeastFiveRungs` 补全 `_whenY`；消除与 buildTiers 重复的分组逻辑，改走 public API
      · 集成测试补 type/tier 经 HTTP/Jackson 的断言（STREAK_7 → type=STREAK, tier=2）
    - 最终验证: 全量 **1352 tests / 0 failures / 0 errors**（1 skipped）；jacoco INSTRUCTION **95.24%**（阈值 80%）+ BRANCH **84.57%**（阈值 70%）；spotlessCheck PASS；compileJava + compileTestJava PASS
    - 状态: ✅ Batch 1 完成（含审查修复），待授权提交（Batch 2 = achievedAt 回推；Batch 3 = progress 高水位；Batch 4 = 周期成就）
- [2026-09-11] - master 生产分支内容边界清理（非 AI 的"开发内容"一并清出）
    - 触发: 用户指出 master 上的 `docs/plans/2026-05-02-terms-acceptance.md` 属 AI 内容必须删除；并纠正我的误判——`dev-docs/` 下的两份 QA 文档是**开发内容**，本就不属 master（我此前建议 cherry-pick 过去是错的）
    - 判据（用户给定）: master = 项目文档（`docs/` 面向用户者）+ 业务代码/测试/版本；`docs/plans/`（AI 计划）与 `dev-docs/`（开发/对接内容）均不进 master
    - 执行:
      · `85cdc51 chore: remove AI implementation plan from production branch`（删 docs/plans/2026-05-02-terms-acceptance.md，457 行）
      · `3779ef7 chore: remove dev-only documentation from production branch`（删 dev-docs/{apikey,oauth,sync,user}/frontend-integration.md，1301 行）
    - 溯源: 4 份 dev-docs 非刻意添加——随 `feat(oauth)`/`docs(pull paging protocol)` 等 cherry-pick 的提交捎带进 master；README/docs 均未引用
    - 用户裁决: ①`docs/terms-acceptance-frontend-guide.md` 从 master 删除（同一内容判据）→ `0be68a1`，430 行 ②R17 加 dev-docs 排除规则
    - R17 加固: 核心原则改为"无 AI 文件、无开发内容"；master 禁止项补 CLAUDE.md/SKILL_GRAPH.md/skills-lock.json + dev-docs/、docs/plans/、对接方指南；新增**内容边界判据**（面向本项目使用者=项目文档进 master；面向 AI 或面向对接方开发者的实施/对接说明=不进 master；位于 docs/ 但内容是接指南者按内容判定不按目录）；master同步规则 cherry-pick 排除 `dev-docs/`、`docs/plans/`（feature 提交常携带这些路径被捎带进 master）；验证项补"无 dev-docs、无 docs/plans"
    - master 终态: `docs/` 仅 6 份面向用户项目文档（api-governance/audit-boundary-spec/case-normalization/developer-handbook/security-architecture/time-strategy）；对 AI 与开发内容零命中
    - 验证: 构建 BUILD SUCCESSFUL；develop 保有全部 dev-docs 与初始 plans（零内容丢失，仅在 master 侧清除）；两分支 clean，工作分支 develop
    - 状态: ✅ 全部推送完成
- [2026-09-11] - 领域知识库建设（memory-bank/domains/，R25 落地）+ systemPatterns 去重
    - 背景: R25 规则已立（上一轮吸收自 ctt-web）但 `domains/` 目录不存在——规则搬了、库没建。用户确认「按你推荐来，不从简、不保守」
    - 判定: 建 **4 个**领域（非原荐 3 个）——核查 auth 域知识密度后追加 `auth-lifecycle`：86 个源文件、219+ 历史条目，且 `OAuthStatePayload.Action` 三处同步约定**错放在 AGENTS.md R8.5（规则文件）**，正是 R25 要归位的领域判断
    - 建树（21 文件，全部 ≤78 行，无占位符，链接零断链）:
      · `domains/README.md` 索引（两层分工 + 领域表 + 五件套说明）
      · `stats-aggregation/` — 时间轴 vs 分类维度守恒律、单次截断、时区先于截断、最大余数配平、物化与 live 等价、选项列表同源、插件对齐
      · `sync-protocol/` — LWW 四级优先级链 + 内容幂等、游标不可回卷、push 原子性、origin vs last-writer、水位乐观推进的已知缺口
      · `api-contract/` — 错误码复用优先、状态码归注册表、404 不泄露存在性、兄弟端点错误集一致、429 双发 retryAfter、scope 失败关闭
      · `auth-lifecycle/` — BIND 不发 token 会话不变量、登录元数据全路径、分层解锁、杀开关、防枚举、terms 版本门、token 用途隔离、captcha 配置开关
    - 去重迁移（R25「不得两处重复」）:
      · `systemPatterns.md` 的「同步策略 LWW+软删除」→ 留一行指针指向 sync-protocol/principles.md
      · 「登录元数据设置模式」表 → 迁至 auth-lifecycle/practices.md，systemPatterns 留指针
      · AGENTS.md R8.5 的 `OAuthStatePayload.Action` 约定 → 迁至 auth-lifecycle/scenarios.md，R8.5 改为「领域判断归领域文件」原则条款 + 链接
      · 「客户端分配 ID 实体模式（Device）」保留在 systemPatterns（横切持久层规范），sync-protocol/practices.md 改为链接不复制
    - 保留未迁: 时间策略/邮箱规范/测试风格/接口治理/接口安全/客户端上下文/传输安全/架构风格/安全底座——均为横切规范，符合 R25 分层
    - 验证: 链接完整性脚本零断链；行数全部 ≤200；占位符扫描零命中（两条 grep 命中为 "never as a stub"/"Backfills" 误报）
    - 状态: ✅ 待提交授权
- [2026-09-11] - AGENTS.md 优化：吸收 ctt-web 的 6 条规则补缺（R14 流程）
    - 触发: 用户要求对照 `../ctt-web/AGENTS.md` 优化本项目规则
    - 事实核查: `.agents/` 在 ctt-server 有 48 个受跟踪文件（AI 技能工作区却无保护规则，真实缺口）；`.omp/` 已 gitignore（第 55 行）；AGENTS.md 仅存在于 develop（master 无 AI 文件，R17 一致）
    - 吸收（仅取能补齐真实缺口的，不照搬前端专属）:
      ① R3 扩为「关联项目（只读红线）」——新增 `../ctt-web`，明确严禁修改关联项目任何文件、契约变更走需求文本（此前 R3 只说"主动读取"，无禁止修改；与 ctt-web R3/R23 对齐）
      ② R8 新增「讨论信号」bullet——"为什么/能不能/是否应该/你看呢/是不是…更好" = 讨论确认信号，先分析后实施，严禁当实施指令
      ③ R9「编辑前验证」补全整文件阅读原则（片段不足直接整文件读，不反复片段读）
      ④ 新增 R24 AI 边界——身份（唯一可写仓库=ctt-server）+ `.agents/` 只读保护（对齐 ctt-web R16/R23）+ 审查/调查子任务严格只读禁 --fix（对齐 ctt-web R22，附"子 agent 写盘污染工作区"红线）
      ⑤ 新增 R25 领域知识库——domains/<domain>/ 五件套（meta/principles/scenarios/practices/references），按需建档禁占位，横切规范留 systemPatterns 不重复
      ⑥ 新增 R26 AI 产物位置——docs/ 只放用户文档，AI 产物进 .omp/（此前仅存在于 2026-08-31 activeContext 条目，未固化为规则）
    - 未吸收: ctt-web R13（API 对接 Zod/组件规范）、R9 的 Vue/Tailwind 条目、R7 的状态管理/路由项——前端专属，不适用后端；ctt-web R6 的 `git add` 需授权更严——本项目 R6 已覆盖 commit/push 授权，`git add` 属提交准备动作，保持现状
    - 结构: 记忆库结构段拆为「时间线层 / 领域层」两层（对齐 ctt-web）；现无领域目录（按需建档，不建空结构）
    - 验证: 纯文档规则变更，无代码影响；规则编号 R23→R24/R25/R26 追加、原 R14-R22 保持原位不改号（R14 命名约定）
    - 状态: ✅ 待提交授权
- [2026-09-11] - heatmap-months 端点 + heatmap-years 时区口径修正（v0.67.0）
    - 需求: ctt-web——Coding trend 面板加「自然月」选择器（与年选择同级），选项须限定在有数据的月份；并指出 heatmap-years 无 timezoneOffset、年份按 UTC 提取的同源口径问题（授权我判断是否一并修）
    - 判定: 一并修——年列表与月列表必须互相自洽（否则前端年份行会渲染出空月网格/漏年），且跨年会话在原 SQL 口径下已与 heatmap 不一致
    - 设计决策: ①存在性口径 = 「该月/年至少有一个非零日（timezoneOffset 折算后）」——复用 mergedSecondsByDay（与 heatmap 同一 day-split），filter>0 精确对齐「heatmap 有非零点」，规避亚秒日（floor 后 0 秒）导致的假存在 ②数据源镜像 heatmap：UTC+bootstrapped 读 daily_stats（与 heatmap UTC 路径同源），否则 live sessions —— 保证列表与渲染同源不漂移 ③年与月共用 activeYearMonths(userId, zone) 私有方法，自洽由构造保证 ④删除已无用的 SQL findDistinctYearsByUserIdAndIsDeletedFalse（其 EXTRACT 口径即 bug 源）
    - 实现: StatsCalculator.activeYearMonths(sessions, zone)（public，供 calculator 测试）+ StatsService.activeYearMonths 私有（源选择）/ heatmapYears(userId, zone) / heatmapMonths(userId, zone) + Controller GET /heatmap-months（READ+60/60，timezoneOffset @Min(-720)@Max(720) 默认 0）+ /heatmap-years 加同参数
    - 测试: StatsCalculatorTest ActiveYearMonthsTests +5（UTC+8 边界折算 8/31T16:30Z→2026-09、跨月双向 8+9、跨年双向 2025-12+2026-01、重叠合并+零时长会话不计、空）+ StatsServiceTest 重写 2 + 新增 3（降序/live 时区折算双断言/物化源+零秒日不计+verify 不查 sessions）+ StatsIntegrationTest +2（月列表与 heatmap 逐点一致性断言（需求验收 4）+ 空用户 []）
    - 契约: 纯新增端点；/heatmap-years 加可选 timezoneOffset（默认 0）——非破坏性，但跨年会话年份值会修正（多出溢出年）
    - 验证: 全量 1346/0 + jacoco 门禁 + spotless 全绿
    - 状态: ✅ 实施完成，待提交授权
- [2026-09-09] - distribution 日期窗口 + 全类型精度修复（v0.66.0）
    - 需求: ctt-web 报告 ①/distribution 缺 start/end 窗口参数 ②type=LANGUAGES 的 Total 精度与概览不一致
    - 根因确认: LANGUAGES/PROJECTS/WEEKDAY（accumulateBy）与 DEVICES/IDES（aggregateByLabel）都是每会话 toSeconds() 截断——与 TIME_OF_DAY 第二个 bug 同类（用户数据 3460 会话亚秒起步，累计丢 ~1470s）
    - 设计决策: ①窗口=clipSessions（保留会话身份：project/language/originDeviceId 复制，仅 clamp start/end Instant；与 clipToWindow 语义对齐但保持 CodingSession 类型）②精度=全类型统一收敛到 StatsCalculator.apportion（最大余数法：全精度累加 → floor → leftover∈[0,n-1] 按余量降序 +1）——所有 distribution 桶总和 == summary.total 数学保证
    - 实现: StatsCalculator.clipSessions + apportion 共享 helper + accumulateBy 全精度化 + StatsService.aggregateByLabel 全精度化（reducing Duration::plus）+ timeOfDay 尾部改调 apportion 消重复 + StatsService.distribution 6 参（start/end 校验 end<start→COMMON_003）+ Controller /distribution 加 start/end
    - 测试: StatsCalculatorTest +2（LANGUAGES 亚秒回归 119 vs 逐会话 floor 118/apportion 配平 14398 锁定）+ StatsServiceTest 5 处调用适配 + StatsIntegrationTest +2（LANGUAGES 窗口过滤+亚秒会话 1799 锁定/end<start 400 COMMON_003）
    - 事故: 尾部替换 marker 匹配错位吞掉 weekHourDistribution+两 record（107 行）→ 从 git HEAD 原文恢复（R18 精神：用 git show 取原文，未用 reset）——教训: 跨方法大段 marker 替换前后必须 diff 边界确认
    - 验证: 全量 1336/0（+3）+ jacoco 门禁 + spotless 全绿
    - 状态: ✅ 实施完成，待提交授权
- [2026-09-07] - TIME_OF_DAY 分桶语义对齐插件端（fix(stats)，v0.65.0）
    - 需求: ctt-web 报告 distribution?type=TIME_OF_DAY 与插件端不一致——服务端旧实现按会话开始小时整段入桶且边界 Morning 5-11/Daytime 12-16/Evening 17-21/Night 22-4，插件端逐桶切片且 Night 0-5/Morning 6-11/Daytime 12-17/Evening 18-23（与截图 TimeOfDayPanel 一致）；用户裁决选 (a) 服务端对齐插件 + 重叠会话按并集（最早→最晚）合并后切片（呼应 week-hour 合并裁决）；"总时间与统计概览不一致"根因=重叠双计 + 跨桶会话整段误归开始桶
    - 实现: TimeOfDay 枚举新边界 + boundaryAfter(hour) 切片辅助（Evening 边界 24=次日零点）+ StatsCalculator.timeOfDayDistribution（mergeOverlapping → 桶边界切片 → 桶累加，时长降序）+ StatsService TIME_OF_DAY 分支改调新方法（删旧 fromHour 累积路径）
    - 测试: StatsCalculatorTest DistributionTests +4（新边界四桶/跨桶切片 11-13→Morning+Daytime/重叠并集 3600 非 5100/跨午夜总和守恒）+ StatsIntegrationTest +1（11-13 + 23:50-00:10 两会话桶总和 8400 == summary.total 一致性断言——用户报告的核心问题）
    - 踩坑: ①Evening 边界 boundaryAfter=24，cursor.withHour(24) 抛 DateTimeException——需走次日零点分支（boundaryHour==24 特判）②测试断言 06:00-06:59 误写 3600（59 分钟=3540）③leaderboard NIGHT_OWL(22-5)/EARLY_BIRD(6-9) 与 achievement 窗口是独立语义不受影响（它们用 mergedDurationInDailyWindow/activeDaysInDailyWindow，本就合并+切片）
    - 文档: README Parameters 段——TIME_OF_DAY 切分语义 + 合并语义从 summary/heatmap/streaks 扩展到 TIME_OF_DAY（修正族述"accumulate raw durations for distributions"的范围）
    - 狩猎第二轮（用户实测仍差，先 1472s 后 2s）: ①1472s 根因=每切片 Duration.toSeconds() 丢亚秒尾差（插件写毫秒，3267/3460 会话亚秒起步 × ~0.45s）；修复=byBucket 累加全精度 Duration、单次截断 ②剩余 2s 根因=四桶各自独立 floor，小数余量 0.481/0.702/0.166/0.817 合计 2.17s 被 floor（用户复算逐桶全中）③修复=最大余数法配平（floor 后 leftover∈[0,n-1] 按余量降序 +1）——数学保证桶总和 == 全精度单次截断 == summary.total，对所有用户/时区/未来数据 100% 成立 ④教训: 我方探针两次假绿——SQL 导出 ::bigint 已截亚秒（探针有损）+ 整秒 fixture 踩不中亚秒路径；复刻必须用生产同精度数据
    - 测试补充: shouldNotLoseSubSeconds_whenSessionsStartOffSecond（红绿实跑：注入旧逻辑红/恢复绿）+ shouldApportionRemainder_soBucketSumEqualsTruncatedTotal（4 桶 .1/.2/.3/.9 尾差 → 桶和 14398 == 截断总量）
    - 验证: 全量 1333/0 + jacoco 门禁 + spotless 全绿
    - 状态: ✅ 实施完成，待提交授权
- [2026-09-06] - 依赖升级审计与补丁升级（v0.64.1）
    - 流程: 用户给定依赖升级策略（直接升到最新含主版本，仅 Java/Kotlin 大版本需确认；分层升级每层 clean build 验证）
    - 检测: ben-manes 0.61.0 报 outdated 仅 2 项且 available=None（元数据解析 bug），改用 Maven Central maven-metadata.xml 人工比对全部坐标兜底
    - 发现: ①flyway-database-postgresql 13.4.0 与 boot BOM 管理 flyway-core 12.4.0 版本错配（13 模块 POM 强依赖同版本 core）——测试全绿因恰好兼容，属隐性隐患 ②spotless/gjf 落后补丁 ③其余全部坐标已是最新（BOM 管理）④Gradle 9.7.1 最新 ⑤ben-manes com.github 与 io.github 坐标 0.61.0 并行发布，迁移无版本收益不改
    - 升级（用户裁定 flyway 双升覆盖 BOM）: spotless 8.10.1→8.10.2 + google-java-format 1.35.0→1.36.1 + flyway-database-postgresql 13.4.0→13.5.0 + flyway-core 显式 strictly(13.5.0) 覆盖 BOM 12.4.0
    - 验证: clean build 全绿 + 全量 1326/0（零回归）+ spotlessCheck 通过（gjf 新版本无格式漂移）+ dependencyUpdates outdated=0
    - 教训: ben-manes 对部分坐标报 available=None 不可信，关键坐标用 Maven Central 元数据人工核实；flyway 双模块必须同版本（db 模块 POM parent.version 强绑定）
    - 状态: ✅ 完成，待提交授权
- [2026-09-03] - Hourly 端点日期范围过滤（GET /stats/hourly?start&end，v0.64.0）
    - 需求: ctt-web 提案——Dashboard 顶部筛选器日期区间已实现但前端丢弃 start/end（后端不支持），hourly 图表始终全量数据与筛选器语义冲突
    - 设计决策: ①提取 StatsCalculator.clipToWindow 共享裁剪 helper（消除上轮审查标记的 Duplicated Code——weekHour 的内联单边界裁剪与 both-bounds clipTo 分支收敛为一个 helper），weekHourDistribution 同步改用 ②hourlyDistribution 加 windowStart/windowEnd 参数，activeDays=窗口内活跃天数（需求备注明确选择"过滤范围内的活跃天数"）③校验与 weekHour 一致（end<start → 400 COMMON_003）④向后兼容：不传参数全量历史
    - 实现: StatsCalculator.clipToWindow + hourlyDistribution 扩展 + StatsService.hourly 5 参（start/end/filter）+ StatsController /hourly 加 start/end @Parameter（同 week-hour 模式，@Operation 描述补日期语义）
    - 测试: StatsCalculatorTest +2（窗口裁剪 activeDays=窗口内/单边界回归）+ StatsServiceTest +2（裁剪委托/end<start 400）+ StatsIntegrationTest +2（跨 2025/2026 会话 start=2026 过滤后 activeDays=1 且 unfiltered=2 对比/end<start 400 COMMON_003）
    - 踩坑: 集成断言初版把 2h 会话写成 hour10=7200——hourly 是 per-hour 平均，2h 跨 hour10+hour11 各 3600
    - 验证: 全量 1324/0（+4）+ jacoco 门禁 + spotless 全绿
    - 验证: 全量 1332/0（+4 截断修复后）+ jacoco + spotless 全绿
    - 状态: ✅ 实施完成，待提交授权
- [2026-09-03] - Weekly Coding Activity by Hour 端点（GET /stats/week-hour，v0.63.0）
    - 需求: 前端渲染 7x24 交叉热力图，需 weekday x hour 平均秒 + 可被 dashboard 日期区间（?start&end）控制；对齐插件端 DailyHourDataProvider 口径（R3 已读源码：逐小时切片 + weekdayCount 除数字典）
    - 设计决策: ①切片复用 hourlyDistribution 的逐小时循环模式，仅加 weekday 维度（slice start 落桶）②除数=窗口内每个星期几出现的天数（非活跃天数）——weekdayCounts 字典随响应返回供前端复核 ③只返回有数据的格子（前端补零渲染）④窗口为空 → 聚合全史（对齐插件端 determineTimeRange 回退 min/max）⑤窗口裁剪用既有 clipTo 模式（首实现漏裁剪被自写测试抓出——08-25 会话漏进 09-01..09-07 窗口，修后 clipTo/半开边界 fallback）
    - 实现: StatsCalculator.weekHourDistribution + WeekHourPoint/WeekHourDistribution record + StatsService.weekHour（end<start 抛 COMMON_003）+ StatsController GET /week-hour（READ + 60/60，timezoneOffset/start/end/deviceId/ideName 全参数，@ApiResponses 200/401/403/404/429——与 heatmap 等同参数端点一致不列 400）
    - 测试: StatsCalculatorTest WeekHourTests +5（跨小时切片 3 格 6300s/时区平移 weekday+hour 漂移/同星期重复平均=7200/2/窗口裁剪+全窗口计数/空会话）+ StatsServiceTest +3（裁剪委托/空/双过滤 400）+ StatsIntegrationTest +2（区间裁剪 HTTP 全链路 6300s/设备过滤+互斥 400）
    - 踩坑: ①需求验收示例自相矛盾（说按逐小时切片却断言"两条 points"——10:30-12:15 实为 3 格 1800+3600+900=6300，测试按算法口径写）②集成测试 origin_device_id FK 违约——裸 UUID 不在 devices 表，须先 POST /devices 注册（registerDevice helper 已有，新测试直接调用）③Edit 工具损坏第 5/6 次（import 重复/RecentSessionResponse 被吞/README 段落错位）→ python 行级修复
    - 验证: 全量 1317/0（+10）+ jacoco 门禁 + spotless 全绿
    - 双轴审查修复（reviewer x2 并行）: ①P1 真实 bug——单边界窗口（只给 start 或只给 end）裁剪时 new TimeInterval 在 filter 前构造，会话整体在窗外（start>=end）抛 IAE→400 COMMON_001（TimeInterval 紧凑构造器拒绝 start>=end），修复=先判 start.isBefore(end) 再构造、空则丢弃 + 回归测试锁定（shouldClipSingleBound_whenOnlyStartGiven）②README /stats/recent 行被编辑连带删除（端点仍存活）→ 恢复 ③WeekHourDistributionResponse 字段补 @Schema example（R9）④判断性保留: 逐小时切片循环与 hourlyDistribution 重复（Fowler Duplicated Code，可提取共享 helper）——两热力图锁步演进风险记录，暂不重构（改动面/收益比不划算，留待第三个同形状出现）
    - 审查后验证: 全量 1318/0 + jacoco + spotless 全绿
    - 用户裁决+修正: 重叠会话语义——需求"对齐插件端 DailyHourDataProvider"字面落地为原始累加，但用户判定"累加就是 bug，很明显的逻辑错误"（并行窗口双计）。修正=切片前先 mergeOverlapping（并集=最早 start 到最晚 end，一行复用既有 helper），跨日/跨小时截断语义不变；新增 calculator 重叠合并测试（10:00-11:00 + 10:30-10:45 → hour10 计 3600s 非 3900s）。已出插件端 bug 报告：插件 getDailyHourDistribution/fetchDailyHourlyData 无 merge，同样双计
    - 审查后验证: 全量 1319/0 + jacoco + spotless 全绿
    - 状态: ✅ 实施+双轴审查+修复完成，待提交授权

- [2026-09-02] - Pull 分页实施（hasMore + ctt.sync.pull-batch-size，v0.62.0）
    - 需求: 用户指出 push 方向已优化（插件端 500/批 + 服务端多行 INSERT）但 pull 反方向缺失——新设备同账号服务端有大量数据时一次全量下发（实测 3198 条 ~1MB），应分页
    - 设计决策: ①服务端截断而非客户端循环（客户端对无 LIMIT 响应循环无意义）②fetch LIMIT+1 模式——取 batchSize+1 条判定 hasMore 后裁剪，单查询同时回答"本页"与"是否还有"，无需 count 二次查询 ③batch 可配置（新 SyncProperties record，@ConfigurationProperties ctt.sync.pull-batch-size 默认 1000，对齐 SecurityProperties/CttMailProperties 模式；集成测试 @TestPropertySource 注 5 真实 HTTP 分页验证）④兼容性三方组合全验证：新服务端+旧插件（旧端拿前 N 条推进游标下次续拉，不丢只慢）✓ 旧服务端+新插件（hasMore 缺失=false 退化一次性）✓
    - 实现: SessionChangeRepository.findAllByChangeIdGreaterThanAndUserIdOrderByChangeIdAsc 重载加 Limit 参数（Spring Data 3.2+ 原生支持）+ SyncPullService 双构造器（@Autowired 5 参注入 SyncProperties / package-private 6 参 int 供测试，对齐 LeaderboardService Clock 先例）+ doPull fetch+1 裁剪 + SyncPullResponse 加 hasMore 字段（@Schema 带循环语义说明）
    - 测试: SyncPullServiceTest +2（超批次截断 hasMore=true 游标=本页末/尾页 hasMore=false）+ 既有 4 处 stub 迁移三参 Limit 变体（eq(cursor),eq(userId),any()）+ SyncPullPagingIntegrationTest 新建（batch=5 推 12 会话 → 3 页拉完 hasMore 终止/无重复/升序/游标单调到 pushCursor）+ SyncControllerMockMvcTest 构造器适配
    - 踩坑: ①Edit 工具三次损坏文件（repository 吞签名行、service 重复 import/吞 }、测试文本块重复行）→ python 行级修复+重读验证（既有记录第 4 次）②application.yaml sync: 块先插错到 security: 内部（cors/oauth 之间）→ python 重定位到 ctt: 直接子节点 mail: 前 ③UserRegisterRequest 第 4 字段是 termsVersion 非 clientVersion、LoginRequest 必填 deviceId 非 deviceName——新建集成测试直接复用 SyncIntegrationTest 的 DTO 构造器模式更稳
    - 文档: dev-docs/sync/frontend-integration.md（响应示例 hasMore + 字段表 + 游标语义两条：分页循环/旧客户端兼容）+ README Sync Engine pull 段
    - 插件端（未实施，用户专人负责）: SyncPullResponse 加 hasMore 字段 + SyncCoordinator 两处 pull 改循环（apply→持久化游标→while hasMore）+ SessionRepository.upsertSyncedSessions 批量化（JDBC batch 单事务，applier 逐条 upsert 每行一次事务 fsync 是分钟级瓶颈）+ applier 失败改抛出（吞异常+游标推进=丢行，批量原子性+游标未推进=零丢失）；已交付交接报告
    - 状态: ✅ 实施+全量 1307/0 + jacoco 门禁 + spotless 全绿，待提交授权
- [2026-09-01] - 热力图年份列表端点（GET /heatmap-years，v0.61.0）
    - 需求: ctt-web 提案——Dashboard 热力图"按年查看"需要年份下拉选项；前端无法自推导（拉全量热力图不现实），需轻量端点
    - 设计决策: 数据源=coding_sessions 而非 daily_stats 物化表（物化惰性自举，冷启动用户物化表空但 sessions 有历史；idx_sessions_user_time (user_id, start_time, end_time) 部分索引直接支撑 distinct year 查询）；有效性规则沿用 StatsCalculator 的 start_time < end_time（零时长会话不计入年份），保证年份列表与聚合口径不分裂；倒序返回
    - 实现: CodingSessionRepository.findDistinctYearsByUserIdAndIsDeletedFalse（原生 @Query EXTRACT(YEAR) + 非删除 + start<end）+ StatsService.heatmapYears（descending）+ StatsController GET /heatmap-years（READ + 60/60，对齐 ide-filters 模式）
    - 测试: StatsServiceTest +2（降序/空）+ StatsIntegrationTest +1（真实 push 2026+2025 两会话 → [2026,2025]）
    - 踩坑: Controller 端点插入位置再次触发 @Operation 重复注解（anchor 匹配到 recent 的 @GetMapping 前，新端点 @Operation 叠在 recent 的 @Operation 后）——同 ide-filters 先例，脚本移除块后插到 recent 方法之后修复；第三次同类教训，考虑后续插入端点先定位方法尾
    - 提交: ✅ 2026-09-02 原子提交完成（feat 2ba33cb → fix(test) 2b71510 → docs 01fce54 → bump 0.61.0 → memory）
    - 补充: 提交前全量验证发现既有日期敏感测试失效——streaksShouldReadActiveDays_whenUtcAndBootstrapped 用固定日期 2026-08-29..31，currentStreak 要求最新活跃日是今天/昨天，2026-09-02 起 current=0（git stash 验证 HEAD 也失败，非本次回归）；修复=按同文件 summary 测试惯例锚定 LocalDate.now() 三连天，修复后 StatsServiceTest+StatsIntegrationTest 全绿
- [2026-09-01] - 统计 IDE 过滤实施（ideName 参数 + ide-filters 端点，v0.60.0）
    - 需求: ctt-web 提案——统计接口支持 IDE 维度过滤（方案 1+2，方案 3 协议扩展明确拒绝）；评估确认 Unknown IDE 在任何过滤下排除、按注册表精确匹配、ideName 未匹配任何设备 404、与 deviceId 同传 400
    - 设计: SessionFilter record（deviceId/ideName 二选一，互斥抛 ValidationException COMMON_003——项目惯例对齐 LeaderboardService）收敛过滤器参数消除 Data Clumps；canUseMaterializedDays 泛化（过滤请求回退实时聚合）；sessionsOfIde 按注册表 ide_name 精确匹配解析设备集 → 新增 repository IN 查询；ideFilters() 返回 distinct 非空 ide_name 排序（revoked 设备保留、Unknown 桶永不列出）
    - 实现: StatsService 6 方法签名 UUID deviceId → SessionFilter + ideFilters() + Controller 6 端点加 ideName @Parameter + 新 GET /ide-filters + CodingSessionRepository.findAllByUserIdAndOriginDeviceIdInAndIsDeletedFalse
    - 测试: StatsServiceTest +4（IDE 过滤匹配/无匹配 404/ideFilters distinct 排序）+ 集成 +1（ide-filters 列表/ideName 过滤合并语义/未知 404/双参数 400）+ 既有物化 summary 测试日期缺陷修复（今天周二撞"今天=周一"假设——用 LocalDate.now() 锚定数据 + 周期断言改不变量范围 [1800,12600]，DEBUG 排查确认 stub 命中但 thisMonth 跨月截断）
    - 踩坑: ①Controller ide-filters 插入错位致 @Operation 重复注解（两次脚本重排块位置）②SessionFilter 嵌套类型 import 需全限定 StatsService.SessionFilter ③物化 summary 测试在非周一跑红是既有时间假设缺陷（git stash 验证 HEAD 也失败，非本次回归）④集成断言重叠会话合并=3600 非 7200（pushSession 固定同一 1h 窗口）
    - 状态: ✅ 实施+全量 1301/0 + spotless 全绿，待提交授权
- [2026-09-01] - 用户纠正：未授权提交（R23 固化）+ memory-bank 冷热分层（R13 重写）
    - 纠正: 修复审查发现后自行 commit+push（把「需要修」当成了提交授权）——R6 授权边界误判，已固化 R23「修复≠提交」：修复完成报告后必须停，等当次交互的明确提交指令；本次 7edf735/c066f54 不回滚，下不为例
    - 冷热分层: memory-bank/archive/ 按月分片归档冷数据（修剪=归档而非删除）；activeContext.md 1598→243 行（27 热条目 + 归档指针），150 冷条目入 5 个月度 shard（2026-03..07），完整性校验通过（1622 = 1598 + 6 shard 头）；R13 重写为归档制（禁止直接删除、shard 只写不改、完整性校验步骤）
---

> **归档**：更早的条目已按月归档至 `memory-bank/archive/activeContext-YYYY-MM.md`（R13 冷数据），本文件只保留最近 30 天热条目。
