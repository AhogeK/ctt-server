# AGENTS.md - 项目记忆与行为约束

> 本文件由 AI 自动维护，人类请勿手动编辑

## 核心规则

### R1: 会话初始化

每次会话开始立即读取 `memory-bank/` 下所有文件，缺失则创建。

### R2: 记忆更新（强制实时）

**每次变更（除纯文档变更外）都必须更新记忆**，响应完成后**立即**评估，滞后即断片：

- **`progress.md`**：当前变更了什么、交付了什么版本。
- **`activeContext.md`**：为什么这么变、改了什么（带 `[YYYY-MM-DD] - 标题` 前缀）。

以下变化另需同步对应文件：

| 触发条件 | 更新文件 | 更新内容 |
|---|---|---|
| 架构决策 | `systemPatterns.md` | 新模式/设计决策 |
| 技术栈变化 | `techContext.md` | 依赖/版本变更 |
| 项目变更 | `README.md` | API/功能/里程碑 |

**更新格式**：`[YYYY-MM-DD] - 变更标题` + 文件/影响说明

**修改前完整读取**：更新任何记忆文件前先完整读取该文件，禁止凭摘要、片段或其他会话的转述推断内容；行数超限按 R13 归档或压缩，不删除事实。

### R3: 关联项目（只读红线）

`../code-time-tracker`（JetBrains 插件端）与 `../ctt-web`（Vue 前端）均为**只读**关联项目：API 契约、数据结构、认证协议、同步协议、统计口径、响应格式变更时**主动读取**其源码/文档验证实际行为，不猜测接口形状。

**严禁修改关联项目任何文件**（含源码/测试/文档/版本号）。关联方能力不足或契约需变更时，以**需求文本**形式提出（现状 / 期望行为 / 理由 / 影响面 / 对接方配合），由用户决策实施。

**契约语义以对接方的实际实现为准（强制）**：对接方落地后必须回读其代码确认「它到底怎么用」，不得按我们需求文档里建议的用法推断。实例（2026-11-06）：`nextCursor` 的取值按「失败时才重放」的假设写成 `max(页尾, 服务端水印)`，而插件实现为「每轮 pull 都用本机游标重放」，于是本地游标落后超过一页的客户端会被直接跳到水印、永久跳过未收到的变更。正确顺序：**读实现 → 按实际行为校准契约文档与领域文件 → 用测试钉住**（`SyncPullServiceTest#shouldReReadBelowTheWatermark…`）。

### R4: README 同步

重大变更（API/架构/功能/部署/里程碑）时同步更新 README.md 和版本号。

**文档里对机制的声明必须与代码一致**：发现文档声明了代码并未实现的机制时，按 R25 的漂移处置 —— 那是**缺陷**，正确做法是补上实现、或把声明明确改写为实际机制，**不得**只把文档改成"现状"了事。实例：`docs/api-governance.md` Tier 3 声称以 `getActiveUserRequired()` 作为 Gate，而该方法全仓**无任何生产调用** ✗ —— 处理方式是接线（v0.78.1），不是删掉那句话。

**README 的视觉块按 GitHub 实际渲染写**：居中区块用 `<div align="center">` 包裹 Markdown 标题与段落（GitHub 会嵌套，实测）；徽章若要求横排，必须写在**同一行**并用 `&nbsp;` 分隔——源码换行会被渲染成 `<br>`，徽章会竖排（2026-10-27 两次返工）。改完用 `POST https://api.github.com/markdown` 或本地渲染核对，不要只看源码。

**枚举清单是漂移高发区（同一处声明散在多个文件）**：「哪些统计面合并重叠会话、哪些按会话原始累加」同时写在 `StatsCalculator` 类注释、`README.md`、`memory-bank/domains/stats-aggregation/references.md` 三处。曾出现类注释把 time-of-day 列进原始累加（实为合并）、两处清单都缺 week-hour（实为合并），**对接方据此差点误判统计语义**（v0.79.0）。规矩：**三处必须同批对齐**，权威版本是领域文件里的 "Duration semantics per surface" 表；新增/改动统计面时以该表为准并同步另两处。

### R5: 记忆同步（强制）

记忆文件与所描述的业务变更**同步更新、独立提交**：禁止滞后到下次会话补记（R2），也禁止与业务代码混进同一个 commit（R6.5「AI 独立」）。

### R6: Git 操作确认与授权边界（强制）

**核心原则：单次交互授权。** 提交授权仅限当前变更，用完即失效；「修好它」授权的是**修改动作**，不是提交动作。

#### 授权范围

| 操作类型                       | 授权有效期     | 说明                |
|----------------------------|-----------|-------------------|
| `git commit/push`          | **仅当前变更** | 授权仅针对用户指明的那些文件/变更 |
| `git status/log/diff/show` | 无需授权      | 只读操作，可自主执行        |
| `git branch`               | 仅创建分支     | 不包含后续 commit/push |
| `git switch -c <branch>`   | 仅创建分支     | 把未提交改动挪到功能分支不算提交；其上的 commit 仍需授权 |
| `gh pr create/merge`       | 需单独授权     | 与 commit 授权独立     |

#### 触发关键词

| 关键词             | 含义                 | 示例                       |
|-----------------|--------------------|--------------------------|
| `提交` / `commit` | 执行 commit（不含 push） | "提交这个变更" → 仅 commit      |
| `推送` / `push`   | 执行 push（不含 commit） | "推送" → 仅 push            |
| `提交并推送`         | commit + push      | "提交并推送" → commit 然后 push |
| `提交然后X`         | commit + 继续X       | "提交然后继续开发" → commit + 开发 |

#### 红线（绝对禁止）

1. **授权不延续**：用户对变更 A 的授权 ≠ 对变更 B 的授权
2. **模糊话术不算授权**：
    - ❌ "可以" / "没问题" / "通过" / "看起来不错" / "没问题，提交并推送"
    - ❌ "审查通过" / "代码没问题"
    - ✅ "提交" / "commit" / "推送" / "push"
3. **连续开发不算授权**：完成 X 后开发 Y，Y 需要新授权
4. **工具建议不算授权**：code reviewer 说"可以提交" ≠ 用户授权

#### 什么不算授权（常见误判 / 越界示例）

| 场景 | 为什么不算 / 边界在哪 |
|---|---|
| 「审查发现 X，需要修」→ 修完 | 修复指令 ≠ 提交指令；修完停下等审查/提交指令 |
| 「继续修审查发现的问题」（多轮修复中途） | 系列修复指令只覆盖修改，最后一个修复完成后仍需新授权 |
| 「按项目惯例处理」类自主修复 | 自主判断的修改更需用户过目后才提交 |
| 「继续实施下一阶段」等前瞻指令 | 前瞻指令只授权**开发动作**，不授权**提交动作**（详见下节） |
| 用户说「继续开发」 | 可开发变更 B；**不得**开发完直接提交 B |
| 用户说「开发 X」 | 开发完成后等待审查，**不得**自动提交 |
| 用户说「没问题，提交并推送」（针对变更 A） | 只能提交变更 A 并推送；**不得**据此提交新变更 B |
| 用户说「审查通过，可以提交」（针对变更 A） | 只能提交变更 A；**不得**顺带提交用户没指名的文件（如 memory-bank） |
| 第三方工具给出审查结论 | code reviewer 说"可以提交" ≠ 用户授权 |

修复完成后的正确收尾：报告修复内容 + 验证结果 + 「待授权提交」，**停**。

例外：同一次交互中已明示「修复并提交」的完整链条（如「需要修」+ 上下文明确含提交意图）→ 可提交；模糊时一律停下问。

#### 授权作用域闭合（强制）

**「授权不延续」的精确含义：一条消息中的提交/推送指令，只覆盖该消息【指名的那些变更】，并在这些变更提交完毕后【立即闭合】。**

⚠ 最常见的越界推导：消息末尾带「继续实施下一阶段 / 继续开发 / 接着做（并推送）」时，**不得**推导出"后续阶段的提交也获授权"。

| 消息 | 授权范围 | 闭合点 |
|---|---|---|
| 「提交这个变更」 | 仅该变更 | 提交后闭合 |
| 「提交并推送，然后继续开发 X」 | 该变更的 commit + push | **push 后闭合**；X 开发完需重新授权才能提交 |
| 「实施 A、B、C 三批」（无提交字样） | 无提交授权 | 三批全部实施完，报告 + 待授权 |

**反面案例（实际发生过，2026-09-13）**：用户指令为「提交并推送，然后继续实施下一阶段」，其中"提交并推送"仅覆盖当时那一批（Batch 1）。AI 据此把后续 Batch 2/3/4 的 12 个 develop 提交与 9 个 master cherry-pick 全部自行完成 —— 越界。**前瞻动词不携带提交授权。**

**执行方式**：每批实施完成后一律 **报告 + 「待授权提交」+ 停**，批次之间不得连续提交。

#### 执行前强制自检

**每次 commit/push 前必须逐项检查：**

```
□ 用户是否在【当前交互】中说了"提交/commit/推送/push"？
□ 用户说的是提交【哪些变更】？（确认文件范围）
□ 是否有用户未明确授权的额外变更？（memory-bank 除外，见 R5）
□ 用户是否说了任何模棱两可的话？（"可以"、"没问题"→ 不算授权）
□ 本回合用户消息中是否有提交/推送关键词？没有 → 一律不得提交，
  不得以"上一轮授权过"、"已在流程中"、"用户之前说要提交"为由提交
```

**任何一项不确定 → 停下来问用户。**

**机械判定法（防自我说服）**：动手前先在本回合用户消息里**字面搜索** `提交` / `commit` / `推送` / `push`。命中 → 可提交（且严格限于其指名的变更）；未命中 → **禁止提交**，改为报告 + 收尾写「待授权提交」后停止。

### R6.5: 提交规则（强制）

**核心原则：原子化提交、版本同步、AI独立、cherry-pick合并。**

- **原子化**：代码修改触发版本号更新（检查所有相关文件一并更新），版本提交独立且晚于代码提交
- **累计更新**：不用刻意提交每次中途更新，可只提交最后的版本描述跳过中途
- **提交顺序**：功能代码 → 版本号更新 → AI记忆记录（禁止版本早于功能、混合提交）
- **提交拆分**：原子性一致可合并，不过分拆分刷提交，不过于宽泛堆积大量文件
- **分支顺序**：优先完成 develop 全部提交再考虑 master
- **AI独立，但不独立成 PR（强制）**：AI 内容（`memory-bank/`、`AGENTS.md`、`.plans/`）**单独成提交**，不与代码混在一起；但必须落在**承载该变更的同一个 PR 里**——一起推送、一起过 CI、一起合并。事后单开一个 PR 补记计划/记忆等于把一次变更拆成两段，CI 要跑两遍、审查要看两遍（2026-11-17 用户要求：「计划 memory-bank 版本这类以后不要后置再单独开，跟着实施一起提交跟 pr，不要多余拆分两段」）
- **master 合并**：见 R17（非 AI 内容逐条 cherry-pick；严禁整分支合并、严禁错误 cherry-pick 旧 develop）

