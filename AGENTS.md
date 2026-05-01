# AGENTS.md - 项目记忆与行为约束

> 本文件由 AI 自动维护，人类请勿手动编辑

## 核心规则

### R1: 会话初始化

每次会话开始立即读取 `memory-bank/` 下所有文件，缺失则创建。

### R2: 记忆更新（强制实时）

**严禁滞后更新** - 不在单次交互自动更新易造成断片。响应完成后**立即**评估更新：

| 触发条件 | 更新文件 | 更新内容 |
|---|---|---|
| 代码修改 | `activeContext.md` | 具体变更 + 日期前缀 |
| 任务完成 | `progress.md` | 完成项移至"已完成" |
| 架构决策 | `systemPatterns.md` | 新模式/设计决策 |
| 技术栈变化 | `techContext.md` | 依赖/版本变更 |
| 项目变更 | `README.md` | API/功能/里程碑 |

**更新格式**：`[YYYY-MM-DD] - 变更标题` + 文件/影响说明

### R3: 关联项目

`../code-time-tracker` - API 变更、数据结构修改、协议更新时主动读取。

### R4: README 同步

重大变更（API/架构/功能/部署/里程碑）时同步更新 README.md 和版本号。

### R5: Git 提交同步

记忆文件与业务代码同 commit，禁止单独提交"更新记忆"。

### R6: Git 操作确认（强制）

**核心原则：单次交互授权。提交授权仅限当前变更，用完即失效。**

#### 授权范围

| 操作类型                       | 授权有效期     | 说明                |
|----------------------------|-----------|-------------------|
| `git commit/push`          | **仅当前变更** | 授权仅针对用户指明的那些文件/变更 |
| `git status/log/diff/show` | 无需授权      | 只读操作，可自主执行        |
| `git branch`               | 仅创建分支     | 不包含后续 commit/push |
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

#### 越界示例

| 场景       | 用户说了什么             | AI 能做的     | AI 不能做的                    |
|----------|--------------------|------------|----------------------------|
| 用户授权变更 A | "没问题，提交并推送"        | 提交变更 A 并推送 | 提交变更 B（新开发的内容）             |
| 用户审查变更 A | "审查通过，可以提交"        | 提交变更 A     | 提交变更 A + memory-bank（用户没说） |
| 用户说继续    | "继续开发"             | 开发变更 B     | 开发完直接提交变更 B                |
| 用户开发新功能  | "开发 AuditFixtures" | 开发完成，等待审查  | 开发完自动提交                    |

#### 执行前强制自检

**每次 commit/push 前必须逐项检查：**

```
□ 用户是否在【当前交互】中说了"提交/commit/推送/push"？
□ 用户说的是提交【哪些变更】？（确认文件范围）
□ 是否有用户未明确授权的额外变更？（memory-bank 除外，见 R5）
□ 用户是否说了任何模棱两可的话？（"可以"、"没问题"→ 不算授权）
```

**任何一项不确定 → 停下来问用户。**

### R6.5: 提交规则（强制）

**核心原则：原子化提交、版本同步、AI独立、cherry-pick合并。**

- **原子化**：代码修改触发版本号更新（检查所有相关文件一并更新），版本提交独立且晚于代码提交
- **累计更新**：不用刻意提交每次中途更新，可只提交最后的版本描述跳过中途
- **提交顺序**：功能代码 → 版本号更新 → AI记忆记录（禁止版本早于功能、混合提交）
- **提交拆分**：原子性一致可合并，不过分拆分刷提交，不过于宽泛堆积大量文件
- **分支顺序**：优先完成 develop 全部提交再考虑 master
- **AI独立**：AI相关内容（memory-bank等）单独提交，不与代码混在一起
- **master合并**：非AI内容单独cherry-pick进master，严禁整条分支合并（导致AI污染），严禁错误cherry-pick旧develop导致污染

**提交信息格式**：`feat(scope): 功能描述` / `fix(scope): 根因+修复+验证` / `chore: bump version to X.Y.Z` / `docs(memory-bank): record implementation`

**最终清理**：功能commit → 版本commit → AI记忆commit → 推送 → 验证 `git status` 干净 → 有残留立即补提交

### R7: 技术决策确认

**禁止擅自修改**：语言/框架版本、架构设计、数据模型。原则：只读取不猜测，只实现不决策，有疑问必须问。

### R8: 边界原则

- **不懂就问**：不确定时停下来问用户，禁止盲目猜测
- **现代 Java**：优先 `record`、`sealed class`、`pattern matching`，避免 Lombok
- **验证优先**：不确定内容先验证再使用
- **变更溯源**：发现与预期/记忆不一致时，优先猜想"是否被用户修改了"而非"AI 忘了改/改错了"
    - **第一步**：检查 git diff/commit history 确认变更来源
    - **第二步**：验证当前业务逻辑是否正确（测试通过 = 逻辑正确）
    - **第三步**：如用户修改了，更新记忆适应新逻辑，不要恢复"旧版本"
    - **禁止**：笃定"这应该是错的"、"之前改的忘了恢复"等揣测性结论

