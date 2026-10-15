# SKILL GRAPH — AI Agent 技能索引

> **最后更新**: 2026-10-12（按各技能 `SKILL.md` 的 frontmatter 重新生成）
> **技能总数**: **496** 个唯一技能 / 507 个 SKILL.md 文件
> **来源**: 项目 `.agents/skills` 4 · 内置 omp 2 · 内置 superpowers 15 · 用户级 `~/.agents/skills` 416 · opencode 配置 70
> **用途**: AI 执行任务前定位相关技能；人类查本机可用工具与用法
> **用法**: 先看 §0 快速查找指南；否则用检索（如 `grep -i "关键词" SKILL_GRAPH.md`）定位技能名，**不要整篇读取**（全表约 12 万字节）
> **维护**: 本文件是索引，不复制技能内容；技能增删后按同一规则重新生成。名称以技能 frontmatter 的 `name:` 为准（与目录名可能不同，如目录 `gstack-cso` 的技能名是 `cso`）

---

## 0. 快速查找指南（下表每个技能名均已核对存在于本机）

| 我要做什么 | 技能链（按顺序） |
| --- | --- |
| 规划新功能 | `brainstorming` → `writing-plans` → `executing-plans` |
| 修复 bug | `systematic-debugging` → `test-driven-development` → `verification-before-completion` |
| 代码审查 | `requesting-code-review` → `code-review` → `receiving-code-review` |
| Git / 提交 | `git-workflow-and-versioning` → `create-pull-request` |
| 测试策略 | `tdd` → `test-audit` → `qa` |
| 写作 / 文档 | `write` → `docs-update` → `documentation-and-adrs` |
| UI / 前端 | `ui` → `design-taste-frontend` → `frontend-ui-engineering` |
| 浏览器验证 | `browse` → `webapp-testing` → `browser-use` |
| 性能优化 | `web-performance-audit` → `performance-optimization` → `debug-optimize-lcp` |
| 安全审计 | `cso` → `security-and-hardening` |
| 部署上线 | `deploy-to-vercel` → `shipping-and-launch` → `ci-cd-and-automation` |
| CLI 封装 | `cli-anything` → `cli-hub-meta-skill` |
| 深度研究 | `doko-research` → `parallel-web` → `paper-lookup` |
| 上下文交接 | `context-save` → `context-restore` |
| 会话纪律 / 防护 | `careful` → `guard` → `freeze` |
| 科研写作 | `scientific-writing` → `nature-figure` → `scientific-visualization` |
| 单细胞 | `scanpy` → `anndata` → `scvi-tools` → `scvelo` |
| 分子 ML | `rdkit` → `datamol` → `deepchem` |
| 量子计算 | `qiskit` → `cirq` → `pennylane` → `qutip` |
| 并行子代理 | `dispatching-parallel-agents` → `subagent-driven-development` |

---

## 1. 项目技能（`.agents/skills/`，4 个）

> 本仓库自带；由 `skills-lock.json` 锁定来源与哈希（此处不复制锁定内容）

| 技能 | 何时使用 |
| --- | --- |
| `code-reviewer` | Reviews Java 25 and Spring Boot 4 codebases, pull requests, files, and modules for migration risks, architecture boundary violations, JSpecify null-safety issues, security flaws, performance regressions, and Spring Data pitfalls. Use when… |
| `creating-springboot-projects` | Creates Java 25 and Spring Boot 4 project structures, scaffolds, and implementation starting points for new services, REST APIs, and modular backends. Use when the task is to initialize a Spring Boot project, choose an architecture, select… |
| `spring-data-jpa` | Designs and implements Spring Data JPA repositories, projections, query patterns, custom repositories, CQRS read models, entity relationships, and persistence performance fixes for Java 25 and Spring Boot 4 projects. Use when the task need… |
| `springboot-migration` | Migrates Spring Boot applications to Boot 4 with Java 25, including related Spring Modulith 2 and Testcontainers 2 upgrade work. Use when the task is a concrete upgrade, dependency transition, starter rename, test-annotation migration, or… |

---

## 2. 内置技能（17 个：omp 2 + superpowers 15）

## 2.1 内置 — omp（`~/.omp/agent/skills/`，2 个）

> 随 omp 安装的内置技能

| 技能 | 何时使用 |
| --- | --- |
| `teach` | Teach the user anything so it actually locks in and is understood, not just memorized. Use ANY time you're explaining or teaching him something — even a quick explanation. Based on two teaching principles he has personally verified to work… |
| `visualize` | Add a correct, minimal visual to a lesson — a diagram or geometric picture — that renders inline in the Obsidian log. Use when an idea is genuinely clearer as a picture: a dependency graph, system/flow, sequence, state machine, tree, compa… |

---

### 2.2 内置 — superpowers（`~/.omp/plugins/node_modules/superpowers/skills/`，15 个）

> Superpowers 插件包；通过 `skill://<name>` 读取

| 技能 | 何时使用 |
| --- | --- |
| `brainstorming` | You MUST use this before any creative work - creating features, building components, adding functionality, modifying behavior, or planning anything new, in software or out of it (a talk, a business, a renovation). |
| `diagnosing-superpowers` | Use when a superpowers session went wrong and your human partner wants to know why — repeated work, ignored plans, stumbles, poor results, a skill that didn't fire, "it took too long", "why is it so expensive", "what is it doing" — or want… |
| `dispatching-parallel-agents` | Use when facing 2+ independent tasks that can be worked on without shared state or sequential dependencies |
| `executing-plans` | Use when executing an implementation plan in the current session as the implementer yourself — your human partner chose inline execution, or no subagent tool is available |
| `finishing-a-development-branch` | Use when implementation is complete, all tests pass, and you need to decide how to integrate the work |
| `receiving-code-review` | Use when receiving code review feedback, before implementing suggestions, especially if feedback seems unclear or technically questionable - requires technical rigor and verification, not performative agreement or blind implementation |
| `requesting-code-review` | Use when completing tasks, implementing major features, or before merging to verify work meets requirements |
| `subagent-driven-development` | Use when executing implementation plans with independent tasks in the current session |
| `systematic-debugging` | Use when encountering any bug, test failure, or unexpected behavior, before proposing fixes |
| `test-driven-development` | Use when implementing any feature or bugfix, before writing implementation code |
| `using-git-worktrees` | Use when starting feature work that needs isolation from current workspace or before executing implementation plans - ensures an isolated workspace exists via native tools or git worktree fallback |
| `using-superpowers` | Use when starting any conversation - establishes how to find and use skills, requiring skill invocation before ANY response including clarifying questions |
| `verification-before-completion` | Use when about to claim work is complete, fixed, or passing, before committing or creating PRs - requires running verification commands and confirming output before making any success claims; evidence before assertions always |
| `writing-plans` | Use when you have a spec or requirements for a multi-step task, before touching code |
| `writing-skills` | Use when creating new skills, editing existing skills, or verifying skills work before deployment |

---

## 3. 用户级技能（`~/.agents/skills/`，416 个）

> 本机技能库主体；按名称排序

