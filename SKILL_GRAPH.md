# SKILL GRAPH — AI Agent 技能索引

> **最后更新**: 2026-07-11
> **技能总数**: 436+ 个（新增 149 个）
> **用途**: AI 执行任务前扫描此文件识别相关技能；人类快速定位需要的工具

---

## 快速查找指南

| 我要做什么 | 推荐技能链 | 说明 |
|-----------|-----------|------|
| 规划新功能 | `think` → `brainstorming` → `planning-and-task-breakdown` → `implement` | 先想清楚再动手 |
| 修复 bug | `hunt` → `systematic-debugging` → `tdd` → `check` | 先找根因再修 |
| 代码审查 | `check` → `code-review` → `code-review-and-quality` | 多维度审查 |
| 创建 UI | `ui` → `design-taste-frontend` → `frontend-ui-engineering` | 设计先行 |
| 写文档 | `write` → `docs-update` → `documentation-and-adrs` | 去 AI 味 |
| Git 操作 | `git-workflow-and-versioning` → `create-pull-request` | 原子化提交 |
| 浏览器测试 | `browser` → `browser-use` → `webapp-testing` | 真机验证 |
| 学术写作 | `nature-writing` → `nature-polishing` → `nature-figure` | Nature 系列 |
| CLI 工具 | `cli-anything` → `cli-hub-meta-skill` | 为 GUI 应用构建 CLI |
| 部署上线 | `deploy-to-vercel` → `shipping-and-launch` → `ci-cd-and-automation` | 发布流水线 |
| 安全审计 | `security-and-hardening` → `cso` → `security-research` | 漏洞排查 |
| 性能优化 | `web-performance-audit` → `performance-optimization` → `debug-optimize-lcp` | 数据驱动 |
| 生物信息学 | `biopython` → `scanpy` → `scvi-tools` | 基因组分析全流程 |
| 药物发现 | `rdkit` → `datamol` → `deepchem` → `pytdc` | 分子 ML 管道 |
| 单细胞分析 | `scanpy` → `anndata` → `scvi-tools` → `scvelo` | scRNA-seq 全流程 |
| 蛋白质工程 | `esm` → `tamarind` → `diffdock` | 结构预测与对接 |
| 量子计算 | `qiskit` / `cirq` / `pennylane` / `qutip` | 按硬件选择框架 |
| 科研绘图 | `scientific-visualization` → `matplotlib` → `seaborn` | 出版级图表 |
| 文献综述 | `paper-lookup` → `literature-review` → `citation-management` | 系统性文献检索 |

---

## 分类索引

### 思维 & 规划

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `think` | 把模糊想法变成可执行计划 | 5 阶段流程：理解意图 → 探索约束 → 评估方案 → 生成计划 → 用户批准。不写代码，只做决策 | "怎么设计"、"用什么方案"、"值不值得做"、"给个方案" |
| `brainstorming` | 创建功能前的强制探索 | 逐个问澄清问题，提出 2-3 个方案并比较 trade-off，用户批准后才写代码。**任何功能开发前必须先过这个** | "我要做一个 X"、"添加功能"、"创建组件" |
| `idea-refine` | 细化原始想法 | 通过发散/收敛思维，把粗糙想法变成精确概念。适合还在探索阶段、不确定要做什么的情况 | "我有个想法"、"帮我细化一下"、"还不确定" |
| `planning-and-task-breakdown` | 把大任务拆成可执行步骤 | 分析依赖关系，识别可并行的任务，输出有序的 TODO list。适合 3 步以上的复杂任务 | "帮我拆任务"、"这个怎么做"、"太大了不知道从哪开始" |
| `spec-driven-development` | 编码前写规格说明 | 先写 spec（需求、约束、验收标准），再按 spec 实现。适合新项目或重大功能 | "写个规格"、"先定义清楚再做" |
| `executing-plans` | 在独立会话中执行书面计划 | 按照已有的 plan 文件逐步执行，适合跨会话的大型任务 | "按这个计划执行"、"继续上次的计划" |
| `subagent-driven-development` | 用子代理并行执行独立任务 | 把任务分发给多个子代理同时执行，适合互不依赖的并行工作 | "并行做"、"同时搞" |
| `dispatching-parallel-agents` | 面对 2+ 独立任务时的并行分发 | 和 subagent-driven-development 类似，但更侧重于任务编排和结果汇总 | "同时处理多个事情" |
| `wayfinder` | 把大项目分解为 issue 追踪 | 适合跨多个会话的大型项目，生成 issue tracker 上的 ticket 列表 | "这个项目要做很久"、"帮我规划路线图" |
| `to-spec` | 把对话变成规格说明 | 从当前对话中提取需求，生成 spec 并发布到 issue tracker | "把刚才讨论的变成 spec" |
| `to-tickets` | 把计划拆成 tickets | 把 plan 或对话拆成 tracer-bullet tickets，每个都有明确的依赖关系 | "把这些变成 tickets" |
| `what-if-oracle` | 结构化 What-If 场景分析 | 4-6 分支可能性探索：最佳、最可能、最差、狂野、逆向、二阶效应 | "如果 X 会怎样"、"压力测试决策" |
| `consciousness-council` | 多视角心智议会审议 | 从多个专家视角审视问题：技术、设计、商业、用户、风险 | "不同专家怎么看"、"多角度分析" |
| `experimental-design` | 实验设计（数据收集前） | 选择设计、随机化、区组、处理组合，确保结果可解释 | "怎么设计实验"、"随机化"、"DOE" |

### 代码 & 实现

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `implement` | 基于 spec 或 tickets 实现功能 | 读取 spec/tickets，按 TDD 流程实现，完成后自动触发 code review | "按这个 spec 实现"、"做这个功能" |
| `incremental-implementation` | 增量交付变更 | 把大变更拆成小步骤，每步都可验证。避免一次性写太多代码导致难以调试 | "这个改动很大"、"分步来做" |
| `tdd` / `test-driven-development` | 测试驱动开发 | 红 → 绿 → 重构循环。先写失败的测试，再写最少代码让它通过，最后重构 | "TDD"、"测试先行"、"红绿重构" |
| `full-output-enforcement` | 强制完整代码生成 | 禁止截断、禁止 placeholder、禁止"你可以扩展这个"。确保输出完整可用 | "给我完整的"、"不要省略" |
| `code-simplification` | 简化代码提高清晰度 | 重构代码使其更易读，但不改变行为。适合代码能工作但难以维护的情况 | "这段代码太复杂"、"简化一下" |
| `codebase-design` | 设计深层模块接口 | 帮你决定模块边界、接口设计、依赖方向。适合架构层面的设计决策 | "这个模块怎么设计"、"接口怎么定义" |
| `domain-modeling` | 构建/优化领域模型 | DDD 风格的领域建模，识别实体、值对象、聚合根、领域事件 | "领域模型"、"DDD"、"业务对象" |
| `ubiquitous-language` | 提取 DDD 术语表 | 从代码和对话中提取领域术语，建立团队统一语言 | "统一术语"、"领域语言" |
| `deprecation-and-migration` | 管理废弃和迁移 | 帮你安全地废弃旧 API、迁移用户、设置过渡期 | "要废弃这个 API"、"迁移旧代码" |
| `migrate-to-shoehorn` | 迁移测试文件到 shoehorn | 把测试中的 `as` 类型断言迁移到 @total-typescript/shoehorn | "测试迁移" |
| `optimize-for-gpu` | GPU 加速 Python 代码 | CuPy、Numba CUDA、Warp、cuDF、cuML 等 GPU 加速方案 | "GPU 加速"、"CUDA" |
| `modal` | Modal 无服务器 Python | 按需运行 Python，包括 GPU。适合 AI/ML 推理、批量处理 | "部署到 Modal"、"GPU 推理" |

### 调试 & 问题排查

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `hunt` | 找到根因再修复 | 3 次假设限制：如果 3 次尝试都没找到根因，停下来重新分析。**不修症状，只修根因** | "报错了"、"崩溃了"、"不工作"、"以前是好的" |
| `systematic-debugging` | 系统性根因调试 | 结构化的调试流程：收集证据 → 形成假设 → 验证假设 → 修复 → 验证修复 | "帮我调试"、"为什么失败" |
| `diagnosing-bugs` | 硬 bug 和性能回归的诊断循环 | 专门处理难以复现、难以定位的 bug。包括性能回归、内存泄漏、竞态条件 | "这个 bug 很难查"、"偶发的" |
| `debugging-and-error-recovery` | 系统性根因调试指南 | 和 systematic-debugging 类似，但更侧重于错误恢复策略 | "调试指南" |
| `memory-leak-debugging` | JS/Node.js 内存泄漏 | 使用 memlab 等工具分析堆快照，定位泄漏源 | "内存泄漏"、"OOM"、"内存一直涨" |
| `troubleshooting` | Chrome DevTools 排查连接问题 | 专门处理浏览器连接、目标页面不可达等问题 | "连不上"、"页面打不开" |
| `ci-fix` | 诊断修复 GitHub Actions CI 失败 | 分析 CI 日志，定位失败原因，自动修复并推送 | "CI 红了"、"构建失败" |
| `resolving-merge-conflicts` | 解决 git 合并/变基冲突 | 分析冲突原因，选择正确的解决策略，手动解决复杂冲突 | "有冲突"、"合并失败" |

