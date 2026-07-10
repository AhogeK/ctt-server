# 🧠 SKILL GRAPH — AI Agent 技能索引

> **最后更新**: 2026-07-09
> **技能总数**: 287 个 (223 ~/.agents/skills + 61 ~/.config/opencode/skills + 4 项目级 - 1 重复)
> **用途**: AI 在执行任务前快速扫描此文件，识别并加载最相关的技能

---

## 🎯 快速查找指南

| 我要... | 使用这些技能 |
|---------|-------------|
| 规划一个新功能 | `think` → `brainstorming` → `planning-and-task-breakdown` → `implement` |
| 修复一个 bug | `hunt` → `systematic-debugging` → `diagnosing-bugs` |
| 代码审查 | `check` → `code-review` → `code-review-and-quality` |
| 创建 UI | `ui` → `design-taste-frontend` → `frontend-ui-engineering` |
| 写文档 | `write` → `docs-update` → `documentation-and-adrs` |
| Git 操作 | `git-workflow-and-versioning` → `create-pull-request` → `resolving-merge-conflicts` |
| 浏览器测试 | `browser` → `browser-use` → `browser-harness` |
| 学术写作 | `nature-writing` → `nature-polishing` → `nature-paper2ppt` |
| CLI 工具开发 | `cli-anything` → `cli-hub-meta-skill` |
| 部署 | `deploy-to-vercel` → `shipping-and-launch` → `ci-cd-and-automation` |

---

## 📂 分类索引

### 🧠 思维 & 规划 (Thinking & Planning)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `think` | 将粗糙想法转化为决策完备的计划 | 规划, 设计, 方案, 判断, 值不值得 |
| `brainstorming` | 创建功能/组件前的强制探索 | 创建, 构建, 添加, 修改行为 |
| `idea-refine` | 通过发散/收敛思维细化原始想法 | 细化, 完善, 压力测试 |
| `planning-and-task-breakdown` | 将工作分解为可执行任务 | 分解, 规划, 任务拆分 |
| `spec-driven-development` | 编码前创建规格说明 | 规格, 需求, 新项目 |
| `executing-plans` | 在独立会话中执行书面计划 | 执行计划, 分步实施 |
| `subagent-driven-development` | 用子代理执行独立任务 | 并行任务, 子代理 |
| `dispatching-parallel-agents` | 面对2+独立任务时的并行分发 | 并行, 多任务 |
| `wayfinder` | 将大块工作分解为 issue 追踪 | 大项目, 路线图 |
| `to-spec` | 将对话转化为规格说明并发布到 issue tracker | 规格, spec, issue |
| `to-tickets` | 将计划/对话拆解为 tracer-bullet tickets | tickets, 拆分, issue |

### 💻 代码 & 实现 (Code & Implementation)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `implement` | 基于 PRD 或 issue 实现功能 | 实现, 开发, 编码 |
| `incremental-implementation` | 增量交付变更 | 增量, 小步快跑, 多文件 |
| `tdd` / `test-driven-development` | 测试驱动开发 | TDD, 红绿重构, 测试先行 |
| `full-output-enforcement` | 强制完整代码生成 | 完整输出, 禁止截断 |
| `code-simplification` | 简化代码提高清晰度 | 简化, 重构, 清晰化 |
| `codebase-design` | 设计深层模块接口 | 模块设计, 接口, 接缝 |
| `domain-modeling` | 构建/优化领域模型 | DDD, 领域模型, 通用语言 |
| `ubiquitous-language` | 提取 DDD 风格的术语表 | 术语表, 通用语言 |
| `deprecation-and-migration` | 管理废弃和迁移 | 废弃, 迁移, 升级 |
| `migrate-to-shoehorn` | 迁移测试文件到 shoehorn | 测试迁移, 类型断言 |