**提交信息格式**：`feat(scope): 功能描述` / `fix(scope): 根因+修复+验证` / `chore: bump version to X.Y.Z` / `docs(memory-bank): record implementation`

**最终清理**：功能commit → 版本commit → AI记忆commit → 推送 → 验证 `git status` 干净 → 有残留立即补提交

### R7: 技术决策确认

**禁止擅自修改**：语言/框架版本、架构设计、数据模型。原则：只读取不猜测，只实现不决策，有疑问必须问。

### R8: 边界原则

- **不懂就问**：不确定时停下来问用户，禁止盲目猜测
- **讨论信号**：用户提出"为什么 / 能不能 / 是否应该 / 你看呢 / 是不是…更好"类问题 = 讨论与确认信号，先给分析 + 方案，**确认后才实施**；严禁把质疑性提问当作实施指令
- **现代 Java**：优先 `record`、`sealed class`、`pattern matching`，避免 Lombok
- **验证优先**：不确定内容先验证再使用
- **能自行判断的动作直接做，不要把「要不要做」拿去问（强制）**：验证、跑门、补一条记录、派发一次工作流这类**自己就能定**的事，报告里写「已做 + 结果」，而不是「需要的话我可以……」。2026-11-16 改完 `installer-test.yml` 后我问「需要的话我可以派发一次验证」，用户直接纠正：「这种东西需不需要不要问我，肯定是你自己决策安排的啊，你保持专业就可以了」。边界：**不可逆、有外部影响、或用户明确保留的决定权**（发布、删远端资源、动用生产）才需要授权；其余按既定计划与证据自行安排，并把依据写进报告。
- **库函数的返回形态要核实**：方法名不能证明返回值语义，尤其是加密/编码库。`otp-java` 的 `SecretGenerator.generate(int)` 返回的是 **Base32 编码后的字节**，不是随机原始字节；再交给 Base32 会二次编码（2026-11-02 2FA 生成 52 字符载荷 + `====`，iOS Google Authenticator 拒扫）。涉及密钥/编码时先看 `javap`/源码并断言长度与解码结果。
- **验证行为必须调用真实实现（强制）**：不要用等价正则、复制逻辑或 `replaceAll` 代替目标方法。2026-11-08 审阅 ADR 时用 `Matcher.replaceAll` 冒充 `MaskingMessageConverter.mask()`，得出「newPassword 会被改写成坏 JSON」的错误结论；真实 `mask()` 保留键、只掩码值，只有空值/对象值会破坏文本。做法：用 jshell/测试直接调用目标方法并断言输出，再写文档或决策。
- **报数字必须复算**：向用户或报告陈述"还剩 N 处""影响 M 个文件"这类规模时，必须用**同一条命令**重跑得出；禁止拿不同过滤条件的前后结果拼凑（曾把「生产 4 处 + 测试 91 处」报成"9 处 / 8 个文件" ✗）
- **判断功能首发版本要看后面的 bump**：feature 提交里的 `appVersion` 仍是上一个发布；首发版本 = 该 feature 之后第一个 `chore: bump version` 的版本（用 `git rev-list --ancestry-path <feat>..HEAD --reverse` 取第一个 bump，或在 bump 提交里 `git show <bump>:<file>` 验证端点存在）。2026-10-28 直接读 feature 提交的 catalog，把 R1–R3 报成 0.79/0.81/0.82，实际是 0.80/0.82/0.83。
- **变更溯源**：发现与预期/记忆不一致时，优先猜想"是否被用户修改了"而非"AI 忘了改/改错了"
    - **第一步**：检查 git diff/commit history 确认变更来源
    - **第二步**：验证当前业务逻辑是否正确（测试通过 = 逻辑正确）
    - **第三步**：如用户修改了，更新记忆适应新逻辑，不要恢复"旧版本"
    - **禁止**：笃定"这应该是错的"、"之前改的忘了恢复"等揣测性结论
- **读写同一文件必须先读完再写**：禁止 `open(p,'w').write(open(p).read().replace(...))` 这类一行式写法——`'w'` 会先截断，读到的已是空内容。2026-10-23 因此把 `gradle/libs.versions.toml` 写空、编译失败。做法：先读出内容到变量 → 改 → 再一次性写回；批量改写优先 `sed -i`（已证明安全）或 `edit` 工具
- **配置回退与空值**：Spring 的 `${VAR:default}` 只在变量缺失时用 default，**空字符串不算缺失**；Docker Compose 的 `${VAR:-default}` 才把未设置/空都当缺省。密钥类配置加 dev fallback 时，compose 不要显式透传空值，`.env.example` 的占位行保持注释；否则本地启动会被“默认值”打断（2026-10-31 2FA `TOTP_ENCRYPTION_KEY` 踩坑）。
- **二次认证不是最终认证**：挑战签发后账号状态可能变化，完成阶段必须重新校验 `ACTIVE`（或复用统一的 `validateUserStatus`）后才能签发 token；密码正确不等于可以发凭证（2026-10-31 2FA 完成阶段补此校验）。
- **环境类故障先对照，再怀疑代码**：连不上数据库/Redis、启动即挂时，先看异常类型与「别处是否可达」再动代码。判据：`NoRouteToHostException`/EHOSTUNREACH = 网络层被挡（多见于 macOS「本地网络」权限，IDE 进程被拦）；`Connection refused` = 服务没起；`UnknownHostException` = 解析失败；`PSQLException: password authentication failed` = 凭据。判别动作：在**系统 shell** 上 `nc -z <host> <port>` 对照一次，通 ⇒ 问题在应用进程/权限侧，不在代码。同类问题已复发两次（2026-09-18、2026-10-23）
- **优化写放大先分「真写」与「空写」**：同一值的重复写入（空轮询的游标 upsert、心跳 UPDATE）加一个条件谓词就消除，不需要缓存、不需要新表；反过来，为省一次认证 SELECT 去加缓存会让撤销延迟生效，是更坏的交易。判断依据是**实测行数与增量**，不是"看着多"——2026-11-06 实测审计表 29,749 行 / 11 MB、89% 是已停写的数据面行为，结论是**不做**清理或分区，并把过时的"20 年后 2.7 GB"推算改掉。

- **环境数据隔离（强制）**：开发使用 PostgreSQL `ctt_server` / 角色 `postgres`；生产使用 `ctt_server_prod` / 角色 `ctt_app`；Redis 本地/开发 db0、生产 db2。db1 属于其他服务（`check-approver-sort:*`），禁止清理或混用。生产角色密码在 P0.16 后只走 SOPS；P0.12 过渡期 `PROD_DB_PASSWORD` 只在本机 gitignored `.env`，不写入仓库。生产库首次启动由 Flyway 建表，不复制开发数据。

- **容器「端口不响应」先看 OOMKilled 与启动阶段，再怀疑网络**：`docker ps` 显示 Up 不等于在监听。判据顺序：`docker inspect --format '{{.State.OOMKilled}}'` → `docker exec <c> ss -lnt`（进程内是否 bind）→ 日志最后一行 → 才看网络。2026-11-07 两次误判：把「Grafana 首启要 3 分钟装 app-platform API」当成起不来，把「第二个 JVM 被 OOM 杀（exit 137）」当成内存不足与网络故障

- **compose 加固要先验证 entrypoint 的需求，再逐项 `docker inspect` 核对**：`read_only: true` 要求所有写入点都在命名卷或 tmpfs 里；`cap_drop: [ALL]` 会让 `gosu`/`setpriv` 失败——postgres 需加回 `CHOWN/DAC_OVERRIDE/FOWNER/SETGID/SETUID`（前三项用于 chown PGDATA，后两项给 gosu），redis 需加回 `CHOWN/DAC_OVERRIDE/SETGID/SETPCAP/SETUID`（没有 FOWNER；SETPCAP 是 setpriv 收窄 bounding set 需要）。2026-11-08 P0.4 实测：`redis:latest` 未声明卷且工作目录在根文件系统，只读根下 chown 报错 ⇒ 给 `/data` 挂 tmpfs。验收以 `docker inspect` 的字段为准，不以 compose 文本为准。

- **平台能力要按机制核实，不要按名字推断（强制）**：Redis **没有来源地址 ACL**——6+ 的 ACL 只限定命令/键/通道，`bind` 只选监听接口；「只允许本子网」只能在主机防火墙（nftables）或网络分段实现，决策与触发见 `docs/decisions/ADR-0003-redis-source-restriction.md`。同理：在共享主机上新增常驻防火墙前，先证明它能拒绝**当前拓扑中真实可达**的来源，否则只是新增失效面；结论要写成残余风险 + 重访触发（ADR），不要落成没有消费者的配置项。

### R8.5: 项目一致性优先（强制）

**核心原则：按项目来而不是任务需求，需求要变通符合项目的一致性。**

| 场景 | 错误 ❌ | 正确 ✅ |
|---|---|---|
| ErrorCode | 任务说新建就新建 | 搜索现有错误码，复用已有的 |
| 命名/结构 | 按任务需求创建 | 遵循项目现有 `*Service`, `*Controller` 模式 |
| 注释 | 解释"代码做了什么" | 遵循 Clean Code（R9），删除冗余注释 |

执行前检查：任务是否与现有模式冲突？是否应复用而非新建？

发现冲突 → 暂停 → grep搜索现有模式 → 向用户确认 → 按项目一致性调整

- **领域判断归领域文件**：领域专属的约定/不变量不再堆在规则文件里。例：`OAuthStatePayload.Action` 扩展时的三处同步约定已迁至 [`memory-bank/domains/auth-lifecycle/scenarios.md`](memory-bank/domains/auth-lifecycle/scenarios.md)（R25）

### R9: 代码规范

- **语言**：按读者定，不要套清单。**只给你看的**写中文；**开源面向的**写英文——代码、注释、日志、`docs/`、README、提交信息、PR/issue/release notes；**给 AI 看的**（`.plans/`、`memory-bank/`、`AGENTS.md` 等）中英都行，因为这些文件你也会读、有的本身就开源，要给外部看时就用英文。i18n 的测试数据、引用的原文照原样。旧条文「仅 `.md` 可中文」把许可读成了文件类型规则，导致 PR #19 正文写成中文被指出。