### 审查 & 验证

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `check` | 代码差异/PR/发布就绪审查 | 读 diff，找问题，能修的直接修，不能修的问用户。**合并前必做** | "审查一下"、"看看代码"、"合并前检查" |
| `code-review` | 沿标准/规格两轴审查 | 两个维度：是否符合项目编码标准、是否符合 spec/PRD 要求 | "代码审查"、"PR 审查" |
| `code-review-and-quality` | 多轴代码审查 | 更全面的审查：正确性、安全性、性能、可维护性、测试覆盖 | "深度审查"、"质量审查" |
| `receiving-code-review` | 接收代码审查反馈 | 收到 reviewer 的反馈后，逐条分析、验证、实施。**不盲目接受，也不盲目拒绝** | "收到 review 意见" |
| `requesting-code-review` | 完成任务后请求审查 | 自动准备 PR 描述、变更说明、测试结果，请求 reviewer 审查 | "请求审查" |
| `verification-before-completion` | 声称完成前的验证 | 运行测试、检查覆盖率、验证构建，确保真的完成了 | "验证一下"、"确认完成" |
| `health` | 工程健康审计 | 综合评估：测试覆盖率、代码质量、依赖安全、文档完整性，给出 0-10 分 | "项目健康吗"、"代码质量评分" |
| `review-animations` | 审查动画/动效代码 | 按 Emil Kowalski 设计哲学审查动画代码：是否流畅、是否符合直觉 | "动画审查" |
| `web-accessibility-audit` | WCAG 无障碍审计 | 检查语义 HTML、ARIA 标签、键盘导航、颜色对比度 | "无障碍审查"、"a11y" |
| `seo-aeo-audit` | SEO/AEO 审计 | 检查 meta 标签、结构化数据、AI 可引用性 | "SEO 审计" |
| `web-performance-audit` | Web 性能审计 | 使用 Lighthouse/Core Web Vitals 分析页面性能 | "性能审计"、"加载慢" |
| `peer-review` | 结构化稿件/基金评审 | 用 CONSORT/STROBE 清单评估方法论、统计有效性、报告标准 | "审稿"、"写评审意见" |
| `scholar-evaluation` | 学术工作系统评估 | ScholarEval 框架：问题表述、方法论、分析、写作，量化评分 | "评估论文质量" |

### UI & 设计

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `ui` | 生产级 UI 构建 | 不是模板化输出，而是有设计观点的 UI。考虑排版、间距、响应式、可访问性 | "做页面"、"做组件"、"不好看" |
| `design-taste-frontend` | 反模板化前端设计 | 拒绝千篇一律的 AI 设计，追求有辨识度的视觉风格 | "设计着陆页"、"重设计" |
| `design-taste-frontend-v1` | v1 版本（向后兼容） | 旧版设计技能，保留用于需要兼容的项目 | "用旧版设计" |
| `frontend-ui-engineering` | 生产级 UI 工程 | 侧重于组件架构、状态管理、性能优化，而非纯视觉 | "前端工程"、"组件设计" |
| `redesign-existing-projects` | 升级现有网站到高级质量 | 先审计现有设计，找出 generic/AI 感，再逐个修复 | "这个网站太丑了"、"升级设计" |
| `image-to-code` | 图片到代码转换 | 看设计稿 → 分析布局 → 生成代码。**先生成参考图，再实现** | "按这个设计稿做" |
| `imagegen-frontend-web` | 前端设计参考图生成 | 为每个 section 生成独立的水平参考图，用于指导实现 | "生成设计参考" |
| `imagegen-frontend-mobile` | 移动端设计概念图 | iOS/Android 风格的移动端设计概念图，带手机 mockup 框架 | "移动端设计" |
| `brandkit` | 品牌套件图生成 | Logo 系统、品牌指南、视觉世界呈现。适合品牌建设 | "品牌设计"、"Logo" |
| `high-end-visual-design` | 高端代理设计风格 | 定义什么让网站感觉"贵"：字体、间距、阴影、卡片结构、动画 | "高端感"、"精致" |
| `minimalist-ui` | 极简编辑风格 | 温暖单色调、排版对比、扁平 bento 网格、柔和色彩 | "极简"、"温暖" |
| `industrial-brutalist-ui` | 工业粗野风格 | 瑞士印刷 + 军事终端美学。适合数据仪表盘、作品集 | "粗野"、"工业风" |
| `stitch-design-taste` | Google Stitch 语义设计系统 | 语义化的设计系统，生成 agent 友好的 DESIGN.md | "语义设计" |
| `gpt-taste` | 精英 UX/UI + GSAP 动效 | Python 驱动的真随机布局、严格 AIDA 页面结构、GSAP ScrollTrigger | "GSAP 动效" |
| `emil-design-eng` | Emil Kowalski 设计哲学 | UI polish、组件设计、动画决策、不可见的细节 | "精致"、"细节控" |
| `design-an-interface` | 生成多种不同接口设计 | 用并行子代理生成 2-3 种完全不同的接口设计方案 | "探索设计方案" |
| `diagram-design` | 技术/产品图表设计 | 架构图、流程图、时序图、ER 图、时间线，输出为 HTML+SVG | "画架构图"、"画流程图" |
| `animation-vocabulary` | 动画效果术语反查 | "那个弹跳的东西叫什么" → "Pop in"。帮你找到动画效果的正确术语 | "这个动效叫什么" |
| `infographics` | 专业信息图生成 | Nano Banana Pro AI 生成，Gemini 3 Pro 质量审查。10 种类型、8 种风格 | "做信息图" |
| `generate-image` | AI 图像生成/编辑 | FLUX、Nano Banana 2 模型。通用图像生成：照片、插画、概念艺术 | "生成图片" |
| `scientific-schematics` | 出版级科学图表 | Nano Banana 2 AI 生成：神经网络架构、系统图、流程图、生物通路 | "画科学图" |
| `latex-posters` | LaTeX 研究海报 | beamerposter/tikzposter/baposter。会议展示、学术海报 | "做学术海报" |
| `pptx-posters` | HTML/CSS 研究海报 | 现代 Web 海报设计，可导出 PDF/PPTX | "做 Web 海报" |

### 写作 & 文档

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `write` | 重写润色散文，去 AI 味 | **不只是润色，是让文字听起来像人写的**。删除"值得注意的是"、"总而言之"等 AI 套话 | "帮我写"、"润色"、"去AI味" |
| `docs-update` | 代码变更后更新文档 | 自动检测代码变更影响了哪些文档，同步更新 | "更新文档" |
| `documentation-and-adrs` | 记录决策和文档 | 创建 ADR（架构决策记录），记录为什么选择这个方案 | "记录决策"、"ADR" |
| `edit-article` | 编辑改进文章 | 重组段落、改善清晰度、收紧文字。适合已有草稿的改进 | "帮我改这篇文章" |
| `writing-guidelines` | 写作指南审查 | 按项目写作规范审查文档：风格、术语、格式 | "审查写作" |
| `writing-fragments` | 原始素材挖掘 | 从对话、笔记、代码中提取可写作的素材片段 | "挖掘素材" |
| `writing-beats` | 组装素材为节奏旅程 | 把零散素材组装成有节奏的文章结构 | "组装文章" |
| `writing-shape` | 将素材塑形为文章 | 逐段落塑造，把素材变成完整的文章 | "塑形文章" |
| `writing-great-skills` | 编写优质技能的参考 | 技能编写的最佳实践：描述、触发词、检查清单 | "写技能" |
| `writing-plans` | 多步骤任务前的规划 | 在写代码之前，先写实施计划 | "先规划" |
| `writing-skills` | 创建/编辑/验证技能 | 创建新技能、编辑现有技能、验证技能是否正常工作 | "创建技能" |
| `grill-me` / `grill-with-docs` | 压力测试计划/设计 | 像审问一样追问你的方案：假设是什么？边界在哪？失败了怎么办？ | "压力测试"、"审问一下" |
| `loop-me` | 工作流规格审问 | 专门审问工作流规格：步骤是否完整？异常怎么处理？ | "审问规格" |
| `teach` | 教授新技能/概念 | 交互式教学：解释概念、给示例、检查理解 | "教我"、"解释一下" |
| `scientific-writing` | 科学稿件撰写 | IMRAD 结构、APA/AMA/Vancouver 引用、CONSORT/STROBE 报告指南 | "写论文"、"学术写作" |
| `researchwrite` | 研究写作管道 | 提案优先：先写论点，再写段落。四层 QA 管道 | "写研究提案" |
| `literature-review` | 系统性文献综述 | PubMed、arXiv、bioRxiv、Semantic Scholar 多库检索，生成引用文档 | "文献综述" |
| `citation-management` | 引用管理 | Google Scholar/PubMed 搜索，提取元数据，生成 BibTeX | "管理引用" |
| `clinical-reports` | 临床报告撰写 | CARE 案例报告、ICH-E3 临床试验报告、SOAP 病历 | "写临床报告" |
| `clinical-decision-support` | 临床决策支持文档 | 患者队列分析、治疗推荐报告，GRADE 证据分级 | "CDS 文档" |
| `treatment-plans` | 医疗治疗计划 | 3-4 页聚焦的治疗计划，SMART 目标框架，证据导向 | "写治疗计划" |
| `iso-13485-certification` | ISO 13485 QMS 文档 | 医疗器械质量管理体系文档：差距分析、质量手册、程序文件 | "ISO 13485" |
| `research-grants` | 研究基金申请 | NSF、NIH、DOE、DARPA 申请书撰写，预算、更广泛影响、合规 | "写基金申请" |
| `docx` | Word 文档操作 | 创建、读取、编辑 .docx 文件：目录、标题、页码、模板 | "做 Word 文档" |
| `pptx` | PowerPoint 演示文稿 | 创建、编辑 .pptx 文件：演讲者备注、模板、图表 | "做 PPT" |
| `xlsx` | Excel 电子表格 | 创建、编辑 .xlsx 文件：公式、格式、财务模型、数据分析 | "做 Excel" |
| `pdf` | PDF 文件操作 | 读取、合并、拆分、旋转、水印、OCR、表单填写 | "处理 PDF" |
| `markitdown` | 文件转 Markdown | 支持 PDF、DOCX、PPTX、XLSX、图片、音频、HTML、CSV、JSON、XML、ZIP | "转 Markdown" |
| `liteparse` | 本地文档解析 | PDF、DOCX、Office 文件、图片解析，OCR，带空间坐标的 JSON | "解析文档" |
| `markdown-mermaid-writing` | Markdown + Mermaid 图表写作 | 科学文档写作标准：文本图表优先，24 种图表类型参考 | "写 Markdown" |