### 🐛 调试 & 问题排查 (Debugging & Troubleshooting)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `hunt` | 找到根因再修复 | 排查, 报错, 崩溃, 不工作 |
| `systematic-debugging` | 系统性根因调试 | 调试, 失败, 异常 |
| `diagnosing-bugs` | 硬 bug 和性能回归的诊断循环 | 诊断, 调试, 坏了 |
| `debugging-and-error-recovery` | 系统性根因调试指南 | 调试, 恢复, 错误 |
| `memory-leak-debugging` | JS/Node.js 内存泄漏 | 内存泄漏, OOM |
| `troubleshooting` | Chrome DevTools 排查连接问题 | 连接问题, 目标问题 |
| `ci-fix` | 诊断修复 GitHub Actions CI 失败 | CI 失败, 构建失败 |
| `resolving-merge-conflicts` | 解决 git 合并/变基冲突 | 冲突, 合并, 变基 |

### 🔍 审查 & 验证 (Review & Verification)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `check` | 代码差异/PR/发布就绪审查 | 审查, 检查, 发布前 |
| `code-review` | 沿标准/规格两轴审查 | 代码审查, PR 审查 |
| `code-review-and-quality` | 多轴代码审查 | 质量审查, 合并前 |
| `receiving-code-review` | 接收代码审查反馈 | 收到反馈, 实施建议 |
| `requesting-code-review` | 完成任务后请求审查 | 请求审查, 验证 |
| `verification-before-completion` | 声称完成前的验证 | 验证, 确认完成 |
| `health` | 工程健康审计 | 健康检查, 项目评分 |
| `review-animations` | 审查动画/动效代码 | 动画审查, GSAP |
| `web-accessibility-audit` | WCAG 无障碍审计 | 无障碍, a11y |
| `seo-aeo-audit` | SEO/AEO 审计 | SEO, 搜索优化 |
| `web-performance-audit` | Web 性能审计 | 性能审计, Core Web Vitals |

### 🎨 UI & 设计 (UI & Design)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `ui` | 生产级 UI 构建 | UI, 页面, 组件, 排版 |
| `design-taste-frontend` | 反模板化前端设计 | 设计, 着陆页, 重设计 |
| `design-taste-frontend-v1` | v1 版本（向后兼容） | 旧版设计 |
| `frontend-ui-engineering` | 生产级 UI 工程 | 前端, 组件, 布局 |
| `redesign-existing-projects` | 升级现有网站到高级质量 | 重设计, 升级 |
| `image-to-code` | 图片到代码转换 | 图片转代码 |
| `imagegen-frontend-web` | 前端设计参考图生成 | 设计图, 参考 |
| `imagegen-frontend-mobile` | 移动端设计概念图 | 移动端设计 |
| `brandkit` | 品牌套件图生成 | 品牌, Logo |
| `high-end-visual-design` | 高端代理设计风格 | 高端, 精致 |
| `minimalist-ui` | 极简编辑风格 | 极简, 温暖 |
| `industrial-brutalist-ui` | 工业粗野风格 | 粗野, 数据仪表盘 |
| `stitch-design-taste` | Google Stitch 语义设计系统 | Stitch, 语义设计 |
| `gpt-taste` | 精英 UX/UI + GSAP 动效 | GSAP, 动效 |
| `emil-design-eng` | Emil Kowalski 设计哲学 | 精致, 细节 |
| `design-an-interface` | 生成多种不同接口设计 | 接口设计, 探索 |
| `diagram-design` | 技术/产品图表设计 | 架构图, 流程图, 时序图 |
| `animation-vocabulary` | 动画效果术语反查 | 动画术语 |
| `codebase-design` | 深层模块设计词汇 | 模块设计 |