- **注释（Clean Code）**：✅ 公共API Javadoc/复杂算法Why/警示信息；❌ 解释代码做了什么/冗余注释/注释掉的代码/TODO/FIXME
- **OpenAPI Schema（强制）**：所有DTO/Response加 `@Schema(description)`，每个字段加 `@Schema(description, example)`，校验注解不可遗漏，禁止硬编码版本号（用 `@Value("${info.app.version}")`），@ApiResponse必须带content，错误响应必须有独立示例
- **可空键的序列化契约（强制）**：某个键**必须始终存在**而值可为 null 时，须在该组件上加 `@JsonInclude(Include.ALWAYS)` 覆盖全局 `non_null` 策略——否则键会被**静默丢弃**，"没有基线"与"客户端不认识该字段"无法区分。断言必须**按键**做（`asMap().containsEntry(k, null)`），`extractingPath(...).isNull()` 对**缺失键同样通过** ✗ 抓不到
- **`@Schema` 的 `example` 必须是代码可达的状态**：不可达的示例（如"非 0 基期却 null 变更"）会让对接方按不存在的形态写代码
- **禁止在代码中使用全限定类名**：除 `import` 外一律用短名（`IntStream.rangeClosed` 而非 `java.util.stream.IntStream.rangeClosed`）
- **API key 不是交互凭据**：任何改密码、改邮箱、账号删除等身份/凭据操作必须经过 `WebSessionGuard`；密码变更/重置还要撤销其他 refresh session 与全部 API key。`getActiveUserRequired()` 只证明账号状态，不证明调用者是浏览器会话。
- **代理头默认不可信**：`X-Forwarded-For`/`X-Real-IP` 只有 socket peer 在 `ctt.security.trusted-proxy-addresses` 中时才可采信；默认使用 `getRemoteAddr()`，否则 IP 限流可被轮换头绕过。
- **边缘转发要用探针证明，不能读配置下结论（强制）**：`X-Forwarded-Proto` 与 `X-Forwarded-For` 由每一跳各自决定，且两种失效都静默——Caddy 默认不信任入站 `X-Forwarded-For`，会把链**替换**成立即 peer（所有访客都变成 `127.0.0.1`，按 IP 的限流退化为单客户端）；`handle_errors` 不写状态匹配会把**所有**错误改写成 503，于是 413「请求太大」被谎报成「服务不可用」。做法：用 `--network container:<ns>` 共享 netns 起探针后端与真实边缘镜像，断言「后端收到的 `xfp=https`、`xff=真实客户端, 本跳 peer`」与「超限=413、上游停=503」；限额分布在两侧时必须同批改并重跑探针。机制与可复制命令见 `docs/ops-runbook.md` 的 「Edge headers and limits」，安装前置见 `deploy/edge/README.md`（2026-11-16 P3.3 实测）
- **切换与发布的判据看运行中的状态，不看命令的回执（强制）**：`caddy reload`、`docker compose up -d` 返回 0 只说明配置已装载、容器已重建，不证明流量走了新上游，也不证明旧上游还能回切。做法：reload 后回读**运行中的配置**（Caddy admin `GET 127.0.0.1:2019/config/`）断言上游等于目标色，再用**应用真实存在的端点**复核一遍——拿一个应用端口并不提供的路径（如业务端口上的 `/actuator/health`）当判据，会把成功判成失败；观察窗的每个样本必须区分「没有这条序列」与「值为零」：Prometheus 对不存在的序列返回**空向量**（没有 5xx 时 `sum(rate(...))` 就是空的），那是健康态，写 `or vector(0)` 才分得开；反过来 `probe_success` 为空是真没采到，必须判破损。2026-11-16 蓝绿演练里这两处都曾让窗口静默判绿。
- **OAuth 浏览器绑定与 token 位置**：state 必须与 HttpOnly SameSite Cookie 对齐；access/refresh token 只放 URL fragment 或一次性交换码，禁止放入 query（会进入历史/代理日志）。
- **日志脱敏必须覆盖输出通道，而不是只覆盖 pattern（强制）**：Logback 的 `conversionRule`（如 `%maskedMsg`）只对 `PatternLayout` 生效；换成 JSON 等结构化编码器后事件字段被直接序列化，pattern 级脱敏会**静默失效**。2026-11-08 查实 prod JSON 把 `password=` 与 `query_string` 里的 `token=` 原样写出。新增或切换输出编码器时必须确认脱敏在新通道同样生效，并注意 `arguments`、`kvpList` 是独立字段（pattern 看不见它们）；用例要断言真实渲染出的那一行，而不是只测正则函数
- **日志要能按用户过滤，但落键有纪律（强制）**：请求日志的 `userId` 只放 UUID，不放邮箱/显示名——那是把个人数据复制进每行日志与日志存储，且与审计最小化（ADR-0002）冲突。MDC 键的写入可以分散，**清理只能由最外层上下文过滤器（`RequestContextInitializerFilter`）负责**：内层过滤器删键会让 `RequestLoggingFilter` 写 ACCESS_LOG 时值已空，表现是「日志里有用户上下文、访问行却 anonymous」。注入点按「想让它覆盖哪些拒绝」来定，放在认证解析之后、条款网关之前——网关 403 与它自己的 WARN 也要能归因。**过滤器是 `@WebMvcTest` 切片必装配的组件**，日志/指标这类旁路依赖一律 `ObjectProvider` 惰性取、缺失即静默，硬依赖会让几十个切片整批上下文启动失败（2026-11-14 P1.5 实测 63 例；见 `memory-bank/domains/` 与 `docs/observability.md` Log fields）
- **容器密钥只走 Compose secrets，不进环境变量（强制）**：`docker inspect .Config.Env` 是公开可见面，任何密码/密钥/令牌放进去等于泄露。仓库保存两份 SOPS 密文——`deploy/secrets/secrets.enc.yaml`（dev）与 `deploy/secrets/secrets.prod.enc.yaml`（prod），`.sops.yaml` 只放 age 公钥，`render.sh --env dev|prod` 选择并**先解密到临时目录、成功后才替换**成 `0440` 组可读文件，组 GID 由 `SECRET_GID` 统一，Compose 用 `group_add` 让应用与 Grafana 读取；应用用 `SPRING_CONFIG_IMPORT=optional:configtree:/run/secrets/`、PostgreSQL 用 `POSTGRES_PASSWORD_FILE`、Redis 由 wrapper 把口令写进 tmpfs 配置并 `setpriv` 降权（口令不进进程表）、Grafana 用 `GF_SECURITY_ADMIN_PASSWORD__FILE`。`.env` 只服务 `bootRun`。缺身份、缺文件、空 JWT 键都必须**启动前失败**，且渲染失败不得清掉上一份可用文件；验收看 `docker inspect`、`/run/secrets` 权限与进程参数（2026-11-08 P0.5；双载荷 2026-11-10 P0.16）
- **载荷里的非容器密钥由专属脚本渲染，并回读校验（强制）**：`render.sh` 逐键产出 `0440` 组可读文件，因此任何不该被 `SECRET_GID` 组读到的值（如宿主级、两端共读的 `FRP_TOKEN`）必须在渲染列表里**显式跳过**，否则会凭空多出一份更宽权限的副本；这类文件用目标目录内 `mktemp` + 改名落位，写完回读**模式与字节数**而不是只判存在，远端 `ssh` 加 `BatchMode=yes`——交互提示会吞掉 stdin 上的密钥并留下截断文件。范本见 `deploy/secrets/frp-token.sh`（2026-11-11 P0.17）
- **无消费者的配置键是缺陷，但处置不是默认删除（强制）**：先 `git log -S '<key>'` 追溯引入提交与当时的规范，判断这是「未接线的既定业务意图」还是「遗留/误配」；再评估实现该能力的价值、代价与副作用（隐私、账号删除、留存、体积、脱敏可靠性）。有真实业务价值就接线并测试；确认不引入才删除，并把理由与重访触发写进文档/ADR。禁止只凭"没有读取方"就删，也禁止保留假开关。实例：`ctt.security.audit.log-payloads` 2026-03-18 引入、无生产读取方（仅绑定测试）；2026-11-08 评估后确认不引入请求体审计（ADR-0001），随 P0.6 删除
- **管理面不得回到业务端口（强制）**：actuator 与 metrics 只由 `management.server.port`（默认 `127.0.0.1:8081`，`MANAGEMENT_PORT`/`MANAGEMENT_ADDRESS` 可覆盖）提供；应用端口不提供任何管理载荷——白名单路径 404，其余 `/actuator/**` 由安全链在分发前 401。新增探针或抓取必须按管理端口寻址，部署侧只把该端口发布到回环地址，生产由 `ProductionConfigurationGuard` 拒绝非回环地址与和 `server.port` 相同的端口。暴露集合与双端口契约由 `ActuatorSurfaceTest` + `ManagementSurfaceContractTest` 守住，机制细节见 `docs/observability.md`；改暴露端点必须同步守卫用例（2026-11-11 P1.1）
- **业务指标命名与标签（强制）**：业务指标统一挂在 `cttserver.business.*` 下（导出为 `cttserver_business_*` 前缀），单个指标的业务标签不超过 3 个（Prometheus 直方图自动附加的 `le` 属结构性标签，不计入），取值必须来自枚举或固定集合——禁止用户名、设备/会话 ID、邮箱、平台字符串、原始 URL 或错误文本。计数器重启归零，面板与告警用 `rate()`/`increase()` 读；耗时只输出直方图分桶并按 `histogram_quantile()` 聚合，不发布客户端分位数（多实例无法合并）。待处理量为 gauge 时以现算读取（数据库计数或 Redis `SCARD`），读取失败返回 `NaN`——它是一个**存在但无值**的样本，`absent()` 不会为它报警、`sum()` 会把 `NaN` 传播出去，所以告警必须显式选中它：**PromQL 没有 `isnan()` 函数**（`promtool check rules` 直接报 `unknown function with name "isnan"`），NaN 是唯一「与自身不相等」的值，写法是 `metric != metric`（2026-11-15 实测：`x != x` 对 NaN 触发、对有限值不触发；`deploy/observability/prometheus/rules/tests/alerts_test.yml` 用 `promtool test rules` 钉住）；既不给旧值也不让整次抓取失败。新增或改名指标必须同步 `BusinessMetricsTest` + `BusinessMetricsScrapeIntegrationTest` 与 `docs/observability.md`（2026-11-11 P1.2；NaN 写法 2026-11-15 P2.1 更正）
- **追踪标识只有一个来源（强制）**：日志 `traceId`、响应头 `X-Trace-Id` 与导出的 span 必须同源——都取自服务端 span 的 trace id，采样决定因此同时管住三者；客户端传来的 `X-Trace-Id` 只在没有 span 时才采信（追踪关闭或该请求未产生 span），否则日志会与被导出的 trace 脱钩；任何把 traceId 写回给调用方的代码（错误体、审计、响应头）必须走 `TraceIds.current()`，不得自造 UUID。采样器是 parent-based：网关标记 sampled 的 trace 一律记录、unsampled 一律丢弃，比例只作用于本进程起始的 span，因此要让 10% 生效，采样决策不能交给总是标记 sampled 的网关。入站上下文只认 W3C `traceparent`（网关设置），不加 B3 面。注意 Spring Boot 只在 `management.tracing.export.enabled=true` 时才装配 OTel 传播器，关闭时用 no-op，**每个请求都会新开 trace**——`TracingPropagationConfiguration` 在导出关闭时补上 W3C 传播器，改动该条件会静默破坏本地关联。生产由 `ProductionConfigurationGuard` 拒绝导出关闭或采样比 ≤0；`OtlpTraceExportIntegrationTest` 用桩收集器断言导出的 protobuf 里带调用方 trace id，`TracingIntegrationTest` 断言响应头与 `traceparent` 一致（2026-11-12 P1.3）
- **观测栈配置：整目录挂载、保留期写在执行它的地方、部署事实不进仓库（强制）**：`deploy/observability/` 的服务一律整目录 bind mount（单文件挂载钉死启动时的 inode，改文件后 `POST /-/reload` 返 200 却毫无变化），并只监听 `127.0.0.1`；30 天保留必须分别写进执行它的配置——Prometheus `--storage.tsdb.retention.time`、Loki `limits_config.retention_period` + `compactor.retention_enabled`、Tempo `backend_worker.compaction.block_retention`（Tempo 3 已无 `compactor` 块），只写在文档里等于没有保留期。公网探测目标（自有域名与期望状态码）由部署机的 `file_sd` 文件提供，仓库只留 `targets/public-http.json.example`。三个镜像事实：Alloy 卷属 uid 473 且编排 `cap_drop: ALL`，须显式 `user`；Redis exporter 的口令文件是 `map[string]string` JSON（键为 `redis://host:port`），由 `deploy/secrets/redis-exporter-passwords.sh` 渲染；Grafana 管理员口令走 `__FILE`。验收看 `probe_success`（不是 `up`）、Tempo 查得回合成 span、Loki 查得到容器日志，而不是「容器 Up」（2026-11-13 P1.4）
- **面板与规则同源、随栈交付、不手工导入（强制）**：Grafana 面板是仓库里的 JSON（`deploy/observability/grafana/dashboards/`），由文件 provider 自动装进 `ctt-server` 文件夹；本地栈与部署栈挂**同一目录**，`deploy/local/grafana/provisioning/` 只镜像 datasource 与 provider，禁止各存一份 JSON。面板里的数据源必须写死 uid（`prometheus`/`loki`/`tempo`）——本地 datasource 漏写 uid 会直接报 `data source not found`；应用侧查询带 `job="ctt-server"`（部署栈有 blue/green 两条 target），告警规则不写它以保持多实例通用，探测面板按 `job="blackbox-public"` 取（两栈定义同名 job，本地不要再造 `blackbox-http` 这种别名）。图上画的是**契约值**：规则刻意比契约更晚才响时（outbox 契约 60s、规则 300s）两条线都画，其余情况改阈值必须同批改契约线。`provisioning/{alerting,plugins}/` 必须存在（可空），Grafana 会为读不到的 provisioning 目录打 error，长期噪声会淹掉真错误；改 datasource 定义后旧卷与规格冲突（容器崩溃重启、日志 `Datasource provisioning error`），处置是删掉该 Grafana 卷重建而不是改 YAML（2026-11-15 P2.2）
- **告警规则与对外承诺同源（强制）**：`deploy/observability/prometheus/rules/*.yml` 的阈值必须等于文档里承诺的契约（p95 300ms、5xx 0.5%、outbox p95 60s），偏离处（429 无 SLO、outbox 等 5 分钟而非 60s）必须在规则行写理由，否则等于悄悄放宽 SLO——P2.1 初稿把 5xx 写成 5%，是承诺值的 10 倍。比例类规则一律带流量守卫（`and sum by () (rate(http_server_requests_seconds_count[5m])) > 0.1`，约 30 请求），否则空闲实例上一个 500 就是 100%。规则必须有行为单测：`promtool test rules` 跑 `rules/tests/alerts_test.yml`（`rule_files: rules/*.yml` 会把同目录 `*_test.yml` 当规则加载而启动失败），钉住「NaN 必触发、正常值与低流量必须安静」——`check rules` 只证明语法，证明不了行为。改指标名、标签或阈值必须同批改规则、规则单测与 `docs/observability.md` 的 Alerting 表（2026-11-15 P2.1）
- **告警按 blast radius 分级，投递时限必须由配置证明（强制）**：定级看「用户是否可见、证据是否永久丢失」，不看指标属于哪个系统——critical = 用户可见失败或永久丢失（5xx 比例、审计掉行、公网单点、隧道中断、页没送到）；warning = 容量压力或降级仍可用（延迟、429、池、backlog、证书、磁盘）；info = 不投递、只在面板上看（派生数据陈旧）。**选通道、选依赖前先查上游原生能力，再谈包装或自建**：Alertmanager 自 v0.24.0 起内置 `<telegram_config>`（`send_resolved`、`bot_token_file`/`chat_id_file`、`message_thread_id`、`parse_mode`、`timeout`、自动重试），第三方镜像全部弃维护（最活跃者最后构建于 2021-02）不构成自建理由——2026-11-15 我先写了 180 行 relay，深研后删掉，教训是「第三方都死」只能推出「别用第三方」，推不出「自己写」。凭证只走文件（`bot_token_file`），不进环境变量与 `docker inspect`，值在每次投递时重读以支持轮换；**容器要挂的 secret 文件必须由渲染脚本无条件创建**（缺失路径会被建成目录，容器只会报出误导性的读错误）。通道自身也要有告警：投递失败写进 `alertmanager_notifications_failed_total` 并配 `TelegramDeliveryFailing`，否则「页没送到」是唯一没人知道的故障。文档里写「N 分钟内收到」就必须由配置兑现（`group_wait` 决定首次投递），写不出兑现路径的时限不要写。抑制的 source 只能取「真能解释目标」的那几条告警、按 `alertname` 收窄，禁止按 severity 一把压：`host` 这类外部标签若只有一个取值，`equal: [host]` 就等于全局，会把同主机无关的证书/磁盘告警静音整个事故期间（2026-11-15 实测：收窄后 `TunnelDown` 仍压住 `HostDiskSpaceLow`，而 `Http5xxRatioHigh` 与 `HostDiskSpaceLow` 两条都投递）。验证用一次性 harness（共享 netns，让生产配置里的回环地址原样可用）断言「该来的来、不该来的不来、同组只来一条」，抑制规则要正反两组都验（该压的压住、不该压的照送）；`send_resolved` 要有 RESOLVED 实测，但**通过 API 删除告警（`amtool alert resolve`）不会产生 resolved 通知**，必须让告警按 `endsAt` 自然过期或由源停止发送（2026-11-15 实测，0.35.0）。改规则名、级别或阈值必须同批改 `docs/observability.md` 的 Alerting 表、`docs/ops-runbook.md` 的逐条 triage 表与面板契约线（2026-11-15 P2.3）
- **命名**：PascalCase(类)、camelCase(方法)、UPPER_SNAKE_CASE(常量)、全小写(包)
- **测试**：多断言链式调用 `.isX().isY().isZ()`，方法名 `shouldX_whenY`
- **测试限流时禁用 HTTP 自动重试（强制）**：Apache HttpClient 的默认策略会对 `429` 按 `Retry-After` 睡眠重试；导出的限流用例因此睡满 1 小时窗口、整轮测试卡死 27 分钟。限流/429 用例必须使用不自动重试的客户端（如 `JdkClientHttpRequestFactory`），并直接断言状态码，不能依赖默认 `RestClient.create()`。