### Git & 版本控制

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `git-workflow-and-versioning` | 结构化 git 工作流 | **每次代码变更都用这个**。原子化提交、语义化版本、changelog 管理 | "提交"、"版本"、"分支" |
| `git-guardrails-claude-code` | 阻止危险 git 命令 | 设置 hooks 阻止 `push --force`、`reset --hard`、`clean` 等危险操作 | "Git 安全" |
| `create-pull-request` | 创建 GitHub PR | 自动生成 PR 描述、关联 issue、设置 reviewers | "创建 PR" |
| `resolving-merge-conflicts` | 解决合并冲突 | 分析冲突原因，选择正确的解决策略 | "有冲突" |
| `finishing-a-development-branch` | 完成开发分支集成 | 开发完成后，决定怎么集成：merge、PR、还是 cleanup | "分支做完了" |
| `using-git-worktrees` | 功能隔离的 git worktree | 并行开发时用 worktree 隔离不同功能，避免分支切换 | "并行开发" |
| `github-bug-report-triage` | 分类 GitHub bug 报告 | 评估 bug 报告是否可操作，识别缺失信息 | "分类 bug" |
| `github-issue-dedupe` | 检测重复 GitHub issue | 用语义搜索检测重复 issue | "有没有重复" |

### 浏览器自动化

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `browser` | CDP 直接浏览器控制 | 通过 CDP 协议直接控制已运行的 Chrome，适合需要精确控制的场景 | "浏览器自动化" |
| `browser-use` | Web 交互首选 | 高级浏览器交互：点击、填写、截图、等待。**首选方案** | "打开网页"、"测试网站" |
| `browser-harness` | CDP 浏览器控制 | 和 browser 类似，但提供更完整的 harness 封装 | "浏览器控制" |
| `browser-testing-with-devtools` | Chrome DevTools 测试 | 使用 DevTools MCP 进行真实浏览器测试：DOM 检查、网络分析、性能分析 | "浏览器测试" |
| `chrome-devtools` | DevTools MCP 调试 | 通过 MCP 使用 Chrome DevTools，适合调试和性能分析 | "DevTools 调试" |
| `chrome-devtools-cli` | DevTools CLI 脚本 | 用 shell 脚本自动化浏览器任务 | "CLI 浏览器" |
| `troubleshooting` | 连接/目标问题排查 | 专门处理浏览器连接失败、目标页面不可达 | "连不上" |
| `a11y-debugging` | 无障碍调试 | 使用 DevTools 检查无障碍问题 | "a11y 调试" |
| `debug-optimize-lcp` | LCP 优化调试 | 专门优化 Largest Contentful Paint | "LCP 优化" |
| `ai-chat-browser` | Gemini/Perplexity 浏览器通信 | 通过浏览器和 AI 聊天服务交互 | "和 AI 聊天" |

### Nature 系列（学术写作）

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `nature-writing` | Nature 风格稿件撰写 | 按 Nature 期刊风格撰写：引言、方法、结果、讨论 | "写论文"、"学术写作" |
| `nature-polishing` | 学术散文润色 | 润色学术英语，使其符合 Nature 风格 | "润色论文" |
| `nature-paper2ppt` | 论文转 PPT | 从论文生成演示文稿，包含关键图表和要点 | "做 PPT" |
| `nature-paper-to-patent` | 论文转专利 | 从研究论文提取可专利的技术贡献，生成专利草稿 | "转专利" |
| `nature-figure` | 论文配图 | 生成 publication-ready 的科研图表（matplotlib/ggplot） | "画图" |
| `nature-data` | 数据可用性声明 | 准备 Nature 要求的数据可用性声明 | "数据声明" |
| `nature-citation` | Nature/CNS 引用 | 自动添加 Nature/Cell/Science 系列引用 | "加引用" |
| `nature-reader` | 论文中英对照阅读 | 生成中英对照的论文阅读笔记 | "读论文" |
| `nature-reviewer` | 模拟审稿人评估 | 模拟 Nature 审稿人给出评审意见 | "预审" |
| `nature-response` | 审稿意见回复 | 撰写 point-by-point 的审稿意见回复 | "回复审稿人" |
| `nature-academic-search` | 多源文献检索 | 从 PubMed/CrossRef/arXiv/Scopus 多源检索文献 | "查文献" |
| `nature-literature-pipeline` | 文献发现管道 | 自动化的文献发现 → 评分 → 阅读 → 交付流水线 | "文献综述" |
| `nature-downloader` | 学术文献下载 | 通过机构权限下载学术论文 PDF | "下载论文" |
| `nature-experiment-log` | 实验日志记录 | 标准化的实验日志记录，输出到 Obsidian vault | "实验日志" |

### CLI 工具

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `cli-anything` | 为 GUI 应用构建 CLI | **核心技能**：把任何 GUI 应用变成 CLI 工具。分析应用结构，生成 Python harness | "给 X 做个 CLI" |
| `cli-hub-meta-skill` | 发现 Agent 原生 CLI | 从 CLI Hub 目录发现可用的 CLI 工具 | "有什么 CLI 可用" |
| `opencli-usage` | OpenCLI 顶层地图 | OpenCLI 的使用指南：发现适配器、通用标志、输出格式 | "OpenCLI 怎么用" |
| `cli-anything-*` | 70+ 个具体 CLI 工具 | 每个 `cli-anything-*` 都是为特定软件构建的 CLI harness。完整列表见下方 | 需要控制特定软件时 |

<details>
<summary>完整的 cli-anything-* 列表（70+ 个）</summary>

