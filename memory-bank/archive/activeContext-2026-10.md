# Active Context 归档 · 2026-10

> 冷数据归档（R13）。仅供回溯，不再更新。

> 2026-11-07：热文件达 200 行上限，把 2026-10-12 的两条完整移入本 shard（零改动迁移）。



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
