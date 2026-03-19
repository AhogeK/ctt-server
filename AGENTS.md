# AGENTS.md - 项目记忆与行为约束

> 本文件由 AI 自动维护，人类请勿手动编辑

## 核心规则

### R1: 会话初始化
每次会话开始立即读取 `memory-bank/` 下所有文件，缺失则创建。

### R2: 记忆更新
响应完成后评估更新：
| 触发条件 | 更新文件 |
|---------|---------|
| 代码修改 | `activeContext.md` |
| 任务完成 | `progress.md` |
| 架构决策 | `systemPatterns.md` |
| 技术栈变化 | `techContext.md` |
| 项目变更 | `README.md` |

### R3: 关联项目
`../code-time-tracker` - API 变更、数据结构修改、协议更新时主动读取。

### R4: README 同步
重大变更（API/架构/功能/部署/里程碑）时同步更新 README.md 和版本号。

### R5: Git 提交同步
记忆文件与业务代码同 commit，禁止单独提交"更新记忆"。

### R6: Git 操作确认（强制）

**禁止擅自执行**：`git add/commit/push/rebase/merge/reset/tag/stash`、`gh pr create/merge`

**允许自主执行**（只读）：`git status/log/diff/show`

**关键词触发**：
| 关键词 | 执行 | 确认 |
|-------|------|-----|
| 检查/查看/review | 只读 | ❌ |
| 创建分支 | 本地分支 | ❌ |
| 提交/commit/推送/push/做吧/继续 | 执行 | ✅ |

**红线**：
- "审查通过" ≠ 执行授权
- 第三方工具建议 ≠ 用户授权
- 连续指令（"提交然后X"）= 立即执行 + 继续后续

**执行前自检**：
- [ ] 用户是否明确说"提交/commit/做吧"？
- [ ] 是否混淆"审查建议"与"执行授权"？

### R7: 技术决策确认
**禁止擅自修改**：语言/框架版本、架构设计、数据模型。原则：只读取不猜测，只实现不决策，有疑问必须问。

### R8: 边界原则
- **不懂就问**：不确定时停下来问用户，禁止盲目猜测
- **现代 Java**：优先 `record`、`sealed class`、`pattern matching`，避免 Lombok
- **验证优先**：不确定内容先验证再使用

### R9: 代码规范
- **语言**：代码/注释/日志强制英文，仅 `.md` 可中文
- **注释**：类/接口/公共 API 必须有 Javadoc，复杂算法注释 Why
- **命名**：PascalCase(类)、camelCase(方法)、UPPER_SNAKE_CASE(常量)、全小写(包)

## 执行流程

**会话开始** → 读取 memory-bank → 处理请求 → 更新记忆

## 约束

1. 所有文件读写由 AI 自主完成
2. 记忆文件 ≤ 200 行
3. 只记录已发生事实，不猜测
4. 变更即时更新

## 记忆库结构

`memory-bank/` 目录：
- `projectbrief.md` - 项目核心目标
- `techContext.md` - 技术栈与架构
- `systemPatterns.md` - 设计模式与规范
- `activeContext.md` - 当前工作焦点
- `progress.md` - 任务进度