### ✍️ 写作 & 文档 (Writing & Documentation)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `write` | 重写润色散文，去 AI 味 | 写作, 润色, 去AI味 |
| `docs-update` | 代码变更后更新文档 | 更新文档 |
| `documentation-and-adrs` | 记录决策和文档 | ADR, 架构决策 |
| `edit-article` | 编辑改进文章 | 编辑, 修改, 审稿 |
| `writing-guidelines` | 写作指南审查 | 写作规范 |
| `writing-fragments` | 原始素材挖掘 | 素材, 片段 |
| `writing-beats` | 组装素材为节奏旅程 | 节奏, 旅程 |
| `writing-shape` | 将素材塑形为文章 | 文章, 段落 |
| `writing-great-skills` | 编写优质技能的参考 | 技能编写 |
| `writing-plans` | 多步骤任务前的规划 | 计划编写 |
| `writing-skills` | 创建/编辑/验证技能 | 技能开发 |
| `grill-me` / `grill-with-docs` | 压力测试计划/设计 | 审问, 压力测试 |
| `loop-me` | 工作流规格审问 | 规格审问 |
| `grill-with-docs` | 压力测试+生成文档 | 审问, 文档生成 |
| `teach` | 教授新技能/概念 | 教学 |

### 🔄 Git & 版本控制 (Git & Version Control)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `git-workflow-and-versioning` | 结构化 git 工作流 | Git, 提交, 分支 |
| `git-guardrails-claude-code` | 阻止危险 git 命令 | Git 安全, 防护 |
| `create-pull-request` | 创建 GitHub PR | PR, 创建拉取请求 |
| `resolving-merge-conflicts` | 解决合并冲突 | 冲突, 合并 |
| `finishing-a-development-branch` | 完成开发分支集成 | 分支完成, 合并 |
| `using-git-worktrees` | 功能隔离的 git worktree | Worktree, 隔离 |
| `github-bug-report-triage` | 分类 GitHub bug 报告 | Bug 分类 |
| `github-issue-dedupe` | 检测重复 GitHub issue | 重复 issue |

### 🌐 浏览器自动化 (Browser Automation)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `browser` | CDP 直接浏览器控制 | 浏览器, 自动化 |
| `browser-use` | Web 交互首选 | Web 交互, 自动化 |
| `browser-harness` | CDP 浏览器控制 | 浏览器控制 |
| `browser-testing-with-devtools` | Chrome DevTools 测试 | 浏览器测试 |
| `chrome-devtools` | DevTools MCP 调试 | DevTools, 调试 |
| `chrome-devtools-cli` | DevTools CLI 脚本 | CLI 浏览器 |
| `troubleshooting` | 连接/目标问题排查 | 连接问题 |
| `a11y-debugging` | 无障碍调试 | a11y, 无障碍 |
| `debug-optimize-lcp` | LCP 优化调试 | LCP, 性能 |
| `ai-chat-browser` | Gemini/Perplexity 浏览器通信 | AI 聊天 |

### 📚 Nature 系列 (学术写作)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `nature-writing` | Nature 风格稿件撰写 | 学术写作, 论文 |
| `nature-polishing` | 学术散文润色 | 润色, 翻译 |
| `nature-paper2ppt` | 论文转 PPT | PPT, 演示 |
| `nature-paper-to-patent` | 论文转专利 | 专利, 转化 |
| `nature-figure` | 论文配图 | 科研绘图 |
| `nature-data` | 数据可用性声明 | 数据声明 |
| `nature-citation` | Nature/CNS 引用 | 引用, 参考文献 |
| `nature-reader` | 论文中英对照阅读 | 论文阅读 |
| `nature-reviewer` | 模拟审稿人评估 | 审稿, 预审 |
| `nature-response` | 审稿意见回复 | 回复审稿人 |
| `nature-academic-search` | 多源文献检索 | 文献检索 |
| `nature-literature-pipeline` | 文献发现管道 | 文献发现 |
| `nature-downloader` | 学术文献下载 | 下载论文 |
| `nature-experiment-log` | 实验日志记录 | 实验日志 |