| 技能名 | 目标软件 | 用途 |
|--------|---------|------|
| `cli-anything-adguardhome` | AdGuard Home | 网络广告过滤和 DNS 管理 |
| `cli-anything-anygen` | AnyGen | OpenAPI 生成专业幻灯片/文档/网页 |
| `cli-anything-audacity` | Audacity | 音频编辑 |
| `cli-anything-blender` | Blender | 3D 场景编辑 |
| `cli-anything-browser` | 浏览器 | DOMShell MCP 浏览器自动化 |
| `cli-anything-calibre` | Calibre | 电子书管理和格式转换 |
| `cli-anything-ccswitch` | CC Switch | AI 编码工具配置管理 |
| `cli-anything-chromadb` | ChromaDB | 向量数据库管理 |
| `cli-anything-cloudanalyzer` | CloudAnalyzer | 点云评估和 QA |
| `cli-anything-cloudcompare` | CloudCompare | 3D 点云和网格处理 |
| `cli-anything-comfyui` | ComfyUI | AI 图像生成工作流 |
| `cli-anything-dify-workflow` | Dify | 工作流 DSL 管理 |
| `cli-anything-drawio` | Draw.io | 图表创建和编辑 |
| `cli-anything-eez-studio` | EEZ Studio | LVGL 嵌入式 UI 编辑 |
| `cli-anything-eth2-quickstart` | ETH2 | 以太坊节点部署 |
| `cli-anything-exa` | Exa | 网页搜索和内容检索 |
| `cli-anything-firefly-iii` | Firefly III | 个人财务管理 |
| `cli-anything-freecad` | FreeCAD | 参数化 3D CAD 建模 |
| `cli-anything-gimp` | GIMP | 图像编辑 |
| `cli-anything-godot` | Godot | 游戏引擎项目管理 |
| `cli-anything-hermes` | Hermes Agent | CLI 构建 |
| `cli-anything-inkscape` | Inkscape | 矢量图形编辑 |
| `cli-anything-intelwatch` | IntelWatch | 竞争情报和 OSINT |
| `cli-anything-iterm2` | iTerm2 | 终端会话控制 |
| `cli-anything-iterm2-ctl` | iTerm2 高级 | 高级终端控制 |
| `cli-anything-joplin` | Joplin | 笔记管理 |
| `cli-anything-jumpserver` | JumpServer | 堡垒机管理 |
| `cli-anything-kdenlive` | Kdenlive | 视频编辑 |
| `cli-anything-krita` | Krita | 数字绘画 |
| `cli-anything-libreoffice` | LibreOffice | 文档编辑 |
| `cli-anything-live2d` | Live2D | Cubism 模型管理 |
| `cli-anything-lldb` | LLDB | Python API 调试 |
| `cli-anything-macrocli` | MacroCLI | GUI 宏命令 |
| `cli-anything-mailchimp` | Mailchimp | 营销 API 管理 |
| `cli-anything-mermaid` | Mermaid | 图表创建和渲染 |
| `cli-anything-minimax` | MiniMax AI | AI 聊天和 TTS |
| `cli-anything-mubu` | Mubu | 实时乐谱桥接 |
| `cli-anything-musescore` | MuseScore | 乐谱写和编辑 |
| `cli-anything-n8n` | n8n | 工作流自动化 |
| `cli-anything-notebooklm` | NotebookLM | 笔记本管理 |
| `cli-anything-novita` | Novita AI | OpenAI 兼容 AI API |
| `cli-anything-nsight-graphics` | Nsight Graphics | GPU 追踪分析 |
| `cli-anything-nslogger` | NSLogger | 日志文件解析 |
| `cli-anything-obs-studio` | OBS Studio | 场景集合编辑 |
| `cli-anything-obsidian` | Obsidian | 知识库管理 |
| `cli-anything-ollama` | Ollama | 本地 LLM 推理 |
| `cli-anything-openrefine` | OpenRefine | 数据清洗 |
| `cli-anything-openscreen` | Openscreen | 录屏编辑 |
| `cli-anything-pm2` | PM2 | Node.js 进程管理 |
| `cli-anything-qgis` | QGIS | 地理信息处理 |
| `cli-anything-quietshrink` | macOS HEVC | 录屏压缩（硬件加速） |
| `cli-anything-rekordbox` | Rekordbox | DJ 库和现场混音 |
| `cli-anything-renderdoc` | RenderDoc | 图形调试 |
| `cli-anything-rms` | Teltonika RMS | 设备管理 |
| `cli-anything-safari` | Safari | 浏览器自动化 |
| `cli-anything-sbox` | s&box | Source 2 游戏引擎 |
| `cli-anything-seaclip` | SeaClip-Lite | 项目管理 |
| `cli-anything-shotcut` | Shotcut | 视频编辑 |
| `cli-anything-siyuan` | 思源笔记 | 知识库管理 |
| `cli-anything-slay-the-spire-ii` | Slay the Spire 2 | 游戏控制 |
| `cli-anything-threemf` | 3MF | 网格几何编辑 |
| `cli-anything-tigris` | Tigris | 对象存储 |
| `cli-anything-unimol-tools` | Uni-Mol | 分子性质预测 |
| `cli-anything-unrealinsights` | Unreal Engine | 性能追踪分析 |
| `cli-anything-videocaptioner` | VideoCaptioner | 视频字幕生成 |
| `cli-anything-wavetone` | WaveTone | 音频分析 |
| `cli-anything-web-yu-pri` | Japan Post | 邮政 Web 服务 |
| `cli-anything-wiremock` | WireMock | HTTP Mock 服务器 |
| `cli-anything-zoom` | Zoom | 会议管理 |
| `cli-anything-zotero` | Zotero | 文献管理 |

</details>

### 研究 & 学习

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `research` | 针对主源调查问题 | 用后台代理研究问题，引用一手资料（官方文档、源码、规范），输出带引用的 Markdown | "帮我查一下"、"研究一下" |
| `learn` | 六阶段研究工作流 | 收集材料 → 消化 → 组织 → 输出。适合不熟悉的领域，输出可发表的参考文档 | "学习一下"、"深入研究" |
| `read` | 读取 URL/PDF 并总结 | 抓取网页或 PDF 内容，生成简洁总结或完整 Markdown | "读一下这个"、"总结这个网页" |
| `find-skills` | 发现安装技能 | 帮你找到能做某件事的技能 | "有没有做 X 的技能" |
| `ask-matt` | 询问适合的技能/流 | 路由器：根据你的需求推荐最合适的技能 | "我该用什么技能" |
| `using-agent-skills` | 发现调用技能 | 元技能：发现和调用其他技能 | "怎么用技能" |
| `exa-search` | Exa 网页搜索 | 语义搜索、研究论文过滤、URL 内容提取 | "搜索学术内容" |
| `parallel-web` | 并行网页工具包 | 网页搜索、URL 提取、批量数据丰富、深度研究报告 | "网页研究" |
| `paper-lookup` | 学术文献 API 搜索 | 10 个学术数据库：PubMed、arXiv、OpenAlex、Crossref、Semantic Scholar | "找论文" |
| `paperzilla` | 论文推荐和详情 | 项目推荐、规范论文详情、Markdown 摘要 | "推荐论文" |
| `research-lookup` | 研究信息检索 | 快速网页搜索、深度多源综合、学术论文搜索 | "查研究" |
| `database-lookup` | 公共数据库 API 查询 | 科学、监管、金融数据库的可重复查询 | "查数据库" |
| `literature-review` | 系统性文献综述 | PubMed、arXiv、bioRxiv、Semantic Scholar 多库检索 | "文献综述" |
| `hypothesis-generation` | 结构化假设制定 | 从观察数据制定可检验的假设、预测、机制 | "提出假设" |
| `hypogenic` | 自动化假设生成和测试 | LLM 驱动的表格数据假设探索 | "数据假设" |
| `scientific-brainstorming` | 创意研究构思 | 开放式头脑风暴、跨学科联系、识别研究空白 | "科研创意" |
| `scientific-critical-thinking` | 科学声明评估 | 评估实验设计有效性、识别偏倚、GRADE 证据分级 | "批判性思维" |
| `exploratory-data-analysis` | 科学数据 EDA | 200+ 文件格式的全面探索性分析，自动检测文件类型 | "EDA" |

### 部署 & CI/CD

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `deploy-to-vercel` | 部署到 Vercel | 一键部署到 Vercel，返回预览链接 | "部署到 Vercel" |
| `vercel-cli-with-tokens` | Token 认证部署 | 用 access token 而非交互式登录部署 | "用 token 部署" |
| `vercel-optimize` | Vercel 成本优化 | 分析 Vercel 用量，找出省钱的机会 | "Vercel 太贵了" |
| `shipping-and-launch` | 生产发布准备 | 发布前检查清单：监控、分阶段发布、回滚策略 | "准备发布" |
| `ci-cd-and-automation` | CI/CD 管道自动化 | 设置 GitHub Actions、质量门禁、自动测试 | "配置 CI/CD" |
| `ci-fix` | CI 失败诊断修复 | 分析 CI 日志，定位失败原因，自动修复 | "CI 红了" |
| `setup-pre-commit` | Husky pre-commit hooks | 设置提交前自动 lint、类型检查、测试 | "设置 pre-commit" |

### 性能 & 优化

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `performance-optimization` | 应用性能优化 | 分析瓶颈，优化关键路径。有 before/after 数据 | "优化性能" |
| `web-performance-audit` | Web 性能审计 | Lighthouse + Core Web Vitals 分析 | "页面加载慢" |
| `debug-optimize-lcp` | LCP 优化 | 专门优化 Largest Contentful Paint | "LCP 高" |
| `memory-leak-debugging` | 内存泄漏调试 | 使用 memlab 分析堆快照 | "内存泄漏" |
| `vercel-optimize` | Vercel 成本优化 | 分析 Vercel 用量和费用 | "Vercel 费用" |
| `optimize-for-gpu` | GPU 加速 Python | CuPy、Numba CUDA、Warp、cuDF、cuML 等 | "GPU 加速" |

### 安全

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `security-and-hardening` | 代码加固 | OWASP Top 10、输入验证、认证加固、敏感数据保护 | "安全审查"、"加固" |
| `git-guardrails-claude-code` | 阻止危险 git 命令 | 设置 hooks 阻止 force push、reset --hard 等 | "Git 安全" |

### 测试

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `tdd` / `test-driven-development` | 测试驱动开发 | 红 → 绿 → 重构循环。先写失败测试，再写代码 | "TDD"、"测试先行" |
| `test-driven-development` | 实现功能前先写测试 | 和 tdd 相同，强调在写代码之前先写测试 | "先写测试" |
| `webapp-testing` | Web 应用测试 | 用 Playwright 测试 Web 应用：截图、交互、验证 | "测试网站" |
| `web-performance-audit` | 性能审计 | Lighthouse 分析 | "性能测试" |
| `web-accessibility-audit` | 无障碍审计 | WCAG 合规检查 | "无障碍测试" |
| `seo-aeo-audit` | SEO 审计 | 搜索引擎优化检查 | "SEO 测试" |

### MCP & 工具构建

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `mcp-builder` | 构建 MCP 服务器 | 用 FastMCP (Python) 或 MCP SDK (TypeScript) 构建 MCP 服务器 | "做 MCP 服务器" |
| `cli-anything` | CLI 工具构建 | 为 GUI 应用构建 CLI harness | "做 CLI" |

