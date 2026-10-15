<a id="top"></a>
# SKILL GRAPH — 本机技能索引（人机共读）

> **最后更新**: 2026-10-12
> **技能总数**: **496** 个唯一技能（项目 4 · 内置 17 · 用户级 412 · opencode 配置 63；跨源同名 4 组见 §5）
> **来源**: 项目 `.agents/skills` · 内置 omp `~/.omp/agent/skills` · 内置 superpowers 插件 · 用户级 `~/.agents/skills` · opencode `~/.config/opencode/skills`
> **名称**: 以技能 `SKILL.md` 的 frontmatter `name:` 为准；与目录名不同时在括号内标注目录（如 `cso`（目录 `gstack-cso`））
> **用法**: 先看 §0 快速查找；其余用检索定位（如 `grep -i "单细胞" SKILL_GRAPH.md`），**不要整篇通读**
> **维护**: 本文件只做索引，不复制技能内容；技能增删后按同一规则重新生成；说明由维护者用中文撰写，不复述英文原文

---

## 目录

### 按任务（20 条技能链）

| 任务 | 直达 | 任务 | 直达 |
| --- | --- | --- | --- |
| 规划新功能 | [跳转](#t-01) | 部署上线 | [跳转](#t-11) |
| 修复 bug | [跳转](#t-02) | CLI 封装 | [跳转](#t-12) |
| 代码审查 | [跳转](#t-03) | 深度研究 | [跳转](#t-13) |
| Git / 提交 | [跳转](#t-04) | 上下文交接 | [跳转](#t-14) |
| 测试策略 | [跳转](#t-05) | 会话纪律 / 防护 | [跳转](#t-15) |
| 写作 / 文档 | [跳转](#t-06) | 科研写作 | [跳转](#t-16) |
| UI / 前端 | [跳转](#t-07) | 单细胞 | [跳转](#t-17) |
| 浏览器验证 | [跳转](#t-08) | 分子 ML | [跳转](#t-18) |
| 性能优化 | [跳转](#t-09) | 量子计算 | [跳转](#t-19) |
| 安全审计 | [跳转](#t-10) | 并行子代理 | [跳转](#t-20) |

### 按用途分类

| 分类 | 数量 | 直达 |
| --- | --- | --- |
| 需求与规划 | 16 | [跳转](#c-plan) |
| 方案评审与决策 | 10 | [跳转](#c-design-review) |
| 编码与重构 | 16 | [跳转](#c-impl) |
| 调试与排障 | 10 | [跳转](#c-debug) |
| 代码审查与 Git | 13 | [跳转](#c-review) |
| 测试与验证 | 8 | [跳转](#c-test) |
| UI / 视觉与设计 | 30 | [跳转](#c-ui) |
| 浏览器与检索 | 26 | [跳转](#c-browser) |
| 写作与文档 | 17 | [跳转](#c-doc) |
| 科研与学术 | 26 | [跳转](#c-academic) |
| 生信与组学 | 44 | [跳转](#c-bio) |
| 分子 · 药物 · 实验室 | 24 | [跳转](#c-mol) |
| 数据 · 可视化与 ML | 30 | [跳转](#c-data) |
| 部署 · 运维与安全 | 22 | [跳转](#c-ops) |
| 代理与会话 | 24 | [跳转](#c-agent) |
| CLI 封装与外部工具 | 6 | [跳转](#c-cli) |

### 按章节

| 章节 | 内容 | 直达 |
| --- | --- | --- |
| §0 | 快速查找指南（按任务，20 条链） | [跳转](#s0) |
| §1 | 项目技能（4） | [跳转](#s1) |
| §2 | 内置技能（17） | [跳转](#s2) |
| §3 | 用户级技能（412，按名称排序 —— 检索轴） | [跳转](#s3) |
| §4 | opencode 配置技能（63） | [跳转](#s4) |
| §5 | 跨源同名 | [跳转](#s5) |
| §6 | 家族速查 | [跳转](#s6) |
| §7 | 校正记录 | [跳转](#s7) |


## 用途分类索引

> 按"我要做什么"归类，可从上方目录的分类表直达；一个技能可出现在多个用途下（此处是索引，技能本体只在 §1–§4 收录一次）；未列入的技能按名称在 §3/§4 检索。

<a id="c-plan"></a>
### 需求与规划（16）

把模糊想法变成可执行方案：先问清、再拆解、后落地。

- `brainstorming` — 任何创造性工作开工前必须先做：做功能、改行为、起方案之前先把问题与选项谈清。
- `writing-plans` — 手里已有需求或多步任务规格、尚未动代码时，先写实施计划。
- `executing-plans` — 在当前会话内自己当执行者落地实施计划时使用（含无子代理可用的情况）。
- `think` — 把粗糙想法变成经确认、可决策的完整方案后再动手（架构、重构、交接与选型场景）。
- `to-spec` — 把当前对话整理成规格并发布到项目 issue tracker（不追问，直接成文）。
- `to-tickets` — 把计划、规格或对话拆成一组"曳光弹"式工单，每张都声明其边界与验收。
- `implement` — 依据一份规格或一组工单（tickets）落地实现。
- `implement-spec` — 把 /to-spec 与 /to-tickets 的产物落成代码。
- `planning-and-task-breakdown` — 把需求拆成有序、可实现的任务清单（有规格或明确需求但还没拆解时使用）。
- `spec-driven-development` — 编码前先写规格：新项目、新功能或重大改动且尚无规格时使用。
- `idea-refine` — 把粗糙想法打磨成清晰可执行的概念：结构化发散与收敛，适用于开工前的构思阶段。
- `interview-me` — 通过连续单问挖掘用户真正想要的东西（而非其自以为想要的）。
- `grill-me` — 就方案或设计接受一场不留情面的追问，用以打磨思路。
- `loop-me` — 就"你想构建的工作流"接受规格层面的连续盘问，把需求问清。
- `arbor` — 用假设树细化研究/工程问题：可重复评估器、孤立实验、证据追踪与基准优化。
- `what-if-oracle` — 用结构化"假如…会怎样"情景压测研究计划（不做概率预测）。

<a id="c-design-review"></a>
### 方案评审与决策（10）

上码前用多视角压测方案：CEO / 工程 / 设计 / DX 四种视角与对抗式追问。

- `plan-ceo-review` — 以 CEO/创始人视角评审计划：重述问题、找 10 分产品、挑战前提并扩展范围。
- `plan-eng-review` — 以工程经理视角锁定执行计划：架构、数据流、图示、边界与测试。
- `plan-design-review` — 以设计师视角评审计划：逐维度 0–10 打分并说明理由。
- `plan-devex-review` — 交互式开发者体验计划评审：开发者画像、竞品对标与 DX 设计。
- `autoplan` — 自动跑完整计划评审：无需逐项确认，一次性拿到多视角评审结论。
- `plan-tune` — 调整提问敏感度与开发者画像（gstack 的观察式自调优）。
- `doubt-driven-development` — 关键决策先过一遍全新上下文的对抗式评审：交叉盘问假设、压测隐藏失败模式。
- `consciousness-council` — 多视角议事：模拟不同思维原型辩论决策与取舍，分离证据/假设/价值并给出条件化建议。
- `software-engineering-laws-and-philosophy` — 用 56 条软件工程定律指导技术决策、架构设计、规划与团队管理。
- `grilling` — 就计划、决策或想法对用户进行高强度盘问，压力测试其思考。

<a id="c-impl"></a>
### 编码与重构（16）

实现、简化，并守住质量底线。

- `tdd` — 测试先行开发：用户要求 test-first 或提到红绿重构时使用。
- `test-driven-development` — 写任何功能或修任何 bug 的实现代码之前使用：先写失败测试。
- `incremental-implementation` — 把改动切成薄而可验证的小步交付，适用于任何需要控制风险的功能或重构。
- `constraint-driven-development` — 把项目质量标准写成契约（CONSTRAINTS.md）并阻止代理悄悄降低门槛。
- `source-driven-development` — 让每个实现决策都落在官方文档上：动手前先查证官方说法而非凭记忆。
- `prototype` — 造一个用完即弃的原型来回答设计问题（验证某方案是否可行、手感如何）。
- `full-output-enforcement` — 覆盖模型默认截断行为：强制完整代码输出、禁止占位符、妥善处理 token 上限切分。
- `code-simplification` — 不改变行为地简化代码：可读性与可维护性差、复杂度堆积时使用。
- `codebase-design` — 深模块设计的共同语言：模块接口、加深机会、接缝位置、可测性与 AI 可导航性。
- `domain-modeling` — 建立并打磨项目领域模型：术语表 GLOSSARY.md、ADR 讨论与代码术语统一。
- `api-and-interface-design` — 接口与模块边界设计：REST/GraphQL 端点、类型契约、前后端边界与稳定演进。
- `improve-codebase-architecture` — 扫描代码库找出"加深模块"的机会，产出可视化 HTML 报告并逐个推翻或采纳。
- `setup-ts-deep-modules` — 在 TypeScript 仓库接入 dependency-cruiser，使每个包成为隐藏实现细节的深模块。
- `migrate-to-shoehorn` — 把测试里的 `as` 类型断言迁移到 @total-typescript/shoehorn。
- `deslop-shared-libs` — 在近期改动中发现值得抽出的共享代码，并只推荐真正可共享的部分。
- `subagent-driven-development` — 用子代理执行含独立任务的实施计划时使用。

<a id="c-debug"></a>
### 调试与排障（10）

先根因后修复：症状定位、日志追踪与 CI 失败诊断。

- `systematic-debugging` — 遇到 bug、测试失败或异常行为时，在提出修复方案之前使用的系统排查流程。
- `diagnosing-bugs` — 难缠 bug 与性能回归的诊断循环：用户说"诊断/调试这个"或报告坏了/抛错/失败/变慢时使用。
- `diagnosing-superpowers` — 技能会话本身出问题时复盘：重复劳动、计划被忽略、技能没触发、耗时或花费异常。
- `hunt` — 先定位根因再动手修：报错、崩溃、回归或"以前能跑"时使用；不用于代码审查或新功能。
- `debugging-and-error-recovery` — 系统化根因调试：测试失败、构建断裂、昨天还能跑今天不行、行为不符预期时使用。
- `investigate` — 系统化调试（四阶段）：调查 → 分析 → 假设 → 实施，先根因后修复。
- `troubleshooting` — 用 Chrome DevTools MCP 排查连接与目标（page/target）异常。
- `memory-leak-debugging` — 排查 JavaScript/Node.js 内存泄漏：高内存占用与 OOM，含堆快照分析。
- `ci-fix` — 诊断并修复 GitHub Actions 失败：看 workflow 运行与日志、定位根因、最小修复并推到修复分支。
- `problem-report` — 以证据诊断问题并分轮盘问：代码锚定的报告（前情 / 代码 / 取证），先诊断再动手。

<a id="c-review"></a>
### 代码审查与 Git（13）

合入前的审查、提交规范与分支收尾。

- `code-review` — 以固定点（commit/分支/tag）为基准做双轴审查：Standard（是否符合本仓库规范）与 Spec（是否实现需求）。
- `code-review-and-quality` — 多维代码审查，合入任何变更前使用（含自己、他人或代理写的代码）。
- `requesting-code-review` — 完成任务、实现大功能或合入前，请求审查以确认是否符合需求。
- `receiving-code-review` — 收到审查意见后、动手改之前使用：要求技术论证与验证，不做表演式认同。
- `review` — 上库前 PR 评审：对照基线分支检查 SQL 安全、LLM 信任边界与回归风险。
- `check` — 审查 diff、PR、发布就绪度与发布后续；不做根因调试，也不审文案。
- `code-reviewer` — 审查 Java 25 / Spring Boot 4 代码与 PR：迁移风险、模块边界越界、JSpecify 空安全、安全与性能回归、Spring Data 陷阱。
- `pr` — 撰写 PR 描述正文（按项目约定组织变更说明）。
- `create-pull-request` — 按项目约定创建 GitHub PR：分析提交、整理分支并用 gh CLI 提交审阅。
- `git-workflow-and-versioning` — git 工作流规范：提交、分支、冲突处理、把混乱工作区拆成干净原子提交、PR 与版本发布。
- `git-guardrails-claude-code` — 为 Claude Code 配置 git 危险命令拦截钩子（push、reset --hard、clean、branch -D 等）。
- `setup-pre-commit` — 在当前仓库配置 Husky pre-commit（lint-staged、类型检查与测试）。
- `finishing-a-development-branch` — 实现完成且测试通过后，决定分支如何收尾合入时使用。

<a id="c-test"></a>
### 测试与验证（8）

测试先行、测试体检与端到端验证。

- `tdd` — 测试先行开发：用户要求 test-first 或提到红绿重构时使用。
- `test-audit` — 找出低价值或重复的测试及其维系的"仅供测试"代码；默认只报告，批量删除需你确认。
- `qa` — 对网站或 Web 应用做 QA 并给 1–5 分（5=无懈可击）与证据；发现问题时给修复建议。
- `qa-only` — 只报告不修复：产出浏览器/API/CLI/任务/worker/webhook 的结构化 bug 报告。
- `webapp-testing` — 用 Playwright 测试本地 Web 应用：验证界面、截图与收集日志。
- `browser-testing-with-devtools` — 用 Chrome DevTools 在真实浏览器中测试：查 DOM、抓控制台错误、看网络请求、做性能与视觉校验。
- `test-auth-bootstrap` — 在开发/测试环境自动取得可用凭据（或完整账号），使端到端验证不被登录/验证码卡住。
- `verification-before-completion` — 声称"完成/修好/通过"之前必须运行验证命令并核对输出，先有证据再下结论。

<a id="c-ui"></a>
### UI / 视觉与设计（30）

界面设计、视觉打磨、动效与无障碍。

- `ui` — 构建与改造生产级 UI：组件、排版、布局与视觉一致性（不含后端逻辑）。
- `design-taste-frontend` — 前端艺术指导（tasteskill v2）：基于 247 个精选站点研究的构图、字体、配色与摄影方向。
- `design-taste-frontend-v1` — 上一版 taste-skill，仅在需要与其行为逐字兼容的旧项目中保留使用。
- `frontend-ui-engineering` — 构建生产级、无障碍、自适应的界面：组件、布局、状态管理与 WCAG 达标。
- `design-consultation` — 设计咨询：为新项目建立设计体系、品牌规范或 DESIGN.md。
- `design-html` — 把定稿的设计或方案落成可交付的 HTML/CSS。
- `design-review` — 视觉 QA 与设计打磨（不做事前的方案设计评审）。
- `design-shotgun` — 设计发散：一次给出多个 UI 方向与变体供挑选。
- `impeccable` — 界面设计的通用改进流程：设计、重塑、评审、审计、打磨、澄清、精简与加固。
- `minimalist-ui` — 简洁编辑风界面：暖色单色调、字体对比、扁平 bento 网格与低饱和配色。
- `industrial-brutalist-ui` — 工业粗野风格界面：瑞士印刷版式与军用终端美学的融合——刚性网格、极简配色。
- `high-end-visual-design` — 按高端设计机构标准做界面：精确字体、间距、阴影、卡片结构与动效，并封堵廉价感默认样式。
- `gpt-taste` — 高级 UX/UI 与 GSAP 动效工程：Python 驱动的版面随机化、AIDA 结构、编辑式字体与 bento 网格。
- `oil-ui` — 网站/应用/仪表盘/组件的界面设计、改进与评审：先探索不同设计方向再落地。
- `oiloil-ui-ux-guide` — 整理项目 UI/UX 规范并产出 design-spec.md，或对现有界面做优先级排序的评审；单个颜色字号这类小问题不适用。
- `stitch-design-taste` — 为 Google Stitch 生成 DESIGN.md：把高端 UI 标准写成代理可执行的语义设计系统。
- `emil-design-eng` — Emil Kowalski 的 UI 打磨哲学：组件设计、动效取舍与让产品"手感好"的细节。
- `motion-spec` — 把模糊动效诉求（"丝滑一点""高级感""加点动画"）写成明确的动效规格：时长、曲线、编排与降级。
- `animation-vocabulary` — 反查动画/动效术语：把"弹一下""橡皮筋滚动"这类描述对应到准确名称，供检索与沟通。
- `review-animations` — 按高标准审查动画/动效代码（源自 Emil Kowalski 的设计工程标准）。
- `draw-ui` — 设计 App、后台、游戏界面与完整落地页并产出 UI 设计稿；也可把设计稿或截图还原为 HTML/CSS、现有前端项目或微信小程序。不用于插画、海报、故事板与纯图片编辑。
- `image-to-code` — 视觉要求高的网页任务：先生成设计稿，再据此产出代码（图像到代码的完整链路）。
- `generate-image` — 经 OpenRouter 图像 API 生成或编辑图片（Gemini/Seedream/Recraft/GPT-Image）：照片、插画、概念图、标志与合成。
- `brandkit` — 生成高端品牌视觉：品牌规范板、标志系统、识别手册与视觉世界提案。
- `redesign-existing-projects` — 把现有网站/应用升级到高品质：审计现状、识别廉价 AI 套路并给出改造方案。
- `web-design-guidelines` — 界面评审：可访问性、设计一致性与 UX 问题清单。
- `web-accessibility-audit` — 审计 WCAG 无障碍合规并给出修复建议与优先级。
- `a11y-debugging` — 用 Chrome DevTools 排查无障碍问题：语义 HTML、ARIA、焦点态、键盘导航、点击区域与对比度。
- `infographics` — 用 OpenRouter 上的 Nano Banana 2 生成或评审信息图（统计摘要、时间线、流程）。
- `diagram-design` — 生成品牌化专业图：架构与增量架构、现状图、流程、时序、状态机、ER、时间线、泳道、象限、雷达、树状与组织结构等。

<a id="c-browser"></a>
### 浏览器与检索（26）

真机浏览器自动化、网页抓取与多源检索。

- `browse` — 驱动真实浏览器（Aside）：打开页面、阅读、走完流程、截图并检查控制台错误。
- `browser-use` — 直接的浏览器控制（CDP）：自动化、抓取、测试、截图与站点操作。
- `browser-harness` — 经 CDP 驱动真实浏览器：点击输入、导航、已登录会话、JS 渲染或反爬页面（纯静态抓取请用 curl）。
- `chrome-devtools` — 经 MCP 使用 Chrome DevTools：调试、排查与浏览器自动化（性能与网络分析）。
- `chrome-devtools-cli` — 用命令行/脚本方式经 Chrome DevTools 完成浏览器自动化任务。
- `opencli-browser` — 用 opencli 驱动真实 Chrome：检视页面、填表、点击与抓取数据。
- `opencli-usage` — OpenCLI 会话的入口总览：都有哪些能力、如何发现适配器与命令、输出格式。
- `opencli-adapter-author` — 为新站点编写 OpenCLI 适配器，或给现有站点增加命令。
- `opencli-autofix` — OpenCLI 命令失败时的自动修复：定位并修补失效适配器。
- `scrape` — 用已登录的真实浏览器会话抓取页面数据（只读）。
- `dokobot` — 读取 JS 渲染的动态页面与 SPA 内容。
- `doko-search` — 用本地浏览器在 Google/Bing/DuckDuckGo 上免费检索。
- `doko-summarize` — 通过真实浏览器抓取并总结网页要点。
- `doko-translate` — 整页翻译并尽量保留结构、格式、表格与代码块。
- `doko-research` — 多轮网页研究：交叉核对来源并产出结构化、带引用的报告。
- `smart-search` — 基于 opencli 的智能检索路由：按站点、社交、技术资料、新闻、购物、金融或中文内容选择最佳检索路径。
- `ai-chat-browser` — 在 Gemini / Perplexity 网页版界面中自动化提问与检索（非 API 方式）。
- `user-chrome-tabs` — 操作用户真实 macOS Chrome 与其既有登录会话中的标签页。
- `setup-browser-cookies` — 把真实 Chromium 的 cookie 导入无头浏览会话，并做登录校验。
- `remote-browser` — 在受限沙箱中用 Browser Use CLI 控制隔离的云浏览器。
- `cloud` — Browser Use Cloud 文档参考：托管浏览器自动化 API（v2–v4）与 Python/TS SDK 用法。
- `x402` — 用 Browser Use Cloud 的加密/USDC 钱包完成支付（无需 API Key 的方案）。
- `exa-search` — 用 Exa 检索科研与技术网页，并批量抽取网页/PDF 正文（支持学术类目与域名过滤）。
- `parallel-web` — 用 Parallel CLI 做网页检索、URL 抽取、深度研究、结构化数据补全与实体发现。
- `read` — 抓取 URL 与 PDF 并总结或输出干净 Markdown（读、引用、保存网页时使用）。
- `liteparse` — 本地文档与 PDF 解析：输出带边界框的空间文本，便于版面相关抽取。

<a id="c-doc"></a>
### 写作与文档（17）

人读文档、写作法、排版与图表表达。

- `write` — 起草、编辑或本地化中英文文稿（不含代码注释）。
- `docs-update` — 代码变更后同步用户文档：更新/审阅文档、处理文档变更或分析近期提交的文档影响。
- `documentation-and-adrs` — 记录决策与文档：写 ADR、公共 API 变更说明、功能发布说明与给未来工程师的背景。
- `document-generate` — 为缺失文档补写教程、操作指南与模块说明。
- `document-release` — 发布前审计并同步文档与代码差异。
- `writing-beats` — 写作"节拍"：把内容拆成有推进感的段落与节拍再成文。
- `writing-fragments` — 写作"碎片"：先积累片段与素材，再组装成完整文稿。
- `writing-shape` — 写作"成形"：为素材确定整体结构与叙事形状。
- `writing-guidelines` — 审查文档与散文的写作规范（文风、结构、可读性）。
- `writing-for-agents` — 面向代理写文档：技能、AGENTS.md/CLAUDE.md 与规则文件的写法与维护边界。
- `writing-skills` — 新建、修改技能，或部署前验证技能可用时使用。
- `kami` — 用 Kami 模板排版专业文档：简历、单页、白皮书、信函与作品集。
- `make-pdf` — 把 Markdown 转成出版级 PDF（1 英寸页边距、智能分页与页码）。
- `markdown-mermaid-writing` — 写科研向 Markdown 文档与 Mermaid 图（流程、关系、时间线与结构）。
- `documd-visuals` — 用 Markdown 做文本驱动可视化：图表、示意图、卡片、架构与页面布局（含可靠性/运营类图表）。
- `diagram` — 把流程、架构、状态机画成图并导出 SVG/Excalidraw。
- `venue-templates` — 按目标会议的模板与格式要求排版论文、海报、基金与 LaTeX 稿件。

<a id="c-academic"></a>
### 科研与学术（26）

文献检索、稿件写作、投稿与审稿全流程。

- `research` — 对高质量一手来源做调研，并把结论落成 Markdown 文件存档。
- `research-lookup` — 为科学稿件或研究简报汇编最新学术证据（需明确要求时才用）。
- `researchwrite` — 基于已有证据撰写、修改或审查研究提案、开题报告与研究计划。
- `research-grants` — 科研基金申请的准备与评审支持（NSF/NIH/DOE/DARPA 及台湾 NSTC 等）。
- `literature-review` — 用 PubMed/arXiv/bioRxiv/Semantic Scholar 等做系统综述、范围综述与叙述性综述。
- `citation-management` — 文献引用管理：检索 OpenAlex/PubMed/Scholar、核对元数据、校验引用并生成规范 BibTeX。
- `paper-lookup` — 跨 18 个学术 API 检索论文、预印本、引用、开放全文与仓储记录。
- `paperzilla` — 通过 pz 管理 Paperzilla 研究源：读项目、检索订阅流、取推荐与经典论文。
- `paperclip` — 检索并阅读生物医学论文、FDA/PMDA/EMA 文件、临床试验与蛋白记录。
- `bgpt-paper-search` — 按主题或 DOI 检索 BGPT 论文并取全文级论断证据（实验、统计、范围、局限与出处）。
- `peer-review` — 起草有证据边界的建设性同行评审与结构化稿件评估。
- `scholar-evaluation` — 对学术作品做定性优先、可溯源的成长性评审与低风险评分卡审计。
- `scientific-writing` — 撰写、修改与审查科学稿件或报告，标注证据出处与报告规范。
- `scientific-visualization` — 用 Matplotlib/Seaborn 等产出真实、无障碍、可出版的科研图表（含不确定度表达）。
- `scientific-slides` — 制作科研汇报与会议幻灯片（PowerPoint/演示文稿）。
- `scientific-schematics` — 用 Nano Banana 2 生成科学示意图草稿并迭代精修。
- `scientific-critical-thinking` — 评估科学主张与证据质量：实验设计有效性、偏倚、混杂与 GRADE 框架。
- `scientific-brainstorming` — 科学创意发散：独立产出、结构化讨论、显式区分事实与假设。
- `hypothesis-generation` — 把观察转成可检验的科学问题：候选假设、竞争解释、因果/相关主张、判别性预测与预注册分析计划。
- `experimental-design` — 数据采集前设计实验：选设计、随机化、区组与处理组合排布，保证结果可解释。
- `statistical-analysis` — 研究数据的统计分析指引：检验选择、假设检查、效应量与功效。
- `statistical-power` — 研究规划阶段的样本量与统计功效计算（需要多少受试者才能检验出效应）。
- `nature-academic-search` — 跨源检索文献、核对与管理引用，构建 MeSH 检索式与引用影响力核查。
- `nature-citation` — 为稿件论断查找并验证 Nature/CNS 系列文献，做论断到来源的映射与补引。
- `nature-downloader` — 合法获取学术全文：CNKI 机构访问、英文开放获取与出版社平台下载。
- `nature-reader` — 生成中英对照的论文精读稿：正文、图表与公式对齐。

<a id="c-bio"></a>
### 生信与组学（44）

单细胞、基因组、蛋白与影像组学的分析流水线。

- `scanpy` — 单细胞 RNA-seq 标准流程：质控、标准化、HVG、PCA/UMAP/t-SNE、聚类与注释（可在 RDS/h5ad 间转换）。
- `anndata` — 单细胞注释矩阵与 .h5ad / Zarr 数据格式：读取、切片与 scverse 生态衔接（分析流程用 scanpy）。
- `scvi-tools` — 单细胞组学概率模型：scVI 批次整合、scANVI 注释与 totalVI 等。
- `scvelo` — RNA 速率分析：从 spliced/unspliced 计数拟合动力学模型并推断潜在时间。
- `biopython` — Biopython 序列处理：读写 FASTA/GenBank/PDB、系统发育、经 Bio.Entrez 访问 NCBI/PubMed。
- `bioservices` — 用 Python 调 UniProt、KEGG、ChEMBL、Reactome、QuickGO、UniChem 等，做跨库注释与标识映射。
- `pysam` — 读写、查询与过滤 SAM/BAM/CRAM/VCF/BCF/索引文件（HTSlib 封装）。
- `pydeseq2` — 用 PyDESeq2 做 bulk RNA-seq 差异表达：计数校验、设计公式与结果解读。
- `bulk-rnaseq` — bulk RNA-seq 定量到差异表达的准备：nf-core/rnaseq 或 Salmon/STAR/featureCounts，含链向与参考来源校验。
- `deeptools` — NGS 分析工具箱：BAM 转 bigWig、相关性/PCA/指纹 QC、TSS 与 peak 的热图与剖面图。
- `nextflow` — 编写、运行与调试 Nextflow DSL2 与 nf-core 流程（.nf、channel、配置与报错定位）。
- `qiime2-amplicon` — 把双端 16S 扩增子数据跑成 QIIME 2 的 ASV 与物种注释，保留可追溯产物。
- `gget` — 用 CLI/Python 快速查询 20+ 生物信息库：基因信息、BLAST/BLAT、病毒序列、PDB/mmCIF、富集与 OpenTargets 等。
- `cellxgene-census` — 查询 CZ CELLxGENE Census 的公开单细胞与空间转录组数据（版本化元数据、表达切片与下载地址）。
- `onekgpd` — 查询 1000 Genomes 项目（3202 个全基因组个体，GRCh38）的个体级变异、携带者、基因型与亲缘关系。
- `tiledbvcf` — 用 TileDB-VCF 存取基因组变异调用：样本级 VCF/BCF 摄取、区域查询与队列导出。
- `gtars` — 本地基因组区间模型与集合代数：重叠与计数、共识与覆盖、分词、片段处理与 refget/BEDbase 规划（Python/Rust/CLI）。
- `geniml` — 本地基因组区间模型与集合代数：校验 BED/universe 契约，规划 Region2Vec/scEmbed 运行并检查一致性。
- `genomic-coordinates` — 坐标约定转换、变异表示规范化与比较、装配版本/染色体命名不一致的提前发现。
- `genomic-intelligence` — 用托管 DNA 语言模型从序列直接预测调控特征、基因结构与表达（六类任务，REST 与 MCP，无需本地 GPU）。
- `pathway-enrichment` — 对基因列表或排序基因做通路/基因集富集分析，并解读结果（GSEA/GO/KEGG 等）。
- `depmap` — 取用并分析 DepMap 数据：CRISPR Chronos 基因效应、癌症模型注释、组学标志物与 PRISM 药敏。
- `mageck` — 用 MAGeCK 分析混合 CRISPR 筛选的 FASTQ 与 guide-count 矩阵：文库校验、富集与命中排序。
- `flowio` — 读写 FCS 2.0/3.0/3.1 流式细胞术文件：元数据与通道检视、NumPy 事件提取、多数据集与导出（门控分析用 flowkit）。
- `flowkit` — 流式细胞术分析：溢漏补偿、logicle/双指数变换、层级门控、GatingML 与 FlowJo 10 工作区。
- `histolab` — 病理全片图像处理：切片检视、组织掩膜、随机/网格/评分抽样与 H&E 染色归一，产出可供训练的图块集。
- `pathml` — 本地计算病理研究：切片加载与切块、预处理与质控、h5path 与深度学习流程衔接。
- `omero-integration` — 用 omero-py/BlitzGateway/CLI 检视并自动化 OMERO 服务器上的显微图像流程（含注释与 ROI）。
- `cellprofiler` — 可复现的 CellProfiler 显微图像流程：细胞核分割、计数与单目标荧光测量（无头批处理）。
- `deepspot-m` — 由 H&E 病理图生成全转录组虚拟空间表达（DeepSpot-M）：224×224 切片、按基因符号查询。
- `bids` — 组织、查询、校验与转换 BIDS 脑影像数据集（MRI/EEG/MEG/iEEG/PET 等）。
- `nwb-conversion` — 用 NeuroConv/PyNWB 把神经科学采集数据转成 NWB 标准文件，保留元数据与时间对齐。
- `neuropixels-analysis` — Neuropixels 胞外记录全流程：SpikeInterface 读取、Kilosort4 分选与单元质量筛选。
- `relion` — 校验并执行 RELION 单颗粒冷冻电镜精修与半图后处理（STAR、光学、掩膜与 FSC）。
- `phylogenetics` — 用 MAFFT 比对 + IQ-TREE 最大似然（含 FastTree）构建并分析系统发育树。
- `etetoolkit` — 用 ETE 4 分析、比较、注释与可视化系统发育树：Newick/Nexus 读写、拓扑编辑、RF 比较与基因树事件。
- `scikit-bio` — 生物数据工具箱：序列分析、比对、系统发育树、多样性指标与生物文件 I/O。
- `pacsomatic` — 准备并启动 nf-core/pacsomatic 的肿瘤-正常配对 PacBio HiFi 流程。
- `waypoint-bio` — 微生物组基础模型与基准：Waypoint 检查点、Compass 基准与丰度表分析。
- `latchbio-integration` — 在 Latch 平台上构建、注册、调试并运行生信流程：Python SDK、CLI、Latch Data 与 Registry。
- `dnanexus-integration` — 在 DNAnexus 上构建并运行可复现的基因组流程：dx CLI、dxpy、app/applet、原生工作流与 dxCompiler。
- `lamindb` — 用 LaminDB 管理生物数据集与模型：产物登记、血缘追踪、schema 与关联查询。
- `adaptyv` — 经 Adaptyv Foundry API/SDK 设计蛋白表征实验：选靶点目录、估成本、提交序列、跟踪进度并取结果。
- `folklore-variant-evidence` — 取 ClinGen 基因-疾病有效性断言，并核对某 GRCh38 胚系 SNV/小 indel 的公开证据与文献。

<a id="c-mol"></a>
### 分子 · 药物 · 实验室（24）

分子建模、药物筛选与实验自动化（含实验平台与自制硬件）。

- `rdkit` — 化学信息学底层控制：SMILES/SDF 解析、描述符、指纹与反应处理（更简单场景用 datamol）。
- `datamol` — RDKit 的 Pythonic 封装：SMILES 解析、标准化、描述符、指纹、聚类与 3D 构象（常规药物发现首选）。
- `deepchem` — 用 DeepChem 做分子性质预测与 MoleculeNet 流程：SMILES 特征化、骨架划分、图模型与预训练迁移。
- `medchem` — 药物化学过滤与化合物初筛：类药规则（Lipinski/Veber/CNS）、结构警示与 PAINS。
- `molfeat` — 用 Molfeat 生成分子特征，用于 QSAR/QSPR、相似性与虚拟筛选。
- `pytdc` — 经 PyTDC 使用 Therapeutics Data Commons：数据集检索、任务与评测指标。
- `diffdock` — 用 DiffDock/DiffDock-L 预测蛋白-小分子结合构象：批量对接、构象筛选、置信度解读与虚拟筛选。
- `esm` — 用 Biohub esm SDK 做 ESM3 蛋白生成、ESMC 嵌入与 ESMFold2 全原子折叠（本地推理或托管客户端）。
- `tamarind` — 使用 Tamarind 平台上的开源分子设计与结构生物学工具（AlphaFold/RFdiffusion/Boltz/ESMFold 等）。
- `rowan` — 用 Rowan 云端分子建模与药物化学工作流（Python API）：pKa、构象、对接与 MD。
- `molecular-dynamics` — 用 OpenMM/MDAnalysis 搭建并分析分子动力学模拟（蛋白与配体体系、轨迹分析）。
- `primekg` — 查询固定版本 PrimeKG 精准医学知识图谱：基因、药物、疾病与表型的类型化关联与多跳路径。
- `alphagenome` — 查 GRCh38 单核苷酸变异的 AlphaGenome 预计算效应（AVI 分、Phred、18 项 SHAP 归因与各模态分数）。
- `glycoengineering` — 糖基化分析与改造：扫描 N-糖基化序列、S/T 富集区、糖基证据，准备 NetNGlyc/NetOGlyc/GlycoSHIELD 流程。
- `ncats-arax` — 查询 NCATS Translator ARAX 生产 API，取有界、带类型与出处的单跳/受限路径关系。
- `pkpd-modeling` — 药代/药效建模：非房室分析、房室与群体 PK、TMDD、PBPK 与给药方案模拟。
- `benchling-integration` — 用 Benchling SDK / v2 REST API 自动化实验室数据：登记实体、库存、ELN 条目、工作流与数仓查询。
- `labarchive-integration` — 对接 LabArchives ELN 与 Inventory API：区域端点选择、签名请求与条目读写。
- `opentrons-integration` — 编写、评审、迁移、仿真与排错 Opentrons 官方 Python Protocol API v2 协议。
- `pylabrobot` — 开发与评审 PyLabRobot 实验自动化：移液方案、离线仿真与硬件对接。
- `ginkgo-cloud-lab` — 在 Ginkgo Cloud Lab 上选方案、备输入、核价与下单：无细胞/大肠杆菌/毕赤酵母表达与多种读出方式。
- `lab-hardware-cad` — 用 build123d 参数化设计自制实验室硬件，并导出 STEP/STL 等加工文件。
- `protocolsio-integration` — 按 protocols.io 官方 REST/MCP 契约读取、校验并安全导出实验方案数据。
- `fictiv` — 在浏览器中完成 Fictiv（按需制造平台）全流程：上传 CAD、配置工艺材料与公差、即时/人工报价与下单。

<a id="c-data"></a>
### 数据 · 可视化与 ML（30）

数据处理、统计建模、绘图与模型训练。

- `polars` — 高性能 DataFrame：Python ETL、分析与从 pandas 迁移，支持表达式与惰性执行。
- `polars-bio` — 在 Polars DataFrame 上做基因组区间运算：重叠、最近邻、合并、覆盖与差集，读写 BED/VCF/BAM。
- `dask` — 用 Dask 扩展 pandas/NumPy 与自定义流程：分块、调度器、DataFrame/Array/Bag/Futures 与分布式诊断。
- `vaex` — 处理超内存的 HDF5/Arrow/CSV/Parquet 数据集：表达式计算与统计。
- `statsmodels` — 用 statsmodels 拟合与诊断统计模型：OLS、GLM、离散与混合模型、ARIMA/SARIMAX。
- `matplotlib` — 用 Matplotlib 精细控制科研绘图（自定义元素与复杂多面板布局）。
- `seaborn` — 统计可视化：分布、关系、分类与热图（与 pandas 集成）。
- `umap-learn` — 非线性降维与二维/三维可视化：UMAP、DensMAP、参数化 UMAP 与嵌入聚类。
- `networkx` — 用 NetworkX 创建、分析并可视化复杂网络与图结构。
- `sympy` — 精确符号数学：代数、微积分、方程求解与符号线性代数。
- `matlab` — MATLAB / GNU Octave 数值工作流：数组、表格与时间数据、迁移与规划。
- `pycalphad` — 从热力学数据库计算有限温度 CALPHAD 平衡：相分数、相组成与稳定性扫描。
- `pymatgen` — 用 pymatgen 分析、校验、转换与变换材料结构与计算材料数据（相图、电子结构）。
- `timesfm-forecasting` — 用 Google TimesFM 做零样本时间序列预测（含 CSV 规整、分位数与协变量）。
- `aeon` — 时间序列机器学习：分类、回归、聚类、预测、异常检测、分段与相似性检索。
- `scikit-learn` — 经典机器学习：分类、回归、聚类、预处理与 Pipeline 搭建。
- `scikit-survival` — 右删失与竞争风险生存分析：模型构建、评估与审计。
- `shap` — 用 SHAP 解释与审计模型预测：选择 explainer/masker 与全局/局部归因。
- `pymc` — 用 PyMC 构建并检验贝叶斯模型：层次模型、NUTS、变分推断与 PSIS-LOO 诊断。
- `pymoo` — 用 pymoo 求解与验证单目标/多目标/超多目标优化（NSGA-II/III、MOEA/D、约束与 Pareto 前沿）。
- `uncertainty-and-units` — 物理单位换算、GUM 不确定度传播、量纲检查与数量级合理性判断。
- `exploratory-data-analysis` — 对受支持的科学数据文件做有界本地探索分析：CSV/TSV/JSON 画像，可选 NumPy/HDF5/FASTA/图像元数据与缺失/泄漏检查。
- `analysis-artifacts` — 数据分析过程中同步产出可复现产物：SQL、Python 可视化与汇总表（BigQuery 深挖场景）。
- `dbt-model-index` — 提供 dbt 模型（BigQuery 表）查询索引，把模糊的数据问题落到具体表。
- `database-lookup` — 按明确端点、过滤、分页与出处查询公开数据库 API，保证科学/监管/金融事实可复现。
- `usfiscaldata` — 查询美国财政数据：国债、财政部报表、联邦支出与收入、证券。
- `market-research-reports` — 产出可溯源的市场研究报告与假设驱动的市场规模/预测情景。
- `pytorch-lightning` — 用 Lightning 组织 PyTorch 代码：LightningModule、Trainer、回调与分布式扩展。
- `transformers` — Hugging Face Transformers：加载 Hub 模型、pipeline 推理、文本生成与 Trainer 微调。
- `optimize-for-gpu` — 在 NVIDIA 硬件上加速科学 Python，并验证结果正确且确实更快（CUDA/RAPIDS/CuPy/Numba）。

<a id="c-ops"></a>
### 部署 · 运维与安全（22）

发布上线、性能与可观测性、安全与合规。

- `deploy-to-vercel` — 部署应用到 Vercel：上线、生成预览部署或给出访问链接。
- `vercel-cli-with-tokens` — 用令牌（非交互）方式部署与管理 Vercel 项目。
- `vercel-optimize` — 降低 Vercel 账单并优化性能指标（含 Core Web Vitals）。
- `vercel-react-best-practices` — React/Next.js 性能最佳实践：包体积、数据获取与渲染优化。
- `vercel-composition-patterns` — 组件组合模式重构：消除布尔 props 泛滥，改进复合组件与可复用结构。
- `vercel-react-view-transitions` — React/Next.js 页面转场与共享元素动画（ViewTransition）。
- `vercel-react-native-skills` — React Native / Expo 移动端组件、动画与性能优化。
- `shipping-and-launch` — 生产上线准备：发布清单、灰度与回滚策略、上线前检查。
- `land-and-deploy` — 落地并部署：合并 PR、等 CI 与部署完成，再用 canary 检查验证生产健康。
- `landing-report` — 只读的队列看板：显示当前有哪些版本槽位被打开的 PR 占用。
- `canary` — 上线后监控：验证灰度发布、发现回归。
- `ship` — 发布工作流：合并基线分支、跑测试、评审 diff、升版本、更新 CHANGELOG 并提交。
- `setup-deploy` — 为 /land-and-deploy 配置部署平台与参数（Fly.io、Render、Vercel 等）。
- `ci-cd-and-automation` — 搭建与改造 CI/CD：质量门禁、测试运行器配置与部署策略。
- `observability-and-instrumentation` — 给代码加可观测性：日志、指标、追踪与告警，让线上行为可诊断。
- `performance-optimization` — 全栈性能优化：前端、后端、查询与数据库；性能不达标或变慢时使用。
- `benchmark` — 网页性能、Core Web Vitals 与 Lighthouse 基准（不用于 AI 评测）。
- `web-performance-audit` — 剖析并优化页面速度与 Lighthouse 分数（加载与渲染瓶颈定位）。
- `debug-optimize-lcp` — 用 Chrome DevTools 调试并优化 LCP：慢加载、Core Web Vitals 问题定位。
- `terraform-style-check` — 按 HashiCorp 官方风格约定编写与审查 Terraform HCL。
- `security-and-hardening` — 代码安全加固：审计输入处理、认证授权、OWASP 漏洞与依赖安全。
- `cso` — 安全审计：静态发现为主，具备条件时补充复现与修复候选（OWASP、威胁建模、漏洞复核）。

<a id="c-agent"></a>
### 代理与会话（24）

技能体系本身、会话交接与代理协作。

- `using-superpowers` — 会话开始时用：规定任何回应（含追问）之前先定位并加载相关技能。
- `using-agent-skills` — 会话开始或需要判断该用哪个技能/流程时，给出技能加载与使用规范。
- `dispatching-parallel-agents` — 有 2 个以上互不依赖、无共享状态的任务时，分派并行子代理。
- `chief-of-staff` — 在单次会话内协调多个子代理，推进一个长期目标。
- `handoff` — 把当前对话压缩成交接文档，供另一个代理接手继续。
- `claude-handoff` — 把当前对话整理成交接文档，交给新的后台代理立即接手。
- `context-save` — 保存当前工作上下文：Git 状态、已定决策与待办，供后续会话无缝接手。
- `context-restore` — 恢复此前保存的上下文（优先当前分支），跨会话/工作区继续工作。
- `health` — 体检 agent 配置与 AI 可维护性：指令漂移、hooks/MCP、验证器缺失；不查应用 bug，也不做 PR 审查。
- `retro` — 对一次编码会话做复盘（哪些有效、哪些浪费、下次怎么改）。
- `scheduler` — 设置本机定时提醒与轻量本地任务（不涉及云端代理）。
- `skillify` — 把最近一次成功的 /scrape 流程固化成磁盘上的常驻 browser-skill，供后续直接调用。
- `gstack` — gstack 技能套件的总入口：把任何 gstack 请求路由到正确的子技能（规划、评审、QA、发布…）。
- `gstack-upgrade` — 升级 gstack 到最新版：识别全局安装还是仓库内置，执行升级并说明变更。
- `pi-agent` — 安装、配置与使用 Pi（极简终端编码代理），含 provider 配置与嵌入方式。
- `claude-code` — 调用 Claude Code 做代码评审、"claude review"、对抗式质询或征求第二意见。
- `codex` — 调用 Codex 做代码评审、对抗式挑战、咨询或第二意见。
- `pair-agent` — 把远程 AI 代理与你的浏览器配对：一条命令生成配对密钥并给出对方要执行的说明。
- `sisyphus-execution-rules` — 会话开始强制加载：代理分工、实时更新、Git 限制与资源管理规则。
- `using-git-worktrees` — 需要与当前工作区隔离地开发（或执行计划前）时，建立独立 worktree。
- `careful` — 危险命令防护：在 rm -rf、DROP TABLE、force-push、reset --hard 等操作前告警。
- `guard` — 全面安全模式：危险命令告警 + 目录级编辑限制（careful + freeze 的合体）。
- `freeze` — 冻结编辑范围：把本会话的写入限制在指定目录内，防止顺手改无关代码。
- `unfreeze` — 解除 freeze 的目录编辑限制，恢复全目录写入。

<a id="c-cli"></a>
### CLI 封装与外部工具（6）

给 GUI 应用与非编程工具套一层命令行，让代理可自动化。

- `cli-anything` — 为 GUI 应用或源码仓库构建、打磨、校验 CLI-Anything 命令行封装（不改动其生成格式）。
- `cli-anything-*` — 家族：69 个具体应用的命令行驱动（Blender / GIMP / QGIS / Zotero / iTerm2 / Obsidian / FreeCAD / Postman… 完整清单见 §3 的 `cli-anything-` 前缀族）
- `cli-hub-meta-skill` — 在实时目录里检索可用的 agent-native CLI 工具（创意、效率、AI 等方向）。
- `opencli-*` — 家族：4 个 OpenCLI 适配器相关技能（`opencli-usage` / `opencli-browser` / `opencli-adapter-author` / `opencli-autofix`，均已在上方「浏览器与检索」列出）
- `cli-anything-macrocli` — 用命令行驱动 MacroCLI：把 GUI 操作定义成可调用的参数化宏。
- `reverse-engineering` — 逆向 macOS 二进制与私有 API：stripped Mach-O、arm64e、系统进程（Ghidra/LLDB）。

---

<a id="s0"></a>
## 0. 快速查找指南（按任务）

> 每个任务是一个独立小节，可从上方目录直达；链内技能按使用顺序排列。

| 任务 | 直达 |
| --- | --- |
| 规划新功能 | [跳转](#t-01) |
| 修复 bug | [跳转](#t-02) |
| 代码审查 | [跳转](#t-03) |
| Git / 提交 | [跳转](#t-04) |
| 测试策略 | [跳转](#t-05) |
| 写作 / 文档 | [跳转](#t-06) |
| UI / 前端 | [跳转](#t-07) |
| 浏览器验证 | [跳转](#t-08) |
| 性能优化 | [跳转](#t-09) |
| 安全审计 | [跳转](#t-10) |
| 部署上线 | [跳转](#t-11) |
| CLI 封装 | [跳转](#t-12) |
| 深度研究 | [跳转](#t-13) |
| 上下文交接 | [跳转](#t-14) |
| 会话纪律 / 防护 | [跳转](#t-15) |
| 科研写作 | [跳转](#t-16) |
| 单细胞 | [跳转](#t-17) |
| 分子 ML | [跳转](#t-18) |
| 量子计算 | [跳转](#t-19) |
| 并行子代理 | [跳转](#t-20) |

<a id="t-01"></a>
### 0.1 规划新功能

`brainstorming` → `writing-plans` → `executing-plans`

- `brainstorming` — 任何创造性工作开工前必须先做：做功能、改行为、起方案之前先把问题与选项谈清。
- `writing-plans` — 手里已有需求或多步任务规格、尚未动代码时，先写实施计划。
- `executing-plans` — 在当前会话内自己当执行者落地实施计划时使用（含无子代理可用的情况）。

<a id="t-02"></a>
### 0.2 修复 bug

`systematic-debugging` → `test-driven-development` → `verification-before-completion`

- `systematic-debugging` — 遇到 bug、测试失败或异常行为时，在提出修复方案之前使用的系统排查流程。
- `test-driven-development` — 写任何功能或修任何 bug 的实现代码之前使用：先写失败测试。
- `verification-before-completion` — 声称"完成/修好/通过"之前必须运行验证命令并核对输出，先有证据再下结论。

<a id="t-03"></a>
### 0.3 代码审查

`requesting-code-review` → `code-review` → `receiving-code-review`

- `requesting-code-review` — 完成任务、实现大功能或合入前，请求审查以确认是否符合需求。
- `code-review` — 以固定点（commit/分支/tag）为基准做双轴审查：Standard（是否符合本仓库规范）与 Spec（是否实现需求）。
- `receiving-code-review` — 收到审查意见后、动手改之前使用：要求技术论证与验证，不做表演式认同。

<a id="t-04"></a>
### 0.4 Git / 提交

`git-workflow-and-versioning` → `create-pull-request`

- `git-workflow-and-versioning` — git 工作流规范：提交、分支、冲突处理、把混乱工作区拆成干净原子提交、PR 与版本发布。
- `create-pull-request` — 按项目约定创建 GitHub PR：分析提交、整理分支并用 gh CLI 提交审阅。

<a id="t-05"></a>
### 0.5 测试策略

`tdd` → `test-audit` → `qa`

- `tdd` — 测试先行开发：用户要求 test-first 或提到红绿重构时使用。
- `test-audit` — 找出低价值或重复的测试及其维系的"仅供测试"代码；默认只报告，批量删除需你确认。
- `qa` — 对网站或 Web 应用做 QA 并给 1–5 分（5=无懈可击）与证据；发现问题时给修复建议。

<a id="t-06"></a>
### 0.6 写作 / 文档

`write` → `docs-update` → `documentation-and-adrs`

- `write` — 起草、编辑或本地化中英文文稿（不含代码注释）。
- `docs-update` — 代码变更后同步用户文档：更新/审阅文档、处理文档变更或分析近期提交的文档影响。
- `documentation-and-adrs` — 记录决策与文档：写 ADR、公共 API 变更说明、功能发布说明与给未来工程师的背景。

<a id="t-07"></a>
### 0.7 UI / 前端

`ui` → `design-taste-frontend` → `frontend-ui-engineering`

- `ui` — 构建与改造生产级 UI：组件、排版、布局与视觉一致性（不含后端逻辑）。
- `design-taste-frontend` — 前端艺术指导（tasteskill v2）：基于 247 个精选站点研究的构图、字体、配色与摄影方向。
- `frontend-ui-engineering` — 构建生产级、无障碍、自适应的界面：组件、布局、状态管理与 WCAG 达标。

<a id="t-08"></a>
### 0.8 浏览器验证

`browse` → `webapp-testing` → `browser-use`

- `browse` — 驱动真实浏览器（Aside）：打开页面、阅读、走完流程、截图并检查控制台错误。
- `webapp-testing` — 用 Playwright 测试本地 Web 应用：验证界面、截图与收集日志。
- `browser-use` — 直接的浏览器控制（CDP）：自动化、抓取、测试、截图与站点操作。

<a id="t-09"></a>
### 0.9 性能优化

`web-performance-audit` → `performance-optimization` → `debug-optimize-lcp`

- `web-performance-audit` — 剖析并优化页面速度与 Lighthouse 分数（加载与渲染瓶颈定位）。
- `performance-optimization` — 全栈性能优化：前端、后端、查询与数据库；性能不达标或变慢时使用。
- `debug-optimize-lcp` — 用 Chrome DevTools 调试并优化 LCP：慢加载、Core Web Vitals 问题定位。

<a id="t-10"></a>
### 0.10 安全审计

`cso` → `security-and-hardening`

- `cso` — 安全审计：静态发现为主，具备条件时补充复现与修复候选（OWASP、威胁建模、漏洞复核）。
- `security-and-hardening` — 代码安全加固：审计输入处理、认证授权、OWASP 漏洞与依赖安全。

<a id="t-11"></a>
### 0.11 部署上线

`deploy-to-vercel` → `shipping-and-launch` → `ci-cd-and-automation`

- `deploy-to-vercel` — 部署应用到 Vercel：上线、生成预览部署或给出访问链接。
- `shipping-and-launch` — 生产上线准备：发布清单、灰度与回滚策略、上线前检查。
- `ci-cd-and-automation` — 搭建与改造 CI/CD：质量门禁、测试运行器配置与部署策略。

<a id="t-12"></a>
### 0.12 CLI 封装

`cli-anything` → `cli-hub-meta-skill`

- `cli-anything` — 为 GUI 应用或源码仓库构建、打磨、校验 CLI-Anything 命令行封装（不改动其生成格式）。
- `cli-hub-meta-skill` — 在实时目录里检索可用的 agent-native CLI 工具（创意、效率、AI 等方向）。

<a id="t-13"></a>
### 0.13 深度研究

`doko-research` → `parallel-web` → `paper-lookup`

- `doko-research` — 多轮网页研究：交叉核对来源并产出结构化、带引用的报告。
- `parallel-web` — 用 Parallel CLI 做网页检索、URL 抽取、深度研究、结构化数据补全与实体发现。
- `paper-lookup` — 跨 18 个学术 API 检索论文、预印本、引用、开放全文与仓储记录。

<a id="t-14"></a>
### 0.14 上下文交接

`context-save` → `context-restore`

- `context-save` — 保存当前工作上下文：Git 状态、已定决策与待办，供后续会话无缝接手。
- `context-restore` — 恢复此前保存的上下文（优先当前分支），跨会话/工作区继续工作。

<a id="t-15"></a>
### 0.15 会话纪律 / 防护

`careful` → `guard` → `freeze`

- `careful` — 危险命令防护：在 rm -rf、DROP TABLE、force-push、reset --hard 等操作前告警。
- `guard` — 全面安全模式：危险命令告警 + 目录级编辑限制（careful + freeze 的合体）。
- `freeze` — 冻结编辑范围：把本会话的写入限制在指定目录内，防止顺手改无关代码。

<a id="t-16"></a>
### 0.16 科研写作

`scientific-writing` → `nature-figure` → `scientific-visualization`

- `scientific-writing` — 撰写、修改与审查科学稿件或报告，标注证据出处与报告规范。
- `nature-figure` — 用 Python 或 R 创建、修改、审查并导出论文配图（多面板、子图与期刊规范）。
- `scientific-visualization` — 用 Matplotlib/Seaborn 等产出真实、无障碍、可出版的科研图表（含不确定度表达）。

<a id="t-17"></a>
### 0.17 单细胞

`scanpy` → `anndata` → `scvi-tools` → `scvelo`

- `scanpy` — 单细胞 RNA-seq 标准流程：质控、标准化、HVG、PCA/UMAP/t-SNE、聚类与注释（可在 RDS/h5ad 间转换）。
- `anndata` — 单细胞注释矩阵与 .h5ad / Zarr 数据格式：读取、切片与 scverse 生态衔接（分析流程用 scanpy）。
- `scvi-tools` — 单细胞组学概率模型：scVI 批次整合、scANVI 注释与 totalVI 等。
- `scvelo` — RNA 速率分析：从 spliced/unspliced 计数拟合动力学模型并推断潜在时间。

<a id="t-18"></a>
### 0.18 分子 ML

`rdkit` → `datamol` → `deepchem`

- `rdkit` — 化学信息学底层控制：SMILES/SDF 解析、描述符、指纹与反应处理（更简单场景用 datamol）。
- `datamol` — RDKit 的 Pythonic 封装：SMILES 解析、标准化、描述符、指纹、聚类与 3D 构象（常规药物发现首选）。
- `deepchem` — 用 DeepChem 做分子性质预测与 MoleculeNet 流程：SMILES 特征化、骨架划分、图模型与预训练迁移。

<a id="t-19"></a>
### 0.19 量子计算

`qiskit` / `cirq` / `pennylane` / `qutip`

<a id="t-20"></a>
### 0.20 并行子代理

`dispatching-parallel-agents` → `subagent-driven-development`

- `dispatching-parallel-agents` — 有 2 个以上互不依赖、无共享状态的任务时，分派并行子代理。
- `subagent-driven-development` — 用子代理执行含独立任务的实施计划时使用。

---
<a id="s1"></a>
## 1. 项目技能（`.agents/skills/`，4 个）

> 本仓库自带，由 `skills-lock.json` 锁定来源与哈希。

| 技能 | 说明 |
| --- | --- |
| `code-reviewer` | 审查 Java 25 / Spring Boot 4 代码与 PR：迁移风险、模块边界越界、JSpecify 空安全、安全与性能回归、Spring Data 陷阱。 |
| `creating-springboot-projects` | 新建 Spring Boot 4 工程：目录结构、架构选型与实现起点（新服务、REST API、模块化后端）。 |
| `spring-data-jpa` | 设计 Spring Data JPA 仓储：投影、查询模式、自定义仓储、CQRS 读模型、关联映射与持久化性能修复。 |
| `springboot-migration` | 迁移到 Boot 4 + Java 25：依赖与 starter 改名、测试注解迁移，含 Spring Modulith 2 / Testcontainers 2 升级。 |

---

[返回目录](#top)

---

<a id="s2"></a>
## 2. 内置技能（17 个）

### 2.1 omp 内置（`~/.omp/agent/skills/`，2 个）

| 技能 | 说明 |
| --- | --- |
| `teach` | 讲解任何内容都要让人真正理解而非记住：按已验证的教学原则组织解释，任何程度的讲解都可调用。 |
| `visualize` | 给讲解配一张最小正确的图（依赖图、流程、时序、状态机、树、对比等），以内联 SVG 渲染。 |

### 2.2 superpowers 插件（`~/.omp/plugins/node_modules/superpowers/skills/`，15 个）

| 技能 | 说明 |
| --- | --- |
| `brainstorming` | 任何创造性工作开工前必须先做：做功能、改行为、起方案之前先把问题与选项谈清。 |
| `systematic-debugging` | 遇到 bug、测试失败或异常行为时，在提出修复方案之前使用的系统排查流程。 |
| `test-driven-development` | 写任何功能或修任何 bug 的实现代码之前使用：先写失败测试。 |
| `verification-before-completion` | 声称"完成/修好/通过"之前必须运行验证命令并核对输出，先有证据再下结论。 |
| `requesting-code-review` | 完成任务、实现大功能或合入前，请求审查以确认是否符合需求。 |
| `receiving-code-review` | 收到审查意见后、动手改之前使用：要求技术论证与验证，不做表演式认同。 |
| `writing-plans` | 手里已有需求或多步任务规格、尚未动代码时，先写实施计划。 |
| `executing-plans` | 在当前会话内自己当执行者落地实施计划时使用（含无子代理可用的情况）。 |
| `dispatching-parallel-agents` | 有 2 个以上互不依赖、无共享状态的任务时，分派并行子代理。 |
| `subagent-driven-development` | 用子代理执行含独立任务的实施计划时使用。 |
| `using-git-worktrees` | 需要与当前工作区隔离地开发（或执行计划前）时，建立独立 worktree。 |
| `finishing-a-development-branch` | 实现完成且测试通过后，决定分支如何收尾合入时使用。 |
| `writing-skills` | 新建、修改技能，或部署前验证技能可用时使用。 |
| `using-superpowers` | 会话开始时用：规定任何回应（含追问）之前先定位并加载相关技能。 |
| `diagnosing-superpowers` | 技能会话本身出问题时复盘：重复劳动、计划被忽略、技能没触发、耗时或花费异常。 |

---

[返回目录](#top)

---

<a id="s3"></a>
## 3. 用户级技能（`~/.agents/skills/`，412 个）

> 本机技能库主体，**按名称排序** —— 这是**检索轴**：已知名字时快速定位。
> 想按用途找技能，请看上方的 **用途分类索引** —— 那是**发现轴**：按「我要做什么」归类，并给出每个成员的说明。
> 字母本身不表达用途，因此这里不再按首字母拆小节。

| 技能 | 说明 |
| --- | --- |
| `13c-metabolic-flux` | 用稳态 ¹³C 同位素示踪数据估算胞内代谢通量（原子映射、mfapy 模拟、约束多起点拟合与通量诊断）。 |
| `a11y-debugging` | 用 Chrome DevTools 排查无障碍问题：语义 HTML、ARIA、焦点态、键盘导航、点击区域与对比度。 |
| `adaptyv` | 经 Adaptyv Foundry API/SDK 设计蛋白表征实验：选靶点目录、估成本、提交序列、跟踪进度并取结果。 |
| `aeon` | 时间序列机器学习：分类、回归、聚类、预测、异常检测、分段与相似性检索。 |
| `alphagenome` | 查 GRCh38 单核苷酸变异的 AlphaGenome 预计算效应（AVI 分、Phred、18 项 SHAP 归因与各模态分数）。 |
| `analysis-artifacts` | 数据分析过程中同步产出可复现产物：SQL、Python 可视化与汇总表（BigQuery 深挖场景）。 |
| `analytical-method-validation` | 按 ICH Q2(R2)/Q14、USP、ICH M10、CLSI EP、ISO 17025 规划并记录分析方法验证、确认与转移。 |
| `animation-vocabulary` | 反查动画/动效术语：把"弹一下""橡皮筋滚动"这类描述对应到准确名称，供检索与沟通。 |
| `anndata` | 单细胞注释矩阵与 .h5ad / Zarr 数据格式：读取、切片与 scverse 生态衔接（分析流程用 scanpy）。 |
| `api-and-interface-design` | 接口与模块边界设计：REST/GraphQL 端点、类型契约、前后端边界与稳定演进。 |
| `arbor` | 用假设树细化研究/工程问题：可重复评估器、孤立实验、证据追踪与基准优化。 |
| `arboreto` | 从表达数据推断基因调控网络（GRNBoost2 / GENIE3），排序转录因子-靶基因关联，支持 Dask 与稀疏输入。 |
| `archify` | 生成可交互的独立 HTML 架构图：架构、流程、时序、数据流、状态机、ER、类图、树、时间线、瀑布图（含明暗主题）。 |
| `archify-review` | 从价值/成本/影响三维度审查 Archify 的 issue、PR 或代码，支撑维护决策。 |
| `ask-matt` | 不确定该用哪个技能或流程时，由它在本仓库技能清单中路由推荐。 |
| `astropy` | 天文数据处理：单位与量、坐标、FITS 读写、表、时间系统、WCS 与宇宙学。 |
| `autoskill` | 扫描用户指定的 Screenpipe 历史窗口，发现重复的研究流程并起草新技能或组合配方待审。 |
| `beautify-github-readme` | 重做 GitHub README 首页或产出项目原生 SVG/PNG/GIF 视觉资产（美化、重塑品牌、审计）。 |
| `benchling-integration` | 用 Benchling SDK / v2 REST API 自动化实验室数据：登记实体、库存、ELN 条目、工作流与数仓查询。 |
| `bgpt-paper-search` | 按主题或 DOI 检索 BGPT 论文并取全文级论断证据（实验、统计、范围、局限与出处）。 |
| `bids` | 组织、查询、校验与转换 BIDS 脑影像数据集（MRI/EEG/MEG/iEEG/PET 等）。 |
| `biopython` | Biopython 序列处理：读写 FASTA/GenBank/PDB、系统发育、经 Bio.Entrez 访问 NCBI/PubMed。 |
| `bioservices` | 用 Python 调 UniProt、KEGG、ChEMBL、Reactome、QuickGO、UniChem 等，做跨库注释与标识映射。 |
| `brandkit` | 生成高端品牌视觉：品牌规范板、标志系统、识别手册与视觉世界提案。 |
| `browser-harness` | 经 CDP 驱动真实浏览器：点击输入、导航、已登录会话、JS 渲染或反爬页面（纯静态抓取请用 curl）。 |
| `browser-testing-with-devtools` | 用 Chrome DevTools 在真实浏览器中测试：查 DOM、抓控制台错误、看网络请求、做性能与视觉校验。 |
| `browser-use` | 直接的浏览器控制（CDP）：自动化、抓取、测试、截图与站点操作。 |
| `bulk-rnaseq` | bulk RNA-seq 定量到差异表达的准备：nf-core/rnaseq 或 Salmon/STAR/featureCounts，含链向与参考来源校验。 |
| `cantera` | 用 Cantera 做均相反应与点火延迟计算：机理来源、守恒检查与数值收敛。 |
| `cellprofiler` | 可复现的 CellProfiler 显微图像流程：细胞核分割、计数与单目标荧光测量（无头批处理）。 |
| `cellxgene-census` | 查询 CZ CELLxGENE Census 的公开单细胞与空间转录组数据（版本化元数据、表达切片与下载地址）。 |
| `chart-designer` | 设计数据可视化与图表：生成 ECharts / Chart.js 等配置，搭建仪表盘与报表。 |
| `check` | 审查 diff、PR、发布就绪度与发布后续；不做根因调试，也不审文案。 |
| `chief-of-staff` | 在单次会话内协调多个子代理，推进一个长期目标。 |
| `chrome-devtools` | 经 MCP 使用 Chrome DevTools：调试、排查与浏览器自动化（性能与网络分析）。 |
| `chrome-devtools-cli` | 用命令行/脚本方式经 Chrome DevTools 完成浏览器自动化任务。 |
| `ci-cd-and-automation` | 搭建与改造 CI/CD：质量门禁、测试运行器配置与部署策略。 |
| `ci-fix` | 诊断并修复 GitHub Actions 失败：看 workflow 运行与日志、定位根因、最小修复并推到修复分支。 |
| `cirq` | Google 量子计算框架：面向 Google 硬件、噪声感知线路与表征实验的低层设计。 |
| `citation-management` | 文献引用管理：检索 OpenAlex/PubMed/Scholar、核对元数据、校验引用并生成规范 BibTeX。 |
| `claude-handoff` | 把当前对话整理成交接文档，交给新的后台代理立即接手。 |
| `cli-anything` | 为 GUI 应用或源码仓库构建、打磨、校验 CLI-Anything 命令行封装（不改动其生成格式）。 |
| `cli-anything-adguardhome` | 用命令行驱动 AdGuard Home（DNS 过滤/家庭网络管控）：把该应用常用操作封装成命令，便于无人值守自动化。 |
| `cli-anything-anygen` | 用命令行驱动 AnyGen（幻灯片/文档/网页生成）：把该应用常用操作封装成命令，便于无人值守自动化。 |
| `cli-anything-audacity` | 用命令行驱动 Audacity（音频编辑）：把该应用常用操作封装成命令，便于无人值守自动化。 |
| `cli-anything-blender` | 用命令行驱动 Blender（3D 场景编辑）：把该应用常用操作封装成命令，便于无人值守自动化。 |
| `cli-anything-browser` | 用命令行驱动浏览器（经 DOMShell 把可访问性树映射为虚拟文件系统）：便于无人值守自动化。 |
| `cli-anything-calibre` | 用命令行驱动 Calibre（电子书库与格式转换）：把常用操作封装成命令，便于无人值守自动化。 |
| `cli-anything-ccswitch` | 用命令行驱动 CC Switch（AI 编码工具配置切换）。 |
| `cli-anything-chromadb` | 用命令行驱动 ChromaDB（向量库）：集合、文档与语义检索管理。 |
| `cli-anything-cloudanalyzer` | 用命令行驱动 CloudAnalyzer（建图/定位/感知评测）：点云、轨迹与分组评测。 |
| `cli-anything-cloudcompare` | 用命令行驱动 CloudCompare（点云与网格处理）：工程、会话与点云操作 40+ 命令。 |
| `cli-anything-comfyui` | 用命令行驱动 ComfyUI（图像生成工作流）：排队、模型管理与结果下载。 |
| `cli-anything-dify-workflow` | 用命令行驱动 Dify 工作流 DSL：创建、检查、校验、编辑与导出。 |
| `cli-anything-drawio` | 用命令行驱动 Draw.io：图表的创建、编辑与导出。 |
| `cli-anything-eez-studio` | 用命令行驱动 EEZ Studio：改 .eez-project、编辑 LVGL 界面与 SCPI，并调用真实后端构建。 |
| `cli-anything-eth2-quickstart` | 用命令行驱动 eth2-quickstart：自动化部署加固的以太坊节点、装执行/共识客户端并配置验证者。 |
| `cli-anything-exa` | 用命令行驱动 Exa：网络搜索与内容抓取。 |
| `cli-anything-firefly-iii` | 用命令行驱动 Firefly III：个人财务记账与预算。 |
| `cli-anything-freecad` | 用命令行驱动 FreeCAD：参数化 3D 建模，覆盖 Part/Sketcher/PartDesign 等全部工作台。 |
| `cli-anything-gimp` | 用命令行驱动 GIMP：图像编辑（底层 Pillow）。 |
| `cli-anything-godot` | 用命令行驱动 Godot：项目管理、场景、导出与脚本执行。 |
| `cli-anything-hermes` | 用 Hermes Agent 构建、打磨、测试与校验 GUI 应用的 CLI-Anything 封装。 |
| `cli-anything-inkscape` | 用命令行驱动 Inkscape：矢量图形编辑。 |
| `cli-anything-intelwatch` | 用命令行驱动 IntelWatch：竞品情报、并购尽调与 OSINT。 |
| `cli-anything-iterm2` | 用命令行驱动 iTerm2：向会话发送文本、读实时输出与回滚、管理窗口分屏与 tmux 工作流。 |
| `cli-anything-iterm2-ctl` | 用命令行驱动 iTerm2：会话文本发送、终端输出读取、窗口与分屏管理（同上，控制入口）。 |
| `cli-anything-joplin` | 用命令行驱动 Joplin：笔记读写（基于真实 joplin 终端后端）。 |
| `cli-anything-jumpserver` | 用命令行驱动 JumpServer 堡垒机：资产、用户、权限、会话与审计管理。 |
| `cli-anything-kdenlive` | 用命令行驱动 Kdenlive：视频编辑。 |
| `cli-anything-krita` | 用命令行驱动 Krita：数字绘画的工程、图层、滤镜与导出。 |
| `cli-anything-libreoffice` | 用命令行驱动 LibreOffice：文档编辑并产出真实 ODF 文件。 |
| `cli-anything-live2d` | 用命令行处理 Live2D Cubism 模型（.model3.json）：校验、编辑、批量与发布。 |
| `cli-anything-lldb` | 用命令行驱动 LLDB：经 Python API 做有状态调试。 |
| `cli-anything-macrocli` | 用命令行驱动 MacroCLI：把 GUI 操作定义成可调用的参数化宏。 |
| `cli-anything-mailchimp` | 用命令行驱动 Mailchimp 营销 API：名单、活动、报表、自动化与模板。 |
| `cli-anything-mermaid` | 用命令行驱动 Mermaid：创建、编辑并渲染图表（经 mermaid.ink）。 |
| `cli-anything-minimax` | 用命令行驱动 MiniMax：对话（M3/M2.7）与 speech-2.x 语音合成。 |
| `cli-anything-mubu` | 用命令行驱动 Mubu（幕布）：实时桥接的标准入口。 |
| `cli-anything-musescore` | 用命令行驱动 MuseScore：移调、分谱、乐器管理与 PDF/音频/MIDI 导出。 |
| `cli-anything-n8n` | 用命令行驱动 n8n：工作流、执行记录、凭据、变量与标签管理。 |
| `cli-anything-notebooklm` | 用命令行驱动 NotebookLM：笔记本与来源管理、提问、生成并下载产物。 |
| `cli-anything-novita` | 用命令行驱动 Novita AI：OpenAI 兼容接口（DeepSeek、GLM 等）。 |
| `cli-anything-nsight-graphics` | 用命令行驱动 Nsight Graphics（Windows）：捕获、GPU Trace 摘要与 ngfx-replay 分析。 |
| `cli-anything-nslogger` | 用命令行处理 NSLogger 日志（.rawnsloggerdata/.nsloggerdata）：解析、过滤、导出与监控。 |
| `cli-anything-obs-studio` | 用命令行驱动 OBS Studio：场景集合编辑。 |
| `cli-anything-obsidian` | 用命令行驱动 Obsidian：经 Local REST API 管理笔记、搜索与执行命令。 |
| `cli-anything-ollama` | 用命令行驱动 Ollama：本地模型管理、生成、对话与嵌入。 |
| `cli-anything-openrefine` | 用命令行驱动 OpenRefine：导入脏数据、应用操作历史、检视与导出清洗结果。 |
| `cli-anything-openscreen` | 用命令行驱动 Openscreen：录屏剪辑的缩放、变速、裁剪、标注与导出。 |
| `cli-anything-pm2` | 用命令行驱动 PM2：Node 进程的启动、停止、重启与日志。 |
| `cli-anything-qgis` | 用命令行驱动 QGIS：工程、可写图层、要素、出图与 qgis_process。 |
| `cli-anything-quietshrink` | 用命令行驱动 QuietShrink：Apple Silicon 硬件 HEVC 压缩 macOS 录屏（静音、体积降 70–90%）。 |
| `cli-anything-rekordbox` | 用命令行驱动 Rekordbox（DJ 曲库与 deck 控制）：曲库检视、播放列表与现场混音。 |
| `cli-anything-renderdoc` | 用命令行驱动 RenderDoc：图形调试捕获分析。 |
| `cli-anything-rms` | 用命令行驱动 Teltonika RMS：设备管理与监控。 |
| `cli-anything-safari` | 用命令行驱动 Safari（macOS，经 safari-mcp）：保留登录态的原生浏览器自动化，84 个工具 1:1 暴露。 |
| `cli-anything-sbox` | 用命令行驱动 s&box（Source 2）：工程管理、场景/预制体编辑、配置、C# 代码生成与校验。 |
| `cli-anything-seaclip` | 用命令行驱动 SeaClip-Lite：议题、流水线、代理、日程与动态管理。 |
| `cli-anything-shotcut` | 用命令行驱动 Shotcut：基于 MLT XML 的视频编辑。 |
| `cli-anything-siyuan` | 用命令行驱动 SiYuan 思源笔记：笔记本、文档、块与搜索。 |
| `cli-anything-slay-the-spire-ii` | 用命令行驱动杀戮尖塔 2：经本地桥接模组读取对局状态并下达战斗/导航/奖励操作。 |
| `cli-anything-threemf` | 用命令行处理 3MF：检测并调整圆孔尺寸、修复网格、比较打印文件（BambuStudio/PrusaSlicer）。 |
| `cli-anything-tigris` | 用命令行驱动 Tigris：S3 兼容对象存储的桶、对象、预签名 URL 与访问密钥。 |
| `cli-anything-unimol-tools` | 用命令行驱动 Uni-Mol：分子性质预测的训练与推理。 |
| `cli-anything-unrealinsights` | 用命令行驱动 Unreal Insights：捕获 Trace、检视 Trace Store、导出与汇总时序/计数器数据。 |
| `cli-anything-videocaptioner` | 用命令行驱动 VideoCaptioner：转写、字幕优化/翻译与烧字幕。 |
| `cli-anything-wavetone` | 用命令行驱动 WaveTone 2.61（Windows）：经 JSON 清单控制波形编辑。 |
| `cli-anything-web-yu-pri` | 用命令行驱动日本邮政 Web Yu-pri：登录、检视、截图、试算与寄件表单填写。 |
| `cli-anything-wiremock` | 用命令行驱动 WireMock：HTTP mock 服务管理。 |
| `cli-anything-zoom` | 用命令行驱动 Zoom：会议、参会人与录制管理。 |
| `cli-anything-zotero` | 用命令行驱动 Zotero：文献库管理。 |
| `cli-hub-meta-skill` | 在实时目录里检索可用的 agent-native CLI 工具（创意、效率、AI 等方向）。 |
| `clinical-decision-support` | 仅用于研究性临床决策支持的产出与校验：证据档案、队列、生存与生物标志模型、隐私与治理材料。 |
| `clinical-reports` | 起草临床病例/诊断/试验/安全性报告的受限结构并做本地确定性检查（仅限合成或去标识化输入）。 |
| `cloud` | Browser Use Cloud 文档参考：托管浏览器自动化 API（v2–v4）与 Python/TS SDK 用法。 |
| `cobrapy` | 约束型代谢建模：FBA/pFBA/FVA、基因敲除、通量采样、产率包络与 SBML 校验。 |
| `code-review` | 以固定点（commit/分支/tag）为基准做双轴审查：Standard（是否符合本仓库规范）与 Spec（是否实现需求）。 |
| `code-review-and-quality` | 多维代码审查，合入任何变更前使用（含自己、他人或代理写的代码）。 |
| `code-simplification` | 不改变行为地简化代码：可读性与可维护性差、复杂度堆积时使用。 |
| `codebase-design` | 深模块设计的共同语言：模块接口、加深机会、接缝位置、可测性与 AI 可导航性。 |
| `consciousness-council` | 多视角议事：模拟不同思维原型辩论决策与取舍，分离证据/假设/价值并给出条件化建议。 |
| `constraint-driven-development` | 把项目质量标准写成契约（CONSTRAINTS.md）并阻止代理悄悄降低门槛。 |
| `context-engineering` | 优化 agent 上下文：新会话、输出质量下降、任务切换或配置项目规则文件时使用。 |
| `create-pull-request` | 按项目约定创建 GitHub PR：分析提交、整理分支并用 gh CLI 提交审阅。 |
| `dask` | 用 Dask 扩展 pandas/NumPy 与自定义流程：分块、调度器、DataFrame/Array/Bag/Futures 与分布式诊断。 |
| `database-lookup` | 按明确端点、过滤、分页与出处查询公开数据库 API，保证科学/监管/金融事实可复现。 |
| `datalad` | 用 DataLad/git-annex 取用、版本化与发布科研数据集，并用 datalad run/rerun 记录计算溯源。 |
| `datamol` | RDKit 的 Pythonic 封装：SMILES 解析、标准化、描述符、指纹、聚类与 3D 构象（常规药物发现首选）。 |
| `dbt-model-index` | 提供 dbt 模型（BigQuery 表）查询索引，把模糊的数据问题落到具体表。 |
| `debug-optimize-lcp` | 用 Chrome DevTools 调试并优化 LCP：慢加载、Core Web Vitals 问题定位。 |
| `debugging-and-error-recovery` | 系统化根因调试：测试失败、构建断裂、昨天还能跑今天不行、行为不符预期时使用。 |
| `deepchem` | 用 DeepChem 做分子性质预测与 MoleculeNet 流程：SMILES 特征化、骨架划分、图模型与预训练迁移。 |
| `deepspot-m` | 由 H&E 病理图生成全转录组虚拟空间表达（DeepSpot-M）：224×224 切片、按基因符号查询。 |
| `deeptools` | NGS 分析工具箱：BAM 转 bigWig、相关性/PCA/指纹 QC、TSS 与 peak 的热图与剖面图。 |
| `deploy-to-vercel` | 部署应用到 Vercel：上线、生成预览部署或给出访问链接。 |
| `depmap` | 取用并分析 DepMap 数据：CRISPR Chronos 基因效应、癌症模型注释、组学标志物与 PRISM 药敏。 |
| `deprecation-and-migration` | 管理弃用与迁移：下线旧系统/API/功能、把用户迁到新实现、在线改库表结构。 |
| `design-taste-frontend` | 前端艺术指导（tasteskill v2）：基于 247 个精选站点研究的构图、字体、配色与摄影方向。 |
| `design-taste-frontend-v1` | 上一版 taste-skill，仅在需要与其行为逐字兼容的旧项目中保留使用。 |
| `dhdna-profiler` | 用 DHDNA 框架分析给定文本的推理与写作模式（认知风格画像、文本推理比较）。 |
| `diagnosing-bugs` | 难缠 bug 与性能回归的诊断循环：用户说"诊断/调试这个"或报告坏了/抛错/失败/变慢时使用。 |
| `diagram-design` | 生成品牌化专业图：架构与增量架构、现状图、流程、时序、状态机、ER、时间线、泳道、象限、雷达、树状与组织结构等。 |
| `diffdock` | 用 DiffDock/DiffDock-L 预测蛋白-小分子结合构象：批量对接、构象筛选、置信度解读与虚拟筛选。 |
| `dnanexus-integration` | 在 DNAnexus 上构建并运行可复现的基因组流程：dx CLI、dxpy、app/applet、原生工作流与 dxCompiler。 |
| `docs-update` | 代码变更后同步用户文档：更新/审阅文档、处理文档变更或分析近期提交的文档影响。 |
| `documd-visuals` | 用 Markdown 做文本驱动可视化：图表、示意图、卡片、架构与页面布局（含可靠性/运营类图表）。 |
| `documentation-and-adrs` | 记录决策与文档：写 ADR、公共 API 变更说明、功能发布说明与给未来工程师的背景。 |
| `domain-modeling` | 建立并打磨项目领域模型：术语表 GLOSSARY.md、ADR 讨论与代码术语统一。 |
| `doubt-driven-development` | 关键决策先过一遍全新上下文的对抗式评审：交叉盘问假设、压测隐藏失败模式。 |
| `draw-ui` | 设计 App、后台、游戏界面与完整落地页并产出 UI 设计稿；也可把设计稿或截图还原为 HTML/CSS、现有前端项目或微信小程序。不用于插画、海报、故事板与纯图片编辑。 |
| `emil-design-eng` | Emil Kowalski 的 UI 打磨哲学：组件设计、动效取舍与让产品"手感好"的细节。 |
| `esm` | 用 Biohub esm SDK 做 ESM3 蛋白生成、ESMC 嵌入与 ESMFold2 全原子折叠（本地推理或托管客户端）。 |
| `etetoolkit` | 用 ETE 4 分析、比较、注释与可视化系统发育树：Newick/Nexus 读写、拓扑编辑、RF 比较与基因树事件。 |
| `exa-search` | 用 Exa 检索科研与技术网页，并批量抽取网页/PDF 正文（支持学术类目与域名过滤）。 |
| `experimental-design` | 数据采集前设计实验：选设计、随机化、区组与处理组合排布，保证结果可解释。 |
| `exploratory-data-analysis` | 对受支持的科学数据文件做有界本地探索分析：CSV/TSV/JSON 画像，可选 NumPy/HDF5/FASTA/图像元数据与缺失/泄漏检查。 |
| `fictiv` | 在浏览器中完成 Fictiv（按需制造平台）全流程：上传 CAD、配置工艺材料与公差、即时/人工报价与下单。 |
| `find-skills` | 帮用户发现并安装技能："怎么做 X""有没有做 X 的技能""能不能扩展能力"这类请求时使用。 |
| `flowio` | 读写 FCS 2.0/3.0/3.1 流式细胞术文件：元数据与通道检视、NumPy 事件提取、多数据集与导出（门控分析用 flowkit）。 |
| `flowkit` | 流式细胞术分析：溢漏补偿、logicle/双指数变换、层级门控、GatingML 与 FlowJo 10 工作区。 |
| `fluidsim` | 规划、配置、检视、重启与分析有界 CFD 仿真（FluidSim）：求解器选择、参数复核、FFT/MPI 与数值有效性检查。 |
| `folklore-variant-evidence` | 取 ClinGen 基因-疾病有效性断言，并核对某 GRCh38 胚系 SNV/小 indel 的公开证据与文献。 |
| `frontend-ui-engineering` | 构建生产级、无障碍、自适应的界面：组件、布局、状态管理与 WCAG 达标。 |
| `full-output-enforcement` | 覆盖模型默认截断行为：强制完整代码输出、禁止占位符、妥善处理 token 上限切分。 |
| `generate-image` | 经 OpenRouter 图像 API 生成或编辑图片（Gemini/Seedream/Recraft/GPT-Image）：照片、插画、概念图、标志与合成。 |
| `geniml` | 本地基因组区间模型与集合代数：校验 BED/universe 契约，规划 Region2Vec/scEmbed 运行并检查一致性。 |
| `genomic-coordinates` | 坐标约定转换、变异表示规范化与比较、装配版本/染色体命名不一致的提前发现。 |
| `genomic-intelligence` | 用托管 DNA 语言模型从序列直接预测调控特征、基因结构与表达（六类任务，REST 与 MCP，无需本地 GPU）。 |
| `geomaster` | 地理空间研究：遥感、矢量/栅格 GIS、空间统计、地形与网络分析、对地观测机器学习。 |
| `geopandas` | 直接使用 GeoPandas 的 Python 流程指引与本地审计：GeoSeries/GeoDataFrame、空间操作与矢量数据 I/O。 |
| `get-available-resources` | 探测本机可用资源（CPU/内存/磁盘/调度器/容器/加速器限额），用于做资源敏感任务的规划。 |
| `gget` | 用 CLI/Python 快速查询 20+ 生物信息库：基因信息、BLAST/BLAT、病毒序列、PDB/mmCIF、富集与 OpenTargets 等。 |
| `ginkgo-cloud-lab` | 在 Ginkgo Cloud Lab 上选方案、备输入、核价与下单：无细胞/大肠杆菌/毕赤酵母表达与多种读出方式。 |
| `git-guardrails-claude-code` | 为 Claude Code 配置 git 危险命令拦截钩子（push、reset --hard、clean、branch -D 等）。 |
| `git-workflow-and-versioning` | git 工作流规范：提交、分支、冲突处理、把混乱工作区拆成干净原子提交、PR 与版本发布。 |
| `github-bug-report-triage` | 判断 GitHub bug 报告是否可行动，并指出缺失的复现信息。 |
| `github-issue-dedupe` | 用语义检索与关键词匹配发现重复 issue，或建立自动查重。 |
| `glycoengineering` | 糖基化分析与改造：扫描 N-糖基化序列、S/T 富集区、糖基证据，准备 NetNGlyc/NetOGlyc/GlycoSHIELD 流程。 |
| `gpt-taste` | 高级 UX/UI 与 GSAP 动效工程：Python 驱动的版面随机化、AIDA 结构、编辑式字体与 bento 网格。 |
| `grill-me` | 就方案或设计接受一场不留情面的追问，用以打磨思路。 |
| `grill-with-docs` | 同上，并在追问过程中同步产出 ADR 与术语表文档。 |
| `grilling` | 就计划、决策或想法对用户进行高强度盘问，压力测试其思考。 |
| `gtars` | 本地基因组区间模型与集合代数：重叠与计数、共识与覆盖、分词、片段处理与 refget/BEDbase 规划（Python/Rust/CLI）。 |
| `handoff` | 把当前对话压缩成交接文档，供另一个代理接手继续。 |
| `health` | 体检 agent 配置与 AI 可维护性：指令漂移、hooks/MCP、验证器缺失；不查应用 bug，也不做 PR 审查。 |
| `high-end-visual-design` | 按高端设计机构标准做界面：精确字体、间距、阴影、卡片结构与动效，并封堵廉价感默认样式。 |
| `histolab` | 病理全片图像处理：切片检视、组织掩膜、随机/网格/评分抽样与 H&E 染色归一，产出可供训练的图块集。 |
| `hugging-science` | 在 Hugging Science 目录中发现并评估科研数据集、模型、方法帖与 Space（生物/化学/材料/气候/天文等）。 |
| `hunt` | 先定位根因再动手修：报错、崩溃、回归或"以前能跑"时使用；不用于代码审查或新功能。 |
| `hypogenic` | 规划与审计用 HypoGeniC/HypoRefine 做 LLM 辅助假设生成（hypogenic 包、任务配置、假设库与 HypoBench）。 |
| `hypothesis-generation` | 把观察转成可检验的科学问题：候选假设、竞争解释、因果/相关主张、判别性预测与预注册分析计划。 |
| `idea-refine` | 把粗糙想法打磨成清晰可执行的概念：结构化发散与收敛，适用于开工前的构思阶段。 |
| `image-to-code` | 视觉要求高的网页任务：先生成设计稿，再据此产出代码（图像到代码的完整链路）。 |
| `imagegen-frontend-mobile` | 生成移动端 App 的高保真界面概念与流程稿，用作原生风格设计参考。 |
| `imagegen-frontend-web` | 生成面向转化的网站设计参考图，强调版式与转化路径。 |
| `imaging-data-commons` | 查询并下载 NCI Imaging Data Commons 的公开肿瘤影像数据（集合发现、元数据与影像检索）。 |
| `impeccable` | 界面设计的通用改进流程：设计、重塑、评审、审计、打磨、澄清、精简与加固。 |
| `implement` | 依据一份规格或一组工单（tickets）落地实现。 |
| `implement-spec` | 把 /to-spec 与 /to-tickets 的产物落成代码。 |
| `improve-codebase-architecture` | 扫描代码库找出"加深模块"的机会，产出可视化 HTML 报告并逐个推翻或采纳。 |
| `incremental-implementation` | 把改动切成薄而可验证的小步交付，适用于任何需要控制风险的功能或重构。 |
| `industrial-brutalist-ui` | 工业粗野风格界面：瑞士印刷版式与军用终端美学的融合——刚性网格、极简配色。 |
| `infographics` | 用 OpenRouter 上的 Nano Banana 2 生成或评审信息图（统计摘要、时间线、流程）。 |
| `interview-me` | 通过连续单问挖掘用户真正想要的东西（而非其自以为想要的）。 |
| `iso-standards-readiness` | 准备并按结构审查 ISO 管理体系与实验室能力（如 ISO 13485/15189/17025）的就绪证据。 |
| `kami` | 用 Kami 模板排版专业文档：简历、单页、白皮书、信函与作品集。 |
| `lab-hardware-cad` | 用 build123d 参数化设计自制实验室硬件，并导出 STEP/STL 等加工文件。 |
| `labarchive-integration` | 对接 LabArchives ELN 与 Inventory API：区域端点选择、签名请求与条目读写。 |
| `lamindb` | 用 LaminDB 管理生物数据集与模型：产物登记、血缘追踪、schema 与关联查询。 |
| `latchbio-integration` | 在 Latch 平台上构建、注册、调试并运行生信流程：Python SDK、CLI、Latch Data 与 Registry。 |
| `latex-posters` | 用 LaTeX 制作学术海报（beamerposter / tikzposter / baposter）。 |
| `learn` | 六阶段研究流程：从资料包到可发布产出，适用于陌生领域的系统性学习与研究。 |
| `liteparse` | 本地文档与 PDF 解析：输出带边界框的空间文本，便于版面相关抽取。 |
| `literature-review` | 用 PubMed/arXiv/bioRxiv/Semantic Scholar 等做系统综述、范围综述与叙述性综述。 |
| `loop-me` | 就"你想构建的工作流"接受规格层面的连续盘问，把需求问清。 |
| `mageck` | 用 MAGeCK 分析混合 CRISPR 筛选的 FASTQ 与 guide-count 矩阵：文库校验、富集与命中排序。 |
| `marine-carbonate-chemistry` | 用 PyCO2SYS 解海水碳酸盐体系：化学海洋学、海洋酸化与碳循环计算。 |
| `markdown-mermaid-writing` | 写科研向 Markdown 文档与 Mermaid 图（流程、关系、时间线与结构）。 |
| `market-research-reports` | 产出可溯源的市场研究报告与假设驱动的市场规模/预测情景。 |
| `markitdown` | 用 Microsoft MarkItDown 把各类文档与链接转成 Markdown，便于分析与检索。 |
| `matchms` | 处理、清洗、比较与检索串联质谱：MS/MS 文件 I/O、元数据统一与谱库匹配。 |
| `matlab` | MATLAB / GNU Octave 数值工作流：数组、表格与时间数据、迁移与规划。 |
| `matplotlib` | 用 Matplotlib 精细控制科研绘图（自定义元素与复杂多面板布局）。 |
| `mcp-builder` | 构建高质量 MCP 服务器，让 LLM 能安全调用外部服务。 |
| `medchem` | 药物化学过滤与化合物初筛：类药规则（Lipinski/Veber/CNS）、结构警示与 PAINS。 |
| `memory-leak-debugging` | 排查 JavaScript/Node.js 内存泄漏：高内存占用与 OOM，含堆快照分析。 |
| `migrate-to-shoehorn` | 把测试里的 `as` 类型断言迁移到 @total-typescript/shoehorn。 |
| `minimalist-ui` | 简洁编辑风界面：暖色单色调、字体对比、扁平 bento 网格与低饱和配色。 |
| `modal` | 用 Modal 无服务器平台按需运行 Python（含按需 GPU），部署函数与批处理任务。 |
| `molecular-dynamics` | 用 OpenMM/MDAnalysis 搭建并分析分子动力学模拟（蛋白与配体体系、轨迹分析）。 |
| `molfeat` | 用 Molfeat 生成分子特征，用于 QSAR/QSPR、相似性与虚拟筛选。 |
| `motion-spec` | 把模糊动效诉求（"丝滑一点""高级感""加点动画"）写成明确的动效规格：时长、曲线、编排与降级。 |
| `nature-academic-search` | 跨源检索文献、核对与管理引用，构建 MeSH 检索式与引用影响力核查。 |
| `nature-citation` | 为稿件论断查找并验证 Nature/CNS 系列文献，做论断到来源的映射与补引。 |
| `nature-data` | 起草或审查稿件的 Data/Code Availability 声明、数据获取路径、仓库计划与 FAIR 元数据。 |
| `nature-downloader` | 合法获取学术全文：CNKI 机构访问、英文开放获取与出版社平台下载。 |
| `nature-experiment-log` | 标准实验日志：读图片/语音/文字产出带 YAML frontmatter 的 Markdown，可接飞书 CLI 与 Obsidian。 |
| `nature-figure` | 用 Python 或 R 创建、修改、审查并导出论文配图（多面板、子图与期刊规范）。 |
| `nature-literature-pipeline` | 全自动文献流水线：多源检索 → 六维打分 → 精读 → 汇总分发。 |
| `nature-paper-to-patent` | 把论文或发明材料转成有证据支撑的中文发明专利草稿与技术交底书。 |
| `nature-paper2ppt` | 由论文或阅读笔记生成中文学术 PPTX（含原图与要点提炼）。 |
| `nature-polishing` | 润色、翻译或压缩既有学术文字，保持事实、术语与证据边界不变。 |
| `nature-reader` | 生成中英对照的论文精读稿：正文、图表与公式对齐。 |
| `nature-response` | 起草、审查或修改审稿回复、修回信与标记稿/LaTeX 修订。 |
| `nature-reviewer` | 模拟同行评审：按重要性、有效性、证据强度给出结构化意见。 |
| `nature-writing` | 从作者材料出发撰写或重构论文论证、章节与投稿材料。 |
| `ncats-arax` | 查询 NCATS Translator ARAX 生产 API，取有界、带类型与出处的单跳/受限路径关系。 |
| `networkx` | 用 NetworkX 创建、分析并可视化复杂网络与图结构。 |
| `neurokit2` | 用 NeuroKit2 做生理时间序列的可复现预处理、事件提取、变异性与复杂度分析。 |
| `neuropixels-analysis` | Neuropixels 胞外记录全流程：SpikeInterface 读取、Kilosort4 分选与单元质量筛选。 |
| `nextflow` | 编写、运行与调试 Nextflow DSL2 与 nf-core 流程（.nf、channel、配置与报错定位）。 |
| `nmrglue` | 用 nmrglue 处理一维复数 NMR FID：相位校正、谱图、峰与积分。 |
| `nwb-conversion` | 用 NeuroConv/PyNWB 把神经科学采集数据转成 NWB 标准文件，保留元数据与时间对齐。 |
| `observability-and-instrumentation` | 给代码加可观测性：日志、指标、追踪与告警，让线上行为可诊断。 |
| `oil-ui` | 网站/应用/仪表盘/组件的界面设计、改进与评审：先探索不同设计方向再落地。 |
| `oiloil-ui-ux-guide` | 整理项目 UI/UX 规范并产出 design-spec.md，或对现有界面做优先级排序的评审；单个颜色字号这类小问题不适用。 |
| `omero-integration` | 用 omero-py/BlitzGateway/CLI 检视并自动化 OMERO 服务器上的显微图像流程（含注释与 ROI）。 |
| `onekgpd` | 查询 1000 Genomes 项目（3202 个全基因组个体，GRCh38）的个体级变异、携带者、基因型与亲缘关系。 |
| `ontology-term-resolution` | 把自由文本科学标签解析为本体术语 ID，并校验既有 CURIE（EBI Ontology Lookup）。 |
| `open-notebook` | 用自托管 Open Notebook（NotebookLM 替代）组织研究：来源导入、基于引用的问答与会话。 |
| `open-source` | browser-use 开源库的 Python 编码参考：Agent、Browser 与 Tools 用法（非 Cloud API）。 |
| `opencli-adapter-author` | 为新站点编写 OpenCLI 适配器，或给现有站点增加命令。 |
| `opencli-autofix` | OpenCLI 命令失败时的自动修复：定位并修补失效适配器。 |
| `opencli-browser` | 用 opencli 驱动真实 Chrome：检视页面、填表、点击与抓取数据。 |
| `opencli-usage` | OpenCLI 会话的入口总览：都有哪些能力、如何发现适配器与命令、输出格式。 |
| `openpiv` | 用 OpenPIV 做粒子图像测速：从 PIV 图像序列提取速度场。 |
| `opentrons-integration` | 编写、评审、迁移、仿真与排错 Opentrons 官方 Python Protocol API v2 协议。 |
| `optimize-for-gpu` | 在 NVIDIA 硬件上加速科学 Python，并验证结果正确且确实更快（CUDA/RAPIDS/CuPy/Numba）。 |
| `pacsomatic` | 准备并启动 nf-core/pacsomatic 的肿瘤-正常配对 PacBio HiFi 流程。 |
| `paper-lookup` | 跨 18 个学术 API 检索论文、预印本、引用、开放全文与仓储记录。 |
| `paperclip` | 检索并阅读生物医学论文、FDA/PMDA/EMA 文件、临床试验与蛋白记录。 |
| `paperzilla` | 通过 pz 管理 Paperzilla 研究源：读项目、检索订阅流、取推荐与经典论文。 |
| `parallel-web` | 用 Parallel CLI 做网页检索、URL 抽取、深度研究、结构化数据补全与实体发现。 |
| `pathml` | 本地计算病理研究：切片加载与切块、预处理与质控、h5path 与深度学习流程衔接。 |
| `pathogen-variant-surveillance` | 查询 GenSpectrum LAPIS 公开数据做病原基因组监测：谱系命名、周度频率与突变查询。 |
| `pathway-enrichment` | 对基因列表或排序基因做通路/基因集富集分析，并解读结果（GSEA/GO/KEGG 等）。 |
| `peer-review` | 起草有证据边界的建设性同行评审与结构化稿件评估。 |
| `pennylane` | 构建并可微分的 PennyLane 量子线路与混合 PyTorch/JAX 模型（VQE、QAOA 等）。 |
| `performance-optimization` | 全栈性能优化：前端、后端、查询与数据库；性能不达标或变慢时使用。 |
| `phylogenetics` | 用 MAFFT 比对 + IQ-TREE 最大似然（含 FastTree）构建并分析系统发育树。 |
| `pi-agent` | 安装、配置与使用 Pi（极简终端编码代理），含 provider 配置与嵌入方式。 |
| `pkpd-modeling` | 药代/药效建模：非房室分析、房室与群体 PK、TMDD、PBPK 与给药方案模拟。 |
| `planning-and-task-breakdown` | 把需求拆成有序、可实现的任务清单（有规格或明确需求但还没拆解时使用）。 |
| `polars` | 高性能 DataFrame：Python ETL、分析与从 pandas 迁移，支持表达式与惰性执行。 |
| `polars-bio` | 在 Polars DataFrame 上做基因组区间运算：重叠、最近邻、合并、覆盖与差集，读写 BED/VCF/BAM。 |
| `pptx-posters` | 制作并审查可在 PowerPoint 中编辑的学术海报（.pptx，无宏）。 |
| `pr` | 撰写 PR 描述正文（按项目约定组织变更说明）。 |
| `primekg` | 查询固定版本 PrimeKG 精准医学知识图谱：基因、药物、疾病与表型的类型化关联与多跳路径。 |
| `primer-design` | 设计并审查 PCR/RT-qPCR 引物：Primer3、明确的热力学条件与脱靶检查。 |
| `problem-report` | 以证据诊断问题并分轮盘问：代码锚定的报告（前情 / 代码 / 取证），先诊断再动手。 |
| `protocolsio-integration` | 按 protocols.io 官方 REST/MCP 契约读取、校验并安全导出实验方案数据。 |
| `prototype` | 造一个用完即弃的原型来回答设计问题（验证某方案是否可行、手感如何）。 |
| `pufferlib` | PufferLib 强化学习：环境与向量化、策略配置、PuffeRL 训练与检查点核查（按版本给指引）。 |
| `pybamm` | 用 PyBaMM 模拟锂离子电池充放电与静置实验：参数集来源记录与结果校验。 |
| `pycalphad` | 从热力学数据库计算有限温度 CALPHAD 平衡：相分数、相组成与稳定性扫描。 |
| `pydeseq2` | 用 PyDESeq2 做 bulk RNA-seq 差异表达：计数校验、设计公式与结果解读。 |
| `pydicom` | 读写、检视、变换与预检本地 DICOM 数据集与像素数据。 |
| `pyhealth` | 构建并校验 PyHealth 临床机器学习流程（EHR、信号、影像与医疗编码）。 |
| `pylabrobot` | 开发与评审 PyLabRobot 实验自动化：移液方案、离线仿真与硬件对接。 |
| `pymatgen` | 用 pymatgen 分析、校验、转换与变换材料结构与计算材料数据（相图、电子结构）。 |
| `pymc` | 用 PyMC 构建并检验贝叶斯模型：层次模型、NUTS、变分推断与 PSIS-LOO 诊断。 |
| `pymoo` | 用 pymoo 求解与验证单目标/多目标/超多目标优化（NSGA-II/III、MOEA/D、约束与 Pareto 前沿）。 |
| `pyopenms` | 用 pyOpenMS 处理质谱数据：蛋白组与代谢组流程、特征检测与定量。 |
| `pysam` | 读写、查询与过滤 SAM/BAM/CRAM/VCF/BCF/索引文件（HTSlib 封装）。 |
| `pytdc` | 经 PyTDC 使用 Therapeutics Data Commons：数据集检索、任务与评测指标。 |
| `pytorch-lightning` | 用 Lightning 组织 PyTorch 代码：LightningModule、Trainer、回调与分布式扩展。 |
| `pyzotero` | 用 pyzotero 管理 Zotero 文献库：检索、创建、更新与删除条目、集合与附件。 |
| `qa` | 对网站或 Web 应用做 QA 并给 1–5 分（5=无懈可击）与证据；发现问题时给修复建议。 |
| `qiime2-amplicon` | 把双端 16S 扩增子数据跑成 QIIME 2 的 ASV 与物种注释，保留可追溯产物。 |
| `qiskit` | 用 Qiskit 构建、模拟、编译并执行量子线路（IBM Quantum Runtime），面向 IBM 硬件。 |
| `qutip` | 用 QuTiP 5 模拟并审查封闭/开放量子系统：确定性、轨迹与相空间方法。 |
| `rdkit` | 化学信息学底层控制：SMILES/SDF 解析、描述符、指纹与反应处理（更简单场景用 datamol）。 |
| `rea-changelog-update` | 依据固定 Git 历史准备或重写 REA 发布日志与 GitHub Release 说明。 |
| `rea-tool-design` | 设计或变更 REA 调查工具：CLI/MCP 契约、provider 能力与 Evidence 语义。 |
| `read` | 抓取 URL 与 PDF 并总结或输出干净 Markdown（读、引用、保存网页时使用）。 |
| `redesign-existing-projects` | 把现有网站/应用升级到高品质：审计现状、识别廉价 AI 套路并给出改造方案。 |
| `relion` | 校验并执行 RELION 单颗粒冷冻电镜精修与半图后处理（STAR、光学、掩膜与 FSC）。 |
| `relsa-severity-assessment` | 实验动物福利的多变量严重度评估与终点时间预测（探索性打分）。 |
| `remote-browser` | 在受限沙箱中用 Browser Use CLI 控制隔离的云浏览器。 |
| `research` | 对高质量一手来源做调研，并把结论落成 Markdown 文件存档。 |
| `research-grants` | 科研基金申请的准备与评审支持（NSF/NIH/DOE/DARPA 及台湾 NSTC 等）。 |
| `research-lookup` | 为科学稿件或研究简报汇编最新学术证据（需明确要求时才用）。 |
| `researchwrite` | 基于已有证据撰写、修改或审查研究提案、开题报告与研究计划。 |
| `retro` | 对一次编码会话做复盘（哪些有效、哪些浪费、下次怎么改）。 |
| `reverse-engineer-anything` | 用 REA 逆向本机、托管、Electron/JavaScript、打包产物、固件与浏览器目标。 |
| `review-animations` | 按高标准审查动画/动效代码（源自 Emil Kowalski 的设计工程标准）。 |
| `rowan` | 用 Rowan 云端分子建模与药物化学工作流（Python API）：pKa、构象、对接与 MD。 |
| `scaffold-exercises` | 生成练习目录结构（章节、题目、解答与讲解），并保证通过 lint。 |
| `scanpy` | 单细胞 RNA-seq 标准流程：质控、标准化、HVG、PCA/UMAP/t-SNE、聚类与注释（可在 RDS/h5ad 间转换）。 |
| `scheduler` | 设置本机定时提醒与轻量本地任务（不涉及云端代理）。 |
| `scholar-evaluation` | 对学术作品做定性优先、可溯源的成长性评审与低风险评分卡审计。 |
| `scientific-brainstorming` | 科学创意发散：独立产出、结构化讨论、显式区分事实与假设。 |
| `scientific-critical-thinking` | 评估科学主张与证据质量：实验设计有效性、偏倚、混杂与 GRADE 框架。 |
| `scientific-schematics` | 用 Nano Banana 2 生成科学示意图草稿并迭代精修。 |
| `scientific-slides` | 制作科研汇报与会议幻灯片（PowerPoint/演示文稿）。 |
| `scientific-visualization` | 用 Matplotlib/Seaborn 等产出真实、无障碍、可出版的科研图表（含不确定度表达）。 |
| `scientific-writing` | 撰写、修改与审查科学稿件或报告，标注证据出处与报告规范。 |
| `scikit-bio` | 生物数据工具箱：序列分析、比对、系统发育树、多样性指标与生物文件 I/O。 |
| `scikit-learn` | 经典机器学习：分类、回归、聚类、预处理与 Pipeline 搭建。 |
| `scikit-survival` | 右删失与竞争风险生存分析：模型构建、评估与审计。 |
| `scvelo` | RNA 速率分析：从 spliced/unspliced 计数拟合动力学模型并推断潜在时间。 |
| `scvi-tools` | 单细胞组学概率模型：scVI 批次整合、scANVI 注释与 totalVI 等。 |
| `seaborn` | 统计可视化：分布、关系、分类与热图（与 pandas 集成）。 |
| `security-and-hardening` | 代码安全加固：审计输入处理、认证授权、OWASP 漏洞与依赖安全。 |
| `seo-aeo-audit` | 搜索与 AI 引用优化：SEO/AEO、结构化数据、元信息与可被引用的写法。 |
| `setup-matt-pocock-skills` | 为本仓库配置工程技能：接入 issue tracker、triage 标签词表与文档约定。 |
| `setup-pre-commit` | 在当前仓库配置 Husky pre-commit（lint-staged、类型检查与测试）。 |
| `setup-ts-deep-modules` | 在 TypeScript 仓库接入 dependency-cruiser，使每个包成为隐藏实现细节的深模块。 |
| `shap` | 用 SHAP 解释与审计模型预测：选择 explainer/masker 与全局/局部归因。 |
| `shipping-and-launch` | 生产上线准备：发布清单、灰度与回滚策略、上线前检查。 |
| `show-me` | 用简洁图表、代码结构草图与要点帮用户理解当前话题。 |
| `simpy` | 用 SimPy 构建、检视与分析有界离散事件仿真（进程、资源队列与中断）。 |
| `slack-qa-investigate` | 只读调查并回答代码库问题（不修改任何文件），给出有依据的答案。 |
| `smart-search` | 基于 opencli 的智能检索路由：按站点、社交、技术资料、新闻、购物、金融或中文内容选择最佳检索路径。 |
| `software-engineering-laws-and-philosophy` | 用 56 条软件工程定律指导技术决策、架构设计、规划与团队管理。 |
| `source-driven-development` | 让每个实现决策都落在官方文档上：动手前先查证官方说法而非凭记忆。 |
| `spec-driven-development` | 编码前先写规格：新项目、新功能或重大改动且尚无规格时使用。 |
| `stable-baselines3` | 用 Stable Baselines3 训练与评估单智能体强化学习（PPO/SAC/DQN/TD3/DDPG 等）。 |
| `statistical-analysis` | 研究数据的统计分析指引：检验选择、假设检查、效应量与功效。 |
| `statistical-power` | 研究规划阶段的样本量与统计功效计算（需要多少受试者才能检验出效应）。 |
| `statsmodels` | 用 statsmodels 拟合与诊断统计模型：OLS、GLM、离散与混合模型、ARIMA/SARIMAX。 |
| `stitch-design-taste` | 为 Google Stitch 生成 DESIGN.md：把高端 UI 标准写成代理可执行的语义设计系统。 |
| `sympy` | 精确符号数学：代数、微积分、方程求解与符号线性代数。 |
| `tamarind` | 使用 Tamarind 平台上的开源分子设计与结构生物学工具（AlphaFold/RFdiffusion/Boltz/ESMFold 等）。 |
| `tdd` | 测试先行开发：用户要求 test-first 或提到红绿重构时使用。 |
| `tellurium` | 用 Tellurium/libRoadRunner 从 SBML/Antimony 模拟生化动力模型并检查守恒。 |
| `terraform-style-check` | 按 HashiCorp 官方风格约定编写与审查 Terraform HCL。 |
| `think` | 把粗糙想法变成经确认、可决策的完整方案后再动手（架构、重构、交接与选型场景）。 |
| `tiledbvcf` | 用 TileDB-VCF 存取基因组变异调用：样本级 VCF/BCF 摄取、区域查询与队列导出。 |
| `timesfm-forecasting` | 用 Google TimesFM 做零样本时间序列预测（含 CSV 规整、分位数与协变量）。 |
| `to-questionnaire` | 把"你无法独自拍板"的决策转成一份问卷，交给该拍板的人填写。 |
| `to-spec` | 把当前对话整理成规格并发布到项目 issue tracker（不追问，直接成文）。 |
| `to-tickets` | 把计划、规格或对话拆成一组"曳光弹"式工单，每张都声明其边界与验收。 |
| `torch-geometric` | PyTorch Geometric 图神经网络：节点/边/图分类、消息传递与采样。 |
| `torchdrug` | TorchDrug 0.2.1 分子图工作流：性质预测、自监督预训练与生成、逆合成。 |
| `transformers` | Hugging Face Transformers：加载 Hub 模型、pipeline 推理、文本生成与 Trainer 微调。 |
| `treatment-plans` | 在临床决策已完成后，对治疗方案文档做格式整理与结构化校验（不参与临床决策）。 |
| `triage` | 让 issue 与外部 PR 走完 triage 状态机：分类、校验、必要时盘问后分派。 |
| `troubleshooting` | 用 Chrome DevTools MCP 排查连接与目标（page/target）异常。 |
| `ui` | 构建与改造生产级 UI：组件、排版、布局与视觉一致性（不含后端逻辑）。 |
| `umap-learn` | 非线性降维与二维/三维可视化：UMAP、DensMAP、参数化 UMAP 与嵌入聚类。 |
| `uncertainty-and-units` | 物理单位换算、GUM 不确定度传播、量纲检查与数量级合理性判断。 |
| `user-chrome-tabs` | 操作用户真实 macOS Chrome 与其既有登录会话中的标签页。 |
| `usfiscaldata` | 查询美国财政数据：国债、财政部报表、联邦支出与收入、证券。 |
| `using-agent-skills` | 会话开始或需要判断该用哪个技能/流程时，给出技能加载与使用规范。 |
| `vaex` | 处理超内存的 HDF5/Arrow/CSV/Parquet 数据集：表达式计算与统计。 |
| `venue-templates` | 按目标会议的模板与格式要求排版论文、海报、基金与 LaTeX 稿件。 |
| `vercel-cli-with-tokens` | 用令牌（非交互）方式部署与管理 Vercel 项目。 |
| `vercel-composition-patterns` | 组件组合模式重构：消除布尔 props 泛滥，改进复合组件与可复用结构。 |
| `vercel-optimize` | 降低 Vercel 账单并优化性能指标（含 Core Web Vitals）。 |
| `vercel-react-best-practices` | React/Next.js 性能最佳实践：包体积、数据获取与渲染优化。 |
| `vercel-react-native-skills` | React Native / Expo 移动端组件、动画与性能优化。 |
| `vercel-react-view-transitions` | React/Next.js 页面转场与共享元素动画（ViewTransition）。 |
| `wait-what` | 当某段解释没听懂时，请它换一种更直白的说法把同一件事讲清楚。 |
| `wayfinder` | 在陌生代码库里定位"该改哪里"：以目标为导向的代码导航与入口发现。 |
| `waypoint-bio` | 微生物组基础模型与基准：Waypoint 检查点、Compass 基准与丰度表分析。 |
| `web-accessibility-audit` | 审计 WCAG 无障碍合规并给出修复建议与优先级。 |
| `web-design-guidelines` | 界面评审：可访问性、设计一致性与 UX 问题清单。 |
| `web-performance-audit` | 剖析并优化页面速度与 Lighthouse 分数（加载与渲染瓶颈定位）。 |
| `webapp-testing` | 用 Playwright 测试本地 Web 应用：验证界面、截图与收集日志。 |
| `what-if-oracle` | 用结构化"假如…会怎样"情景压测研究计划（不做概率预测）。 |
| `wizard` | 需要人工操作（凭据、开通、割接）时的分步引导式 bash 流程。 |
| `write` | 起草、编辑或本地化中英文文稿（不含代码注释）。 |
| `write-plan` | 把需求写成分步实施计划（写计划、规格转任务、多文件改动前使用）。 |
| `writing-beats` | 写作"节拍"：把内容拆成有推进感的段落与节拍再成文。 |
| `writing-for-agents` | 面向代理写文档：技能、AGENTS.md/CLAUDE.md 与规则文件的写法与维护边界。 |
| `writing-fragments` | 写作"碎片"：先积累片段与素材，再组装成完整文稿。 |
| `writing-guidelines` | 审查文档与散文的写作规范（文风、结构、可读性）。 |
| `writing-shape` | 写作"成形"：为素材确定整体结构与叙事形状。 |
| `x402` | 用 Browser Use Cloud 的加密/USDC 钱包完成支付（无需 API Key 的方案）。 |
| `zarr-python` | 分块 N 维科学数组的读写：Zarr 分片、压缩与云存储 I/O。 |

[返回目录](#top)

---
<a id="s4"></a>
## 4. opencode 配置技能（`~/.config/opencode/skills/`，63 个）

> 11 个本地目录（含 `gstack` 包内子技能）+ 56 个指向 gstack 仓库的符号链接；链接目录名形如 `gstack-<name>`，技能名为 `<name>`。

| 技能 | 说明 |
| --- | --- |
| `gstack` | gstack 技能套件的总入口：把任何 gstack 请求路由到正确的子技能（规划、评审、QA、发布…）。 |
| `gstack-upgrade` | 升级 gstack 到最新版：识别全局安装还是仓库内置，执行升级并说明变更。 |
| `ai-chat-browser` | 在 Gemini / Perplexity 网页版界面中自动化提问与检索（非 API 方式）。 |
| `autoplan` | 自动跑完整计划评审：无需逐项确认，一次性拿到多视角评审结论。 |
| `benchmark` | 网页性能、Core Web Vitals 与 Lighthouse 基准（不用于 AI 评测）。 |
| `benchmark-models` | 比较 LLM 的延迟、成本与质量（不用于网页性能）。 |
| `browse` | 驱动真实浏览器（Aside）：打开页面、阅读、走完流程、截图并检查控制台错误。 |
| `canary` | 上线后监控：验证灰度发布、发现回归。 |
| `careful` | 危险命令防护：在 rm -rf、DROP TABLE、force-push、reset --hard 等操作前告警。 |
| `claude-code` | 调用 Claude Code 做代码评审、"claude review"、对抗式质询或征求第二意见。 |
| `codex` | 调用 Codex 做代码评审、对抗式挑战、咨询或第二意见。 |
| `context-save` | 保存当前工作上下文：Git 状态、已定决策与待办，供后续会话无缝接手。 |
| `context-restore` | 恢复此前保存的上下文（优先当前分支），跨会话/工作区继续工作。 |
| `cso` | 安全审计：静态发现为主，具备条件时补充复现与修复候选（OWASP、威胁建模、漏洞复核）。 |
| `design-consultation` | 设计咨询：为新项目建立设计体系、品牌规范或 DESIGN.md。 |
| `design-html` | 把定稿的设计或方案落成可交付的 HTML/CSS。 |
| `design-review` | 视觉 QA 与设计打磨（不做事前的方案设计评审）。 |
| `design-shotgun` | 设计发散：一次给出多个 UI 方向与变体供挑选。 |
| `deslop-shared-libs` | 在近期改动中发现值得抽出的共享代码，并只推荐真正可共享的部分。 |
| `devex-review` | 开发现场 DX 审计：上手测试与 TTHW（首个胜利耗时）测量。 |
| `diagram` | 把流程、架构、状态机画成图并导出 SVG/Excalidraw。 |
| `document-generate` | 为缺失文档补写教程、操作指南与模块说明。 |
| `document-release` | 发布前审计并同步文档与代码差异。 |
| `doko-research` | 多轮网页研究：交叉核对来源并产出结构化、带引用的报告。 |
| `doko-search` | 用本地浏览器在 Google/Bing/DuckDuckGo 上免费检索。 |
| `doko-summarize` | 通过真实浏览器抓取并总结网页要点。 |
| `doko-translate` | 整页翻译并尽量保留结构、格式、表格与代码块。 |
| `dokobot` | 读取 JS 渲染的动态页面与 SPA 内容。 |
| `freeze` | 冻结编辑范围：把本会话的写入限制在指定目录内，防止顺手改无关代码。 |
| `guard` | 全面安全模式：危险命令告警 + 目录级编辑限制（careful + freeze 的合体）。 |
| `investigate` | 系统化调试（四阶段）：调查 → 分析 → 假设 → 实施，先根因后修复。 |
| `ios-clean` | 从 iOS 工程移除 DebugBridge SPM 包与所有 #if DEBUG 调试接线。 |
| `ios-design-review` | 在真机上做 iOS 视觉设计审计（经 StateServer 连接真实 iPhone）。 |
| `ios-fix` | 自主修复 iOS bug：读源码、改代码、重编译、重部署并验证（配合 /ios-qa 结果）。 |
| `ios-qa` | 真机 iOS QA（SwiftUI）：经 USB CoreDevice 隧道连接 iPhone/iPad，读取状态并找 bug。 |
| `ios-sync` | 按上游模板重新生成 iOS 调试桥（StateServer.swift 等访问器）。 |
| `land-and-deploy` | 落地并部署：合并 PR、等 CI 与部署完成，再用 canary 检查验证生产健康。 |
| `landing-report` | 只读的队列看板：显示当前有哪些版本槽位被打开的 PR 占用。 |
| `make-pdf` | 把 Markdown 转成出版级 PDF（1 英寸页边距、智能分页与页码）。 |
| `notion-mcp` | 经 MCP 操作 Notion：创建、检索、更新页面、数据库与视图。 |
| `office-hours` | YC Office Hours 式提问：创业模式六问逼出真实需求；或对想法做可行性盘问。 |
| `open-gstack-browser` | 启动 GStack Browser：内置侧栏扩展的 AI 可控 Chromium。 |
| `pair-agent` | 把远程 AI 代理与你的浏览器配对：一条命令生成配对密钥并给出对方要执行的说明。 |
| `plan-ceo-review` | 以 CEO/创始人视角评审计划：重述问题、找 10 分产品、挑战前提并扩展范围。 |
| `plan-design-review` | 以设计师视角评审计划：逐维度 0–10 打分并说明理由。 |
| `plan-devex-review` | 交互式开发者体验计划评审：开发者画像、竞品对标与 DX 设计。 |
| `plan-eng-review` | 以工程经理视角锁定执行计划：架构、数据流、图示、边界与测试。 |
| `plan-tune` | 调整提问敏感度与开发者画像（gstack 的观察式自调优）。 |
| `qa-only` | 只报告不修复：产出浏览器/API/CLI/任务/worker/webhook 的结构化 bug 报告。 |
| `reverse-engineering` | 逆向 macOS 二进制与私有 API：stripped Mach-O、arm64e、系统进程（Ghidra/LLDB）。 |
| `review` | 上库前 PR 评审：对照基线分支检查 SQL 安全、LLM 信任边界与回归风险。 |
| `scrape` | 用已登录的真实浏览器会话抓取页面数据（只读）。 |
| `setup-browser-cookies` | 把真实 Chromium 的 cookie 导入无头浏览会话，并做登录校验。 |
| `setup-deploy` | 为 /land-and-deploy 配置部署平台与参数（Fly.io、Render、Vercel 等）。 |
| `setup-gbrain` | 安装并初始化 gbrain（本地 PGLite 或 Supabase），并注册到当前编码代理。 |
| `ship` | 发布工作流：合并基线分支、跑测试、评审 diff、升版本、更新 CHANGELOG 并提交。 |
| `sisyphus-execution-rules` | 会话开始强制加载：代理分工、实时更新、Git 限制与资源管理规则。 |
| `skillify` | 把最近一次成功的 /scrape 流程固化成磁盘上的常驻 browser-skill，供后续直接调用。 |
| `spec` | 五阶段把模糊意图写成精确可执行的规格，并落到 issue tracker。 |
| `sync-gbrain` | 让 gbrain 与仓库代码保持同步，并刷新 CLAUDE.md 里的检索指引。 |
| `test-audit` | 找出低价值或重复的测试及其维系的"仅供测试"代码；默认只报告，批量删除需你确认。 |
| `test-auth-bootstrap` | 在开发/测试环境自动取得可用凭据（或完整账号），使端到端验证不被登录/验证码卡住。 |
| `unfreeze` | 解除 freeze 的目录编辑限制，恢复全目录写入。 |

---

[返回目录](#top)

---

<a id="s5"></a>
## 5. 跨源同名（已去重，按来源优先级解析）

| 技能名 | 出现在 | 处理 |
| --- | --- | --- |
| `teach` | 内置 omp、用户级 | 以 **内置 omp** 为准（随工具安装） |
| `test-driven-development` | 内置 superpowers、用户级 | 以 **内置 superpowers** 为准 |
| `health` | 用户级、opencode | 以**用户级**为准（`~/.agents/skills/health`） |
| `learn` | 用户级、opencode | 以**用户级**为准 |
| `qa` | 用户级、opencode | 以**用户级**为准 |
| `retro` | 用户级、opencode | 以**用户级**为准 |

> 优先级：项目 > 内置 > 用户级 > opencode。另有同源同名不同目录（`browser-harness` 对应 `browser-harness` 与 `browser_harness`；`gstack/plan-design-review` 与 `gstack-plan-design-review` 等）已合并为一行。

---

[返回目录](#top)

---

<a id="s6"></a>
## 6. 家族速查

| 前缀 / 族 | 数量 | 说明 |
| --- | --- | --- |
| `cli-anything-*` | 69 | 为各类 GUI 应用封装的命令行驱动（详见 §3 对应条目） |
| `nature-*` | 14 | 学术写作与投稿全流程（检索、引用、配图、润色、审稿回复、专利、PPT） |
| `doko-*` | 4 | 浏览器检索 / 摘要 / 翻译 / 多轮研究 |
| `gstack-*`（目录名） | 56 | 符号链接指向外部 gstack 仓库；技能名不带前缀，见 §4 |
| `plan-*-review` | 4 | 计划的多视角评审（CEO / 设计 / DX / 工程） |
| `ios-*` | 5 | 真机 iOS 的 QA、修 bug、设计审计与调试桥同步 |
| `vercel-*` | 6 | Vercel 部署与 React/Next.js 性能与组织规范 |

---

[返回目录](#top)

---

<a id="s7"></a>
## 7. 校正记录（本次重写）

- **名称口径**：以技能 `SKILL.md` 的 frontmatter `name:` 为准。上一版按目录名收录，导致 `gstack-cso` 记成 `cso` 之外的名字、`browser_harness` 记成目录名等错位；本版修正，并在名字与目录不同时标注目录。
- **删除**：上一版提及的名字中 **114 个在本机已不存在**（如 `arkcli-*`、`ast-grep`、`docx`、`browser`、`clean`、`git-master` 等），已全部移除；**140 个新增/变形**已按实际收录。
- **去重**：合并 `browser-harness`（3 行 → 1 行）与 `gstack` 包内同名子技能（`plan-design-review`、`office-hours`、`gstack-upgrade`）；跨源同名的 6 组改记于 §5，正文只出现一次。
- **语言**：说明改由维护者用**简体中文重写**（此前为机器翻译腔的直译），技能名/命令/路径/产品名保持原文；每条 ≤40 字，讲清"做什么 + 何时用"。
- **口径（可复核）**：项目 4 · 内置 omp 2 · 内置 superpowers 15 · 用户级 416 个 SKILL.md（去重后 412）· opencode 70 个 SKILL.md（去重后 63）= 507 个 SKILL.md → **496 个唯一技能**。

[返回目录](#top)