### 🔧 CLI 工具 (CLI Tools)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `cli-anything` | 为 GUI 应用构建 CLI | CLI 构建 |
| `cli-hub-meta-skill` | 发现 Agent 原生 CLI | CLI 发现 |
| `opencli-usage` | OpenCLI 顶层地图 | OpenCLI |
| `cli-anything-adguardhome` | AdGuard Home CLI | AdGuard |
| `cli-anything-anygen` | AnyGen OpenAPI CLI | AnyGen |
| `cli-anything-audacity` | Audacity 音频编辑 CLI | Audacity |
| `cli-anything-blender` | Blender 3D 编辑 CLI | Blender |
| `cli-anything-browser` | 浏览器自动化 CLI | 浏览器 CLI |
| `cli-anything-calibre` | Calibre 电子书 CLI | Calibre |
| `cli-anything-ccswitch` | CC Switch 配置管理 | CC Switch |
| `cli-anything-chromadb` | ChromaDB CLI | ChromaDB |
| `cli-anything-cloudanalyzer` | CloudAnalyzer CLI | CloudAnalyzer |
| `cli-anything-cloudcompare` | CloudCompare CLI | CloudCompare |
| `cli-anything-comfyui` | ComfyUI CLI | ComfyUI |
| `cli-anything-dify-workflow` | Dify 工作流 CLI | Dify |
| `cli-anything-drawio` | Draw.io CLI | Draw.io |
| `cli-anything-eez-studio` | EEZ Studio CLI | EEZ Studio |
| `cli-anything-eth2-quickstart` | ETH2 快速启动 | ETH2 |
| `cli-anything-exa` | Exa CLI | Exa |
| `cli-anything-firefly-iii` | Firefly III 个人财务 | Firefly III |
| `cli-anything-freecad` | FreeCAD CLI | FreeCAD |
| `cli-anything-gimp` | GIMP 图像编辑 CLI | GIMP |
| `cli-anything-godot` | Godot 游戏引擎 CLI | Godot |
| `cli-anything-hermes` | Hermes Agent CLI 构建 | Hermes |
| `cli-anything-inkscape` | Inkscape 矢量图形 CLI | Inkscape |
| `cli-anything-intelwatch` | IntelWatch CLI | IntelWatch |
| `cli-anything-iterm2` | iTerm2 CLI 控制 | iTerm2 |
| `cli-anything-iterm2-ctl` | iTerm2 高级控制 | iTerm2 高级 |
| `cli-anything-joplin` | Joplin 笔记 CLI | Joplin |
| `cli-anything-jumpserver` | JumpServer 堡垒机 CLI | JumpServer |
| `cli-anything-kdenlive` | Kdenlive 视频编辑 CLI | Kdenlive |
| `cli-anything-krita` | Krita 数字绘画 CLI | Krita |
| `cli-anything-libreoffice` | LibreOffice CLI | LibreOffice |
| `cli-anything-live2d` | Live2D 模型管理 CLI | Live2D |
| `cli-anything-lldb` | LLDB 调试 CLI | LLDB |
| `cli-anything-macrocli` | MacroCLI 宏命令 | MacroCLI |
| `cli-anything-mailchimp` | Mailchimp API CLI | Mailchimp |
| `cli-anything-mermaid` | Mermaid 图表 CLI | Mermaid |
| `cli-anything-minimax` | MiniMax AI CLI | MiniMax |
| `cli-anything-mubu` | Mubu 乐谱 CLI | Mubu |
| `cli-anything-musescore` | MuseScore 乐谱 CLI | MuseScore |
| `cli-anything-n8n` | n8n 工作流 CLI | n8n |
| `cli-anything-notebooklm` | NotebookLM CLI | NotebookLM |
| `cli-anything-novita` | Novita AI CLI | Novita |
| `cli-anything-nsight-graphics` | Nsight Graphics CLI | Nsight |
| `cli-anything-nslogger` | NSLogger CLI | NSLogger |
| `cli-anything-obs-studio` | OBS Studio CLI | OBS |
| `cli-anything-obsidian` | Obsidian 笔记 CLI | Obsidian |
| `cli-anything-ollama` | Ollama 本地 LLM CLI | Ollama |
| `cli-anything-openrefine` | OpenRefine 数据清洗 CLI | OpenRefine |
| `cli-anything-openscreen` | Openscreen 录屏编辑 CLI | Openscreen |
| `cli-anything-pm2` | PM2 进程管理 CLI | PM2 |
| `cli-anything-qgis` | QGIS 地理信息 CLI | QGIS |
| `cli-anything-quietshrink` | macOS 录屏压缩 CLI | 录屏压缩 |
| `cli-anything-rekordbox` | Rekordbox DJ CLI | Rekordbox |
| `cli-anything-renderdoc` | RenderDoc 图形调试 CLI | RenderDoc |
| `cli-anything-rms` | Teltonika RMS 设备管理 CLI | RMS |
| `cli-anything-safari` | Safari 浏览器自动化 CLI | Safari |
| `cli-anything-sbox` | s&box 游戏引擎 CLI | s&box |
| `cli-anything-seaclip` | SeaClip-Lite 项目管理 CLI | SeaClip |
| `cli-anything-shotcut` | Shotcut 视频编辑 CLI | Shotcut |
| `cli-anything-siyuan` | 思源笔记 CLI | 思源笔记 |
| `cli-anything-slay-the-spire-ii` | Slay the Spire 2 游戏 CLI | 游戏 |
| `cli-anything-threemf` | 3MF 网格编辑 CLI | 3MF |
| `cli-anything-tigris` | Tigris 对象存储 CLI | Tigris |
| `cli-anything-unimol-tools` | Uni-Mol 分子预测 CLI | Uni-Mol |
| `cli-anything-unrealinsights` | Unreal Engine 分析 CLI | Unreal |
| `cli-anything-videocaptioner` | 视频字幕 CLI | 视频字幕 |
| `cli-anything-wavetone` | WaveTone 音频 CLI | WaveTone |
| `cli-anything-web-yu-pri` | Japan Post Web Yu-pri CLI | 邮政 |
| `cli-anything-wiremock` | WireMock HTTP Mock CLI | WireMock |
| `cli-anything-zoom` | Zoom 会议 CLI | Zoom |
| `cli-anything-zotero` | Zotero 文献管理 CLI | Zotero |