### 项目管理

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `qa` | 交互式 QA 会话 | 用户报 bug → agent 探索代码 → 自动创建 GitHub issue | "报 bug"、"QA" |
| `triage` | Issue/PR 分类 | 把 issue/PR 分类：验证、追问、写 brief | "分类 issue" |
| `health` | 工程健康审计 | 综合评分：测试、代码质量、依赖安全、文档 | "项目健康吗" |
| `scheduler` | 设备提醒/本地任务 | 设置本地提醒和定时任务 | "提醒我" |
| `handoff` | 会话交接文档 | 把当前对话压缩成交接文档，供其他 agent 继续 | "交接" |
| `context-engineering` | 代理上下文优化 | 优化 agent 的上下文配置：规则文件、记忆策略 | "优化上下文" |
| `pi-agent` | Pi 终端编码工具 | 安装 Pi、配置提供者/模型/设置、创建技能/扩展 | "用 Pi" |
| `dhdna-profiler` | 认知模式提取 | 从文本提取认知指纹：思维方式、决策风格、推理模式 | "分析思维模式" |
| `autoskill` | 屏幕观察技能发现 | 通过 screenpipe 观察屏幕，检测重复工作流，提出新技能 | "观察我做什么" |

### 设计风格

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `minimalist-ui` | 极简温暖风格 | 温暖单色调、排版对比、扁平 bento 网格 | "极简" |
| `industrial-brutalist-ui` | 工业粗野风格 | 瑞士印刷 + 军事终端美学 | "粗野" |
| `high-end-visual-design` | 高端精致风格 | 字体、间距、阴影、卡片结构、动画 | "高端" |
| `gpt-taste` | GSAP 动效工程 | Python 随机布局、AIDA 结构、GSAP ScrollTrigger | "GSAP" |
| `stitch-design-taste` | Google Stitch 语义 | 语义化设计系统 | "Stitch" |

### 移动端

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `imagegen-frontend-mobile` | 移动端设计图 | iOS/Android 风格的移动端设计概念图 | "移动端设计" |

### 集成 & API

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `api-and-interface-design` | API 设计指南 | REST/GraphQL API 设计最佳实践 | "设计 API" |
| `vercel-composition-patterns` | React 组合模式 | React 组件组合模式，减少 boolean prop 泛滥 | "组件设计" |
| `vercel-react-best-practices` | React 最佳实践 | Vercel 工程团队的 React/Next.js 最佳实践 | "React 最佳实践" |
| `vercel-react-native-skills` | React Native 技能 | React Native 和 Expo 的移动端开发 | "React Native" |
| `vercel-react-view-transitions` | React 视图过渡 | React View Transition API 实现页面过渡动画 | "页面过渡" |

### 架构

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `codebase-design` | 深层模块设计 | 设计深层模块的接口、边界、可测试性 | "模块设计" |
| `domain-modeling` | 领域建模 | DDD 风格的领域建模 | "DDD" |
| `improve-codebase-architecture` | 架构改进报告 | 扫描代码库，找出深层化机会，生成 HTML 报告 | "架构改进" |
| `software-engineering-laws-and-philosophy` | 软件工程法则 | 56 条软件工程法则：技术决策、架构设计、团队管理 | "工程法则" |

### 教学

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `teach` | 教授新技能/概念 | 交互式教学：解释概念、给示例、检查理解 | "教我" |
| `scaffold-exercises` | 创建练习脚手架 | 创建练习目录结构：问题、解答、解释 | "做练习" |

---

## 🧬 生物信息学 & 基因组学（新增 73 个）

### 单细胞分析

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `scanpy` | scRNA-seq 标准分析管道 | QC、归一化、降维（PCA/UMAP/t-SNE）、聚类、差异表达、可视化 | "单细胞分析" |
| `anndata` | 注释矩阵数据结构 | .h5ad 文件处理，scverse 生态系统数据格式 | "h5ad 文件" |
| `scvi-tools` | 单细胞深度生成模型 | scVI 批次校正、迁移学习、不确定性差异表达、多模态整合 | "深度学习单细胞" |
| `scvelo` | RNA 速度分析 | 从未剪接/剪接 mRNA 动态估计细胞状态转换 | "RNA 速度" |
| `cellxgene-census` | CZ CELLxGENE Census 查询 | 群体规模的细胞元数据、基因表达切片、嵌入、参考图谱比较 | "查询公共单细胞数据" |

### 基因组学工具

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `biopython` | 分子生物学工具包 | 序列操作、文件解析（FASTA/GenBank/PDB）、NCBI/PubMed 访问 | "序列处理"、"BLAST" |
| `pysam` | 基因组文件工具 | SAM/BAM/CRAM 比对、VCF/BCF 变异、FASTA/FASTQ 序列读写 | "BAM 处理" |
| `gget` | 快速生物信息学查询 | 20+ 数据库快速查询：基因信息、BLAST、AlphaFold 结构 | "快速查基因" |
| `bioservices` | 40+ 生物信息学服务统一接口 | UniProt、KEGG、ChEMBL、Reactome 等跨数据库分析 | "多库查询" |
| `gtars` | Rust 基因组区间分析 | 高性能基因组区间操作，Python 绑定 | "高性能基因组" |
| `geniml` | 基因组区间 ML | Region2Vec、BEDspace 嵌入训练，scATAC-seq 分析 | "基因组 ML" |
| `polars-bio` | Polars 基因组区间操作 | BED/VCF/BAM/GFF 区间的高性能操作 | "基因组数据框" |
| `onekgpd` | 1000 Genomes Project | 3,202 个全基因组测序个体的变异查询 | "千人基因组" |
| `tiledbvcf` | 基因组变异存储 | TileDB 高效 VCF/BCF 存储和查询 | "变异数据存储" |
| `pacsomatic` | nf-core/pacsomatic 管道 | 肿瘤-正常配对 BAM 输入的工作流操作 | "肿瘤基因组" |

### 生物实验平台

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `benchling-integration` | Benchling SDK/API | 注册实体、库存、ELN 条目、工作流、数据仓库查询 | "Benchling 自动化" |
| `labarchive-integration` | 电子实验笔记本 API | 访问笔记本、管理条目/附件、备份、集成 protocols.io | "实验记录" |
| `protocolsio-integration` | protocols.io API | 搜索、创建、更新、发布实验方案 | "实验方案" |
| `opentrons-integration` | Opentrons 协议 API | OT-2 和 Flex 机器人的官方协议 API | "液体处理机器人" |
| `pylabrobot` | 厂商无关实验室自动化 | Hamilton、Tecan、Opentrons 等多厂商统一编程 | "实验室自动化" |
| `ginkgo-cloud-lab` | Ginkgo Bioworks Cloud Lab | 蛋白表达纯化、定量、mRNA 合成、热稳定性分析 | "云实验室" |
| `omero-integration` | 显微镜数据管理 | 图像访问、数据集检索、像素分析、ROI 管理 | "显微镜数据" |
| `dnanexus-integration` | DNAnexus 云基因组 | 构建应用、数据管理、工作流执行 | "云基因组" |

### 蛋白质 & 分子建模

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `esm` | ESM 蛋白质语言模型 | ESM3/ESMC 模型、ESMFold2 折叠 | "蛋白质结构预测" |
| `tamarind` | 分子设计和结构生物学 | AlphaFold、RFdiffusion、ProteinMPNN、DiffDock 等云端工具 | "蛋白质设计" |
| `adaptyv` | Adaptyv Bio Foundry API | 蛋白质实验设计、提交、结果检索 | "蛋白质筛选" |
| `rdkit` | 化学信息学工具包 | SMILES/SDF 解析、描述符、指纹、子结构搜索、2D/3D 生成 | "分子操作" |
| `datamol` | RDKit Pythonic 封装 | 简化接口：SMILES 解析、标准化、描述符、指纹、聚类 | "分子标准工作流" |
| `deepchem` | 分子 ML | 多样化特征化器、预构建数据集、MoleculeNet 基准 | "分子 ML" |
| `molfeat` | 分子特征化 | 100+ 特征化器：ECFP、MACCS、描述符、预训练模型 | "分子特征" |
| `torchdrug` | PyTorch GNN 分子 | 图神经网络用于药物发现、蛋白质建模 | "分子 GNN" |
| `pytdc` | Therapeutics Data Commons | AI-ready 药物发现数据集：ADME、毒性、DTI | "药物数据集" |
| `medchem` | 药物化学过滤器 | Lipinski、Veber、CNS、PAINS、NIBR 规则 | "化合物筛选" |
| `diffdock` | 分子对接 | DiffDock/DiffDock-L 蛋白质-小分子姿态预测 | "分子对接" |
| `rowan` | 云端分子建模 | pKa 预测、构象集成、对接、蛋白质-配体共折叠 | "云端分子建模" |
| `molecular-dynamics` | 分子动力学模拟 | OpenMM + MDAnalysis：力场、能量最小化、RMSD/RMSF 分析 | "MD 模拟" |
| `glycoengineering` | 蛋白质糖基化工程 | N-糖基化序列扫描、O-糖基化热点预测 | "糖基化分析" |