| 技能 | 何时使用 |
| --- | --- |
| `13c-metabolic-flux` | Estimates intracellular metabolic fluxes from steady-state carbon-13 isotope-tracing measurements using validated atom maps, mfapy isotope simulation, constrained multistart fitting, and flux-profile diagnostics. Use for 13C-MFA, carbon tr… |
| `a11y-debugging` | Uses Chrome DevTools MCP for accessibility (a11y) debugging and auditing based on web.dev guidelines. Use when testing semantic HTML, ARIA labels, focus states, keyboard navigation, tap targets, and color contrast. |
| `adaptyv` | Uses the Adaptyv Bio Foundry API and Python SDK to design protein characterization experiments, estimate costs, submit sequences, monitor laboratory progress, and retrieve results. Applies to Adaptyv Foundry, its target catalog, binding sc… |
| `aeon` | This skill should be used for time series machine learning tasks including classification, regression, clustering, forecasting, anomaly detection, segmentation, and similarity search. Use when working with temporal data, sequential pattern… |
| `alphagenome` | Looks up precomputed AlphaGenome Atlas effects for any GRCh38 single-nucleotide variant (AVI score with Phred and 18 SHAP feature attributions, plus raw and quantile scores for RNA-seq, DNase, ATAC, ChIP-TF, ChIP-histone, CAGE, PRO-cap, sp… |
| `analysis-artifacts` | Generate reproducible analysis artifacts — SQL queries, Python visualizations, and summary tables — as you work through a BigQuery data analysis. Use when asked to conduct a deep dive, exploratory analysis, or investigation that goes beyon… |
| `analytical-method-validation` | Plans, executes, and documents validation, verification, and transfer of analytical procedures under the governing framework - ICH Q2(R2) and Q14, USP <1220>/<1225>/<1226>, ICH M10 bioanalytical, CLSI EP, or ISO/IEC 17025. Use for HPLC, LC… |
| `animation-vocabulary` | Reverse-lookup glossary that turns a vague description of a web animation or motion effect into its exact term ("the bouncy thing when a popover opens" → Pop in; "the iOS rubber-band scroll" → Rubber-banding). Use when the user asks "what'… |
| `anndata` | Handles annotated matrices in single-cell analysis, .h5ad and Zarr files, and integration with the scverse ecosystem. This is the data format skill—for analysis workflows use scanpy; for probabilistic models use scvi-tools; for population-… |
| `api-and-interface-design` | Guides stable API and interface design. Use when designing APIs, module boundaries, or any public interface. Use when creating REST or GraphQL endpoints, defining type contracts between modules, or establishing boundaries between frontend… |
| `arbor` | Applies Arbor Hypothesis Tree Refinement to research artifacts with repeatable evaluators, including model training, agent harnesses, data synthesis and benchmark optimization. Uses persistent hypotheses, isolated experiments, evidence pro… |
| `arboreto` | Infers candidate gene regulatory networks from bulk or single-cell expression data using AertsLab Arboreto GRNBoost2 and GENIE3. Use for transcription factor-target association ranking, compatible Dask execution, sparse expression inputs,… |
| `archify` | Create validated architecture, workflow, sequence, data-flow, lifecycle/state, entity-relationship, class/interface, tree, timeline, and execution waterfall diagrams as explorable standalone HTML with inline SVG, dark/light themes, optiona… |
| `archify-review` | Review Archify issues, PRs, or code through value, cost, and impact to support evidence-based maintenance decisions. Use for issue triage, change reviews, and code quality assessments. |
| `ask-matt` | Ask which skill or flow fits your situation. A router over the skills in this repo. |
| `astropy` | Core Python library for astronomy and astrophysics workflows that need Astropy APIs, including units/quantities, coordinates, FITS I/O, tables, time systems, WCS, and cosmology. Use when implementing or debugging astronomical data analysis… |
| `autoskill` | Analyzes user-requested Screenpipe history windows to detect repeated research workflows, match existing scientific skills, and stage new skill drafts or composition recipes for review. Requires a reachable Screenpipe HTTP API, normally on… |
| `beautify-github-readme` | Redesign GitHub README homepages or create project-native pure SVG, hybrid SVG-composed PNG/WebP, and opt-in animated GIF assets. Use when a user asks to beautify, redesign, rebrand, visually upgrade, simplify, or audit a GitHub README; cr… |
| `benchling-integration` | Benchling Python SDK and REST API integration for registry entities, inventory, ELN entries, workflows, Benchling Apps, and Data Warehouse queries. Use when automating lab data with benchling-sdk or the v2 API. |
| `bgpt-paper-search` | Searches BGPT scientific papers by topic or DOI and retrieves claim-level evidence extracted from full text, including experiments, reported statistics, scope, limitations, and provenance. Use for literature reviews, evidence synthesis, an… |
| `bids` | Organizes, queries, validates, and converts Brain Imaging Data Structure (BIDS) datasets. Supports organizing neuroscience and biomedical data (MRI, EEG, MEG, iEEG, PET, microscopy, NIRS, motion capture, EMG, MR spectroscopy, behavioral),… |
| `biopython` | Provides Biopython workflows for sequence manipulation, file parsing (FASTA/GenBank/PDB), phylogenetics, and programmatic NCBI/PubMed access (Bio.Entrez). Supports batch processing, custom molecular-biology pipelines, BLAST automation, str… |
| `bioservices` | Provides a Python interface to bioinformatics services including UniProt, KEGG, ChEMBL, Reactome, QuickGO, and UniChem. Used for cross-database protein annotation, pathway retrieval, chemical identifier mapping, and integrated biological d… |
| `brandkit` | Premium brand-kit image generation skill for creating high-end brand-guidelines boards, logo systems, identity decks, and visual-world presentations. Trained for minimalist, cinematic, editorial, dark-tech, luxury, cultural, security, gami… |
| `browser-harness` | Control a real browser via CDP: clicking, typing, navigation, logged-in sessions, JS-rendered or bot-protected pages. Not for plain HTTP fetches of public content - use curl for those. |
| `browser-harness` | Control a real browser via CDP: clicking, typing, navigation, logged-in sessions, JS-rendered or bot-protected pages. Not for plain HTTP fetches of public content - use curl for those. |
| `browser-harness` · 目录 `browser_harness` | Control a real browser via CDP: clicking, typing, navigation, logged-in sessions, JS-rendered or bot-protected pages. Not for plain HTTP fetches of public content - use curl for those. |
| `browser-testing-with-devtools` | Tests in real browsers via Chrome DevTools MCP. Use when building or debugging anything that runs in a browser. Use when you need to inspect the DOM, capture console errors, analyze network requests, profile performance, or verify visual o… |
| `browser-use` | Direct browser control via CDP for web interaction: automation, scraping, testing, screenshots, and site/app work. |
| `bulk-rnaseq` | Prepares bulk RNA-seq FASTQ, Salmon, STAR or featureCounts output for gene-level differential expression. Covers nf-core/rnaseq and standalone quantification, biological replication, strandedness, reference provenance, validated count asse… |
| `cantera` | Runs Cantera homogeneous chemical reactors and evaluates ignition delay with mechanism provenance, conservation checks, and numerical refinement. Use for combustion kinetics, closed adiabatic ideal-gas constant-volume or constant-pressure… |
| `cellprofiler` | Runs reproducible CellProfiler microscopy pipelines for nuclear segmentation, cell counts, and per-object fluorescence measurements. Supports image/channel manifests, headless batch execution, segmentation overlays, and measurement QC for… |
| `cellxgene-census` | Queries the CZ CELLxGENE Census programmatically for versioned public single-cell and spatial transcriptomics data. Use when you need population-scale cell metadata, gene expression slices, Census summary counts, source H5AD URIs/downloads… |
| `chart-designer` | Design effective data visualizations and charts. Generate chart configurations for ECharts, Chart.js, and other libraries. Create dashboards and reports. |
| `check` | Reviews diffs, PRs, release readiness, and publishing follow-through. Use when asked to review, triage issues or PRs, or ship. Not for debugging root causes or prose. |
| `chief-of-staff` | Pursue a long-running goal in a single session by co-ordinating subagents. |
| `chrome-devtools` | Uses Chrome DevTools via MCP for efficient debugging, troubleshooting and browser automation. Use when debugging web pages, automating browser interactions, analyzing performance, or inspecting network requests. This skill does not apply t… |
| `chrome-devtools-cli` | Use this skill to write shell scripts or run shell commands to automate tasks in the browser or otherwise use Chrome DevTools via CLI. |
| `ci-cd-and-automation` | Automates CI/CD pipeline setup. Use when setting up or modifying build and deployment pipelines. Use when you need to automate quality gates, configure test runners in CI, or establish deployment strategies. |
| `ci-fix` | Diagnose and fix GitHub Actions CI failures. Inspects workflow runs and logs, identifies root causes, implements minimal fixes, and pushes to a fix branch. Use when CI is failing, red, broken, or needs diagnosis. |
| `cirq` | Google quantum computing framework. Use when targeting Google Quantum AI hardware, designing noise-aware circuits, or running quantum characterization experiments. Best for Google hardware, noise modeling, and low-level circuit design. For… |
| `citation-management` | Comprehensive citation management for academic research. Search OpenAlex, PubMed, and Google Scholar for papers, extract accurate metadata, validate citations, and generate properly formatted BibTeX entries. This skill should be used when… |
| `claude-handoff` | Hand the current conversation off to a fresh background agent that picks up the work immediately. |
| `cli-anything` | Use when the user wants Codex to build, refine, test, validate, or list CLI-Anything harnesses for GUI applications or source repositories. Adapts the full CLI-Anything methodology to Codex without changing the generated Python harness for… |
| `cli-anything-adguardhome` | Command-line interface for AdGuard Home - Network-wide ad blocking and DNS management via AdGuard Home REST API. Designed for AI agents and power users who need to manage filtering, DNS rewrites, clients, DHCP, and query logs without a GUI. |
| `cli-anything-anygen` | Command-line interface for Anygen - A stateful command-line interface for AnyGen OpenAPI — generate professional slides, documents, webs... |
| `cli-anything-audacity` | Command-line interface for Audacity - A stateful command-line interface for audio editing, following the same patterns as the GIMP and Ble... |
| `cli-anything-blender` | Command-line interface for Blender - A stateful command-line interface for 3D scene editing, following the same patterns as the GIMP CLI ... |
| `cli-anything-browser` | Browser automation CLI using DOMShell MCP server. Maps Chrome's Accessibility Tree to a virtual filesystem for agent-native navigation. |
| `cli-anything-calibre` | Command-line interface for Calibre - A stateful CLI harness for e-book library management, metadata editing, and format conversion wrapping the real Calibre tools (calibredb, ebook-convert, ebook-meta)... |
| `cli-anything-ccswitch` | CLI interface for CC Switch — manage AI coding tool configurations from the terminal |
| `cli-anything-chromadb` | Command-line interface for ChromaDB - A stateless CLI for managing vector database collections, documents, and semantic search. Designed for AI agents and automation via the ChromaDB HTTP API v2. |
| `cli-anything-cloudanalyzer` | Command-line interface for CloudAnalyzer — Agent-friendly harness for CloudAnalyzer, a QA platform for mapping, localization, and perception outputs. Supports 27 commands across 8 groups: point cloud evaluation, trajectory evaluation, grou… |
| `cli-anything-cloudcompare` | Command-line interface for CloudCompare — Agent-friendly harness for CloudCompare, the open-source 3D point cloud and mesh processing software. Supports 41 commands across 9 groups: project management, session control, point cloud operatio… |
| `cli-anything-comfyui` | Command-line interface for ComfyUI - AI image generation workflow management via ComfyUI REST API. Designed for AI agents and power users who need to queue workflows, manage models, download generated images, and monitor the generation que… |
| `cli-anything-dify-workflow` | Wrapper for the Dify workflow DSL CLI. Create, inspect, validate, edit, and export Dify workflow files through a CLI-Anything harness. |
| `cli-anything-drawio` | Command-line interface for Drawio - A CLI harness for **Draw.io** — create, edit, and export diagrams from the command line.... |
| `cli-anything-eez-studio` | Use EEZ Studio from the command line: inspect and modify native .eez-project files, edit LVGL screens/widgets, manage SCPI commands, and invoke the real EEZ Studio backend for LVGL builds. |
| `cli-anything-eth2-quickstart` | Use eth2-quickstart to autonomously deploy a hardened Ethereum node, install execution and consensus clients, configure validator metadata, expose RPC safely, and inspect node health with structured JSON output. |
| `cli-anything-exa` | Agent-native CLI for Exa web search and content retrieval workflows. |
| `cli-anything-firefly-iii` | Firefly III CLI - Personal finance management via CLI-Anything |
| `cli-anything-freecad` | Complete CLI harness for FreeCAD parametric 3D CAD modeler (258 commands). Covers ALL workbenches: Part (29 primitives + boolean + mirror + loft + sweep), Sketcher (26 cmds: geometry + constraints + editing), PartDesign (38 cmds: pad/pocke… |
| `cli-anything-gimp` | Command-line interface for Gimp - A stateful command-line interface for image editing, built on Pillow. Designed for AI agents and pow... |
| `cli-anything-godot` | Agent-native CLI for Godot project management, scenes, exports, and script execution. |
| `cli-anything-hermes` | Use when the user wants Hermes Agent to build, refine, test, or validate a CLI-Anything harness for a GUI application or source repository. Adapts the CLI-Anything methodology to Hermes without changing the generated Python harness format. |
| `cli-anything-inkscape` | Command-line interface for Inkscape - A stateful command-line interface for vector graphics editing, following the same patterns as the GI... |
| `cli-anything-intelwatch` | Zero friction. Full context. Competitive intelligence, M&A due diligence, and OSINT directly from your terminal. Uses Node.js/npx. |
| `cli-anything-iterm2` | Provides the cli-anything-iterm2 commands — the only way to actually send text to iTerm2 sessions, read live terminal output and scrollback history, manage windows/tabs/split panes, run tmux -CC workflows, broadcast to multiple panes, show… |
| `cli-anything-iterm2-ctl` | Provides the cli-anything-iterm2 commands — the only way to actually send text to iTerm2 sessions, read live terminal output and scrollback history, manage windows/tabs/split panes, run tmux -CC workflows, broadcast to multiple panes, show… |
| `cli-anything-joplin` | Command-line interface for Joplin workflows using the real joplin terminal backend |
| `cli-anything-jumpserver` | Stateful CLI harness for JumpServer bastion host management. Supports asset, user, permission, account, session, audit, and operations management via REST API, with both one-shot and interactive REPL modes. |
| `cli-anything-kdenlive` | Command-line interface for Kdenlive - A stateful command-line interface for video editing, following the same patterns as the Blender CLI ... |
| `cli-anything-krita` | CLI harness for Krita digital painting — manage projects, layers, filters, and export via command line. Use when automating Krita workflows, batch processing images, or operating Krita without a GUI. |
| `cli-anything-libreoffice` | Command-line interface for Libreoffice - A stateful command-line interface for document editing, producing real ODF files (ZIP archives with ... |
| `cli-anything-live2d` | Inspect, validate, edit, lint, diff, batch-manage, and deploy Live2D Cubism models (.model3.json) from the command line. |
| `cli-anything-lldb` | Stateful LLDB debugging via LLDB Python API |
| `cli-anything-macrocli` | Use when the agent wants to define, list, inspect, or execute GUI macros via the MacroCLI CLI. Macros are parameterized, CLI-callable workflows — the agent invokes `macro run <name>` and the system handles backend routing (plugin, file tra… |
| `cli-anything-mailchimp` | CLI harness for the Mailchimp Marketing API v3.0 — 303 commands across 30 resource groups (lists, campaigns, reports, automations, ecommerce, templates, and more). Supports JSON output and interactive REPL mode. |
| `cli-anything-mermaid` | Command-line interface for Mermaid Live Editor - Create, edit, and render Mermaid diagrams via stateful project files and mermaid.ink renderer URLs. Designed for AI agents and power users who need to generate flowcharts, sequence diagrams,… |
| `cli-anything-minimax` | Command-line interface for MiniMax AI — chat (MiniMax-M3, MiniMax-M2.7) and speech-2.x TTS via the MiniMax API. |
| `cli-anything-mubu` | Command-line interface for Mubu - Canonical packaged entrypoint for the Mubu live bridge.... |
| `cli-anything-musescore` | CLI for music notation — transpose, export PDF/audio/MIDI, extract parts, manage instruments |
| `cli-anything-n8n` | Command-line interface for n8n workflow automation platform. Manages workflows, executions, credentials, variables, and tags. Based on n8n Public API v1.1.1 (n8n >= 1.0.0). |
| `cli-anything-notebooklm` | Experimental NotebookLM harness for listing notebooks, managing sources, asking questions, generating artifacts, and downloading outputs through an installed notebooklm CLI. |
| `cli-anything-novita` | Command-line interface for Novita AI - An OpenAI-compatible AI API client for DeepSeek, GLM, and other models. |
| `cli-anything-nsight-graphics` | Windows-first CLI harness for Nsight Graphics capture, GPU Trace summary, and ngfx-replay analysis |
| `cli-anything-nslogger` | CLI harness for NSLogger — parse, filter, export, and monitor NSLogger log files (.rawnsloggerdata / .nsloggerdata) |
| `cli-anything-obs-studio` | Command-line interface for Obs Studio - A stateful command-line interface for OBS Studio scene collection editing, following the same patter... |
| `cli-anything-obsidian` | Command-line interface for Obsidian — Knowledge management and note-taking via Obsidian Local REST API. Designed for AI agents and power users who need to manage notes, search the vault, and execute commands without the GUI. |
| `cli-anything-ollama` | Command-line interface for Ollama - Local LLM inference and model management via Ollama REST API. Designed for AI agents and power users who need to manage models, generate text, chat, and create embeddings without a GUI. |
| `cli-anything-openrefine` | Use OpenRefine through an agent-native CLI for importing messy data, applying JSON operation histories, inspecting rows, exporting cleaned data, and managing session undo/redo. |
| `cli-anything-openscreen` | Command-line interface for Openscreen — a screen recording editor. A stateful CLI for editing screen recordings with zoom, speed ramps, trim, crop, annotations, and polished exports. Built on the Openscreen JSON project format with ffmpeg… |
| `cli-anything-pm2` | Command-line interface for PM2 - A stateless CLI for Node.js process management via the PM2 CLI. List, start, stop, restart processes, view logs, and manage system configuration. |
| `cli-anything-qgis` | Stateful QGIS CLI for projects, writable layers, features, layouts, exports, and qgis_process operations using the real QGIS runtime. |
| `cli-anything-quietshrink` | Compress macOS screen recordings with zero CPU stress using Apple Silicon's hardware HEVC encoder. Typically reduces file size 70-90% while staying visually lossless. Computer stays silent during encoding. |
| `cli-anything-rekordbox` | Command-line interface for Pioneer Rekordbox 6/7 - DJ library and live-deck control via guarded SQLCipher master.db access (pyrekordbox) + virtual MIDI mapping. Provides library inspection, playlist creation/add/clear, and live-deck mixing… |
| `cli-anything-renderdoc` | CLI harness for RenderDoc graphics debugger capture analysis |
| `cli-anything-rms` | Teltonika RMS device management and monitoring CLI |
| `cli-anything-safari` | Safari browser automation CLI on macOS via safari-mcp. Controls real Safari (native, keeps logins) by wrapping the safari-mcp MCP server. Every one of the 84 MCP tools is exposed 1:1 with schema-accurate arguments — guaranteed parity, no m… |
| `cli-anything-sbox` | Agent-native CLI for the s&box game engine (Facepunch Studios, Source 2): project management, scene/prefab editing, material/sound/localization configs, C# code generation, asset graph queries, project validation, and editor launch. |
| `cli-anything-seaclip` | Command-line interface for SeaClip-Lite - A stateless CLI for managing issues, pipelines, agents, schedules, and activity on the SeaClip-Lite project management board. |
| `cli-anything-shotcut` | Command-line interface for Shotcut - A stateful command-line interface for video editing, built on the MLT XML format. Designed for AI ag... |
| `cli-anything-siyuan` | SiYuan (思源笔记) CLI — manage notebooks, documents, blocks, and search your knowledge base from the terminal. |
| `cli-anything-slay-the-spire-ii` | Command-line interface for Slay the Spire 2 - Control the real game through a local bridge mod HTTP API. Reads normalized game state and sends action commands for combat, navigation, rewards, and menu management. |
| `cli-anything-threemf` | 3MF mesh geometry editor — detect and resize cylindrical holes, repair meshes, compare 3D printing files. Works with BambuStudio and PrusaSlicer 3MF files. |
| `cli-anything-tigris` | Command-line interface for Tigris object storage — wraps the official `tigris` CLI to expose buckets, objects, presigned URLs, snapshots, IAM, and scoped access keys to AI agents. Globally distributed, S3-compatible, no egress fees. |
| `cli-anything-unimol-tools` | Interactive CLI for Uni-Mol molecular property prediction training and inference workflows. |
| `cli-anything-unrealinsights` | Capture Unreal Engine traces, inspect Trace Store files, keep Unreal Insights GUI open, and export/summarize timing/counter data. |
| `cli-anything-videocaptioner` | AI-powered video captioning — transcribe speech, optimize/translate subtitles, and burn them into video via the stable VideoCaptioner backend. Free ASR and translation included. |
| `cli-anything-wavetone` | Control WaveTone 2.61 workflows through a JSON manifest and launch the real Windows WaveTone executable. |
| `cli-anything-web-yu-pri` | Use Japan Post Web Yu-pri from a CLI by driving the real browser UI for login, inspection, screenshots, dry-run planning, and contents-form filling. |
| `cli-anything-wiremock` | Python CLI harness for WireMock HTTP mock server administration |
| `cli-anything-zoom` | Command-line interface for Zoom - CLI harness for **Zoom** — manage meetings, participants, and recordings from the command line via t... |
| `cli-anything-zotero` | CLI harness for Zotero. |
| `cli-hub-meta-skill` | Discover agent-native CLIs for professional software. Access the live catalog to find tools for creative workflows, productivity, AI, and more. |
| `clinical-decision-support` | Prepares and validates research-only clinical decision-support evaluation, evidence-profile, cohort, survival, biomarker/model, privacy, and governance artifacts. Supports aggregate or synthetic research documentation and traceability, exc… |
| `clinical-reports` | Creates safety-bounded draft structures and runs local deterministic checks for clinical case, diagnostic, trial, safety, and aggregate research reports. Use only with synthetic, de-identified, or aggregate inputs and verified source-fact… |
| `cloud` | Documentation reference for using Browser Use Cloud — the hosted API and SDK for browser automation. Use this skill whenever the user needs help with the Cloud REST API (v2, v3, or v4), browser-use-sdk (Python or TypeScript), X-Browser-Use… |
| `cobrapy` | Performs constraint-based metabolic modeling with COBRApy, including FBA, pFBA, FVA, gene knockouts, flux sampling, growth media, production envelopes, gap filling, and SBML model validation for systems biology and metabolic engineering. |
| `code-review` | Review the changes since a fixed point (commit, branch, tag, or merge-base) along two axes: Standards (does the code follow this repo's documented coding standards?) and Spec (does the code match what the originating issue/spec asked for?)… |
| `code-review-and-quality` | Conducts multi-axis code review. Use before merging any change. Use when reviewing code written by yourself, another agent, or a human. Use when you need to assess code quality across multiple dimensions before it enters the main branch. U… |
| `code-simplification` | Simplifies code for clarity. Use when refactoring code for clarity without changing behavior. Use when code works but is harder to read, maintain, or extend than it should be. Use when reviewing code that has accumulated unnecessary comple… |
| `codebase-design` | Shared vocabulary for designing deep modules. Use when the user wants to design or improve a module's interface, find deepening opportunities, decide where a seam goes, make code more testable or AI-navigable, or when another skill needs t… |
| `consciousness-council` | Structures a multi-perspective council exercise for decisions, research trade-offs, and creative challenges. Simulates thinking archetypes, separates evidence from assumptions and values, and synthesizes a conditional recommendation. Use w… |
| `constraint-driven-development` | Establishes a project's quality bar as a written contract and stops agents quietly lowering it. Interviews the user on which dimensions matter, supplies sane default thresholds when they have no number in mind, records everything in CONSTR… |
| `context-engineering` | Optimizes agent context setup. Use when starting a new session, when agent output quality degrades, when switching between tasks, or when you need to configure rules files and context for a project. |
| `create-pull-request` | Create a GitHub pull request following project conventions. Use when the user asks to create a PR, submit changes for review, or open a pull request. Handles commit analysis, branch management, and PR creation using the gh CLI tool. |
| `dask` | Scales pandas, NumPy, and custom Python research workflows beyond memory or across clusters with Dask. Covers DataFrames, Arrays, Bags, Futures, chunking, schedulers, and distributed diagnostics. Use for partitioned file processing, scient… |
| `database-lookup` | Queries documented public database APIs with explicit endpoints, filters, pagination, and provenance. Used when a scientific, regulatory, financial, or other database-backed fact must be retrieved reproducibly from a named source rather th… |
| `datalad` | Retrieves, versions, and publishes scientific datasets with DataLad and git-annex, and captures computational provenance with datalad run, rerun, and containers-run. Use when cloning or fetching data from OpenNeuro, DANDI, datasets.datalad… |
| `datamol` | Pythonic wrapper around RDKit with simplified interface and sensible defaults. Preferred for standard drug discovery including SMILES parsing, standardization, descriptors, fingerprints, clustering, 3D conformers, parallel processing. Retu… |
| `dbt-model-index` | Provide a lookup index of dbt models (BigQuery tables) to guide query writing against a data warehouse. Use when you need to query, analyze, or look up data in a dbt-powered data warehouse, or when resolving a vague data question into the… |
| `debug-optimize-lcp` | Guides debugging and optimizing Largest Contentful Paint (LCP) using Chrome DevTools MCP tools. Use this skill whenever the user asks about LCP performance, slow page loads, Core Web Vitals optimization, or wants to understand why their pa… |
| `debugging-and-error-recovery` | Guides systematic root-cause debugging. Use when tests fail, builds break, something that worked yesterday broke, behavior doesn't match expectations, or you encounter any unexpected error. Use when you need to figure out what broke and wh… |
| `deepchem` | Builds molecular property prediction and MoleculeNet workflows with DeepChem, including SMILES featurization, scaffold or grouped holdouts, masked labels, graph models and explicit pretrained encoder transfer. Used for ADMET, toxicity, sol… |
| `deepspot-m` | Generates transcriptome-wide virtual spatial transcriptomics from H&E histology with DeepSpot-M. Used for predicted log1p-CPM expression from 224x224 tiles at about 20x, querying the released protein-coding gene panel by symbol, and whole-… |
| `deeptools` | NGS analysis toolkit. BAM to bigWig conversion, QC (correlation, PCA, fingerprints), heatmaps/profiles (TSS, peaks), for ChIP-seq, RNA-seq, ATAC-seq visualization. |
| `deploy-to-vercel` | Deploy applications and websites to Vercel. Use when the user requests deployment actions like "deploy my app", "deploy and give me the link", "push this live", or "create a preview deployment". |
| `depmap` | Retrieves and analyzes Cancer Dependency Map (DepMap) release data, including CRISPR Chronos gene effects, cancer model annotations, omics biomarkers, and PRISM drug sensitivity. Supports cancer-selective dependency, co-essentiality, and c… |
| `deprecation-and-migration` | Manages deprecation and migration. Use when removing old systems, APIs, or features. Use when migrating users from one implementation to another. Use when migrating a database schema in production, such as renaming or dropping a column wit… |
| `design-taste-frontend` | tasteskill v2, the anti-slop frontend skill. Art direction for websites, distilled from a study of 247 curated gallery sites (One Page Love, Awwwards Sites of the Day). Deals researched directions (idea, composition, type, palette, photogr… |
| `design-taste-frontend-v1` | The original v1 taste-skill, preserved for projects depending on its exact behavior. The current default is `design-taste-frontend` (v2), which is a new skill. Use this v1 install name only if you need exact backward compatibility. |
| `dhdna-profiler` | Applies the DHDNA framework as an exploratory rubric for reasoning and writing patterns in supplied text. Used for explicit requests for DHDNA, cognitive-style reflection, a thinking-pattern profile, or comparisons of textual reasoning. Sc… |
| `diagnosing-bugs` | Diagnosis loop for hard bugs and performance regressions. Use when the user says "diagnose"/"debug this", or reports something broken/throwing/failing/slow. |
| `diagram-design` | Create branded architecture, architecture delta, IT current-state, flowchart, sequence, state machine, ER/data model, timeline, swimlane, quadrant, radar/spider, polar chart (polar/radial lollipop), loop/flywheel, nested, tree, org chart,… |
| `diffdock` | Predicts protein-small-molecule binding poses with DiffDock and DiffDock-L from PDB or sequence plus SMILES/SDF/MOL2. Covers batch docking, pose triage, confidence interpretation, and validation. Use for molecular docking and virtual-scree… |
| `dnanexus-integration` | Builds and operates reproducible genomics workloads on DNAnexus with the dx CLI, dxpy, apps/applets, native workflows, dxCompiler, and Nextflow. Supports DNAnexus data transfers, dxapp.json development, execution monitoring, workflow impor… |
| `docs-update` | Update user-facing documentation when code changes. Use when asked to update docs, review docs, handle documentation changes, run scheduled documentation tasks, or analyze recent commits for documentation needs. |
| `documd-visuals` | Create text-driven visuals in Markdown: charts, diagrams, cards, architecture and page layouts. Charts and analytical views (bar, line, pie, heatmap, correlation, regression); reliability and operations (latency, incident, throughput, cycl… |
| `documentation-and-adrs` | Records decisions and documentation. Use when you need to document an architecture decision (ADR) or the reasoning behind a design choice, when changing public APIs, shipping features, or when you need to record context that future enginee… |
| `domain-modeling` | Build and sharpen a project's domain model. Use when discussing codebase terminology, writing or editing a GLOSSARY.md, or recording or editing an ADR. |
| `doubt-driven-development` | Subjects every non-trivial decision to a fresh-context adversarial review before it stands. Use when you want every assumption cross-examined before proceeding, when stress-testing a plan for hidden failure modes, when correctness matters… |
| `draw-ui` | 设计 App、后台、游戏界面和完整网站落地页，生成 UI 设计稿；按需把设计稿或截图还原为 HTML/CSS、现有前端项目或微信小程序。用户要求页面设计、完整落地页、截图还原或小程序高保真时使用。不用于普通插画、海报、故事板、纯图片编辑或无设计任务的业务代码修改。 |
| `emil-design-eng` | This skill encodes Emil Kowalski's philosophy on UI polish, component design, animation decisions, and the invisible details that make software feel great. |
| `esm` | Uses the Biohub esm Python SDK for ESM3 protein generation, ESMC embeddings, and ESMFold2 all-atom folding. Applies to local model inference and Biohub hosted clients, including former Forge workflows; distinguishes the separate legacy fai… |
| `etetoolkit` | Analyzes, manipulates, compares, annotates, and visualizes phylogenetic or other hierarchical trees with ETE 4. Supports Newick/Nexus tree I/O, topology edits and pattern matching, Robinson-Foulds comparisons, gene-tree evolutionary events… |
| `exa-search` | Searches scientific and technical web content with Exa and extracts page or PDF text from URLs in batches. Supports scholarly discovery with the publication category and academic domain filters. Applies to requests to search the web, look… |
| `experimental-design` | Designs experiments and studies BEFORE data is collected — choosing a design, randomizing, blocking, and laying out treatment combinations so results are interpretable. Use whenever someone is planning a study, asks how to assign subjects/… |
| `exploratory-data-analysis` | Performs bounded, local exploratory analysis of explicitly supported scientific files. Supports redacted CSV/TSV/JSON profiles; optional NumPy, HDF5, FASTA/FASTQ, and basic image metadata inspection; missingness/leakage audits; outlier and… |
| `fictiv` | Operates Fictiv (app.fictiv.com), the on-demand manufacturing platform, end to end in the user's browser. Covers uploading CAD parts, configuring process, material, finish, threads, tolerances and inspections, getting instant or manual quo… |
| `find-skills` | Helps users discover and install agent skills when they ask questions like "how do I do X", "find a skill for X", "is there a skill that can...", or express interest in extending capabilities. This skill should be used when the user is loo… |
| `flowio` | Reads, inspects, and writes Flow Cytometry Standard (FCS) 2.0, 3.0, and 3.1 files with FlowIO. Use for low-level FCS metadata and channel inspection, NumPy event extraction, multi-dataset files, table export, and FCS 3.1 creation; use Flow… |
| `flowkit` | Analyzes flow cytometry data with FlowKit, including spillover compensation, logicle and biexponential transforms, hierarchical gating, GatingML strategies, and supported FlowJo 10 workspaces. Use for reproducible gate counts, population p… |
| `fluidsim` | Plans, configures, inspects, restarts, and analyzes bounded FluidSim computational-fluid-dynamics simulations with explicit numerical-validity and HPC safety checks. Use for FluidSim solver selection, parameter review, FFT/MPI setup, outpu… |
| `folklore-variant-evidence` | Retrieves ClinGen gene-disease validity assertions for a public gene or disease, and reviews source-linked public evidence and literature for one supported GRCh38 germline nuclear SNV or simple indel through Folklore Clinical Variant Inter… |
| `frontend-ui-engineering` | Builds production-quality, accessible, responsive user-facing UIs. Use when building or modifying interfaces and pages, creating components, implementing layouts, meeting WCAG accessibility requirements, managing state, or when the output… |
| `full-output-enforcement` | Overrides default LLM truncation behavior. Enforces complete code generation, bans placeholder patterns, and handles token-limit splits cleanly. Apply to any task requiring exhaustive, unabridged output. |
| `generate-image` | Generates or edits images with AI models through the OpenRouter Image API (Gemini, Seedream, Recraft, GPT-Image, Riverflow). Use for photos, illustrations, artwork, concept art, visual assets, logos, and image editing or compositing from r… |
| `geniml` | Supports audited local Geniml genomic-interval workflows: validate BED and universe contracts, plan Region2Vec or scEmbed runs, inspect model/tokenizer compatibility, and assess consensus universes. |
| `genomic-coordinates` | Converts genomic intervals between coordinate conventions, normalises and compares variant representations, and detects assembly or contig-naming mismatches before they corrupt an analysis. Used whenever coordinates cross a format, tool, o… |
| `genomic-intelligence` | Predicts regulatory features, gene structure, and expression directly from DNA sequence using Genomic Intelligence's hosted transformer DNA language models — no local GPU or model weights. Six tasks over a REST API and a hosted MCP server… |
| `geomaster` | Supports geospatial research workflows for remote sensing, vector and raster GIS, spatial statistics, terrain and network analysis, and machine learning for Earth observation. Use when processing satellite imagery, aligning coordinate syst… |
| `geopandas` | Guidance and local audit tools for Python workflows that directly use GeoPandas GeoSeries, GeoDataFrame, spatial operations, or vector-data I/O. |
| `get-available-resources` | Detects host inventory and effective CPU, memory, disk, scheduler, container, and accelerator limits when a user asks for resource-aware planning or before a clearly resource-sensitive local workload. Produces a redacted JSON snapshot and… |
| `gget` | Queries 20+ bioinformatics resources through CLI/Python. Supports quick lookups of gene info, BLAST/BLAT, viral sequence downloads, PDB/mmCIF structures, G2P residue annotations, enrichment analysis, OpenTargets, COSMIC, CELLxGENE, and 8cu… |
| `ginkgo-cloud-lab` | Guides protocol selection, input preparation, pricing checks, and browser ordering on Ginkgo Bioworks Cloud Lab (cloud.ginkgo.bio). Applies to cell-free, E. coli, and Pichia protein expression; HiBiT, A280, and LabChip readouts; IVT mRNA/c… |
| `git-guardrails-claude-code` | Set up Claude Code hooks to block dangerous git commands (push, reset --hard, clean, branch -D, etc.) before they execute. Use when user wants to prevent destructive git operations, add git safety hooks, or block git push/reset in Claude C… |
| `git-workflow-and-versioning` | Structures git workflow practices. Use when making any code change. Use when committing, branching, resolving conflicts, splitting uncommitted work in a messy working tree into clean atomic commits, opening or reviewing a pull request (PR)… |
| `github-bug-report-triage` | Triage GitHub bug reports for actionability. Use when evaluating whether a bug issue has sufficient detail and identifying missing information from the reporter. |
| `github-issue-dedupe` | Detect duplicate GitHub issues using semantic search and keyword matching. Use when asked to find duplicates, check for similar issues, or set up automated duplicate detection. |
| `glycoengineering` | Analyzes and engineers protein glycosylation by scanning canonical N-glycosylation sequons, describing S/T-rich regions, checking curated glycan evidence, and preparing NetNGlyc, NetOGlyc and GlycoSHIELD workflows. Use for glycoprotein eng… |
| `gpt-taste` | Elite UX/UI & Advanced GSAP Motion Engineer. Enforces Python-driven true randomization for layout variance, strict AIDA page structure, wide editorial typography (bans 6-line wraps), gapless bento grids, strict GSAP ScrollTriggers (pinning… |
| `grill-me` | A relentless interview to sharpen a plan or design. |
| `grill-with-docs` | A relentless interview to sharpen a plan or design, which also creates docs (ADR's and glossary) as we go. |
| `grilling` | Grill the user relentlessly about a plan, decision, or idea. Use when the user wants to stress-test their thinking, or uses any 'grill' trigger phrases. |
| `gtars` | Supports Gtars for local genomic interval models and set algebra, overlaps and counts, consensus and coverage, tokenization, fragment processing, and refget/BEDbase planning across Python, Rust, and the CLI. |
| `handoff` | Compact the current conversation into a handoff document for another agent to pick up. |
| `health` | Audits agent config, instruction drift, hooks or MCP, and AI maintainability. Use when Claude, Codex, or Pi setup looks wrong. Not for application bugs or PR review. |
| `high-end-visual-design` | Teaches the AI to design like a high-end agency. Defines the exact fonts, spacing, shadows, card structures, and animations that make a website feel expensive. Blocks all the common defaults that make AI designs look cheap or generic. |
| `histolab` | Extracts and preprocesses whole-slide histology image tiles with Histolab. Use for WSI inspection, tissue masks, random/grid/score-based tile extraction, H&E stain normalization, and tile dataset preparation. For multiplexed imaging or dee… |
| `hugging-science` | Discovers and evaluates scientific datasets, models, methodology posts, and Spaces through the Hugging Science catalog. Used when selecting scientific ML resources in biology, chemistry, genomics, materials, climate, physics, astronomy, me… |
| `hunt` | Finds root cause before any fix. Use when something errors, crashes, regresses, or used to work. Not for code review or new features. |
| `hypogenic` | Plans and audits use of ChicagoHAI HypoGeniC/HypoRefine for LLM-assisted hypothesis generation from labeled text datasets. Use for the `hypogenic` package, its task configs, hypothesis banks, or HypoBench datasets—not for manual hypothesis… |
| `hypothesis-generation` | Formulates evidence-bounded scientific questions, candidate hypotheses, rival explanations, causal or associational claims, discriminating predictions, measurements, and preregistration-ready analysis plans. Used when turning observations… |
| `idea-refine` | Refines raw ideas into sharp, actionable concepts through structured divergent and convergent thinking. Use when an idea is still vague, when you need to stress-test assumptions before committing to a plan, or when you want to expand optio… |
| `image-to-code` | Elite website image-to-code skill for Codex. For visually important web tasks, it must first generate the design image(s) itself, deeply analyze them, then implement the website to match them as closely as possible. In Codex, it must prefe… |
| `imagegen-frontend-mobile` | Elite mobile app image-generation skill for creating premium, app-native screen concepts and flows. Designed for iOS, Android, and cross-platform mobile products. Prioritizes clean hierarchy, comfortably readable text, strong multi-screen… |
| `imagegen-frontend-web` | Elite frontend image-direction skill for generating premium, conversion-aware website design references. CRITICAL OUTPUT RULE — generate ONE separate horizontal image FOR EVERY section. A landing page with 8 sections produces 8 images. Nev… |
| `imaging-data-commons` | Queries and downloads public cancer imaging data from NCI Imaging Data Commons. Supports IDC collection discovery, DICOM access, radiology (CT, MR, PET) and pathology AI datasets, metadata SQL, visualization, licensing, and citations. Uses… |
| `impeccable` | Use when the user wants to design, redesign, shape, critique, audit, polish, clarify, distill, harden, optimize, adapt, animate, colorize, extract, or otherwise improve a frontend interface. Covers websites, landing pages, dashboards, prod… |
| `implement` | Implement a piece of work based on a spec or set of tickets. |
| `implement-spec` | Implement the result of /to-spec and /to-tickets in code. |
| `improve-codebase-architecture` | Scan a codebase for deepening opportunities, present them as a visual HTML report, then grill through whichever one you pick. |
| `incremental-implementation` | Delivers changes incrementally in thin, verifiable slices. Use when implementing any feature or change that touches more than one file, or when picking up the next task from a plan. Use when rolling a change out behind a feature flag, when… |
| `industrial-brutalist-ui` | Raw mechanical interfaces fusing Swiss typographic print with military terminal aesthetics. Rigid grids, extreme type scale contrast, utilitarian color, analog degradation effects. For data-heavy dashboards, portfolios, or editorial sites… |
| `infographics` | Creates and reviews infographics with Nano Banana 2 via OpenRouter. Use for statistical summaries, timelines, comparisons, processes, and visual explanations with supplied data or optional Sonar research. Supports ten layouts, eight style… |
| `interview-me` | Extracts what the user actually wants instead of what they think they should want. Achieves this through one-question-at-a-time interview until ~95% confidence about the underlying intent. Use when an ask is underspecified ("build me X" wi… |
| `iso-standards-readiness` | Prepares and structurally reviews readiness evidence for ISO management-system and laboratory-competence standards - ISO 13485 medical device QMS, ISO 14971 device risk management, ISO/IEC 17025 testing and calibration laboratories, and IS… |
| `kami` | Typeset professional documents with Kami templates: resumes, one-pagers, white papers, letters, portfolios, and slide decks. Use when asked to 做 PDF / 排版 / 简历 / 一页纸 / PPT / slides, or to create a Kami landing page. Not for auditing or rest… |
| `lab-hardware-cad` | Designs custom laboratory hardware as parametric build123d models and exports fabrication artifacts as STEP, STL, and DXF files - microfluidic chips and molds, optomechanical mounts and breadboard adapters, cuvette and microplate holders,… |
| `labarchive-integration` | Integrates with the official LabArchives ELN REST-like API and Inventory API v1. Supports regional endpoint selection, signed-request construction, user authorization and UID flows, local LA container validation, and verified LabArchives i… |
| `lamindb` | Manages biological datasets and models with LaminDB, including artifact registration, lineage tracking, schema validation, Bionty ontology annotation, query/search, collections, branches, storage, and workflow integrations. Use for reprodu… |
| `latchbio-integration` | Builds, registers, debugs, and operates bioinformatics workflows on Latch using the Python SDK, CLI, Latch Data and Registry, Nextflow, Snakemake, programmatic execution, and Latch MCP. Use when authoring or deploying Latch workflows, conf… |
| `latex-posters` | Creates research posters in LaTeX using beamerposter, tikzposter, or baposter. Use for conference posters, academic presentations, multi-column scientific layouts, figure integration, typography, compilation, and PDF preflight. |
| `learn` | Runs a six-phase research workflow from source bundle to publish-ready output. Use when researching an unfamiliar domain or compiling materials into one reference. Not for quick lookups or single-file reads. |
| `liteparse` | Local document and PDF parsing that returns spatial text with bounding boxes. Use for extracting text from PDFs, DOCX, Office files, and images; running OCR on scans; producing layout-preserved JSON for RAG; batch-ingesting folders of pape… |
| `literature-review` | Conducts systematic, scoping, and narrative literature reviews using PubMed, arXiv, bioRxiv, Semantic Scholar, and other appropriate sources. Use for research synthesis, reproducible literature searches, screening, citation checking, or pr… |
| `loop-me` | Grill me about specs for the workflows I want to build, within this workspace. |
| `mageck` | Analyzes pooled CRISPR screen FASTQ reads and guide-count matrices with MAGeCK, validates guide libraries and contrasts, measures replicate and library QC, and produces gene hit rankings with effect sizes and FDR. Use for new knockout, CRI… |
| `marine-carbonate-chemistry` | Solves seawater carbonate chemistry with PyCO2SYS for chemical oceanography, ocean acidification, and marine carbon-cycle research. Use for paired total alkalinity, dissolved inorganic carbon, pH, or seawater pCO2/fCO2 measurements; carbon… |
| `markdown-mermaid-writing` | Writes scientific Markdown documentation and Mermaid diagrams for workflows, relationships, timelines, and schemas. Provides syntax references, document templates, accessibility guidance, and version-aware rendering checks. Use when a user… |
| `market-research-reports` | Builds evidence-traceable market research reports and assumption-driven market sizing or forecast scenarios. Use for market definition, industry and customer evidence, competitive landscapes, TAM/SAM/SOM reconciliation, forecast sensitivit… |
| `markitdown` | Converts heterogeneous documents and selected URIs to Markdown with Microsoft MarkItDown for text analysis, search, and LLM/RAG ingestion. Covers safe local conversion, streams, Office/PDF/data formats, batch workflows, plugins, vision OCR… |
| `matchms` | Processes, cleans, compares, and searches tandem mass spectra with matchms. Use for MS/MS file I/O, metadata harmonization, peak filtering, spectral similarity, library matching, score matrices, and molecular-similarity networks. Use pyope… |
| `matlab` | Builds, reviews, migrates, and plans MATLAB or GNU Octave numerical workflows. Use for arrays, tabular/time data, tests, projects, graphics, MAT files, and explicit Python interoperability. |
| `matplotlib` | Creates and customizes scientific plots with Matplotlib. Used for fine-grained control over plot elements, novel plot types, and scientific workflows. Export to PNG/PDF/SVG for publication. For quick statistical plots use seaborn; for inte… |
| `mcp-builder` | Build high-quality MCP (Model Context Protocol) servers that let LLMs interact with external services through well-designed tools. Use when creating MCP servers to integrate APIs or services in Python (FastMCP) or Node/TypeScript (MCP SDK). |
| `medchem` | Applies medicinal chemistry filters for compound triage, using drug-likeness rules (Lipinski, Veber, CNS), structural alert catalogs (PAINS, NIBR, ChEMBL), complexity metrics, and the medchem query language for library filtering. |
| `memory-leak-debugging` | Diagnoses and resolves memory leaks in JavaScript/Node.js applications. Use when a user reports high memory usage, OOM errors, or wants to capture, compare, or inspect heap snapshots with Chrome DevTools MCP memory tools. |
| `migrate-to-shoehorn` | Migrate test files from `as` type assertions to @total-typescript/shoehorn. Use when user mentions shoehorn, wants to replace `as` in tests, or needs partial test data. |
| `minimalist-ui` | Clean editorial-style interfaces. Warm monochrome palette, typographic contrast, flat bento grids, muted pastels. No gradients, no heavy shadows. |
| `modal` | Modal is a serverless cloud platform for running Python on demand, including on-demand GPUs. Use when deploying or serving AI/ML models, running GPU-accelerated workloads (training, fine-tuning, inference), serving web endpoints, schedulin… |
| `molecular-dynamics` | Runs and analyzes molecular dynamics simulations with OpenMM and MDAnalysis. Sets up protein/small molecule systems, defines force fields, runs energy minimization and production MD, and analyzes trajectories (RMSD, RMSF, contact maps, fre… |
| `molfeat` | Featurizes small molecules with Molfeat for QSAR/QSPR, chemical similarity, virtual screening, and molecular ML. Covers ECFP/MACCS fingerprints, RDKit descriptors, pharmacophores, pretrained embeddings, configuration persistence, and molec… |
| `motion-spec` | Turn vague motion requests ("make it smoother / more premium / add some animation / 丝滑一点 / 高级感 / 动效") into a concrete, reference-grounded spec BEFORE writing animation code, then implement and verify it against a measured baseline. Use thi… |
| `nature-academic-search` | Search literature across sources, verify or manage citations, and build MeSH strategies or citation-impact audits. Use for 文献检索、引文核对、参考文献管理、严格他引 and evidence-backed citer profiles; not for translating a paper or drafting manuscript prose. |
| `nature-citation` | Find and verify Nature/CNS-family literature supporting manuscript claims, with claim-to-source mapping and reference-manager export. Use for Nature系列引用、CNS支撑文献、分段补引用 when this journal scope is requested; use broader literature search for… |
| `nature-data` | Draft or audit manuscript Data/Code Availability statements, dataset access routes, repository plans, and FAIR metadata. Use for 数据可用性声明、数据共享、数据仓库选择 and dataset citations; not general data cleaning or statistical analysis. |
| `nature-downloader` | Use when a user needs lawful academic full text, CNKI institutional access, English OA retrieval, publisher API access, institutional browser fallback, or supporting information downloads. |
| `nature-experiment-log` | 标准化实验日志记录——直接上传或读取本地图片、语音和文字，产出带 YAML frontmatter 的 Markdown；可选集成飞书 CLI 与 Obsidian。 |
| `nature-figure` | Create, revise, audit, and export manuscript scientific figures in Python or R. Use for 论文配图、科研绘图、多面板图 and submission-ready plots, or explicitly requested AI-generated graphical abstracts and mechanism schematics. Not for interactive dashb… |
| `nature-literature-pipeline` | Complete automated literature discovery pipeline: multi-source search → six-dimension scoring → fine reading → formatted delivery → archival. Combines a configurable engine with daily cron-driven application layer. Works with Feishu, Teleg… |
| `nature-paper-to-patent` | Turn research papers or inventor materials into evidence-grounded Chinese invention patent drafts and technical disclosures. Use for 技术交底书、专利撰写、现有技术对比 and Chinese DOCX patent packages; not general manuscript writing. |
| `nature-paper2ppt` | Create or improve a Chinese academic PPTX from a scientific paper or research reading notes, with source figures and speaker notes. Use for 论文做PPT、文献汇报、组会PPT and paper-based conference or defense presentations. |
| `nature-polishing` | Polish, translate, or tighten existing academic prose while preserving facts, terminology, and evidence boundaries. Use for 论文润色、学术翻译、正文精简, or manuscript LaTeX layout fixes. Use nature-writing when the main task is drafting new sections or… |
| `nature-reader` | Create source-grounded Chinese-English paper readers with aligned text, figures, tables, and equations. Use for 全文翻译、中英文对照、论文精读 or source-linked questions about a paper; respect a requested excerpt or question without generating a full rea… |
| `nature-response` | Draft, audit, or revise responses to peer review, revision cover letters, and marked-manuscript or LaTeX revision packages. Use for 审稿意见回复、逐点回复、返修信、 rebuttals and edits to existing response drafts. Initial-submission materials belong to na… |
| `nature-reviewer` | Provide evidence-grounded mock peer review of scientific manuscripts or excerpts, covering significance, validity, and major/minor concerns. Use for 模拟审稿、投稿前自审、审稿人视角评估; not author rebuttal drafting. |
| `nature-writing` | Draft or restructure scientific manuscript arguments, sections, and initial-submission materials from author-provided evidence. Use for 论文写作、章节起草、论证重构、正文压缩、首次投稿材料. Use nature-polishing for language-only edits to existing prose and nature-r… |
| `ncats-arax` | Queries the NCATS Translator ARAX production API for bounded, typed, provenance-rich one-hop and endpoint-pinned two-hop biomedical knowledge-graph relationships. Use for Biolink-constrained RTX-KG2 lookup, explicit selected-provider ARAX… |
| `networkx` | Creates, analyzes, and visualizes complex networks and graphs in Python with NetworkX. Use when working with network/graph data structures, computing graph algorithms (shortest paths, centrality, clustering), detecting communities, generat… |
| `neurokit2` | Builds and audits reproducible NeuroKit2 research workflows for physiological time-series preprocessing, event/interval analysis, multimodal alignment, variability, and complexity. Use when code imports neurokit2 or needs its current APIs,… |
| `neuropixels-analysis` | Analyzes Neuropixels extracellular recordings end-to-end with SpikeInterface. Covers loading SpikeGLX/Open Ephys/NWB data, preprocessing, drift/motion correction, Kilosort4 (and CPU) spike sorting, quality metrics, and unit curation (thres… |
| `nextflow` | Builds, runs, and debugs Nextflow DSL2 pipelines and nf-core workflows. Use for Nextflow, nf-core, .nf files, nextflow.config, processes/channels/operators, samplesheets, nf-test, modules/subworkflows, container and executor configuration,… |
| `nmrglue` | Processes calibrated one-dimensional complex NMR free-induction decays with nmrglue into phased spectra, peak candidates, and signed integration regions. Use for raw 1D NMR processing, ppm-axis verification, apodization, Fourier transforma… |
| `nwb-conversion` | Converts neuroscience acquisition data to Neurodata Without Borders files with NeuroConv and PyNWB, preserves metadata and timebases, checks evidence-based clock alignment, and produces schema validation, NWB Inspector findings and round-t… |
| `observability-and-instrumentation` | Instruments code so production behavior is visible and diagnosable. Use when adding logging, metrics, tracing, or alerting. Use when shipping any feature that runs in production and you need evidence it works. Use when production issues ar… |
| `oil-ui` | Design, improve, and review interfaces for websites, apps, dashboards, and components: explore distinct design directions, compare styles side by side, and refine visual hierarchy against real screenshots. Delivers comparison pages, design… |
| `oiloil-ui-ux-guide` | 梳理项目 UI/UX 设计规范并输出 design-spec.md，评审现有界面并给出按优先级排序的修复建议，或整理指定界面的设计规则。用户要求设计系统、设计规范或完整界面评审时使用；单个颜色、字号或按钮大小的简单问题直接回答。 |
| `omero-integration` | Inspects and automates microscopy data workflows against OMERO.server with omero-py, BlitzGateway, OMERO CLI, tables, annotations, ROIs, rendering, and documented OMERO.web APIs. Use this skill for scoped OMERO inventory, metadata export,… |
| `onekgpd` | Queries the 1000 Genomes Project dataset (3,202 whole-genome-sequenced individuals, GRCh38) at the level of individual participants. Use when a question is about individuals or variants in the 1000 Genomes Project cohort: which individuals… |
| `ontology-term-resolution` | Resolves free-text scientific labels to ontology term IDs and validates existing CURIEs against the EBI Ontology Lookup Service (OLS4). Also looks up prefixes in Bioregistry, resolves compact identifiers via Identifiers.org, maps lab short… |
| `open-notebook` | Organizes research with the self-hosted Open Notebook alternative to NotebookLM. Supports source ingestion (PDFs, web pages, audio, video, and Office documents), cited document chat, text and vector search, notes, custom transformations, a… |
| `open-source` | Documentation reference for writing Python code using the browser-use open-source library. Use this skill whenever the user needs help with Agent, Browser, or Tools configuration, is writing code that imports from browser_use, asks about @… |
| `opencli-adapter-author` | Use when writing an OpenCLI adapter for a new site or adding a new command to an existing site. Guides end-to-end from first recon through field decoding, adapter coding, and verify. Replaces opencli-oneshot / opencli-explorer. For ad-hoc… |
| `opencli-autofix` | Automatically fix broken OpenCLI adapters when commands fail. Load this skill when an opencli command fails — it guides you through collecting a trace artifact, patching the adapter, retrying, and filing an upstream GitHub issue after a ve… |
| `opencli-browser` | Use when an agent needs to drive a real Chrome window via opencli — inspect a page, fill forms, click through logged-in flows, or extract data ad-hoc. Covers the selector-first target contract, compound form fields, stale-ref handling, net… |
| `opencli-usage` | Use at the start of any OpenCLI session — this is the top-level map of what `opencli` can do, how to discover adapters, what flags and output formats are universal, and which specialized skill to load next. Point here when an agent asks "w… |
| `openpiv` | Performs Particle Image Velocimetry (PIV) analysis with OpenPIV. Use when extracting velocity fields from PIV image pairs, analyzing fluid dynamics or flow visualization experiments, cross-correlating interrogation windows, validating and… |
| `opentrons-integration` | Authors, reviews, migrates, simulates, and troubleshoots official Opentrons Python Protocol API v2 protocols for Flex and OT-2 robots. Use for robot-specific liquid handling, deck and labware setup, pipettes, modules, runtime parameters, l… |
| `optimize-for-gpu` | GPU-accelerates scientific Python on NVIDIA hardware and verifies that the result is correct and faster. Use for CUDA/GPU optimization; CPU-bound NumPy, SciPy, pandas, scikit-learn, NetworkX, scikit-image, vector-search, image-processing,… |
| `pacsomatic` | Prepares and launches nf-core/pacsomatic matched tumor-normal PacBio HiFi genomics workflows from unaligned BAM inputs. Supports samplesheet generation, pinned Nextflow launch artifacts, local checks, LSF/Slurm/PBS Pro/SGE launcher submiss… |
| `paper-lookup` | Searches 18 scholarly APIs for papers, preprints, citations, open-access full text, repository records, and journal OA status, and returns results with reproducible provenance. Covers PubMed, PMC, Europe PMC, bioRxiv, medRxiv, arXiv, OpenA… |
| `paperclip` | Searches and reads biomedical papers, FDA/PMDA/EMA documents, clinical trials, and protein records with the GXL Paperclip CLI and Python SDK. Supports source-scoped search, full-text grep, metadata SQL, map/reduce extraction, figure analys… |
| `paperzilla` | Reads projects, searches project feeds, and retrieves recommendations and canonical papers in Paperzilla through the pz CLI. Supports recent recommendations, paper details, markdown-based summaries, recommendation feedback, JSON export, an… |
| `parallel-web` | Uses Parallel CLI for web search, URL extraction, deep research, structured data enrichment, entity discovery, and recurring web monitoring. Best for requests that explicitly need current web evidence, academic-source discovery, repeated e… |
| `pathml` | Supports local computational pathology research with PathML: slide loading and tiling, preprocessing and QC, h5path storage, multiplex quantification, spatial graphs, and bounded model inference. Use for whole-slide H&E, CODEX, Vectra, Mes… |
| `pathogen-variant-surveillance` | Queries public GenSpectrum LAPIS data for pathogen genomic surveillance, current lineage nomenclature, weekly sequence proportions, reporting delays, and descriptive mutation frequencies. Use for variant surveillance, Pango lineage validat… |
| `pathway-enrichment` | Performs pathway and gene-set enrichment analysis on gene lists or ranked gene data and interprets the results. Used when the user has a set of genes (differentially expressed genes from PyDESeq2/Scanpy, CRISPR-screen hits, cluster marker… |
| `peer-review` | Prepares evidence-bounded, constructive peer-review drafts and structured manuscript assessments. Supports authorized review of scientific manuscripts, protocols, preprints, or research proposals; reporting-guideline selection; claim–evide… |
| `pennylane` | Builds and differentiates PennyLane quantum circuits, hybrid PyTorch or JAX models, molecular VQE and QAOA workflows. Use for variational quantum algorithms, quantum machine learning, simulator validation, and moving validated circuits to… |
| `performance-optimization` | Optimizes application performance across frontend, backend, queries, and databases. Use when performance requirements exist, when you suspect performance regressions, when Core Web Vitals or load times need improvement, when N+1 query patt… |
| `phylogenetics` | Builds and analyzes phylogenetic trees using MAFFT multiple sequence alignment, IQ-TREE maximum likelihood with ModelFinder and branch support, and FastTree approximate inference. Uses ETE3 for tree summaries and visualization. Applies to… |
| `pi-agent` | Builds with and operates Pi, the minimal terminal coding harness. Use for installing Pi, configuring providers/models/settings/environment variables, creating Pi skills/extensions/packages/themes/prompt templates, embedding Pi through the… |
| `pkpd-modeling` | Pharmacokinetic and pharmacodynamic modelling and simulation - non-compartmental analysis, compartmental and population PK, PK/PD and exposure-response, TMDD, PBPK orientation, bioequivalence, allometric scaling and first-in-human dose, dr… |
| `planning-and-task-breakdown` | Breaks work into ordered tasks. Use when you have a spec or clear requirements and need to break work into implementable tasks. Use when a task feels too large to start, when you need to estimate scope, or when parallel work is possible. |
| `polars` | High-performance DataFrame library for Python ETL, analytics, and pandas migration. It supports expression-based data manipulation with lazy query optimization, parallel execution, streaming out-of-core processing, Arrow interoperability,… |
| `polars-bio` | Performs genomic interval overlap, nearest, merge, coverage, complement and subtraction on Polars DataFrames, and reads or writes BED, VCF, BCF, BAM, CRAM, GFF, GTF, FASTA and FASTQ data. Use for coordinate-aware genomic joins, read-depth… |
| `pptx-posters` | Creates and audits editable scientific posters in macro-free PowerPoint (.pptx) from author-approved local content and assets. Used when the requested deliverable is a PowerPoint research/conference poster and exact physical, printer, acce… |
| `pr` | Use when writing a PR body. |
| `primekg` | Queries a pinned Precision Medicine Knowledge Graph (PrimeKG) CSV for typed gene, drug, disease, and phenotype nodes, direct associations, disease context, and one- or two-hop paths. Use for PrimeKG reproducibility, biological association… |
| `primer-design` | Designs and audits PCR and RT-qPCR primers with Primer3, explicit thermodynamic conditions, reference-based off-target amplification searches, and traceable sequence coordinates. Use for designing primer pairs, checking existing primers, e… |
| `problem-report` | Diagnose a problem with evidence and then interrogate it in rounds — a code-anchored report (前情 / 代码 / 取证 / 结论 / 未确定 / 提问) followed by numbered questions, each carrying a discriminating axis and a recommended answer, until nothing is left… |
| `protocolsio-integration` | Reads, validates, and safely exports protocols.io data with current official REST/MCP contracts, or creates non-executing mutation plans. The bundled client makes bounded official-host GET requests only with explicit --execute. Use only fo… |
| `prototype` | Build a throwaway prototype to answer a design question. Use when the user wants to sanity-check whether a state model or logic feels right, or explore what a UI should look like. |
| `pufferlib` | Version-aware guidance for PufferLib reinforcement-learning environments, vectorization, policies, PuffeRL training, evaluation, and safe checkpoint review. Covers the native 5.0 build and environment API, published 3.0.0 Gymnasium/Petting… |
| `pybamm` | Simulates lithium-ion battery charge, discharge and rest experiments with PyBaMM, records parameter-set provenance, checks mesh and solver sensitivity, and compares predicted voltage curves with measured cycling data. Use for SPM or DFN el… |
| `pycalphad` | Computes finite-temperature CALPHAD equilibria, phase fractions, and phase compositions from thermodynamic TDB databases using pycalphad. Use for alloy phase stability, equilibrium temperature sweeps, tie lines, lever-rule checks, or repro… |
| `pydeseq2` | Performs bulk RNA-seq differential expression analysis with PyDESeq2, including count validation, formula designs, explicit contrasts, Wald tests, FDR correction, coefficient-matched LFC shrinkage, and result visualization. Use for PyDESeq… |
| `pydicom` | Reads, inspects, writes, transforms, and preflights local DICOM datasets and pixel data. Applies to DICOM metadata, transfer syntaxes, compression plugins, frames, private elements, JSON, and bounded de-identification review. |
| `pyhealth` | Builds and validates PyHealth clinical machine-learning pipelines for EHR, signals, imaging, and medical codes. Use for PyHealth dataset loading, MIMIC-III/IV, eICU or OMOP prediction tasks, patient-level evaluation, mortality/readmission/… |
| `pylabrobot` | Develops and reviews PyLabRobot lab-automation resources, liquid-handling plans, offline simulations, and supported-device integrations. Supports PyLabRobot protocols and API questions; keep physical execution behind an explicit operator s… |
| `pymatgen` | Analyzes, validates, converts, and transforms materials structures and computed materials data with pymatgen. Use for local phase diagrams, symmetry sensitivity, electronic-structure I/O, and bounded Materials Project queries. |
| `pymc` | Builds and checks Bayesian models with PyMC, including hierarchical models, NUTS MCMC, variational inference, mutable-data predictions, posterior predictive checks, diagnostics, and PSIS-LOO model comparison. Use for probabilistic modeling… |
| `pymoo` | Solves and validates single-, multi-, and many-objective optimization with pymoo, including NSGA-II, NSGA-III, MOEA/D, constraints, Pareto approximations, reference directions, and ZDT/DTLZ benchmarks for engineering and research problems. |
| `pyopenms` | Processes mass spectrometry data with pyOpenMS. Supports proteomics and metabolomics workflows—feature detection, peptide/protein identification, label-free quantification, adduct/accurate-mass annotation, and complex LC-MS/MS pipelines. S… |
| `pysam` | Provides Python/HTSlib workflows for genomic files. Used when reading, querying, filtering, or writing SAM/BAM/CRAM, VCF/BCF, FASTA/FASTQ, or tabix data with pysam, including pileup, coverage, indexing, and CRAM references. |
| `pytdc` | Provides Therapeutics Data Commons workflows through PyTDC for registry discovery, dataset access, task-aware splits, evaluator metrics, benchmark groups, and bounded molecular-oracle scoring. Use when working with TDC therapeutic ML datas… |
| `pytorch-lightning` | Deep learning framework (PyTorch Lightning / lightning package). Organize PyTorch code into LightningModules, configure Trainers for multi-GPU/TPU, implement data pipelines, callbacks, logging (W&B, TensorBoard, MLflow), distributed traini… |
| `pyzotero` | Manages Zotero reference libraries using the pyzotero Python client: retrieves, creates, updates, and deletes items, collections, tags, and attachments via the Zotero Web API v3 or local API. Applies when working with Zotero libraries prog… |
| `qa` | QA-test a website or web app and return a 1-5 quality score (5 = flawless, 1 = broken) with evidence. Use when the user wants to test, QA, evaluate, score, or "check how good" a site, page, flow, or app — including a local dev server (e.g.… |
| `qiime2-amplicon` | Processes paired-end 16S amplicon reads into QIIME 2 ASVs and taxonomy with retained artifact provenance. Checks paired FASTQ manifests, primer orientation diagnostics, predicted post-trimming overlap, sample IDs, runtime versions and read… |
| `qiskit` | Builds, simulates, transpiles, and executes quantum circuits with Qiskit and IBM Quantum Runtime. Use for Qiskit 2.x circuits and operators, V2 Sampler or Estimator primitives, target-aware transpilation, local or noisy simulation, IBM QPU… |
| `qutip` | Simulate and audit closed and open quantum-system models with QuTiP 5, including deterministic, trajectory, steady-state, spectral, and phase-space workflows. Use for local quantum-dynamics work where physical assumptions, dimensions, and… |
| `rdkit` | Cheminformatics toolkit for fine-grained molecular control. SMILES/SDF parsing, descriptors (MW, LogP, TPSA), fingerprints, substructure search, 2D/3D generation, similarity, reactions. For standard workflows with simpler interface, use da… |
| `rea-changelog-update` | Prepare or rewrite REA release changelogs and GitHub release notes from pinned Git history, with verified contributor thanks and Release Please synchronization. |
| `rea-tool-design` | Design or change REA investigation tools, CLI/MCP contracts, provider capabilities, and Evidence semantics. |
| `read` | Fetches URLs and PDFs, then summarizes or returns clean Markdown. Use when asked to read, fetch, quote, cite, convert, or save a URL or PDF. Not for local text files already in the repo. |
| `redesign-existing-projects` | Upgrades existing websites and apps to premium quality. Audits current design, identifies generic AI patterns, and applies high-end design standards without breaking functionality. Works with any CSS framework or vanilla CSS. |
| `relion` | Validates and executes RELION single-particle cryo-EM refinement and half-map postprocessing. Supports STAR optics/acquisition checks, particle-stack consistency, gold-standard half sets, soft-mask validation, diagnostic Fourier shell corr… |
| `relsa-severity-assessment` | Supports multivariate severity assessment and exploratory endpoint-time score forecasting for laboratory animal studies using the RELSA (RELative Severity Assessment) score and ARIMA-based foRcast forecasting. Use when combining welfare re… |
| `remote-browser` | Controls an isolated Browser Use Cloud browser from a sandboxed machine with the current Browser Use CLI. |
| `research` | Investigate a question against high-trust primary sources and capture the findings as a Markdown file in the repo. Use when the user wants a topic researched, docs or API facts gathered, or reading legwork delegated to a background agent. |
| `research-grants` | Supports research proposal preparation and review for NSF, NIH, DOE, DARPA, and Taiwan NSTC, including opportunity-specific requirements, aims, review criteria, budgets, broader impacts, forms, and resubmissions. Use for investigator-autho… |
| `research-lookup` | Compiles current scholarly evidence for a scientific manuscript or research brief when the user explicitly asks to gather literature, references, background evidence, competing findings, or a manuscript research packet. Uses Parallel Searc… |
| `researchwrite` | Compose, revise, or audit research proposals, opening reports, and research plans from supporting evidence. Use for 研究计划、开题报告、科研项目申请书. Invoked as researchwrite for compatibility; use nature-writing for general manuscript sections. |
| `retro` | Conduct a retrospective on a coding session. |
| `reverse-engineer-anything` | Reverse engineer native, managed, Electron/JavaScript, packaged, firmware, and browser targets with REA. Use shipped-artifact or requested runtime evidence to explain features, compare versions, decompile code, or guide a reconstruction. S… |
| `review-animations` | Reviews animation and motion code against a high craft bar derived from Emil Kowalski's design engineering philosophy. Default to flagging; approval is earned. |
| `rowan` | Rowan is a cloud-native molecular modeling and medicinal-chemistry workflow platform with a Python API. Use for pKa and macropKa prediction, conformer and tautomer ensembles, docking and analogue docking, protein-ligand cofolding, MSA gene… |
| `scaffold-exercises` | Create exercise directory structures with sections, problems, solutions, and explainers that pass linting. Use when user wants to scaffold exercises, create exercise stubs, or set up a new course section. |
| `scanpy` | Performs Scanpy single-cell RNA-seq QC, normalization, HVG selection, PCA/UMAP/t-SNE, clustering, exploratory marker ranking, pseudobulk preparation, visualization, and Seurat or SingleCellExperiment RDS conversion to h5ad. Applies to esta… |
| `scheduler` | Schedule on-device reminders and local actions only. Use this skill to set personal reminders or run lightweight, local tasks at a specific time or interval (e.g., notifications, local scripts), on the user's computer or with platforms lik… |
| `scholar-evaluation` | Provides qualitative-first, evidence-traceable developmental review of scholarly works and audit low-stakes research-assessment rubrics with optional local quality controls. Never use for ranking people or consequential decisions. |
| `scientific-brainstorming` | Facilitates evidence-aware scientific ideation with independent generation, structured discussion, explicit assumptions, transparent evaluation, adversarial review, and decision logs. Use for early-stage research brainstorming or prioritiz… |
| `scientific-critical-thinking` | Evaluates scientific claims and evidence quality. Applies to experimental design validity, biases and confounders, statistical interpretation, evidence grading frameworks (GRADE, Cochrane Risk of Bias), and teaching critical analysis. Supp… |
| `scientific-schematics` | Generates scientific diagram drafts using Nano Banana 2 AI with smart iterative refinement. Uses Gemini 3.7 Flash for quality review. Refines when the review requests improvement, with at most two generations. Specialized in neural network… |
| `scientific-slides` | Builds slide decks and presentations for research talks. Used for making PowerPoint slides, conference presentations, seminar talks, research presentations, thesis defense slides, or any scientific talk. Provides slide structure, design te… |
| `scientific-visualization` | Creates and audits truthful, accessible, publication-ready scientific figures with Matplotlib, Seaborn, or Plotly. Use it for figure design, multi-panel layouts, uncertainty and missing-data displays, color/contrast review, image metadata… |
| `scientific-writing` | Drafts, revises, and audits scientific manuscripts or reports with explicit evidence provenance, reporting-guideline coverage, authorship accountability, confidentiality controls, and local consistency checks. Use for manuscript sections,… |
| `scikit-bio` | Biological data toolkit. Sequence analysis, alignments, phylogenetic trees, diversity metrics (alpha/beta, UniFrac), ordination (PCoA), PERMANOVA, FASTA/Newick I/O, for microbiome analysis. |
| `scikit-learn` | Supports machine learning in Python with scikit-learn. Applies when working with supervised learning (classification, regression), unsupervised learning (clustering, dimensionality reduction), model evaluation, hyperparameter tuning, prepr… |
| `scikit-survival` | Builds, evaluates, and audits right-censored or competing-risk survival workflows with scikit-survival, including leakage-safe preprocessing, model selection, probability prediction, and censoring-aware metrics. |
| `scvelo` | Performs RNA velocity analysis with scVelo from spliced and unspliced single-cell RNA counts. Fits deterministic or dynamical models, examines gene phase portraits, builds velocity graphs, estimates relative latent time, and ranks velocity… |
| `scvi-tools` | Fits probabilistic models for single-cell omics, including scVI batch integration, scANVI annotation, totalVI CITE-seq, MultiVI RNA/ATAC integration, and posterior differential expression. Use for generative modeling, reference mapping, mu… |
| `seaborn` | Creates Seaborn statistical visualizations with pandas integration for distributions, relationships, categorical comparisons, regression displays, pair plots, and heatmaps. Supports function and objects interfaces with explicit aggregation… |
| `security-and-hardening` | Hardens code against vulnerabilities. Use when auditing an input handler for vulnerabilities, when handling user input, authentication, data storage, or external integrations, or when checking a login flow is safe against the OWASP Top Ten… |
| `seo-aeo-audit` | Optimize for search engine visibility, ranking, and AI citations. Use when asked to improve SEO, optimize for search, fix meta tags, add structured data, or improve AEO and AI visibility. |
| `setup-matt-pocock-skills` | Configure this repo for the engineering skills: set up its issue tracker, triage label vocabulary, and domain doc layout. Run once before first use of the other engineering skills. |
| `setup-pre-commit` | Set up Husky pre-commit hooks with lint-staged (Prettier), type checking, and tests in the current repo. Use when user wants to add pre-commit hooks, set up Husky, configure lint-staged, or add commit-time formatting/typechecking/testing. |
| `setup-ts-deep-modules` | Wire dependency-cruiser into a TypeScript repo so each package is a deep module, with implementation hidden in subfolders and reachable only through its entry-point files. User-invoked. |
| `shap` | Explain and audit machine-learning predictions with SHAP. Use for selecting SHAP explainers and maskers, computing and validating feature attributions, handling multi-output explanations, and producing local or global SHAP visualizations. |
| `shipping-and-launch` | Prepares production launches. Use when preparing to deploy to production, or when asking what needs to be in place before shipping. Use when you need a pre-launch checklist, when setting up monitoring, when planning a staged rollout, or wh… |
| `show-me` | Help the user understand the current topic visually with concise diagrams, code-shape sketches, and focused HTML artifacts. |
| `simpy` | Builds, inspects, tests, and analyzes bounded process-based discrete-event simulations with SimPy. Use for event scheduling, resource queues, interrupts, monitoring, independent replications, warm-up, and reproducible output analysis. |
| `slack-qa-investigate` | Investigate and answer repository questions in read-only mode. Use when asked for research-backed answers that require codebase and documentation investigation without making file changes. |
| `smart-search` | 基于 opencli 命令的智能搜索路由器。当用户想要使用 OpenCLI、CLI 或 API 搜索、查询、查找或研究信息时，尤其是涉及指定网站、社交媒体、技术资料、新闻、购物、旅游、求职、金融或中文内容时，务必使用此 skill |
| `software-engineering-laws-and-philosophy` | Guides technical decisions, architecture design, planning, and team management using the 56 Laws of Software Engineering. Use when diagnosing organizational bottlenecks, reviewing system designs, estimating timelines, or addressing technic… |
| `source-driven-development` | Grounds every implementation decision in official documentation. Use when you want to verify an approach against the official docs before implementing it, or when you want authoritative, source-cited code free from outdated patterns. Use w… |
| `spec-driven-development` | Creates specs before coding. Use when starting a new project, feature, or significant change and no specification exists yet. Use when drafting a PRD or requirements document with objectives and scope, or when requirements are unclear, amb… |
| `stable-baselines3` | Trains and evaluates single-agent reinforcement learning with Stable Baselines3 (PPO, SAC, DQN, TD3, DDPG, A2C), Gymnasium custom environments, vectorized rollouts, callbacks, and checkpoint normalization. Applies to reproducible RL experi… |
| `statistical-analysis` | Guided statistical analysis for research data - test selection, assumption checking, effect sizes, power analysis, Bayesian alternatives, and APA-formatted reporting. Use whenever a user wants to compare groups, test a hypothesis, analyze… |
| `statistical-power` | Calculates sample sizes and statistical power for study planning. Applies when someone asks "how many subjects/samples/replicates do I need", wants an a priori power analysis, a minimum detectable effect (MDE), a power curve, or needs to j… |
| `statsmodels` | Fits and diagnoses Python statistical models including OLS, GLM, discrete and mixed models, ARIMA and SARIMAX. Supports coefficient inference, marginal effects, model comparison and time series forecasting with explicit design and uncertai… |
| `stitch-design-taste` | Semantic Design System Skill for Google Stitch. Generates agent-friendly DESIGN.md files that enforce premium, anti-generic UI standards — strict typography, calibrated color, asymmetric layouts, perpetual micro-motion, and hardware-accele… |
| `sympy` | Performs exact symbolic mathematics with SymPy for algebra, calculus, equation solving, symbolic linear algebra, physics, and lambdify or LaTeX code generation. Use when a task needs symbolic results, explicit assumptions, or exact arithme… |
| `tamarind` | Provides access to a collection of open-source molecular design and structural biology tools on the Tamarind Bio platform, via its REST API or MCP server — no local GPUs required. Tamarind bundles popular open-source models for structure p… |
| `tdd` | Test-driven development. Use when the user wants to build features or fix bugs test-first, mentions "red-green-refactor", or wants integration tests. |
| `tellurium` | Simulates biochemical kinetic models from SBML or Antimony with Tellurium and libRoadRunner, checks model units, compares deterministic parameter perturbations, and exports and replays SBML plus SED-ML COMBINE archives. Use for reaction-ne… |
| `terraform-style-check` | Generate Terraform HCL code following HashiCorp's official style conventions and best practices. Use when writing, reviewing, or generating Terraform configurations. |
| `think` | Turns rough ideas into approved, decision-complete plans before coding. Use when planning architecture, judging whether to build, or writing a handoff. Not for bug fixes or small edits. |
| `tiledbvcf` | Stores and retrieves genomic variant calls with TileDB-VCF. Use for indexed single-sample VCF/BCF ingestion, incremental cohorts, region and sample queries, streaming results, allele statistics, QC, and VCF/BCF export locally or through Ti… |
| `timesfm-forecasting` | Performs zero-shot time-series forecasting with Google's TimesFM, including regular-grid CSV preparation, quantile forecasts, XReg covariates, and held-out evaluation. Uses the Apache-licensed TimesFM 2.5 checkpoint by default and document… |
| `to-questionnaire` | Turn a decision you can't fully answer into a questionnaire for someone else to fill in. |
| `to-spec` | Turn the current conversation into a spec and publish it to the project issue tracker: no interview, just synthesis of what you've already discussed. |
| `to-tickets` | Break a plan, spec, or the current conversation into a set of tracer-bullet tickets, each declaring its blocking edges, published to the configured tracker (edges as text in one file per ticket locally, or native blocking links on a real t… |
| `torch-geometric` | Supports PyTorch Geometric (PyG) graph neural networks — node/link/graph classification, message passing (GCN, GAT, GraphSAGE, GIN), heterogeneous graphs, neighbor sampling, and custom datasets. Use when working with torch_geometric, not f… |
| `torchdrug` | Builds and troubleshoots TorchDrug 0.2.1 workflows for molecular graphs, property prediction, self-supervised pretraining, molecule generation, retrosynthesis, protein representation learning, and knowledge graph reasoning. Use when code i… |
| `transformers` | Hugging Face Transformers for loading Hub models, running pipeline inference, text generation, and Trainer fine-tuning on NLP, vision, audio, and multimodal tasks. Applies when working with AutoModel, pipelines, tokenizers, generation conf… |
| `treatment-plans` | Formats and structurally validates local treatment-plan documentation after clinical decisions have already been supplied and verified by authorized licensed professionals. Use for source traceability, clinician-authored intervention recor… |
| `triage` | Move issues and external PRs through a state machine of triage roles, categorise, verify, grill if needed, and write agent-ready briefs. |
| `troubleshooting` | Uses Chrome DevTools MCP and documentation to troubleshoot connection and target issues. Trigger this skill when list_pages, new_page, or navigate_page fail, or when the server initialization fails. |
| `ui` | Produces distinctive production UI and screenshot-grounded visual polish. Use when building or restyling pages, components, or typography. Not for backend logic or data pipelines. |
| `umap-learn` | Applies UMAP-learn to nonlinear dimensionality reduction, 2D/3D embeddings, clustering preprocessing, supervised or semi-supervised UMAP, DensMAP, AlignedUMAP, and Parametric UMAP workflows. |
| `uncertainty-and-units` | Tracks physical units and propagates measurement uncertainty in scientific calculations using pint and uncertainties. Use for unit conversion and dimensional checking, GUM uncertainty budgets, Type A and Type B evaluation, coverage factors… |
| `user-chrome-tabs` | Drive the user's real Chrome on this macOS machine without hijacking, mutating or leaving behind their tabs. Use this before ANY browser automation here, and whenever a task needs the user's logged-in session, a real click/type/scroll, or… |
| `usfiscaldata` | Queries the U.S. Treasury Fiscal Data REST API for federal financial data. No API key required. Use for national debt (Debt to the Penny), Daily Treasury Statements, Monthly Treasury Statements, Treasury securities auctions, interest rates… |
| `using-agent-skills` | Discovers and invokes agent skills. Use when starting a session, or when you need to decide which skill or workflow applies to the piece of work at hand. This is the meta-skill that governs how all other skills are discovered and invoked. |
| `vaex` | Processes large tabular scientific datasets with Vaex expressions, filtered views, streamed statistics, binned visualizations, and file conversion. Use for larger-than-RAM HDF5, Arrow, CSV, or Parquet analysis, virtual feature engineering,… |
| `venue-templates` | Prepares journal manuscripts, conference papers, research posters, and grant documents using venue-specific formatting guidance and bundled LaTeX scaffolds. Supports selecting an official template, checking current page or anonymity rules,… |
| `vercel-cli-with-tokens` | Deploy and manage projects on Vercel using token-based authentication. Use when working with Vercel CLI using access tokens rather than interactive login — e.g. "deploy to vercel", "set up vercel", "add environment variables to vercel". |
| `vercel-composition-patterns` | React composition patterns that scale. Use when refactoring components with |
| `vercel-optimize` | Use for Vercel cost and performance optimization on deployed projects, especially Next.js, SvelteKit, Nuxt, and limited Astro apps. Collect Vercel metrics, usage, project config, and code scan results first; investigate only metric-backed… |
| `vercel-react-best-practices` | React and Next.js performance optimization guidelines from Vercel Engineering. This skill should be used when writing, reviewing, or refactoring React/Next.js code to ensure optimal performance patterns. Triggers on tasks involving React c… |
| `vercel-react-native-skills` | React Native and Expo best practices for building performant mobile apps. Use |
| `vercel-react-view-transitions` | Guide for implementing smooth, native-feeling animations using React's View Transition API (`<ViewTransition>` component, `addTransitionType`, and CSS view transition pseudo-elements). Use this skill whenever the user wants to add page tra… |
| `wait-what` | Stop. That last message did not land: re-pitch it. |
| `wayfinder` | Plan a huge chunk of work (more than one agent session can hold) as a shared map of decision tickets on your issue tracker, and resolve them one at a time until the way to the destination is clear. |
| `waypoint-bio` | Supports work with Outpost Bio's open microbiome foundation models - the Waypoint checkpoints (Waypoint-6m, Waypoint-45m, Waypoint-170m), the Atlas pretraining corpus, the Compass eight-task benchmark, or the `waypoint` CLI from the `waypo… |
| `web-accessibility-audit` | Audit web applications for WCAG accessibility compliance. Use when asked to run accessibility checks, identify common violations, and provide remediation guidance. |
| `web-design-guidelines` | Review UI code for Web Interface Guidelines compliance. Use when asked to "review my UI", "check accessibility", "audit design", "review UX", or "check my site against best practices". |
| `web-performance-audit` | Audit web performance using Chrome DevTools MCP. Use when asked to audit, profile, debug, or optimize page load performance, Lighthouse scores, or site speed. |
| `webapp-testing` | Test local web applications with Playwright. Use when asked to verify frontend functionality, debug UI behavior, capture browser screenshots, or inspect browser logs. |
| `what-if-oracle` | Supports structured what-if scenario analysis for research planning, experimental contingencies, and scientific project decisions. Explores favorable, reference, adverse, wild-card, contrarian, and second-order scenarios with explicit assu… |
| `wizard` | Generate an interactive bash wizard that walks a human through steps only they can perform. Use when provisioning infrastructure, setting up credentials or CI secrets, walking an unfamiliar third-party dashboard, or running a one-off migra… |
| `write` | Rewrites and polishes Chinese or English prose, technical instructions, and product copy. Use when drafting, editing, localizing, or cutting AI tone. Not for code comments or commit messages. |
| `write-plan` | Write an evidence-anchored implementation plan in the house style — classify first, measure the facts, keep a decision ledger that separates verified from unverified, state what is explicitly not being done, and give every step its own ver… |
| `writing-beats` | Writing, exploit; assemble raw material into a journey of beats, grounding each term before a beat leans on it. |
| `writing-for-agents` | Writing documents for agents. Use when creating or editing skills, or modifying AGENTS.md or CLAUDE.md. |
| `writing-fragments` | Writing, explore: mine raw fragments, no structure yet. |
| `writing-guidelines` | Review docs/prose for Writing Guidelines compliance. Use when asked to "review my docs", "check writing style", "audit prose", "review docs voice and tone", or "check this page against the writing handbook". |
| `writing-shape` | Writing, exploit: shape raw material into an article, paragraph by paragraph. |
| `x402` | Set up Browser Use Cloud payments with x402 — pay per request from a crypto wallet (USDC on Base mainnet), no signup or API key. Two setups it works out up front — "just use it" (set up a wallet so you or Claude Code can run cloud browser… |
| `zarr-python` | Stores and queries chunked N-D scientific arrays with Zarr-Python 3, including codecs, sharding, S3/GCS storage, and NumPy/Dask/Xarray integration. Use for array layout, bounded I/O, format migration, or scientific metadata preservation. |

---

## 4. opencode 配置技能（`~/.config/opencode/skills/`，70 个）

> 11 个本地目录（含 `gstack` 包内 4 个子技能）+ 56 个指向 gstack 仓库的符号链接：链接名 `gstack-<name>`，技能名为 `<name>`

| 技能 | 何时使用 |
| --- | --- |
| `ai-chat-browser` | Use when the user wants to communicate with Gemini or Perplexity via browser automation — sending messages, switching models, extracting responses, or exploring AI chat interfaces. Covers both browser-use CLI and OpenCLI approaches. |
| `autoplan` · 目录 `gstack-autoplan` | Auto-review pipeline — reads the full CEO, design, eng, and DX review skills from disk and runs them sequentially with auto-decisions using 6 decision principles. Surfaces taste decisions (close approaches, borderline scope, outside-review… |
| `benchmark` · 目录 `gstack-benchmark` | Web performance regression detection. Establishes baselines for page load times, Core Web Vitals, and resource sizes. Compares before/after on every PR. Tracks performance trends over time. Use when: "web performance benchmark", "page spee… |
| `benchmark-models` · 目录 `gstack-benchmark-models` | Cross-model benchmark for gstack skills. Runs the same prompt through Claude, GPT (via Codex CLI), and Gemini side-by-side — compares latency, tokens, cost, and optionally quality via LLM judge. Answers "which model is actually best for th… |
| `browse` · 目录 `gstack-browse` | Drive a real browser through Aside: open a page, read it, click through a flow, take screenshots, check console errors. Use when asked to open a site, test a page, take a screenshot, or dogfood a flow. (gstack) Voice triggers (speech-to-te… |
| `canary` · 目录 `gstack-canary` | Post-deploy canary monitoring. Watches the live app for console errors, performance regressions, and page failures. Takes periodic screenshots, compares against pre-deploy baselines, and alerts on anomalies. Use when: "monitor deploy", "ca… |
| `careful` · 目录 `gstack-careful` | Safety guardrails for destructive commands. Warns before rm -rf, DROP TABLE, force-push, git reset --hard, kubectl delete, and similar destructive operations. User can override each warning. Use when touching prod, debugging live systems,… |
| `claude-code` · 目录 `gstack-claude-code` | Claude Code CLI second opinion for non-Claude Code hosts. Review a diff, challenge a change for failure modes, or consult Claude with read-only repo access and session continuity. Use for "claude review", "claude challenge", "ask claude",… |
| `codex` · 目录 `gstack-codex` | OpenAI Codex CLI wrapper — three modes. Code review: independent diff review via codex review with pass/fail gate. Challenge: adversarial mode that tries to break your code. Consult: ask codex anything with session continuity for follow-up… |
| `context-restore` · 目录 `gstack-context-restore` | Restore working context saved earlier by /context-save. Loads the most recent saved state (preferring the current branch, falling back across branches) so you can pick up where you left off — even across Conductor workspace handoffs. Use w… |
| `context-save` · 目录 `gstack-context-save` | Save working context. Captures git state, decisions made, and remaining work so any future session can pick up without losing a beat. Use when asked to "save progress", "save state", "context save", or "save my work". Pair with /context-re… |
| `cso` · 目录 `gstack-cso` | Security audit: supported static findings; qualified profiles add reproduction and repair candidates. (gstack) Use when: "security audit", "threat model", "OWASP", "CSO review", "recheck a vulnerability". Voice triggers (speech-to-text ali… |
| `design-consultation` · 目录 `gstack-design-consultation` | Design consultation: understands your product, researches the landscape, proposes a complete design system (aesthetic, typography, color, layout, spacing, motion), and generates font+color preview pages. Creates DESIGN.md as your project's… |
| `design-html` · 目录 `gstack-design-html` | Design finalization: generates production-quality Pretext-native HTML/CSS. Works with approved mockups from /design-shotgun, CEO plans from /plan-ceo-review, design review context from /plan-design-review, or from scratch with a user descr… |
| `design-review` · 目录 `gstack-design-review` | Designer's eye QA: finds visual inconsistency, spacing issues, hierarchy problems, AI slop patterns, and slow interactions — then fixes them. Iteratively fixes issues in source code, committing each fix atomically and re-verifying with bef… |
| `design-shotgun` · 目录 `gstack-design-shotgun` | Design shotgun: generate multiple AI design variants, open a comparison board, collect structured feedback, and iterate. Standalone design exploration you can run anytime. Use when: "explore designs", "show me options", "design variants",… |
| `deslop-shared-libs` · 目录 `gstack-deslop-shared-libs` | Find worthwhile shared-code extractions in recent work. Use for /deslop-shared-libs, $deslop-shared-libs, or requests to find code worth sharing. Recommendations only. (gstack) |
| `devex-review` · 目录 `gstack-devex-review` | Live developer experience audit. Actually TESTS the developer experience in the Aside browser: navigates docs, tries the getting started flow, times TTHW, screenshots error messages, evaluates CLI help text. Produces a DX scorecard with ev… |
| `diagram` · 目录 `gstack-diagram` | Turn an English description (or mermaid source) into a diagram triplet: the source, an editable .excalidraw file you can open on excalidraw.com, and rendered SVG + PNG. The SVG/PNG use clean mermaid style; the .excalidraw carries the hand-… |
| `document-generate` · 目录 `gstack-document-generate` | Generate missing documentation from scratch for a feature, module, or entire project. Uses the Diataxis framework (tutorial / how-to / reference / explanation) to produce complete, structured documentation. Can be invoked standalone or cal… |
| `document-release` · 目录 `gstack-document-release` | Release documentation audit. Reads relevant project docs, cross-references the diff, builds a Diataxis coverage map (reference/how-to/tutorial/explanation), updates README/ARCHITECTURE/CONTRIBUTING/CLAUDE.md to match what shipped, detects… |
| `doko-research` | Iterative web research with structured synthesis and source tracking. Conducts multi-round searches via local readpage, reads and cross-references sources, builds a structured research report with citations. |
| `doko-search` | Free web search using dokobot read in local mode — read search engine result pages through your real Chrome browser. No API key, no cost, no rate limits. Works with Google, Bing, DuckDuckGo, Baidu, platform search pages, and other search e… |
| `doko-summarize` | Generate concise summaries of web pages with key points extraction. Reads any page via a real Chrome browser and produces structured summaries at adjustable detail levels. |
| `doko-translate` | Translate web page content while preserving structure and formatting. Reads any web page via a real Chrome browser and translates it section by section, keeping headings, lists, tables, and code blocks intact. |
| `dokobot` | Read and extract content from any web page using a real Chrome browser — including SPAs, JavaScript-rendered sites, and complex dynamic pages. Use when fetching page content that headless tools can't render, searching the web, or reading f… |
| `freeze` · 目录 `gstack-freeze` | Restrict file edits to a specific directory for the session. Blocks Edit, Write and NotebookEdit outside the allowed path. Use when debugging to prevent accidentally "fixing" unrelated code, or when you want to scope changes to one module.… |
| `gstack` | Router for the gstack skill suite. Sends any gstack request to the right skill (planning, review, QA, shipping, debugging, docs, security, design). Routes QA by intent and browser interaction to /browse. Use when you invoke gstack without… |
| `gstack-upgrade` | Upgrade gstack to the latest version. Detects global vs vendored install, runs the upgrade, and shows what's new. Use when asked to "upgrade gstack", "update gstack", or "get latest version". Voice triggers (speech-to-text aliases): "upgra… |
| `gstack-upgrade` | Upgrade gstack to the latest version. Detects global vs vendored install, runs the upgrade, and shows what's new. Use when asked to "upgrade gstack", "update gstack", or "get latest version". Voice triggers (speech-to-text aliases): "upgra… |
| `guard` · 目录 `gstack-guard` | Full safety mode: destructive command warnings + directory-scoped edits. Combines /careful (warns before rm -rf, DROP TABLE, force-push, etc.) with /freeze (blocks edits outside a specified directory). Use for maximum safety when touching… |
| `investigate` · 目录 `gstack-investigate` | Systematic debugging with root cause investigation. Four phases: investigate, analyze, hypothesize, implement. Iron Law: no fixes without root cause. Use when asked to "debug this", "fix this bug", "why is this broken", "investigate this e… |
| `ios-clean` · 目录 `gstack-ios-clean` | Remove the DebugBridge SPM package and all #if DEBUG wiring from an iOS app. Cleans up StateServer, DebugOverlay, accessor codegen output, and app-side hooks installed by /ios-qa. This is a convenience wrapper — the structural Release-buil… |
| `ios-design-review` · 目录 `gstack-ios-design-review` | Visual design audit for iOS apps on real hardware. Connects to a real iPhone via the same StateServer as /ios-qa, screenshots every screen, evaluates against Apple HIG, DESIGN.md, and design best practices. Scores each dimension 0-10 with… |
| `ios-fix` · 目录 `gstack-ios-fix` | Autonomous iOS bug fixer. Takes a bug found by /ios-qa, reads the source, writes the fix, rebuilds, redeploys, and verifies the fix on the real device. Closes the loop: find bug → fix bug → confirm fix — zero human intervention. Captures t… |
| `ios-qa` · 目录 `gstack-ios-qa` | Live-device iOS QA for SwiftUI apps. Connects to a real iPhone or iPad via USB CoreDevice IPv6 tunnel, reads Swift source to understand every screen, then runs a vision-driven agent loop: screenshot → analyze → decide → act → verify → repe… |
| `ios-sync` · 目录 `gstack-ios-sync` | Regenerate the iOS debug bridge against the latest upstream gstack templates. Updates StateServer.swift, DebugOverlay.swift, Package.swift, and the typed @Observable state accessors. Use after you upgrade gstack or add new ViewModels/prope… |
| `land-and-deploy` · 目录 `gstack-land-and-deploy` | Land and deploy workflow. Merges the PR, waits for CI and deploy, verifies production health via canary checks. Takes over after /ship creates the PR. Use when: "merge", "land", "deploy", "merge and verify", "land it", "ship it to producti… |
| `landing-report` · 目录 `gstack-landing-report` | Read-only queue dashboard for workspace-aware ship. Shows which VERSION slots are currently claimed by open PRs, which sibling Conductor workspaces have WIP work likely to ship soon, and what slot /ship would pick next. No mutations — just… |
| `make-pdf` · 目录 `gstack-make-pdf` | Turn any markdown file into a publication-quality PDF. Proper 1in margins, intelligent page breaks, page numbers, cover pages, running headers, curly quotes and em dashes, clickable TOC, diagonal DRAFT watermark. Not a draft artifact — a f… |
| `notion-mcp` | Use when interacting with Notion workspace via MCP tools - creating, searching, updating pages, databases, views, comments, or querying data. Requires Notion MCP server configured. |
| `office-hours` | YC Office Hours — two modes. Startup mode: six forcing questions that expose demand reality, status quo, desperate specificity, narrowest wedge, observation, and future-fit. Builder mode: design thinking brainstorming for side projects, ha… |
| `office-hours` · 目录 `gstack-office-hours` | YC Office Hours — two modes. Startup mode: six forcing questions that expose demand reality, status quo, desperate specificity, narrowest wedge, observation, and future-fit. Builder mode: design thinking brainstorming for side projects, ha… |
| `open-gstack-browser` · 目录 `gstack-open-gstack-browser` | Launch GStack Browser — AI-controlled Chromium with the sidebar extension baked in. Opens a visible browser window where you can watch every action in real time. The sidebar shows a live activity feed and chat. Anti-bot stealth built in. U… |
| `pair-agent` · 目录 `gstack-pair-agent` | Pair a remote AI agent with your browser. One command generates a setup key and prints instructions the other agent can follow to connect. Works with OpenClaw, Hermes, Codex, Cursor, or any agent that can make HTTP requests. The remote age… |
| `plan-ceo-review` · 目录 `gstack-plan-ceo-review` | CEO/founder-mode plan review. Rethink the problem, find the 10-star product, challenge premises, expand scope when it creates a better product. Four modes: SCOPE EXPANSION (dream big), SELECTIVE EXPANSION (hold scope + cherry-pick expansio… |
| `plan-design-review` | Designer's eye plan review — interactive, like CEO and Eng review. Rates each design dimension 0-10, explains what would make it a 10, then fixes the plan to get there. Works in plan mode. For live site visual audits, use /design-review. U… |
| `plan-design-review` · 目录 `gstack-plan-design-review` | Designer's eye plan review — interactive, like CEO and Eng review. Rates each design dimension 0-10, explains what would make it a 10, then fixes the plan to get there. Works in plan mode. For live site visual audits, use /design-review. U… |
| `plan-devex-review` · 目录 `gstack-plan-devex-review` | Interactive developer experience plan review. Explores developer personas, benchmarks against competitors, designs magical moments, and traces friction points before scoring. Three modes: DX EXPANSION (competitive advantage), DX POLISH (bu… |
| `plan-eng-review` · 目录 `gstack-plan-eng-review` | Eng manager-mode plan review. Lock in the execution plan — architecture, data flow, diagrams, edge cases, test coverage, performance. Walks through issues interactively with opinionated recommendations. Use when asked to "review the archit… |
| `plan-tune` · 目录 `gstack-plan-tune` | Self-tuning question sensitivity + developer psychographic for gstack (v1: observational). Review which AskUserQuestion prompts fire across gstack skills, set per-question preferences (never-ask / always-ask / ask-only-for-one-way), inspec… |
| `qa-only` · 目录 `gstack-qa-only` | Report browser/API/CLI/job/worker/webhook bugs. Produces a structured report with contract evidence or browser scores and repro steps — but never fixes anything. Use when asked to "just report bugs", "qa report only", or "test but don't fi… |
| `reverse-engineering` | Use when 逆向 macOS 二进制或私有 API：stripped Mach-O、arm64e、Apple 系统进程（Dock/WindowServer/某个 .app），需要 Ghidra headless 静态定位、Hopper（fat/universal、GUI 交互）分析或 LLDB 动态验证，或维护 Pattern/offset 自动化（系统更新后 yabai 一类补丁失效）；也用于配置/验证 REA 引擎（Ghidra/Hopper）与排查 provid… |
| `review` · 目录 `gstack-review` | Pre-landing PR review. Analyzes diff against the base branch for SQL safety, LLM trust boundary violations, conditional side effects, and other structural issues. Use when asked to "review this PR", "code review", "pre-landing review", or… |
| `scrape` · 目录 `gstack-scrape` | Pull data from a web page through the Aside browser — your real, already signed-in sessions. Read-only; returns one JSON document. Use when asked to "scrape", "get data from", "pull", "extract from", or "what's on" a page. (gstack) |
| `setup-browser-cookies` · 目录 `gstack-setup-browser-cookies` | Import cookies from your real Chromium browser into the headless browse session. Opens an interactive picker UI where you select which cookie domains to import. Use before QA testing authenticated pages. Use when asked to "import cookies",… |
| `setup-deploy` · 目录 `gstack-setup-deploy` | Configure deployment settings for /land-and-deploy. Detects your deploy platform (Fly.io, Render, Vercel, Netlify, Heroku, GitHub Actions, custom), production URL, health check endpoints, and deploy status commands. Writes the configuratio… |
| `setup-gbrain` · 目录 `gstack-setup-gbrain` | Set up gbrain for this coding agent: install the CLI, initialize a local PGLite or Supabase brain, register MCP, capture per-remote trust policy. One command from zero to "gbrain is running, and this agent can call it." Use when: "setup gb… |
| `ship` · 目录 `gstack-ship` | Ship workflow: detect + merge base branch, run tests, review diff, bump VERSION, update CHANGELOG, commit, push, create PR. Use when asked to "ship", "deploy", "push to main", "create a PR", "merge and push", or "get it deployed". Proactiv… |
| `sisyphus-execution-rules` | 会话开始强制加载 — Sisyphus执行规则：Agent分工、实时更新、Git限制、资源管理。 触发词：会话开始、执行规则、Agent边界、委托规则、记忆更新、版本同步。 |
| `skillify` · 目录 `gstack-skillify` | Codify the most recent successful /scrape flow into a permanent browser-skill on disk. Future /scrape calls with the same intent run the codified script in ~200ms instead of re-driving the page. Walks back through the conversation, synthes… |
| `spec` · 目录 `gstack-spec` | Turn vague intent into a precise, executable spec in five phases. Files the issue, optionally spawns a Claude Code agent in a fresh worktree, and lets /ship close the source issue on merge. Use when asked to "spec this out", "file an issue… |
| `sync-gbrain` · 目录 `gstack-sync-gbrain` | Keep gbrain current with this repo's code and refresh agent search guidance in CLAUDE.md. Wraps the gstack-gbrain-sync orchestrator with state probing, native code-surface registration, capability checks, and a verdict block. Re-runnable,… |
| `test-audit` · 目录 `gstack-test-audit` | Find low-value or duplicate tests and the test-only code they keep alive. Report-only unless you approve a batch. Use for /test-audit. (gstack) |
| `test-auth-bootstrap` | Automatically obtain a valid auth token (or full account) in a dev/test environment so E2E verification can run without asking the human for credentials. Trigger whenever integration testing is blocked by auth (captcha, email verification,… |
| `unfreeze` · 目录 `gstack-unfreeze` | Clear the freeze boundary set by /freeze, allowing edits to all directories again. Use when you want to widen edit scope without ending the session. Use when asked to "unfreeze", "unlock edits", "remove freeze", or "allow all edits". (gsta… |

---

## 5. 跨源同名（已去重，按来源优先级解析）

| 技能名 | 出现在 | 说明 |
| --- | --- | --- |
| `health` | 用户级技能、opencode 配置 | 同名不同文件；优先级：项目 > 内置 > 用户级 > opencode |
| `learn` | 用户级技能、opencode 配置 | 同名不同文件；优先级：项目 > 内置 > 用户级 > opencode |
| `qa` | 用户级技能、opencode 配置 | 同名不同文件；优先级：项目 > 内置 > 用户级 > opencode |
| `retro` | 用户级技能、opencode 配置 | 同名不同文件；优先级：项目 > 内置 > 用户级 > opencode |
| `teach` | 内置 omp、用户级技能 | 同名不同文件；优先级：项目 > 内置 > 用户级 > opencode |
| `test-driven-development` | 内置 superpowers、用户级技能 | 同名不同文件；优先级：项目 > 内置 > 用户级 > opencode |

同一来源内同名不同目录：`browser-harness`（browser-harness, browser_harness）、`office-hours`（gstack-office-hours, office-hours）、`plan-design-review`（gstack-plan-design-review, plan-design-review）

---

## 6. 家族速查（按名称前缀，机器可核对）

| 前缀 / 族 | 数量 | 说明 |
| --- | --- | --- |
| `cli-anything-` | 70 | 为各类 GUI 应用封装的 CLI 技能 |
| `nature-` | 14 | 学术写作与投稿全流程（Nature 系列） |
| `doko-` | 4 | 浏览器检索 / 摘要 / 翻译（doko 系列） |
| `gstack-*`（目录名） | 56 | 符号链接；技能名不带前缀（如 `cso`、`browse`），见 §4 |

---

## 7. 本次校正记录

- 上一版（2026-08-25，自称 464 个）按目录名收录，与技能实际名称不一致：目录 `gstack-cso` 的技能名是 `cso`。本次改为以 frontmatter `name:` 为准。
- 上一版提及的名字中，114 个在本机已不存在（如 `arkcli-*`、`ast-grep`、`docx`、`browser`、`clean`、`git-master` 等），已全部移除；140 个名称新增或变形已按实际收录。
- 数字口径（四源逐一枚举，非估算）：项目 4 / 内置 2+15 / 用户级 416 / opencode 70（含 56 符号链接）= 507 个 SKILL.md → 去重 496 个唯一技能；全部技能均有 frontmatter `description`，本表『何时使用』直接取自该字段。