### 🔬 研究 & 学习 (Research & Learning)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `research` | 针对主源调查问题 | 研究, 调查 |
| `learn` | 六阶段研究工作流 | 学习, 领域 |
| `read` | 读取 URL/PDF 并总结 | 读取, 总结 |
| `find-skills` | 发现安装技能 | 找技能 |
| `ask-matt` | 询问适合的技能/流 | 选技能 |
| `using-agent-skills` | 发现调用技能 | 技能发现 |

### 🚀 部署 & CI/CD (Deployment)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `deploy-to-vercel` | 部署到 Vercel | 部署, Vercel |
| `vercel-cli-with-tokens` | Token 认证部署 | Vercel CLI |
| `vercel-optimize` | Vercel 成本优化 | Vercel 优化 |
| `shipping-and-launch` | 生产发布准备 | 发布, 上线 |
| `ci-cd-and-automation` | CI/CD 管道自动化 | CI/CD, 自动化 |
| `ci-fix` | CI 失败诊断修复 | CI 修复 |
| `setup-pre-commit` | Husky pre-commit hooks | Pre-commit |

### ⚡ 性能 & 优化 (Performance)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `performance-optimization` | 应用性能优化 | 性能, 优化 |
| `web-performance-audit` | Web 性能审计 | 性能审计 |
| `debug-optimize-lcp` | LCP 优化 | LCP |
| `memory-leak-debugging` | 内存泄漏调试 | 内存泄漏 |
| `vercel-optimize` | Vercel 成本优化 | Vercel 成本 |