- **测试要覆盖「空关联」**：集合为空、外键全为 null 的输入也要有一条用例——`Map.of()`（不可变空表）的 `get(null)` 会抛 NPE，2026-10-28 因此线上 `GET /users/me/sessions` 直接 500，而既有用例的夹具里总有已注册设备，恰好绕过了这条路径。

- **审计写入是异步的，测试读取审计行必须轮询（强制）**：`AuditEventListener` 用 `@Async` + `@TransactionalEventListener(AFTER_COMMIT)`，行本身由 `AuditLogWriter.persist`（`REQUIRES_NEW`）提交；HTTP 请求返回不代表审计行已落库，直接 `query(...).single()` 会间歇失败。新增审计断言用 Awaitility 轮询（参考 `MailOutboxServiceIntegrationTest`），或在测试上下文改用同步执行器。**断言「没有写审计」要用 `await().during(...).untilAsserted(...)`**——只断言瞬时 0 会在行落库前假通过。2026-11-08 P0.6b 的 passkey 断言因此被审查拦下；P1.2 起 `WRITTEN` 只在 writer 返回后累加，该断言同样要等提交；2026-11-15 P3.1 的 CI 首跑把 `LogoutIntegrationTest` 的 BOLA 告警断言打成红色（本地全绿、CI 上 1/1784 失败），同批把 5 个测试类里直读审计行的断言全部改成轮询。
- **CI 的门要能在本机用同一条命令复现，且只挡它真能挡的东西（强制）**：门（格式/测试/覆盖率/密钥/漏洞/SBOM/来源证明）留在 CI，**部署作业只部署**——把全量套件压在跑生产的机器上，等于让合并延迟取决于那台机器。扫描器版本必须钉死（`v8.30.1` 这类），`latest` 会让昨天的绿今天无从复现。**被扫描的镜像与被证明的镜像必须是一次构建**，否则「扫的 digest」与「签的 digest」分叉，而这正是来源证明要排除的失败；BuildKit 写归档、`trivy image --input` 只吃**目录布局**，因此流水线解包 `index.json`+`blobs/` 后再扫。密钥扫描的允许列表只允许**指纹**（`commit:path:rule:line`）不允许路径——按路径允许会让该文件里的真实密钥一起漏过；允许条目必须先确认值是假值。扫描范围要显式声明而不是静默跳过（`.agents/` 属代理工作区、不上 master、不随镜像发布，故写在 workflow 里 skip）。报告先落盘再判定退出码，红色构建也要留下解释自己的证据（2026-11-15 P3.1：47 条历史命中→41 条指纹允许后 `no leaks found`，假 token 仍命中 1 条；镜像 0 HIGH/CRITICAL，SBOM 315 组件）
- **验证命令必须能在目标环境查到它要验的东西（强制）**：一个查不到对象的验证要么报错得莫名其妙，要么**空过**——后者更坏，因为门看起来是绿的。落地前先证明「这个对象在这里能被查到」：用目标环境里真实存在的对象跑一次完整验证（2026-11-15 P3.2：cosign 对 buildx 内联证明报 `no matching attestations`，而镜像确实带着证明——因为那份证明挂在**平台清单**上、注册表又不把它暴露给 cosign，于是只能把同一份证明从 build metadata 取出、用 `cosign attest` 重新绑到被签的 digest 上，部署侧那条 `cosign verify-attestation` 才有对象可查）。**当系统的实际行为与假设不符时，先打印它真实存了什么再下结论**（`docker buildx imagetools inspect --raw`、`cosign verify-attestation` 的错误原文），不要凭文档或直觉补机制。2026-11-15 连续三次发布失败，每次都只暴露下一层原因：引用大小写 → `--yes` 用错子命令 → 证明查不到；因此**一次失败只证明一件事，失败信息要读完再用**