### R8.5: 项目一致性优先（强制）

**核心原则：按项目来而不是任务需求，需求要变通符合项目的一致性。**

| 场景 | 错误 ❌ | 正确 ✅ |
|---|---|---|
| ErrorCode | 任务说新建就新建 | 搜索现有错误码，复用已有的 |
| 命名/结构 | 按任务需求创建 | 遵循项目现有 `*Service`, `*Controller` 模式 |
| 注释 | 解释"代码做了什么" | 遵循 Clean Code（R9），删除冗余注释 |

执行前检查：任务是否与现有模式冲突？是否应复用而非新建？

发现冲突 → 暂停 → grep搜索现有模式 → 向用户确认 → 按项目一致性调整

### R9: 代码规范

- **语言**：代码/注释/日志强制英文，仅 `.md` 可中文
- **注释（Clean Code）**：✅ 公共API Javadoc/复杂算法Why/警示信息；❌ 解释代码做了什么/冗余注释/注释掉的代码/TODO/FIXME
- **OpenAPI Schema（强制）**：所有DTO/Response加 `@Schema(description)`，每个字段加 `@Schema(description, example)`，校验注解不可遗漏，禁止硬编码版本号（用 `@Value("${info.app.version}")`），@ApiResponse必须带content，错误响应必须有独立示例
- **命名**：PascalCase(类)、camelCase(方法)、UPPER_SNAKE_CASE(常量)、全小写(包)
- **测试**：多断言链式调用 `.isX().isY().isZ()`，方法名 `shouldX_whenY`
- **编辑前验证（强制）**：先读完整文件 → 编辑后LSP diagnostics → 编译验证 → 运行相关测试

### R10: 任务规划（强制）

多步骤任务（3步以上）必须先创建todo list，规划后再执行，完成后清理。

### R11: 文件管理（强制）

禁止创建临时文件：❌ 重定向到文件（`> output.log`），❌ `.log/.txt/.tmp`；✅ 输出到控制台。任务完成检查是否误创建文件，发现立即删除。

### R12: 依赖管理（强制）

禁止擅自添加依赖。添加前必须提供分析（目的、选型理由、影响评估、替代方案）并获得用户同意。红线：禁止冗余依赖，禁止重复功能包，优先复用现有依赖。

### R13: 记忆文件维护（强制）

| 文件 | 超限处理 |
|---|---|
| activeContext.md | 保留最近30天，删除>90天前 |
| progress.md | 已完成项归档 |
| systemPatterns.md | 合并相似模式 |
| techContext.md | 删除过时配置 |

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

当同一问题解决2次以上，创建skill记录方案。

存放位置：`.agents/skills/[skill-name]/SKILL.md`

创建流程：确认解决 → 用skill-creator创建 → 写入.agents/skills/ → 更新版本号

### R17: 分支管理（强制 - 防止生产事故）

**核心原则：master 是生产分支，永远保持干净（无 AI 文件）。**

| 分支 | 用途 | 允许 | 禁止 |
|---|---|---|---|
| master | 生产环境 | 业务代码/测试/文档/版本号 | AI文件（memory-bank/.agents/.opencode/AGENTS.md） |
| develop | 开发环境 | 业务代码 + AI文件 | 无 |

**master同步规则**：从固定起点 → `git rm -rf` AI文件 → cherry-pick develop（排除 `docs(memory-bank)`） → 冲突处理（memory-bank冲突用 `git rm -f`，版本号冲突用 `--theirs`） → 验证（`git ls-files` 无AI文件 + `./gradlew build` 通过）

**禁止操作**：直接merge develop、在master创建AI文件、在master提交docs(memory-bank)、`git reset --hard develop`、反向cherry-pick（master→develop）、在master直接修改代码

**事故恢复**：立即停止 → 确认污染 → 重置到安全点 → 重新cherry-pick → `--force-with-lease`推送 → 记录事故

**提交前审查清单**：项目一致性（grep现有模式） + Clean Code（无冗余注释） + 测试覆盖 + 编译通过 + 无回归 + 覆盖率≥80%

**常见错误预防**：重复方法→编辑后grep+LSP；ErrorCode冲突→新建前搜索复用；冗余注释→删除"解释做了什么"；测试遗漏→新增逻辑立即创建测试；命名不一致→搜索现有模式

### R18: Git 恢复禁止（强制）

**禁止执行 git reset 恢复到初始状态** — 这会导致工作丢失且不可恢复，必须经由用户确认。

### R19: 资源清理（强制）

占用资源的工具/服务使用后必须关闭。持续服务需后台静默启动，日志单独输出至文件，避免超时/资源堆积。任务完成后立即清理。

### R20: Skills 选择规范（强制）

使用某类型Skills前先列出所有同类Skills，可同时加载多个，不是只能选一个。

### R21: 外部 AI 咨询能力

可使用skills访问 gemini.google.com / perplexity.ai 咨询高级AI（需选择模型）及网络搜索。

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

`memory-bank/`：projectbrief.md（目标）、techContext.md（技术栈）、systemPatterns.md（规范）、activeContext.md（当前）、progress.md（进度）