### 生物数据分析

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `bulk-rnaseq` | 批量 RNA-seq 全流程 | QC → 比对 → 定量 → 差异表达 → 通路富集 → 图表 | "RNA-seq 分析" |
| `pydeseq2` | 差异基因表达 | PyDESeq2 公式化设计、Wald 检验、FDR 校正、LFC 收缩 | "差异表达" |
| `pathway-enrichment` | 通路和基因集富集 | ORA、GSEA、ssGSEA/GSVA，GO、KEGG、Reactome、WikiPathways | "通路分析" |
| `deeptools` | NGS 分析工具 | BAM → bigWig、QC、热图/剖面图 | "ChIP-seq 可视化" |
| `scikit-bio` | 生物数据工具 | 序列分析、比对、系统发育树、多样性指标 | "微生物组分析" |
| `flowio` | FCS 文件解析 | 流式细胞术标准文件 v2.0-3.1 解析 | "流式数据" |
| `imaging-data-commons` | NCI 成像数据中心 | 公共癌症成像数据查询和下载 | "医学影像" |
| `histolab` | WSI 切片提取 | H&E 切片处理、组织检测、切片提取、染色归一化 | "病理切片" |
| `pathml` | 计算病理学 | 多重免疫荧光、核分割、组织图构建 | "计算病理" |
| `depmap` | Cancer Dependency Map | 基因依赖评分、药物敏感性数据 | "癌症依赖" |
| `pydicom` | DICOM 文件处理 | 医学影像数据读写、像素提取、匿名化 | "DICOM 处理" |
| `pyhealth` | 临床深度学习管道 | EHR/信号/成像数据集、临床预测模型 | "临床 ML" |
| `pyopenms` | 质谱分析 | 蛋白质组学和代谢组学全流程 | "质谱分析" |
| `matchms` | 质谱相似性 | 光谱比较、化合物鉴定、库搜索 | "代谢组学" |
| `primekg` | 精准医学知识图谱 | 基因、药物、疾病、表型的多尺度生物数据 | "知识图谱" |

### 系统发育 & 进化

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `phylogenetics` | 系统发育树构建 | MAFFT 多序列比对、IQ-TREE 2 最大似然、FastTree | "建进化树" |
| `etetoolkit` | 系统发育树工具 | Newick/NHX 树操作、进化事件检测、NCBI 分类 | "树操作" |

### 基因调控

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `arboreto` | 基因调控网络推断 | GRNBoost2、GENIE3 算法，从基因表达数据推断 TF-靶基因关系 | "GRN 推断" |
| `cobrapy` | 约束代谢建模 | FBA、FVA、基因敲除、通量采样、SBML 模型 | "代谢建模" |

### 材料科学

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `pymatgen` | 材料科学工具 | 晶体结构、相图、能带结构、DOS、Materials Project 集成 | "材料计算" |

### 生物信息学平台

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `nextflow` | Nextflow 数据管道 | 构建、运行、调试 Nextflow/nf-core 工作流 | "生物信息学管道" |
| `lamindb` | 生物数据湖仓 | 数据集注册、谱系追踪、验证、本体标注 | "数据管理" |
| `latchbio-integration` | Latch 生物信息学平台 | Latch SDK 构建管道，无服务器部署 | "云生物信息学" |
| `bgpt-paper-search` | 科学论文结构化数据 | 从全文提取 25+ 字段：方法、结果、样本量、质量评分 | "论文数据提取" |

---

## 🔬 科学研究 & 写作（新增 35 个）

### 科研方法论

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `experimental-design` | 实验设计 | 随机化、区组、处理组合，确保结果可解释 | "设计实验" |
| `statistical-analysis` | 统计分析指导 | 检验选择、假设检查、效应量、功效分析、贝叶斯替代 | "统计分析" |
| `statistical-power` | 样本量和功效计算 | 先验功效分析、最小可检测效应、功效曲线 | "计算样本量" |
| `statsmodels` | 统计模型库 | OLS、GLM、混合模型、ARIMA，详细诊断和推断 | "统计建模" |

### 科研可视化

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `scientific-visualization` | 出版级图表 | 多面板布局、显著性标注、误差条、色盲安全调色板 | "论文图表" |
| `scientific-slides` | 研究演讲幻灯片 | PowerPoint/LaTeX Beamer 演示文稿，演讲者指导 | "学术报告" |
| `scientific-schematics` | 出版级科学图表 | 神经网络架构、系统图、流程图、生物通路 | "科学示意图" |
| `infographics` | 专业信息图 | Nano Banana Pro AI 生成，10 种类型、8 种风格 | "信息图" |
| `generate-image` | AI 图像生成 | FLUX、Nano Banana 2 模型，通用图像 | "生成图片" |
| `latex-posters` | LaTeX 研究海报 | beamerposter/tikzposter/baposter | "学术海报" |
| `pptx-posters` | HTML/CSS 研究海报 | 现代 Web 海报，可导出 PDF/PPTX | "Web 海报" |

### 科研写作

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `scientific-writing` | 科学稿件撰写 | IMRAD 结构、引用格式、报告指南 | "写论文" |
| `researchwrite` | 研究写作管道 | 提案优先，四层 QA 管道 | "写提案" |
| `literature-review` | 系统性文献综述 | 多库检索，生成引用文档 | "文献综述" |
| `citation-management` | 引用管理 | Google Scholar/PubMed 搜索，BibTeX 生成 | "管理引用" |
| `peer-review` | 结构化稿件评审 | CONSORT/STROBE 清单评估 | "审稿" |
| `scholar-evaluation` | 学术工作评估 | ScholarEval 框架量化评分 | "评估论文" |
| `venue-templates` | 期刊/会议模板 | Nature、Science、IEEE、ACM 等格式要求 | "投稿模板" |
| `research-grants` | 研究基金申请 | NSF、NIH、DOE、DARPA 申请书 | "写基金" |

### 临床 & 医疗

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `clinical-reports` | 临床报告 | CARE 案例、ICH-E3 试验、SOAP 病历 | "临床报告" |
| `clinical-decision-support` | 临床决策支持 | 患者队列分析、治疗推荐，GRADE 分级 | "CDS" |
| `treatment-plans` | 治疗计划 | 3-4 页聚焦计划，SMART 目标 | "治疗计划" |
| `iso-13485-certification` | ISO 13485 QMS | 医疗器械质量管理体系文档 | "ISO 13485" |

### 文档处理

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `docx` | Word 文档操作 | 创建、读取、编辑 .docx 文件 | "Word 文档" |
| `pptx` | PowerPoint 操作 | 创建、编辑 .pptx 文件 | "PPT" |
| `xlsx` | Excel 操作 | 创建、编辑 .xlsx 文件 | "Excel" |
| `pdf` | PDF 操作 | 读取、合并、拆分、OCR | "PDF" |
| `markitdown` | 文件转 Markdown | 200+ 格式支持 | "转 Markdown" |
| `liteparse` | 本地文档解析 | 带空间坐标的 JSON 输出 | "解析文档" |

---

## 🔭 天文学 & 地球科学（新增 5 个）

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `astropy` | 天文学 Python 库 | 单位/量、坐标、FITS I/O、WCS、宇宙学 | "天文计算" |
| `geomaster` | 地理空间科学 | 遥感、GIS、空间分析、地球观测 ML | "地理空间" |
| `geopandas` | 地理空间向量数据 | Shapefile、GeoJSON、空间分析、几何操作 | "地理数据" |
| `matlab` | MATLAB/Octave 数值计算 | 矩阵运算、数据分析、可视化 | "MATLAB" |
| `sympy` | 符号数学 | 代数、微积分、方程求解、符号线性代数 | "符号计算" |

---

## ⚛️ 量子计算（新增 4 个）

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `qiskit` | IBM 量子计算 | IBM 硬件执行、量子纠错、Qiskit Runtime | "IBM 量子" |
| `cirq` | Google 量子计算 | Google Quantum AI 硬件、噪声感知电路 | "Google 量子" |
| `pennylane` | 硬件无关量子 ML | 自动微分、变分算法、量子神经网络 | "量子 ML" |
| `qutip` | 量子物理模拟 | 主方程、Lindblad 动力学、退相干、量子光学 | "量子模拟" |

---

## 🤖 机器学习 & 深度学习（新增 28 个）

### ML 框架

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `scikit-learn` | Python ML | 监督/无监督学习、模型评估、超参数调优 | "机器学习" |
| `pytorch-lightning` | PyTorch Lightning | LightningModule、Trainer、分布式训练 | "深度学习" |
| `transformers` | Hugging Face Transformers | AutoModel、pipeline、Trainer 微调 | "NLP/视觉/音频" |
| `torch-geometric` | PyTorch Geometric | 图神经网络：GCN、GAT、GraphSAGE | "图神经网络" |
| `stable-baselines3` | 强化学习算法 | PPO、SAC、DQN、TD3、DDPG、A2C | "强化学习" |
| `pufferlib` | 高性能 RL | 并行训练、向量化环境、多智能体 | "高性能 RL" |