- **失败断言必须是它自己的失败（强制）**：「命令返回非零」不等于「被测主体按预期失败」。`bash -c` 里未 `export` 的函数返回 **127**、子进程找不到命令、脚本被 `set -e` 提前中止，都会让「把任意非零当成功」的 `assert_fails` **假通过**——2026-11-16 `scripts/tests/install-functions-test.sh` 的 `parse_version` 用例就是这么绿的（改成直接重定向 stdin 才真的跑到解析器）。判据：断言预期的**那个**退出码，并把 127 单独判为失败（失败信息要能看出主体确实执行过）；`set -e` 下的 `output=$(cmd)` 写成 `output=$(cmd) || output=''`，否则「命令失败 ⇒ 脚本静默退出」会吞掉后面所有判定，让文档里承诺的路径永不触发。
- **等 CI 要用结论判据，合并后还得查新起的那条（强制）**：`gh run watch <id> --exit-status | tail -6` 的退出码取自 `tail`，不是 `gh`——没有 `set -o pipefail` 时红灯就这么显示成绿的；`tail` 还会把结论截成最后几步的 ✓，让人按「看起来都过了」下判断。正确判据是三条同时成立：`set -o pipefail`、等到 `status=completed`、`conclusion == "success"`（直接读 API 的 `--json conclusion`，不要从日志文字推断）——直接用 `.github/scripts/wait-for-run.sh <id>`，它把这三条封在一个退出码里。**禁止 `for … sleep` 轮询**：一轮轮查状态既浪费往返，又可能漏掉中间跳变；`gh run watch` 是单进程阻塞、事件驱动，挂后台等 harness 唤醒即可。另一件事常被漏掉：**合并会产生一条不同的 run**（不同 commit、不同触发事件，`push` 而非 `pull_request`），绿色的 PR 不覆盖基线分支上那条；合并后必须查 `gh run list --branch <base> --event push --limit 1` 并以它的 `conclusion` 为准。2026-11-16 就是把「PR 全绿」说成了「CI 全绿」，漏掉 master 上因 Maven Central 429 失败的一次 push run；外部性失败（429 / Could not resolve / Read timed out / Ryuk 未就绪）用 `.github/scripts/gradle-retry.sh` 原样重试一次，且只重试这些。失败通知由 `.github/workflows/ci-notify.yml` 推送到 Telegram（只推失败；`workflow_run` 只对本工作流已在默认分支之后生效）。
- **运维工具由仓库脚本安装，不手抄 release 页（强制）**：`cosign` / `sops` 这类部署机与开发机都需要的二进制一律走 `scripts/install.sh`——它钉版本（钉在 `scripts/lib/install-functions.sh` 的表里，文档不复述）、按工具表解析资产名与版本命令（两个项目这三样都不一致，猜错会安静地装错或谎报失败）、用项目自己发布的 checksums 校验、**先在同目录暂存再 `mv` 覆盖**（`install`/`cp` 是就地截断，中断会留下坏二进制）、最后用二进制自己的版本命令复验，且从不 `sudo`。具体差异、退出码与验证证据见 `docs/ops-runbook.md` 的 `### Installing the verification tools`。仓内所有消费点（CI 的 sops、`deploy/verify-image.sh`、Jenkins 预检）都指向这个脚本，新增消费点必须同样如此；单测的无网络映射用例与跨 OS 的真实安装用例都随脚本进仓库（`scripts/tests/`）。
- **记忆（含压缩摘要）里的 SHA / run ID / PR 号 / 「已修」结论，引用前必须回源核对（强制）**：2026-11-16 一轮从未发生的「CI 判据修复」被当成已交付写进了计划、记忆与规则——描述细节齐全到足以乱真，但 `git cat-file -t <sha>` 无对象、`gh run view <id>` 404、`gh pr view <n>` 404、`git log -S '<串>'` 全库无痕，相关文件在盘上从未被改。判据：任何要落进文档的哈希、编号或「已改/已修」结论，先跑一次对应命令查它；查不到就不是事实，先删文字、再决定补不补工作，绝不能拿摘要当证据。
- **声明不等于执行（强制）**：阈值、规则、校验写在构建脚本或配置里，**不等于它会跑**——它要被人或任务依赖才算门。`jacocoTestCoverageVerification` 带着 80% / 70% 的阈值存在了很久，却没有任何任务依赖它：本地 `./gradlew check` 从不执行，只有 CI 在命令行里点名才生效，于是「阈值」只存在于流水线一侧（2026-11-16 P3.7 才发现）。判据两条：① 门必须挂进 `check` 这类聚合任务，本地与流水线同一条命令；② **用一次故意的失败证明它会红**（临时把阈值提到 0.99、喂一个坏输入），并把那条失败原文写进计划行的判定——只见过绿灯不算证明。
- **暖缓存会掩盖校验和缺口，冷缓存才是判据（强制）**：`gradle/verification-metadata.xml` 补完不等于补全——`--write-verification-metadata` 只记录这一轮**真的走过校验**的字节，本机缓存里早已存在的模块描述符（如 `junit-bom-5.14.4.module`）会被直接取用而漏记，`./gradlew check --offline` 也照样绿；到 CI 的冷缓存上下载时才 `Dependency verification failed for configuration ':compileClasspath'`（2026-11-17 P3.7 实测）。判据：用一个空 Gradle home 重放一次真实解析——`GRADLE_USER_HOME=$(mktemp -d) ./gradlew bootJar -x test`，再把测试侧跑一遍（`dependencies --configuration testRuntimeClasspath`、`compileTestJava`），全部通过才算集合完整。
- **推送前本地过门、推送后自证结论（强制）**：`scripts/preflight.sh` 按改动范围跑 CI 的同一批门（格式化常在；代码变更跑 `build`（含覆盖率阈值）；构建输入变更跑 `docker build`；`--master` 拒绝 AI 路径），仓库用 `git config core.hooksPath .githooks` 把它装成 pre-push 门，pre-commit 只跑最便宜的 `spotlessCheck`。判据是脚本自己打印的 `PREFLIGHT: green`，不是「我觉得应该过了」。推送后必须自己拿到判定：`.github/scripts/wait-for-head.sh` 按 `headSha` 精确取本次提交的 run，再阻塞到 `conclusion`——推出去的头几秒里 `gh run list --limit 1` 返回的仍是上一次的 run，它的绿与本次提交无关。「还在跑」不是交付状态；红灯要么修，要么把外部原因与证据（`429 Too Many Requests` 的原文、重跑结论）写进报告。
- **一个提交会有多个 run，等的是全部（强制）**：`master` 上合并会让 `CI` 与 `Publish` 同时起跑，`workflow_run` 通知还会再添几个。只拿到先完成那个的绿就宣布完成，等于把另一个的红藏起来——2026-11-17 就是这样漏掉 `Publish` 的 429（`CI` 绿是因为依赖层缓存命中，`Publish` 冷启动才真的去下载）。`wait-for-head.sh <branch>` 现在会在该提交上等**所有** run 收敛（含新出现的），任一失败即 `VERDICT: RED`。PR 的 CI 绿只说明 PR 准入，合并后必须对 master 上那个 merge commit 再跑一次。

- **编辑前验证（强制）**：先读完整文件（片段读取不足时直接整文件读取，不反复片段读同一文件）→ 编辑后LSP diagnostics → 编译验证 → 运行相关测试
- **按行编辑要分清「替换」与「插入」**：`PUT N:` 是**替换**第 N 行，插入必须用 `PUT >N:`。本会话两次用替换语义做插入，把 `import` 与字段声明整行覆盖，编译才暴露。改完 import / 字段区立刻编译一次
- **插入代码时锚点必须落在代码上，不能落在注释/ Javadoc 上（本会话踩了两次）**：格式化器（spotless）会把单行 Javadoc 折成多行，此时用单行注释文本作锚点会匹配到注释块**中间**，把新代码插进注释里 → 文件不再解析（`no longer parses after this edit`）。做法：① 锚点取紧随其后的**方法签名或语句**；② 拿到该告警时**立刻读该区段修回**，不要继续叠加编辑

### R10: 任务规划（强制）

多步骤任务（3步以上）必须先创建todo list，规划后再执行，完成后清理。

**计划待办必须自洽可完成（强制）**：每项待办只包含在当前步骤可完成的内容；不得把依赖后续任务的验收留在前置任务里。发现这种依赖时，把未完成内容拆成排在依赖之后的任务，前置任务按自身交付物收口；所有未开工待办只能依赖已排在它之前的工作。

**完成项必须有总结（强制）**：把计划项标为 ✅ 时，必须同批补充交付内容、验证证据、边界/未执行项与重访触发；禁止只改状态标记。总结写入计划或对应决策记录，并与 `activeContext` / `progress` 同步。