### 🔒 安全 (Security)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `security-and-hardening` | 代码加固 | 安全, 加固 |
| `git-guardrails-claude-code` | 阻止危险 git 命令 | Git 安全 |

### 🧪 测试 (Testing)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `tdd` / `test-driven-development` | 测试驱动开发 | TDD, 测试先行 |
| `test-driven-development` | 实现功能/修复前先写测试 | 测试先行, TDD |
| `webapp-testing` | Web 应用测试 | Web 测试 |
| `web-performance-audit` | 性能审计 | 性能测试 |
| `web-accessibility-audit` | 无障碍审计 | 无障碍测试 |
| `seo-aeo-audit` | SEO 审计 | SEO 测试 |

### 🤖 MCP & 工具构建 (MCP & Tool Building)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `mcp-builder` | 构建 MCP 服务器 | MCP, 工具构建 |
| `cli-anything` | CLI 工具构建 | CLI 构建 |

### 📊 项目管理 (Project Management)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `qa` | 交互式 QA 会话 | QA, Bug 报告 |
| `triage` | Issue/PR 分类 | 分类, 优先级 |
| `health` | 工程健康审计 | 健康, 评分 |
| `scheduler` | 设备提醒/本地任务 | 提醒, 定时 |
| `handoff` | 会话交接文档 | 交接 |
| `context-engineering` | 代理上下文优化 | 上下文, 配置 |
| `health` | 代码质量仪表盘 | 质量, 评分 |

### 🎭 设计风格 (Design Styles)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `minimalist-ui` | 极简温暖风格 | 极简, 温暖 |
| `industrial-brutalist-ui` | 工业粗野风格 | 粗野, 数据 |
| `high-end-visual-design` | 高端精致风格 | 高端, 精致 |
| `gpt-taste` | GSAP 动效工程 | GSAP, 动效 |
| `stitch-design-taste` | Google Stitch 语义 | Stitch |

### 📱 移动端 (Mobile)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `imagegen-frontend-mobile` | 移动端设计图 | 移动端设计 |

### 🔗 集成 & API (Integration & API)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `api-and-interface-design` | API 设计指南 | API 设计, 接口 |
| `vercel-composition-patterns` | React 组合模式 | React, 组合 |
| `vercel-react-best-practices` | React 最佳实践 | React, 最佳实践 |
| `vercel-react-native-skills` | React Native 技能 | React Native |
| `vercel-react-view-transitions` | React 视图过渡 | 视图过渡 |

### 🏗️ 架构 (Architecture)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `codebase-design` | 深层模块设计 | 模块设计 |
| `domain-modeling` | 领域建模 | DDD, 领域 |
| `improve-codebase-architecture` | 架构改进报告 | 架构改进 |
| `software-engineering-laws-and-philosophy` | 软件工程法则 | 法则, 哲学 |

### 🎓 教学 (Teaching)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `teach` | 教授新技能/概念 | 教学 |
| `scaffold-exercises` | 创建练习脚手架 | 练习, 脚手架 |