### 数据处理

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `polars` | 高性能 DataFrame | 表达式驱动、懒查询优化、并行执行 | "数据处理" |
| `dask` | 分布式计算 | 大于 RAM 的 pandas/NumPy 工作流 | "分布式计算" |
| `vaex` | 大表格数据集 | 十亿级行数的 out-of-core DataFrame | "大数据" |
| `zarr-python` | 云存储 N-D 数组 | 压缩数组、并行 I/O、S3/GCS | "云数组" |
| `modal` | 无服务器 Python | 按需 GPU、AI/ML 推理、批量处理 | "无服务器" |

### 可视化

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `matplotlib` | 底层绘图库 | 完全自定义控制，出版级输出 | "自定义图表" |
| `seaborn` | 统计可视化 | pandas 集成，箱线图、小提琴图、热图 | "统计图表" |
| `networkx` | 复杂网络分析 | 图算法、社区检测、网络拓扑 | "网络分析" |
| `umap-learn` | 非线性降维 | 2D/3D 嵌入、聚类预处理 | "降维" |

### ML 解释性

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `shap` | 模型可解释性 | SHAP 值：瀑布图、蜂群图、力图 | "解释模型" |

### 其他 ML

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `scikit-survival` | 生存分析 | Cox 模型、随机生存森林、梯度提升 | "生存分析" |
| `pymc` | 贝叶斯建模 | 层次模型、MCMC（NUTS）、变分推断 | "贝叶斯" |
| `pymoo` | 多目标优化 | NSGA-II、NSGA-III、MOEA/D、Pareto 前沿 | "多目标优化" |
| `simpy` | 离散事件仿真 | 过程、队列、资源、基于时间的事件 | "仿真" |
| `aeon` | 时间序列 ML | 分类、回归、聚类、预测、异常检测 | "时间序列" |
| `timesfm-forecasting` | 零样本时间序列预测 | Google TimesFM 基础模型，无需训练 | "时间序列预测" |
| `neurokit2` | 生物信号处理 | ECG、EEG、EDA、RSP、PPG、EMG 分析 | "生物信号" |
| `neuropixels-analysis` | Neuropixels 记录分析 | SpikeInterface：预处理、漂移校正、尖峰排序 | "电生理" |
| `fluidsim` | 流体动力学模拟 | Navier-Stokes、浅水方程、伪谱方法 | "CFD" |

---

## 🔍 网络搜索 & 数据获取（新增 5 个）

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `exa-search` | Exa 语义搜索 | 研究论文过滤、学术域名过滤 | "学术搜索" |
| `parallel-web` | 并行网页工具包 | 网页搜索、URL 提取、深度研究 | "网页研究" |
| `paper-lookup` | 学术文献搜索 | 10 个学术数据库：PubMed、arXiv、OpenAlex | "找论文" |
| `paperzilla` | 论文推荐 | 项目推荐、规范论文详情 | "推荐论文" |
| `research-lookup` | 研究信息检索 | 快速搜索、深度综合、学术搜索 | "查研究" |
| `database-lookup` | 公共数据库查询 | 科学、监管、金融数据库 | "查数据库" |
| `usfiscaldata` | 美国财政数据 API | 国债、每日/月度财政部声明、拍卖、利率 | "查财政数据" |
| `get-available-resources` | 系统资源检测 | CPU、GPU、内存、磁盘空间检测 | "检查资源" |

---

## 🎯 决策 & 思维工具（新增 4 个）

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `what-if-oracle` | What-If 场景分析 | 4-6 分支可能性探索 | "如果 X 会怎样" |
| `consciousness-council` | 多视角心智议会 | 从多个专家视角审视问题 | "多角度分析" |
| `dhdna-profiler` | 认知模式提取 | 从文本提取认知指纹 | "分析思维" |
| `autoskill` | 屏幕观察技能发现 | 检测重复工作流，提出新技能 | "观察工作" |

---

## 📚 笔记 & 知识管理（新增 4 个）

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `obsidian-vault` | Obsidian 知识库 | 搜索、创建、管理笔记 | "Obsidian" |
| `open-notebook` | 开源 NotebookLM | AI 驱动的研究和文档分析 | "笔记本" |
| `pi-agent` | Pi 终端编码工具 | 安装、配置、创建技能/扩展 | "Pi" |
| `kami` | Kami 文档排版 | 温暖羊皮纸风格，中文/英文/日文 | "做文档" |

---

## 🎨 图表 & 可视化（新增 2 个）

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `diagram-design` | 技术/产品图表 | 架构图、流程图、时序图、ER 图，HTML+SVG | "画图" |
| `markdown-mermaid-writing` | Markdown + Mermaid | 科学文档写作标准，24 种图表类型 | "写 Markdown" |

---

## GStack 技能套件

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `gstack` | GStack 技能路由器 | 路由器：根据请求分发到正确的 GStack 技能 | "用 gstack" |
| `gstack-autoplan` | 自动审查管道 | 一键运行 CEO + 设计 + 工程 + DX 全套审查 | "自动审查" |
| `gstack-browse` | 快速无头浏览器 QA | ~100ms/命令的无头浏览器测试 | "浏览器测试" |
| `gstack-canary` | 部署后金丝雀监控 | 部署后持续监控：截图、对比、告警 | "部署监控" |
| `gstack-careful` | 危险命令安全防护 | `rm -rf`、`DROP TABLE`、`force-push` 前警告 | "安全模式" |
| `gstack-claude` | Claude Code CLI 包装器 | Review: 独立 diff 审查；Challenge: 对抗性审查 | "Claude 审查" |
| `gstack-context-restore` | 恢复工作上下文 | 恢复之前保存的工作状态 | "恢复上下文" |
| `gstack-context-save` | 保存工作上下文 | 保存当前工作状态，供后续恢复 | "保存上下文" |
| `gstack-cso` | 首席安全官模式 | 基础设施优先的安全审计：密钥、依赖、CI/CD、OWASP | "安全审计" |
| `gstack-design-consultation` | 设计咨询 | 理解产品 → 研究竞品 → 提出完整设计系统 | "设计咨询" |
| `gstack-design-html` | 设计转 HTML | 把设计稿变成生产级 HTML/CSS | "设计转代码" |
| `gstack-design-review` | 设计审查 | 找视觉不一致、间距问题、层级问题，逐个修复 | "设计审查" |
| `gstack-design-shotgun` | 设计探索 | 生成多个 AI 设计变体，打开对比板收集反馈 | "探索设计" |
| `gstack-devex-review` | 开发者体验审计 | 实际测试开发者体验：导航文档、尝试入门流程、计时 | "DX 审计" |
| `gstack-diagram` | 图表生成 | 英文描述 → Mermaid 源码 + SVG + PNG | "画图" |
| `gstack-document-generate` | 文档生成 | 从零生成缺失的文档（tutorial/how-to/reference/explanation） | "生成文档" |
| `gstack-document-release` | 发布后文档更新 | 发布后同步更新所有文档 | "更新文档" |
| `gstack-freeze` | 限制文件编辑范围 | 限制编辑到指定目录，防止意外修改 | "冻结编辑" |
| `gstack-guard` | 完整安全模式 | careful + freeze 组合：危险警告 + 范围限制 | "完全锁定" |
| `gstack-health` | 代码质量仪表盘 | 综合评分 0-10，跟踪趋势 | "健康检查" |
| `gstack-investigate` | 系统性调试 | 4 阶段调试：调查 → 分析 → 假设 → 实现 | "调试" |
| `gstack-ios-clean` | iOS 调试桥清理 | 移除 DebugBridge SPM 包和所有 DEBUG 代码 | "iOS 清理" |
| `gstack-ios-design-review` | iOS 设计审查 | 在真机上截图每个屏幕，评估 Apple HIG 合规 | "iOS 设计审查" |
| `gstack-ios-fix` | iOS Bug 修复 | 找到 bug → 修复 → 重新部署 → 验证，全自动 | "iOS 修 bug" |
| `gstack-ios-qa` | iOS 真机 QA | 连接真 iPhone，截图 → 分析 → 操作 → 验证循环 | "iOS 测试" |
| `gstack-ios-sync` | iOS 调试桥同步 | 重新生成 iOS 调试桥代码 | "iOS 同步" |
| `gstack-land-and-deploy` | 合并部署流程 | 合并 PR → 等 CI → 部署 → 验证生产健康 | "部署" |
| `gstack-landing-report` | 着陆报告 | 只读队列仪表盘：哪些 VERSION 被占用 | "查看队列" |
| `gstack-learn` | 管理项目学习 | 审查、搜索、导出项目学到的东西 | "管理学习" |
| `gstack-make-pdf` | Markdown 转 PDF | 专业排版：页边距、页码、封面、目录、水印 | "做 PDF" |
| `gstack-open-gstack-browser` | 启动 GStack 浏览器 | 打开可见的浏览器窗口，侧边栏显示实时活动 | "打开浏览器" |
| `gstack-pair-agent` | 配对远程 AI 代理 | 生成 setup key，让远程 agent 连接你的浏览器 | "配对 agent" |
| `gstack-plan-ceo-review` | CEO 模式审查 | 创始人视角：重新思考问题、找 10 星产品、挑战前提 | "想大一点" |
| `gstack-plan-design-review` | 设计计划审查 | 设计师视角的计划审查：每个维度 0-10 分 | "设计审查" |
| `gstack-plan-devex-review` | DX 计划审查 | 开发者体验视角的计划审查 | "DX 审查" |
| `gstack-plan-eng-review` | 工程计划审查 | 工程经理视角：架构、数据流、测试、性能 | "工程审查" |
| `gstack-plan-tune` | 计划调优 | 调整问题敏感度和开发者心理画像 | "调优问题" |
| `gstack-qa` | QA 测试+修复 | 测试 → 修复 → 验证循环 | "QA" |
| `gstack-qa-only` | 仅 QA 报告 | 只报告 bug，不修复 | "只报告" |
| `gstack-benchmark` | 性能回归检测 | 建立基线，每次 PR 对比 before/after | "性能基准" |
| `gstack-benchmark-models` | 跨模型基准测试 | Claude vs GPT vs Gemini 同一任务对比 | "模型对比" |
| `gstack-office-hours` | YC Office Hours 模式 | 6 个强制问题暴露需求现实 | "创业咨询" |
| `gstack-retro` | 周回顾 | 分析提交历史、工作模式、代码质量指标 | "周回顾" |
| `gstack-review` | PR 着陆前审查 | 分析 diff：SQL 安全、LLM 信任边界、条件副作用 | "PR 审查" |
| `gstack-scrape` | 网页数据抓取 | 首次调用原型化流程，后续调用 ~200ms | "抓数据" |
| `gstack-setup-browser-cookies` | 导入浏览器 Cookie | 从真实 Chromium 导入 cookie 到无头会话 | "导入 cookie" |
| `gstack-setup-deploy` | 配置部署设置 | 检测部署平台，配置健康检查、状态命令 | "配置部署" |
| `gstack-setup-gbrain` | 设置 gbrain | 安装 CLI、初始化本地 brain、注册 MCP | "设置 gbrain" |
| `gstack-ship` | 发布工作流 | 检测基线 → 运行测试 → 审查 → bump VERSION → 创建 PR | "发布" |
| `gstack-skillify` | 固化抓取流为技能 | 把成功的 /scrape 流程固化为永久技能 | "固化流程" |
| `gstack-spec` | 意图转规格 | 把模糊意图变成精确的、可执行的 spec | "写 spec" |
| `gstack-sync-gbrain` | 同步 gbrain | 保持 gbrain 和代码库同步 | "同步 gbrain" |
| `gstack-unfreeze` | 解除冻结 | 解除 /freeze 设置的编辑限制 | "解冻" |
| `gstack-upgrade` | 升级 gstack | 检测安装方式，运行升级，显示新功能 | "升级 gstack" |