**一段任务只做一段事，状态列不许藏条件（强制）**：计划行的 ✅ 只表示「这一行的验收已经全部达成」，写完就结束了。规矩：① 判定列禁止「已达成…但未达成…」这种混合叙述——状态列是给人做决定的，条件藏在长句里等于没有状态；② 如果一件工作**必须**靠后续活动才算完，说明它不是一段任务——把它拆成两段独立任务，各写各的验收，不要用「遗留」「待后续」「归 Px.y 复核」这类指针把两段缝起来；③ 需要后续活动的那一段，就是**新任务本身**，写进它真正所属的阶段行（例如「首次发布走 Jenkins 路径」属于 P3.4 蓝绿发布，不属于质检门 P3.1）；④ **计划按行自上而下执行，验收只能依赖上方的行**：上一行没完成不开下一行，所以每一行的判定必须能在「它上面那些行都已完成」的前提下做完；需要下方阶段才存在的产物（load test 在 P5、公网入口在 P7）时，把那条验收留给下方那一行，本行只证明本行能证明的；⑤ **验收必须在任务所处的位置可达成**：若某条验收依赖后面阶段才存在的东西（域名、证书、生产数据、外部账号），它属于那个阶段的行，不能在当前行后面加一个「b」把顺序挪到自己方便的地方（2026-11-16 P3.3 教训：公网 443 与真实客户端链路要等 P7.1 建立入口，写进 P7.1 而不是 P3.3b）；④ 禁止在计划里另建「遗留/待办总表」这类二级账本——那是把任务规划做成大号 TODO，只会让计划更难读；⑤ **任务的过程记录就地落在行里**：交付物写这一步做了什么，判定写验收结果、遇到的问题和处置；不要另起「交付记录/决策记录」索引表，更不要为此拆出一个记录文件——同一件事只写一遍，读表即可。2026-11-15 用户三次订正：先指出 ✅ 后面挂未达成属于信息不对等，再指出「建总表」是复杂化，最后指出把细节抽到独立文件同样是复杂化——正确做法是改任务划分并把结论写进行里，不是加层级。

### R11: 文件管理（强制）

禁止创建临时文件：❌ 重定向到文件（`> output.log`），❌ `.log/.txt/.tmp`；✅ 输出到控制台。任务完成检查是否误创建文件，发现立即删除。

### R12: 依赖管理（强制）

禁止擅自添加依赖。添加前必须提供分析（目的、选型理由、影响评估、替代方案）并获得用户同意。红线：禁止冗余依赖，禁止重复功能包，优先复用现有依赖。

**升级依赖时（两次踩坑后固化）**：

- **版本以 Maven Central / Gradle Plugin Portal 元数据为准**，`dependencyUpdates` 报告会**漏报关键坐标**（实测只列出 5 项，Spring Boot / spotless / postgresql 全缺）——按坐标逐个查 `maven-metadata.xml`，插件 id 查 Plugin Portal；里程碑与 RC **不算"最新稳定版"**。2026-11-07 复核依旧：报告只列 4 项（flyway / commons-codec / spotless / ben-manes），Spring Boot、springdoc、greenmail、postgresql、junit-platform 等均未列出，逐个查元数据后才确认它们已是最新
- **升级后必须同批更新 `techContext.md` 的版本快照**（R2 的「技术栈变化」包含依赖升级；该表曾长期停留在 Spring Boot 4.0.5 / Flyway 11.4.0 而实际已 4.1.1 / 13.7.0，且留着已移除的依赖）
- **Spotless 与 google-java-format 要成对升级**：2026-10-27 把 google-java-format 1.36.1 → 1.37.0 后，Spotless 8.10.4 下**每个文件**都抛 `InvocationTargetException`（`LINE_UNDEFINED`）；把 1.37.0 的 all-deps jar 单独跑（带 `--add-exports`）却能正常格式化 ⇒ 是两者的集成缺口，不是 JDK 不兼容。升级前先跑一次 `spotlessJavaCheck`，失败就保留旧版并在报告里写明证据。**2026-11-07 实测可用组合：Spotless 8.11.0 + google-java-format 1.36.1（`spotlessCheck` 通过）；1.37.0 仍未启用**

- **同一模块族的版本只能有一个来源**：`flyway-core` 与 `flyway-database-postgresql` 必须同版本（前者被 Boot BOM 管理，靠 `strictly` 覆盖），字面量要写成 `libs.versions.<name>.get()` 而不是第二次硬编码
- **容器镜像一律 `latest`（用户决策）**：`postgres` / `redis` / `mailpit` / `caddy` / Prometheus / Grafana / blackbox 都跟随 `latest`，出现新大版本优先升级；因此可追溯性不能靠 tag，部署时用 `docker inspect --format '{{index .RepoDigests 0}}'` 记下 digest，大版本升级前后各跑一次本地全栈与全量测试。构建镜像（`eclipse-temurin`）按 toolchain 的 Java 版本固定，不跟 `latest`（换大版本等于改语言级别）
- **Gradle wrapper 的升级与依赖同批**：`./gradlew wrapper --gradle-version=X` 会改 `gradle-wrapper.properties`、`gradle-wrapper.jar` 与 `gradlew*`；验证用 `clean build --rerun-tasks`（否则测试可能整批命中缓存，等于没验）

- **新增依赖必须做多维评估（强制）**：至少比较**最新稳定版**、维护活跃度与最近发布、采用度、许可证、已知漏洞、传递依赖体积、**与当前框架/运行时兼容性（Spring Boot BOM、Spring Security、JDK、Jackson 2/3、Servlet/WebFlux、日志栈）**、API 稳定性、可测试性与替换成本；优先使用 BOM/官方管理的版本。禁止因为示例代码、前端建议或“能编译”就直接引入；先记录候选、版本、证据和排除理由，再落代码。解析后必须用 `dependencies` / `dependencyInsight` 核对解析版本，确认没有强制降级、双 Jackson 代际或同一能力重复引入。RC/里程碑不算稳定版。

### R13: 记忆文件维护（强制 - 冷热分层）

**核心原则：修剪 = 归档而非删除。** memory-bank 是项目记忆，历史条目即使当前不用也可能被回溯（排障、决策溯源、模式复用），像数据库冷数据一样分层保存。

**目录结构：**

| 路径 | 角色 | 维护方式 |
|---|---|---|
| `memory-bank/*.md` | 热数据（当前工作集） | activeContext.md 只保留最近 30 天条目，超 200 行触发归档 |
| `memory-bank/archive/` | 冷数据（不可变归档） | 按年月分片 `activeContext-YYYY-MM.md`，shard 只写不改 |

**归档操作（activeContext.md 超 30 天条目或 200 行时）：**
1. 早于 30 天的条目按月移入 `archive/activeContext-YYYY-MM.md`（shard 内倒序，已有 shard 则并入）
2. 每个新 shard 带头注释：`> 冷数据归档（R13）。仅供回溯，不再更新。`
3. activeContext.md 末尾加归档指针（`> **归档**：更早条目见 memory-bank/archive/`）
4. **完整性校验**：归档前后总行数差 = 新增 shard 头行数（零丢失）
5. 归档内容与业务代码同 commit 提交（R6.5 AI 独立规则）

**行数超限但无 30 天外条目时**（规则缝隙）：说明当前工作集本身过密——不得为此归档 30 天内的条目（它们是热数据），也**不得删除内容**。正确处理顺序：
1. 优先**压缩最新条目**的措辞（合并重复表述、删冗余定语），信息量不得减少
2. 仍超限 → 若同月条目累积到明显体量，可将**该月已完结**的成组条目移入当月 shard（须保留结论、丢弃过程细节）
3. 仍超限 → 说明阈值需调整，按 R14 提出规则修订，**不得**靠删记忆达标

| 文件 | 超限处理 |
|---|---|
| activeContext.md | >30 天条目或 >200 行 → 归档至 archive/（禁止直接删除） |
| progress.md | >200 行 → 逐条变更历史整体归档至 `archive/progress-completed.md`，热文件只留**版本里程碑账本**（由 `gradle/libs.versions.toml` + 提交历史推导，不手工维护） |
| systemPatterns.md | 合并相似模式（被合并的旧模式移入 archive/） |
| techContext.md | 过时配置移入 archive/ |

**progress.md 的职责边界**：「什么版本交付了什么」进 progress；「最近具体改了什么」进 activeContext（30 天热窗口）。禁止在 progress 里堆逐条变更——它会过期，过期的清单比没有清单更危险（曾把已交付的同步引擎与排行榜列为未开始）。

### R24: AI 边界（身份 / AI 文件 / 子任务只读）（强制）

**AI 身份**：本仓库（ctt-server）的后端开发者，**唯一可写仓库 = ctt-server**；`../code-time-tracker`、`../ctt-web` 一律只读 + 提需求（R3）。

**AI 文件保护**：`.agents/` 是 AI 技能工作区，不是项目源码。

- ❌ 禁止读取/修改/删除 `.agents/skills/` 下任何文件，禁止因"发现问题"而改动
- ❌ 禁止将 `.agents/` 纳入代码审查 / 重构 / 清理范围
- ✅ 仅当用户明确要求时才可操作；发现问题只提醒用户

**子任务只读**：审查/检查/调查类子任务（code-review、explore、scout 等）严格只读——禁止 `--fix` 类命令（`spotlessApply`、格式化写盘）与任何文件写入；需要验证仅允许只读检查（不带 `--fix` 的检查、不写盘的定位分析）。

**红线**：子 agent 写盘会全项目污染工作区；越权跨仓库改动须回退并记录。

**附件图片先走视觉工具**：消息里附带的图片，本模型通道**不携带像素**——直接"看"、用 `read` 读、或 `acp_read_original_image` 取原图，都只会拿到路径与尺寸。正确做法是把路径交给 `mcp__magpie_vision_analyze_image`（`image` + `prompt`），由它转成文字再判断。禁止在没试过视觉工具前宣称"看不到图"，也禁止凭文件名或上下文猜测图里是什么；读不出内容时说明收到的到底是什么（元数据/路径），不要编造。2026-11-16 连试四张图才定位到这一点，白耗了多个回合。

### R25: 领域知识库（强制）

**核心原则：知识按「领域」沉淀，不按「时间」堆积。** 时间线（activeContext / progress）只回答"最近发生了什么"；跨轮次可复用的判断沉淀到 `memory-bank/domains/<domain>/`。

**第一层永远是领域**（业务/技术能力面，如 `stats-aggregation`、`sync-protocol`），禁止按文档类型建第一层（`principles/`、`practices/` 这类全局扁平目录）。

**每领域五件套**（缺一不可，建立即填实，禁止占位）：

| 文件 | 职责 | 判定标准 |
|---|---|---|
| `meta.md` | 领域边界、负责范围、代码入口、领域术语 | 只看这一个就知道"归哪、从哪看起" |
| `principles.md` | 不变量与第一性原理（决策依据） | 能用来裁决新情况 |
| `scenarios.md` | 触发场景 → 判断 → 动作 | 遇到 X 该怎么做，不用重新推理 |
| `practices.md` | 具体做法、参数、代码模式、踩坑与规避 | 可直接照做，含反例与"为什么" |
| `references.md` | 外部契约、端点、数据字典、文件路径 | 事实性查表，不含判断 |

**五件套是覆盖约束，不是目录美观**：它规定「解决本领域一个问题，**至少**要理解哪些方面」。某个方面没有沉淀 → 说明它缺失，不是「不需要」。

**渐进式披露（强制 - 不一次全读）**：按需加载，下一步由上一步的结论触发：
`meta.md` 判归属 → `scenarios.md`/`principles.md` 定判断 → `practices.md` 拿做法 → `references.md` 查事实 → 回源代码/配置核对。一次性读完整个领域树是反模式（注意力被当前判断用不到的知识占用）。