### 🔧 GStack 技能套件 (GStack Suite)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `gstack` | GStack 技能路由器 | gstack, 套件 |
| `gstack-autoplan` | 自动审查管道 | 自动审查, autoplan |
| `gstack-browse` | 快速无头浏览器 QA | 浏览器测试, QA |
| `gstack-canary` | 部署后金丝雀监控 | 监控, canary |
| `gstack-careful` | 危险命令安全防护 | 安全, 危险命令 |
| `gstack-claude` | Claude Code CLI 包装器 | claude, 审查 |
| `gstack-context-restore` | 恢复工作上下文 | 恢复, 继续 |
| `gstack-context-save` | 保存工作上下文 | 保存, 存档 |
| `gstack-cso` | 首席安全官模式 | 安全审计, cso |
| `gstack-design-consultation` | 设计咨询 | 设计, 咨询 |
| `gstack-design-html` | 设计转 HTML | 设计转代码 |
| `gstack-design-review` | 设计审查 | 设计审查 |
| `gstack-design-shotgun` | 设计探索 | 设计探索, 多方案 |
| `gstack-devex-review` | 开发者体验审计 | DX, 开发体验 |
| `gstack-diagram` | 图表生成 | 图表, 架构图 |
| `gstack-document-generate` | 文档生成 | 生成文档 |
| `gstack-document-release` | 发布后文档更新 | 文档更新 |
| `gstack-freeze` | 限制文件编辑范围 | 冻结, 限制编辑 |
| `gstack-guard` | 完整安全模式 | 安全模式, guard |
| `gstack-health` | 代码质量仪表盘 | 健康, 质量 |
| `gstack-investigate` | 系统性调试 | 调查, 调试 |
| `gstack-ios-clean` | iOS 调试桥清理 | iOS 清理 |
| `gstack-ios-design-review` | iOS 设计审查 | iOS 设计 |
| `gstack-ios-fix` | iOS Bug 修复 | iOS 修复 |
| `gstack-ios-qa` | iOS 真机 QA | iOS 测试 |
| `gstack-ios-sync` | iOS 调试桥同步 | iOS 同步 |
| `gstack-land-and-deploy` | 合并部署流程 | 部署, 合并 |
| `gstack-landing-report` | 着陆报告 | 报告, 队列 |
| `gstack-learn` | 管理项目学习 | 学习, 管理 |
| `gstack-make-pdf` | Markdown 转 PDF | PDF, 生成 |
| `gstack-open-gstack-browser` | 启动 GStack 浏览器 | 浏览器, 启动 |
| `gstack-pair-agent` | 配对远程 AI 代理 | 配对, 远程 |
| `gstack-plan-ceo-review` | CEO 模式审查 | CEO, 战略 |
| `gstack-plan-design-review` | 设计计划审查 | 设计审查 |
| `gstack-plan-devex-review` | DX 计划审查 | DX 审查 |
| `gstack-plan-eng-review` | 工程计划审查 | 工程审查 |
| `gstack-plan-tune` | 计划调优 | 调优, 问题 |
| `gstack-qa` | QA 测试+修复 | QA, 测试 |
| `gstack-qa-only` | 仅 QA 报告 | QA 报告 |
| `gstack-benchmark` | 性能回归检测 | 性能基准, 回归 |
| `gstack-benchmark-models` | 跨模型基准测试 | 模型对比, 基准 |
| `gstack-office-hours` | YC Office Hours 模式 | 创业, 头脑风暴 |
| `gstack-retro` | 周回顾 | 回顾, 总结 |
| `gstack-review` | PR 着陆前审查 | PR 审查 |
| `gstack-scrape` | 网页数据抓取 | 抓取, scrape |
| `gstack-setup-browser-cookies` | 导入浏览器 Cookie | Cookie, 认证 |
| `gstack-setup-deploy` | 配置部署设置 | 部署配置 |
| `gstack-setup-gbrain` | 设置 gbrain | gbrain, 设置 |
| `gstack-ship` | 发布工作流 | 发布, ship |
| `gstack-skillify` | 固化抓取流为技能 | 技能化 |
| `gstack-spec` | 意图转规格 | 规格, spec |
| `gstack-sync-gbrain` | 同步 gbrain | 同步, gbrain |
| `gstack-unfreeze` | 解除冻结 | 解冻, 解除限制 |
| `gstack-upgrade` | 升级 gstack | 升级, 更新 |

### 📝 Doko 技能 (Doko Skills)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `doko-research` | 迭代式网络研究 | 研究, 调查 |
| `doko-search` | 免费网络搜索 | 搜索, 查询 |
| `doko-summarize` | 网页摘要 | 摘要, 总结 |
| `doko-translate` | 网页翻译 | 翻译 |
| `dokobot` | Chrome 浏览器网页读取 | 读取网页 |
| `notion-mcp` | Notion 工作区 MCP 工具集成 | Notion, 数据库, 页面 |