### Doko 技能

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `doko-research` | 迭代式网络研究 | 多轮搜索 → 交叉引用 → 结构化报告 | "深度研究" |
| `doko-search` | 免费网络搜索 | 通过真实 Chrome 读取搜索结果页，无 API 限制 | "搜索" |
| `doko-summarize` | 网页摘要 | 读取网页并生成结构化摘要 | "总结网页" |
| `doko-translate` | 网页翻译 | 保留结构的网页翻译（标题、列表、表格、代码块） | "翻译网页" |
| `dokobot` | Chrome 浏览器网页读取 | 读取任何网页内容，包括 SPA 和 JS 渲染页面 | "读网页" |
| `notion-mcp` | Notion 工作区 MCP 工具 | 创建/搜索/更新 Notion 页面、数据库、视图 | "Notion 操作" |

### 执行规则

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `sisyphus-execution-rules` | Sisyphus 执行规则 | Agent 分工、实时更新、Git 限制、资源管理。**会话开始时自动加载** | 会话开始 |

### 项目级技能

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `code-reviewer` | Java 25 / Spring Boot 4 代码审查 | 专门审查 Java 25 + Spring Boot 4 代码库：迁移风险、架构边界、null 安全、安全漏洞、性能回归、Spring Data 陷阱 | "Java 审查"、"Spring 审查" |
| `creating-springboot-projects` | 创建 Spring Boot 项目结构 | 初始化 Spring Boot 项目：选择架构、选择特性、应用模板 | "创建 Spring 项目" |
| `spring-data-jpa` | Spring Data JPA 仓库/查询设计 | 设计 JPA 仓库、投影、查询模式、自定义仓库、CQRS 读模型 | "JPA 设计" |
| `springboot-migration` | Spring Boot 迁移 | 迁移到 Boot 4 + Java 25：依赖过渡、starter 重命名、测试注解迁移 | "Spring 迁移" |

### 其他技能

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `analysis-artifacts` | 生成可重复分析工件 | SQL 查询 + Python 可视化 + 摘要表 | "数据分析" |
| `dbt-model-index` | dbt 模型查询索引 | BigQuery 数据仓库的模型查询指南 | "查数据" |
| `reverse-engineering` | 逆向工程 | 二进制分析、反编译、协议逆向 | "逆向" |
| `grilling` | 压力测试计划/设计 | 像审问一样追问方案 | "压力测试" |
| `prototype` | 构建原型回答设计问题 | 构建一次性原型验证设计决策 | "做原型" |
| `request-refactor-plan` | 创建重构计划 | 通过用户访谈创建详细的重构计划 | "重构计划" |
| `setup-matt-pocock-skills` | 配置工程技能 | 配置 issue tracker、triage 标签、领域文档 | "配置技能" |
| `slack-qa-investigate` | Slack QA 调查 | 只读模式调查仓库问题 | "Slack QA" |
| `terraform-style-check` | Terraform 代码风格检查 | 按 HashiCorp 官方风格检查 Terraform 配置 | "Terraform 检查" |
| `using-superpowers` | 使用超级技能 | 元技能：发现和使用其他技能 | "怎么用技能" |
| `web-design-guidelines` | Web 设计指南 | 审查 UI 代码是否符合 Web 接口指南 | "设计指南" |
| `wizard` | 生成交互式向导 | 生成 bash 向导引导用户完成手动流程 | "做向导" |
| `claude-handoff` | 会话交接给新代理 | 把当前对话交给新的后台 agent 继续 | "交接" |
| `handoff` | 压缩会话为交接文档 | 把对话压缩成交接文档 | "交接文档" |
| `find-skills` | 发现安装技能 | 帮你找到能做某件事的技能 | "找技能" |
| `ask-matt` | 询问适合的技能/流 | 根据需求推荐最合适的技能 | "用什么技能" |
| `using-agent-skills` | 发现调用技能 | 元技能：发现和调用其他技能 | "怎么用技能" |
| `market-research-reports` | 市场研究报告 | 50+ 页专业报告：McKinsey/BCG/Gartner 风格 | "市场研究" |
| `idea-refine` | 细化原始想法 | 发散/收敛思维，把粗糙想法变成精确概念 | "细化想法" |

---

## 高频组合模式

### 功能开发流程
```
brainstorming → think → planning-and-task-breakdown → implement → tdd → check → create-pull-request
```

### Bug 修复流程
```
hunt → systematic-debugging → tdd → check → create-pull-request
```

### 代码审查流程
```
check → code-review → code-review-and-quality → receiving-code-review
```

### UI 开发流程
```
brainstorming → design-taste-frontend → frontend-ui-engineering → ui → webapp-testing
```

### 学术写作流程
```
nature-academic-search → nature-writing → nature-polishing → nature-figure → nature-paper2ppt
```

### CLI 工具开发
```
cli-anything → cli-hub-meta-skill → opencli-usage
```

### 安全审查
```
security-and-hardening → cso → security-research
```

### 性能优化
```
web-performance-audit → performance-optimization → debug-optimize-lcp
```

### 单细胞分析流程
```
scanpy → anndata → scvi-tools → scvelo → pathway-enrichment
```

### 药物发现流程
```
rdkit → datamol → deepchem → pytdc → medchem → diffdock
```

### 蛋白质工程流程
```
esm → tamarind → diffdock → molecular-dynamics → glycoengineering
```

### 基因组学流程
```
biopython → pysam → scanpy → pydeseq2 → pathway-enrichment
```

### 质谱分析流程
```
pyopenms → matchms → datamol → rdkit
```

### 系统发育流程
```
biopython → phylogenetics → etetoolkit → scikit-bio
```

### 文献综述流程
```
paper-lookup → literature-review → citation-management → nature-citation
```

### 临床报告流程
```
clinical-reports → clinical-decision-support → treatment-plans
```

### 量子计算流程
```
qiskit / cirq / pennylane / qutip → optimize-for-gpu
```

### 地理空间流程
```
geopandas → geomaster → matplotlib → scientific-visualization
```

---

## 使用说明

1. **扫描此文件**: 在执行任务前快速扫描相关类别
2. **加载多个技能**: 可以同时加载多个相关技能
3. **技能优先级**: 过程技能 (brainstorming, systematic-debugging) 优先于实现技能
4. **用户指令优先**: 用户指令 > 技能 > 默认行为
5. **R20 规则**: 使用某类型 Skills 前先列出所有同类 Skills

---

## 技能来源

| 来源 | 说明 | 优先级 |
|------|------|--------|
| `project` | 项目特定技能 (`.agents/skills/`) | 最高 |
| `user` | 用户安装技能 | 高 |
| `opencode` | OpenCode 内置技能 | 中 |
| `builtin` | 内置插件技能 | 低 |

**规则**: 用户安装技能覆盖内置默认。

---

*此文件由 AI 自动维护。当新增或修改技能时，应同步更新此索引。*