**回源（强制 - 不同事实回不同来源）**：

| 事实类型 | 回源到 |
|---|---|
| 系统当前行为 | 代码 + 配置 |
| 契约意图 | Flyway 迁移 / 已确认的决策记录 |
| 历史原因 | 可追溯的实践记录（提交、复盘） |

禁止两种推定：①「代码这么实现了 → 它必然是正确的业务规则」②「旧文档这么写过 → 可以忽略代码已经变了」。

**维护机制（知识不是一次性生成）**：
- **增量**：契约/结构变更（API 签名、DB 语义、状态机、错误码、配置键）时同步更新受影响领域文件
- **校准**：重大改造或复盘后回看领域文件是否已漂移
- **高风险知识**（API 契约、DB 语义、消息 Schema、状态机、历史兼容、安全策略）**语义确认归人**；自动化只负责「发现变化、生成候选、阻止遗漏」，禁止「代码一变就自动覆盖知识」

**元数据**：关键事实标注来源（代码路径 / 迁移文件 / 提交）；每个领域声明**核对基线**（最后对照代码核对的日期或版本）。

**骨架优先于检索**：领域文件是骨架，定义 AI 必须理解的核心事实；检索（grep / 历史文档 / RAG）只用于**补证**与长尾发现。补证结论经确认后回写对应领域文件，否则下次仍要重新检索。

**生长规则**：按需建档（宁可少而实，禁止为凑结构建空领域）；新知识先归类再更新对应文件；`memory-bank/domains/README.md` 索引必须与目录同步；横切规范留 `systemPatterns.md`，领域专属判断进领域文件，**不得两处重复**。操作规程（漂移处置、索引校验、已定格式取舍）见 [`memory-bank/domains/README.md`](memory-bank/domains/README.md)，本规则只留可裁决的约束。

**红线**：❌ 占位文件 / `TODO: fill` / 空章节 ❌ 把领域文件当 changelog 用（版本流水账属 `progress.md`）❌ 与代码/契约不一致的表述（涉契约先只读核对源码）❌ 把推断当事实写入（不确定的标「待确认」并列出待验证项）❌ 代码一变就自动覆盖高风险知识；单文件仍受 ≤200 行约束。

### R26: AI 产物位置（强制）

`docs/` 只放**面向用户的项目文档**（developer-handbook、time-strategy、api-governance 等）；AI 工作产物放 `.omp/`（已 gitignore，不进仓库），**需要人机共读的计划类文档放 `.plans/`（入库，仅 develop）**：

| 产物 | 位置 |
|---|---|
| 实施计划（AI 内部） | `.omp/plans/<YYYY-MM-DD>/<feature>-plan.md`（日期只做文件夹） |
| 交付/对接报告、需求草案、验收、调研 | `.omp/<category>/<YYYY-MM-DD>/<kebab-topic>.md`（见下条归档约定） |
| **计划 / 阶段计划 / 交付记录 / 归档（人机共读）** | **`.plans/<kebab-case-name>.md`（仓库内，中文，仅 develop、不进 master）** |
| Agent 记忆 | `memory-bank/`（受 R1/R2/R13/R25 治理，**需要提交**） |
| 面向用户的项目文档（英文） | `docs/` |

**三层分工**（写进文件抬头，否则日后必混）：`.plans/` 规划与归档（人机共读、中文）／`docs/` 面向使用者的项目文档（英文）／`memory-bank/` 与 `.omp/` AI 自用。

**`docs/` 是给 GitHub 上任何读者看的，不是本部署的记录簿（强制）**：禁止放入部署实例的可识别信息与实测快照——主机名、IP/子网、端口映射、某台机器的具体体积或偏移、验收结论列表。需要证据的决策写**机制、判断与可复核命令**，具体值放 `.omp`；`docs/` 里连"某台机器是多少 M"都不写（2026-11-11 用户指出 `docs/host-logging-and-time.md` 属运维记录，实际归 `.omp/notes/`；同批把 ADR-0003 中的内网地址与主机名改为描述性表述）。判据：一条信息换了部署就失效 ⇒ 属记录，不进 `docs/`。
**ADR 留在 `docs/decisions/`，但只写读者能独立理解的内容（强制）**：ADR 是公开格式（Context / Decision / Alternatives / Consequences / Revisit triggers），外部读者靠它理解「为什么这样定」，所以位置没错；**不得**出现内部计划坐标（`P0.6b`、`P3.8`）、内部版本号（`v0.93.x`）、私有路径（`.plans/`、`.omp/`、验收记录）或共享主机上其他服务的名称。追溯方向只允许 **计划 → ADR**（`.plans/` 与 `memory-bank/` 引用 ADR 文件名），ADR 不反向引用计划；需要的证据写机制与可复核命令，具体值按上条判据处理。理由：外部读者无法解引用 `.plans/`（不在 master），计划编号会随拆分改名失效（2026-11-11 用户指出 `docs/decisions/` 中大量 `P0.x` 与私有路径）。

**对接/变更报告：每轮新建文件，禁止原地修改旧报告（强制）**：给前端/插件等对接方的报告，**每一轮新起一个文件**（路径与命名见下条归档约定），旧文件是历史记录、**不再改动**。最新口径 = 当天/最新那一轮的文件，且新文件必须自述"本文件取代哪一轮的哪些条目"。理由：`.omp/` 无版本控制，原地更新会让同一文件混着旧结论与新结论，读者（和下一个 AI 会话）无法判断哪句有效——2026-10-19 踩过一次，原文已无法还原。

**`.omp/` 一律分类归档（强制）**：`.omp/<category>/<YYYY-MM-DD>/<kebab-topic>.md`——**时间只是文件夹，不进文件名**；类别固定为 `reports`（对接/交付/评审）· `acceptance`（验收）· `requirements`（需求）· `research`（调研）· `plans`（实施计划）· `notes`（其它）；**正文中文、文件名英文**；一个需求一个文件、当天归档，后续轮次**另起**新文件并在文首写明取代关系；`.omp` 顶层不得出现散文件（2026-10-23 用户要求）。原因：同一文件被反复就地修改后，读者与下一个会话都无法判断哪句有效（2026-10-23 用户在 `leaderboard-streak-window-frontend-report.md` 上再次指出）。

**红线**：禁止把实施计划写进 `docs/plans/`；`.plans/` 属面向 AI 与开发过程的内容，**禁止 cherry-pick 进 master**。

### R14: AGENTS.md 自更新（强制）

触发条件：规则漏洞/用户新约束/重复错误需固化。更新流程：记录问题 → 添加/修改规则 → 记录到activeContext.md → 等待用户确认。命名：新增用 `R{n}`，修改保留序号+版本说明，删除标记 `[已废弃]`。

### R15: 版本号管理（强制实时）

**核心原则：任何代码变更必须同步更新版本号，严禁滞后更新（防断片）。**

版本号位置：`gradle/libs.versions.toml` 的 `appVersion` 字段

格式：`MAJOR.MINOR.PATCH[-SNAPSHOT]`

变更规则：Bug修复→PATCH+1，新功能→MINOR+1，破坏性→MAJOR+1，开发中→`-SNAPSHOT`后缀

执行时机：每次代码修改后立即：1.确定新版本号 2.更新libs.versions.toml 3.全局搜索检查硬编码 4.记录到activeContext.md

全局搜索检查（强制）：`grep -rn "0\.\d\+\.\d\+" --include="*.java" --include="*.yaml" --include="*.kts"` → 排除 Javadoc @since/依赖版本/IP/Flyway baseline → 确认 application.yaml/OpenAPI Config 同步

禁止：代码变更不更新、跳版本、未经确认升MAJOR、代码硬编码版本号（用 `@Value("${info.app.version}")`）、修改 Javadoc @since

### R16: 自我学习（强制）

当同一问题解决 2 次以上，可沉淀为 skill 记录方案。

**前置条件**：**必须先询问用户并获同意**（R24 保护 `.agents/`，未经明确要求禁止写入；发现问题只提醒）。存放位置 `.agents/skills/[skill-name]/SKILL.md`；流程：确认解决 → 询问用户 → 获同意后用 skill-creator 创建 → 更新版本号。

### R17: 分支管理（强制 - 防止生产事故）

**核心原则：master 是生产分支，永远保持干净（无 AI 文件、无开发内容）。**

| 分支 | 用途 | 允许 | 禁止 |
|---|---|---|---|
| master | 生产环境 | 业务代码/测试/版本号/面向用户的项目文档（`docs/` 下的 handbook、strategy、governance 等） | AI文件（memory-bank/.agents/AGENTS.md/CLAUDE.md/SKILL_GRAPH.md/skills-lock.json）+ 开发内容（`dev-docs/`、`docs/plans/`、面向对接方的前端/QA 指南） |
| develop | 开发环境 | 业务代码 + AI文件 + 开发内容 | 无 |

**内容边界判据**：「面向本项目的使用者/运维」=项目文档（进 master）；「面向 AI 或面向对接方开发者（前端/插件）的实施过程与对接说明」= AI/开发内容（不进 master）。位于 `docs/` 但内容是对接指南的文件，按**内容**判定，不按目录。

**分支模型：Git Flow + master 仅 PR（2026-11-15 生效）**：`master` 上有 ruleset `master-protection`（`deletion` + `non_fast_forward` + `pull_request`，required approvals 0、`bypass_actors` 为空），**任何人（含管理员）都推不上去**，一切改动经 PR 进入。合并方式只开放 merge 与 squash。

| 分支 | 从哪切 | 合到哪 | 触发者 |
|---|---|---|---|
| `feature/<kebab>` | `develop` | `develop`（PR） | 日常开发（功能、修复、chore、文档都走它） |
| `release/v<x.y.z>` | **`master`** | `master`（PR） | 发布 |
| `hotfix/<yyyymmdd>-<kebab>` | `master` | `master`（PR）→ 再 PR 回 `develop` | 生产事故 |

**命名规则（2026-11-15 定）**：前缀与名字之间用 `/`，名字内部用 `-`。`feature/<kebab>`（如 `feature/p3-1-quality-gates`）、`release/v<主>.<次>.<修订>`（如 `release/v0.107.0`，**必须带修订位**，否则补丁版回滚后同名）、`hotfix/<yyyymmdd>-<kebab>`（日期在前便于按时间排序，主题在后避免同日两次热修撞名）。