### 🤖 执行规则 (Execution Rules)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `sisyphus-execution-rules` | Sisyphus 执行规则 | 执行规则, Agent 分工 |

### 📦 项目级技能 (Project-Level Skills)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `code-reviewer` | Java 25 / Spring Boot 4 代码审查 | Java 审查, Spring 审查 |
| `creating-springboot-projects` | 创建 Spring Boot 项目结构 | Spring Boot 项目 |
| `spring-data-jpa` | Spring Data JPA 仓库/查询设计 | JPA, 仓库, 查询 |
| `springboot-migration` | Spring Boot 迁移 | Spring 迁移, Boot 4 |

---

## 🔥 高频组合模式

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
security-and-hardening → check → code-review
```

### 性能优化
```
web-performance-audit → performance-optimization → debug-optimize-lcp
```

---

## 🧩 其他技能 (Other Skills)

### 分析 & 数据 (Analysis & Data)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `analysis-artifacts` | 生成可重复分析工件 | 分析, SQL, Python |
| `dbt-model-index` | dbt 模型查询索引 | dbt, BigQuery |
| `reverse-engineering` | 逆向工程 | 逆向, 反编译 |

### 调试 & 工具 (Debugging & Tools)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `grilling` | 压力测试计划/设计 | 审问, 压力测试 |
| `prototype` | 构建原型回答设计问题 | 原型, 验证 |
| `request-refactor-plan` | 创建重构计划 | 重构计划 |
| `researchwrite` | 研究写作 | 研究写作 |
| `setup-matt-pocock-skills` | 配置工程技能 | 配置, 设置 |
| `slack-qa-investigate` | Slack QA 调查 | Slack QA |
| `terraform-style-check` | Terraform 代码风格检查 | Terraform |
| `using-superpowers` | 使用超级技能 | 超级技能 |
| `web-design-guidelines` | Web 设计指南 | 设计指南 |
| `wizard` | 生成交互式向导 | 向导, 引导 |

### 笔记 & 知识管理 (Notes & Knowledge)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `obsidian-vault` | Obsidian 知识库管理 | Obsidian, 知识库 |
| `kami` | Kami 笔记管理 | Kami |

### 代理 & 交接 (Agent & Handoff)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `claude-handoff` | 会话交接给新代理 | 交接, 切换 |
| `handoff` | 压缩会话为交接文档 | 交接文档 |

### 工具 & 配置 (Tools & Config)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| `find-skills` | 发现安装技能 | 找技能 |
| `ask-matt` | 询问适合的技能/流 | 选技能 |
| `using-agent-skills` | 发现调用技能 | 技能发现 |

### 🎬 创作 & 媒体 (Creation & Media)

| 技能名 | 用途 | 触发词 |
|--------|------|--------|
| (暂无已安装技能) | | |

---

## 📝 使用说明

1. **扫描此文件**: 在执行任务前快速扫描相关类别
2. **加载多个技能**: 可以同时加载多个相关技能
3. **技能优先级**: 过程技能 (brainstorming, systematic-debugging) 优先于实现技能
4. **用户指令优先**: 用户指令 > 技能 > 默认行为
5. **R20 规则**: 使用某类型 Skills 前先列出所有同类 Skills

---

## 🏷️ 技能来源

| 来源 | 说明 | 优先级 |
|------|------|--------|
| `project` | 项目特定技能 (`.agents/skills/`) | 最高 |
| `user` | 用户安装技能 | 高 |
| `opencode` | OpenCode 内置技能 | 中 |
| `builtin` | 内置插件技能 | 低 |

**规则**: 用户安装技能覆盖内置默认。

---

*此文件由 AI 自动维护。当新增或修改技能时，应同步更新此索引。*