**发布前不建 `release/*`，不占 `v*` tag（2026-11-16 定）**：两者的意义都是「标记一次发布」，没有发布动作就没有它们——提前切一支「预备发布」只会在旧 master 上沉淀一份迟早过期的副本（2026-11-16 清理时 `release/v0.112.0` 与 `hotfix/20261115-registry-tag-docs` 的内容早已并入 master，只剩分支本身；演练用的 `v0.109.0`/`v0.110.0`/`v0.112.0` 三个 tag 同批删除）。把 develop 的成果搬到 master 是另一件事，用 `sync/master-<yyyymmdd>`（见下），合并后即删、不打 tag；演练发布链用 `publish.yml` 的 `workflow_dispatch`。`appVersion` 仍逐次递增：它喂 `/actuator/info`、SBOM 与镜像标签，但不构成「已发布」的证据——发布与否只看 master 上有没有 tag。

**tag 用 `v<x.y.z>`，不加 `release_`/`hotfix_` 前缀**：分支与 tag 同名会让 `git checkout` 直接失败（实测 `warning: refname 'release_v0.107.0' is ambiguous` → `fatal: ambiguous object name`），而 `release/v0.107.0` 与 tag `v0.107.0` 天生不撞。发布类型的追溯写进 annotated tag 的消息（`release v0.107.0 from release/v0.107.0`），不写进名字——名字要给 `git describe` 与语义化版本工具解析。

**发布分支与同步分支都从 master 切，不从 develop 切**：develop 含 AI 文件与开发内容，从它切出来的分支一合并就会把 `AGENTS.md`、`memory-bank/`、`.plans/` 带进生产。两种分支的构造相同——`git switch -c <name> origin/master`，只带**非 AI** 改动，PR 到 master，合并后删除分支。

**PR 的准入条件**：CI 三个 job（`Format, tests and coverage`、`Render secrets with the CI age identity`、`Secrets, vulnerabilities, SBOM and provenance`）全绿。这三项**尚未**写进 ruleset 的 required status checks——GitHub 只允许把近 7 天在本仓库成功跑过的检查设为必需，所以顺序是「先让 PR 跑绿 → 再用 API 写入规则集 → 回读确认」，在此之前红灯 PR 仍可合并。单人仓库设 0 approvals——PR 作者不能批准自己的 PR，要求 1 会让唯一的作者永远合并不进去。

**master 同步规则（PR 版，2026-11-16 修订）**：develop 每交付一批就把非 AI 内容同步到 master，分支名 `sync/master-<yyyymmdd>`；它不是发布——不打 tag，也不额外改版本号（版本已随 develop bump，同步只是把它带过去）。**按内容同步，不按提交 cherry-pick**：两边历史早已分叉（`origin/master..origin/develop` 实测 967 条，多数是 cherry-pick 造成的重影而非内容差），逐条重放只会制造冲突与噪声。做法是 `git diff --name-only origin/master origin/develop -- . ':(exclude)AGENTS.md' ':(exclude).plans' ':(exclude)memory-bank' ':(exclude).agents' ':(exclude).claude' ':(exclude).omp' ':(exclude)docs/plans' ':(exclude)dev-docs'` 列出真实缺口 → `git checkout origin/develop -- <列出的路径>` → 一个提交写清补齐了什么 → push → `gh pr create --base master` → CI 全绿 → `gh pr merge --merge` → 删同步分支。每步用 `git show --name-only` 核对没有 AI 路径混入（`.claude/skills/*` 是指向 `.agents/skills/` 的符号链接，同属 AI 内容）。

**不要做 master→develop 的回灌 PR**（2026-11-16 更正）：develop 在非 AI 路径上永远是 master 的超集，回灌带不回任何内容；而 PR 的 diff 方向是 base→head，`--base develop --head master` 会把 develop 独有、master 没有的 `AGENTS.md`、`memory-bank/`、`.plans/`、`.claude/`、`skills-lock.json` 等判成**删除**，合并即销毁 AI 内容（同步后实测：该方向 diff 为 123 文件 / 30,415 行插入）。需要的只是同步后跑一次 `git diff origin/master origin/develop --stat -- <同上排除项>` 确认为空，空即证明 develop 是超集。

**真发布时**才用 `release/v<x.y.z>`：同样从 master 切、只带非 AI 改动、PR 合并后打 annotated tag `v<x.y.z>`（消息写 `release v<x.y.z> from release/v<x.y.z>`）。版本号与 tag 到这一步才代表「发布过」。发布同样**不回灌 develop**，理由见上。

**禁止操作**：直推 `master`（ruleset 已拒绝，不要试图用 bypass）、整分支合并 develop、在 master 创建 AI 文件、在 master 提交 `docs(memory-bank)`、`git reset --hard develop`、反向 cherry-pick（master→develop）、在 master 直接修改代码

**事故恢复**：立即停止 → 确认污染 → 重置到安全点 → 重新cherry-pick → `--force-with-lease`推送 → 记录事故

**提交前审查清单**：项目一致性（grep现有模式） + Clean Code（无冗余注释） + 测试覆盖 + 编译通过 + 无回归 + 覆盖率≥80%

**Testcontainers 首轮 `Could not connect to Ryuk` 属环境抖动**：换 Gradle / 依赖后的第一次测试若报 Ryuk 连接失败，先重跑一次再判断（2026-10-27 实测：首轮 312 例失败，重跑 1567/0）。与代码无关，不要因此回滚依赖。

**`clean build` 偶发"零结果失败"（已两次踩坑，2026-09 与 2026-10）**：表现为 `> Task :test FAILED`，但 `build/test-results/test/*.xml` **一个都没有**（连带 `tests=0 failures=0`），且无诊断输出——不是代码问题。处置：**单独重跑 `./gradlew test`（或再 `build`）**；若仍失败且仍零结果，才当真实故障排查。禁止用这一次失败就判定"测试挂了"或去改业务代码。

**验证 `.dockerignore` 不能只看 BuildKit 的 `transferring context` 数字**：BuildKit 按内容寻址缓存，同一份文件第二次不重复传，读数会从 249 MB 掉到几十 kB，看起来像"忽略生效"，实际什么也没证明。可靠做法：用 `COPY . /ctx` + `RUN du -sb /ctx` 在镜像里数**实际复制进去的字节**；再单独建一个只 `COPY` 生产 Dockerfile 所需路径（`gradle/`、`gradlew`、构建脚本、`src/`）的验证镜像，确认排除规则没有把构建依赖一起丢掉（2026-11-08 P0.3 实测：249.44 MB → 4.06 MB，不含本地镜像暂存的 88 MB jar）。

**常见错误预防**：**派生分数/物化的失效键必须能从枚举或配置推导**（加维度、放周期靠推导自动失效；只有改公式才手工提代次——2026-10-25 因手工代次未提，窗口榜整片空态却返回 200）；**「谁上榜」也是规则**（例如排除 0 分成员、按成员资格过滤），改它同样要让回填失效，并检查索引/目录这类旁路数据是否一起清；重复方法→编辑后grep+LSP；ErrorCode冲突→新建前搜索复用；**多行/多会话批量加锁必须按固定顺序（如 UUID 升序）**，否则两个并发批量会互相等待；冗余注释→删除"解释做了什么"；测试遗漏→新增逻辑立即创建测试；命名不一致→搜索现有模式；**枚举组合数不要写死**（"20 个 key / 15 个 TTL"这类断言在加枚举值时会误报，改成从 `values() × supports()` 推导一条公式）

### R18: Git 恢复禁止（强制）

**禁止执行 git reset 恢复到初始状态** — 这会导致工作丢失且不可恢复，必须经由用户确认。

### R19: 资源清理（强制）

占用资源的工具/服务使用后必须关闭。持续服务需后台静默启动，日志单独输出至文件，避免超时/资源堆积。任务完成后立即清理。

**用户在用或常驻的本地服务不得被清理波及（强制）**：本机 `ctt-mailpit`（`127.0.0.1:8025`，邮件沙箱）由用户长期开启，数据在 `mailpit_data` 卷里。2026-11-08 P0.2 演练收尾时用 `docker compose … down` 把它一起删除——数据没丢，但用户的服务被打断。规矩：清理只关**本次启动且用户未在使用**的容器；停栈优先 `docker compose stop <service>`，必须 `down` 时结束后立刻 `up -d mailpit` 恢复（compose 已给它 `restart: unless-stopped`）。

### R20: Skills 选择规范（强制）

使用某类型Skills前先列出所有同类Skills，可同时加载多个，不是只能选一个。

### R21: 外部 AI 咨询能力

可使用skills访问 gemini.google.com / perplexity.ai 咨询高级AI（需选择模型）及网络搜索。

### R22: 数据库迁移管理（强制）

**核心原则：init 迁移（`V20260303210000__init_base_schema.sql`）一旦被任何环境应用过，禁止再修改其内容**——Flyway 对已应用迁移做 checksum 校验，改动会导致 validate 失败、服务启动崩溃。

| 场景 | 正确做法 |
|---|---|
| 新表 / 新列 / 新约束 / 约束修改 | 新建独立迁移 `V{时间戳}__{描述}.sql`（如 `V20260831230001__xxx.sql`），版本号递增 |
| 既有约束加枚举值 | 独立迁移中 `ALTER TABLE ... DROP CONSTRAINT` + `ADD CONSTRAINT` 重建 |
| 依赖 init 的测试 | 约束/结构断言需读取独立迁移（如 ResourceTypeTest 读 `ADD CONSTRAINT` 所在文件），不得假设在 init 内 |

触发条件：任何对 `db/migration/` 下已提交文件的修改，先确认该文件是否已被应用（提交过 = 已应用）；已应用 → 绝不直接改，另起迁移。

## 执行流程

会话开始 → 读memory-bank → 创建todo（如需）→ 处理请求 → 清理临时文件 → 更新记忆 → 检查行数修剪 → 代码修改+编辑验证 → 提交前审查+Git授权+版本号

## 约束

1. 文件读写由AI自主完成
2. 记忆文件≤200行
3. 只记录已发生事实，不猜测
4. 变更即时更新
5. **项目一致性优先**（R8.5）
6. **编辑前必须验证**（R9）
7. **提交前必须审查**（R17）
8. 截图保存至 `/Users/ahogek/Pictures/screenshots`

## 记忆库结构

**时间线层**（回答"最近发生了什么"）：

`memory-bank/`：projectbrief.md（目标）、techContext.md（技术栈）、systemPatterns.md（横切规范）、activeContext.md（当前）、progress.md（进度）、`memory-bank/archive/`（R13 冷归档，唯一豁免行数限制）

**领域层**（回答"这里什么是真的、该怎么做"，R25 治理）：

`memory-bank/domains/<domain>/` — 每领域五件套 `meta.md` / `principles.md` / `scenarios.md` / `practices.md` / `references.md`；入口 `domains/README.md`（按需建档，无领域时不存在）
