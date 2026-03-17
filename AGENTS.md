# AGENTS.md - 项目记忆与行为约束

> 本文件由 AI 自动维护，人类请勿手动编辑

## 🎯 核心规则

### 规则 1: 每次会话强制读取

每次用户发第一条消息时，**立即读取**以下记忆文件（如果不存在则自动创建）：

- `memory-bank/projectbrief.md` - 项目核心目标
- `memory-bank/techContext.md` - 技术栈与架构
- `memory-bank/systemPatterns.md` - 设计模式与规范
- `memory-bank/activeContext.md` - 当前工作焦点
- `memory-bank/progress.md` - 任务进度

### 规则 2: 自动更新评估

**每次响应完成后，必须评估**是否需要更新记忆文件：

- 如果进行了代码修改 → 更新 `activeContext.md`
- 如果完成了任务 → 更新 `progress.md`
- 如果确立了新的架构决策 → 更新 `systemPatterns.md`
- 如果技术栈有变化 → 更新 `techContext.md`
- **如果项目结构/功能有重大变更 → 同步更新 `README.md`**

### 规则 3: 自主初始化

首次对话时自动检查记忆库结构，缺失文件立即创建。

### 规则 4: 关联项目访问

**关联本地项目**：`../code-time-tracker` (JetBrains 插件项目)

当任务涉及以下场景时，**主动读取**关联项目文件：

- API 接口变更需同步插件端
- 数据结构修改需对齐两端
- 协议版本更新
- 任何跨项目协调工作

读取路径：`../code-time-tracker/` 下的相关文件

### 规则 5: README 与版本实时同步

**当以下情况发生时，必须同步更新 README.md 和项目版本**：

- 新增或修改 API 端点
- 变更技术栈或架构设计
- 添加新功能模块
- 修改项目结构
- 更新部署方式
- **阶段性里程碑完成（如基建工程完成、主要功能模块发布）**

**版本更新检查点**：
- 基建工程完成 → 0.0.1-SNAPSHOT → 0.1.0-SNAPSHOT
- 主要功能发布（如 JWT 认证完成）→ 0.1.0-SNAPSHOT → 0.2.0-SNAPSHOT
- 正式发布前移除 SNAPSHOT → 1.0.0

**SNAPSHOT 使用规范**：
- 开发阶段始终保持 SNAPSHOT 后缀
- SNAPSHOT 表示开发中的迭代版本，允许频繁变更
- 正式发布（Release）时才移除 SNAPSHOT

**README 更新检查清单**：

- [ ] Overview 功能列表是否完整
- [ ] Tech Stack 是否与实际一致
- [ ] Architecture 图是否反映当前设计
- [ ] API Documentation 链接是否有效
- [ ] Related Projects 链接是否正确

### 规则 6: Git 提交与记忆同步

**关键原则**: 保持提交历史的干净和线性，记忆与业务同Commit

**执行流程**:

1. **代码修改前**: 先更新记忆文件记录计划变更
2. **代码修改后**: 将记忆文件更新与代码变更一起提交
3. **禁止行为**: 提交代码后再单独提交记忆文件更新来记录这次提交

**记忆与业务同Commit原则**:

- 记忆文件（memory-bank/）的更新必须与相关业务代码在**同一个commit**中提交
- 不允许单独提交"更新记忆"的commit
- 原因：避免循环提交，保持commit历史清晰

**示例**:

```bash
# ✅ 正确流程:
# 1. 更新记忆文件
Edit: memory-bank/activeContext.md
# 2. 修改代码
Edit: src/.../Controller.java
# 3. 一起提交
git add -A && git commit -m "feat: add user authentication"

# ❌ 错误流程:
git commit -m "feat: add user authentication"  # 先提交代码
Edit: memory-bank/activeContext.md              # 再更新记忆
git commit -m "docs: update memory bank"        # 再提交记忆（禁止！）
```

### 规则 7: Git 操作需人工确认（强制）

**⚠️ 严厉警告**: 所有 Git 操作必须获得用户明确批准

**禁止擅自执行的操作**:

- `git commit` - 创建提交
- `git push` - 推送到远程
- `git merge` / `git rebase` - 分支操作
- `git reset` / `git revert` - 撤销操作
- `git tag` - 标签操作
- `git stash` - 储藏更改
- `gh pr create` - 创建 Pull Request
- `git add`（除了明确获得授权时）
- 任何修改仓库状态的 Git 命令

**允许自主执行的操作**（只读）:

- `git status` - 查看状态
- `git log` - 查看历史
- `git diff` - 查看差异
- `git show` - 查看提交详情
- `git diff --stat` - 查看变更统计

**执行流程**:

1. **说明**: 清楚说明想执行什么操作及原因
2. **等待**: 等待用户明确确认（如"可以"、"继续"）
3. **确认**: 只有收到明确批准后才执行

**⚠️ 重要边界区分**:

| 用户指令      | AI 理解        | 是否执行 Git   |
|-----------|--------------|------------|
| "检查修改"    | 查看状态/差异      | ❌ 只读       |
| "查看变更"    | 查看 diff      | ❌ 只读       |
| "创建分支"    | 仅创建本地分支      | ❌ 不包含提交/推送 |
| "提交"      | commit       | ✅ 需明确确认    |
| "commit"  | commit       | ✅ 需明确确认    |
| "推送"      | push         | ✅ 需明确确认    |
| "push"    | push         | ✅ 需明确确认    |
| "创建 PR"   | gh pr create | ✅ 需明确确认    |
| "做吧"/"继续" | 继续之前的操作      | ✅ 按上下文     |

**🚨 红线清单 - 绝对禁止擅自执行**:

1. **提交相关**（任何将代码存入 Git 历史的操作）:
    - `git add` / `git stage`
    - `git commit` / `git commit -m "..."`
    - `git commit --amend`

2. **推送相关**（任何将本地更改发送到远程的操作）:
    - `git push` / `git push origin <branch>`
    - `git push -u origin <branch>`
    - `git push --force`

3. **PR 相关**（任何 GitHub 协作操作）:
    - `gh pr create`
    - `gh pr merge`
    - `gh pr edit`

4. **历史修改**（任何改变 Git 历史的操作）:
    - `git rebase`
    - `git merge`
    - `git cherry-pick`
    - `git reset` / `git revert`

**严格禁止的误解**:

- ❌ "检查" ≠ "提交"
- ❌ "查看" ≠ "推送"
- ❌ "review" ≠ "commit"
- ❌ "创建分支" ≠ "提交代码到该分支"
- ❌ 用户说 "A"，AI 不能做 "B"

**例外情况**:
用户明确指令包含以下关键词时无需确认：

- "提交"、"commit"
- "推送"、"push"
- "做吧"、"继续"

**错误示例**（已发生）:

```
❌ AI擅自: git commit -m "fix: restore versions"
❌ AI擅自: git push origin master

后果: Java版本、Spring Boot版本被错误修改并推送，
      严重违反用户技术决策
```

```
❌ AI误解: 用户说"检查修改，并更新README"
❌ AI擅自: git add -A && git commit && git push

后果: 用户仅要求查看和修改文件，并未授权提交推送。
      AI将"检查"误解为"执行"，严重违反规则7边界。

正确做法:
- 仅执行只读操作（git diff/status）
- 修改文件后询问:"文件已修改，是否提交推送？"
- 等待明确确认后再执行 git add/commit/push
```

```
❌ AI误解: 用户说"创建分支，用于开发规范"
❌ AI擅自: git checkout -b scaffold/xxx && git add -A && git commit && git push

后果: 用户授权"创建分支"，但AI擅自执行了提交和推送。
      "创建分支" ≠ "提交推送"，红线清单第1、2条违规。

正确做法:
- 执行 git checkout -b scaffold/xxx（创建本地分支）
- 创建规范文件（Write tool）
- 询问:"分支和规范文件已创建，是否提交并推送？"
- 等待明确确认（如"可以提交"）后再执行 git add/commit/push
```

### 规则 8: 技术决策与重大变更需人工确认（强制）

**⚠️ 绝对禁止 AI 擅自修改以下内容**：

**技术栈与版本**:

- 语言版本（Java、Python、Node.js 等）
- 框架版本（Spring Boot、Django、React 等）
- 依赖库版本（数据库驱动、安全库、工具库等）
- 构建工具版本（Gradle、Maven、npm 等）

**架构设计**:

- 项目结构变更（Package-by-Feature ↔ Package-by-Layer）
- 数据库选型（PostgreSQL ↔ MySQL ↔ MongoDB）
- 缓存策略变更（Redis ↔ Memcached）
- 认证机制变更（JWT ↔ Session ↔ OAuth）
- 部署方式变更（Railway ↔ AWS ↔ 自托管）

**数据模型**:

- 实体字段增减或类型变更
- 数据库表结构调整
- API 接口版本升级（v1 → v2）
- 协议格式变更（JSON ↔ Protobuf ↔ GraphQL）

**执行原则**:

1. **只读取，不猜测**: 看到版本号 X，绝不假设"应该升级到 Y"
2. **只实现，不决策**: 用户说"用版本 X"，绝不说"我觉得 Y 更好"
3. **有疑问必须问**: 不确定是否是重大变更时，**先询问再行动**

**正确示例**:

```
用户: "配置 Gradle 版本管理"
AI: "我看到当前使用 Java 25 和 Spring Boot 4.0.3，版本目录中需要填写这些版本号，确认使用这些版本吗？"
用户: "是的"
AI: [执行配置，保持原版本]
```

**错误示例**（已发生）:

```
用户: "配置 Gradle 版本管理"
AI: [擅自将 Java 25→21，Spring Boot 4.0.3→3.4.3]
后果: 严重违反用户技术决策，破坏项目兼容性
```

---

### 规则 9: AI 边界（不懂就问 + 优先现代 Java）

**⚠️ AI 知识库不一定是最新的，必须遵循以下原则**：

**1. 不懂就问原则**:

- 遇到不确定的技术问题，**停下来问用户**，不要死磕
- 遇到报错无法解决时，描述问题并询问用户
- 遇到知识库可能过时的内容（如 Spring Boot 4.x 新特性），先尝试验证再使用
- **禁止**: 盲目猜测、擅自修改配置、重复尝试已失败的方案

**2. 优先现代 Java 原则**:

- 本项目使用 **Java 25**，应优先使用现代 Java 特性
- ✅ 推荐: `record`（替代冗余 class + Builder）、`sealed class`、`pattern matching`、`switch 表达式`
- ❌ 避免: `Lombok`（`@Data`、`@Builder`）、老旧 Builder 模式、冗余 POJO
- 示例:
  ```java
  // ❌ 老旧 Java 8 思维
  public class ApiResponse<T> {
      private final T data;
      private final String message;
      // 手写 getter、Builder...
  }

  // ✅ 现代 Java (Java 25)
  public record ApiResponse<T>(T data, String message) {}
  ```

**3. 技术验证原则**:

- 对不确定的内容，先通过搜索引擎或官方文档验证
- 遇到版本相关问题时，先确认项目实际版本再行动
- 引用用户提供的文档/链接时，直接使用而非假设

---

### 规则 10: 代码规范边界（国际开源标准）

**⚠️ 代码产出必须遵循国际开源项目规范**：

**1. 语言规范（强制）**:

- **绝对禁止**在代码中使用中文（包括注释、变量名、字符串常量）
- **绝对禁止**使用 emoji（🚀、✅、🔴 等）
- 所有注释、文档、日志消息必须使用**英文**
- 例外：仅允许在 `.md` 文档文件中使用中文与AI交流

**违规示例**:

```java
// ❌ 严重违规：中文注释
/** 🛡️ 审计日志实体 */

// ❌ 严重违规：emoji 和中文
log.info("✅ 所有测试通过");

// ✅ 正确：纯英文，无 emoji
/** Audit log entity for security events. */
log.info("All tests passed");
```

**2. 注释规范（Clean Code）**:

- **不要过度注释**：代码应当自解释，优先通过命名表达意图
- **类/接口必须**有 Javadoc，说明职责和使用场景
- **公共 API 必须**有 Javadoc，说明参数、返回值、异常
- **复杂算法必须**有注释，说明 "Why" 而非 "What"
- **禁止无关注释**：不注释显而易见的代码（如 getter/setter）

**注释原则**:

```java
// ❌ 过度注释：显而易见
/** Gets the user name. */
public String getUserName() { return userName; }

// ❌ 中文注释
// 检查用户是否存在
if (user != null) { ... }

// ✅ 必要注释：解释 "Why"
// Use ReentrantLock instead of synchronized for better throughput
// under high contention scenarios.
private final ReentrantLock lock = new ReentrantLock();
```

**3. 文档规范**:

- Javadoc 必须专业、严谨
- 使用标准 HTML 标签：`<p>`、`<ul>`、`<li>`、`<code>`
- 保持简洁，一行简短描述控制在 80 字符以内
- 示例代码使用 `<pre>{@code ...}</pre>`

**4. 命名规范**:

- 类名：PascalCase，名词（`AuditLog`、`UserService`）
- 方法名：camelCase，动词或动宾短语（`findById`、`validateToken`）
- 常量：UPPER_SNAKE_CASE（`MAX_RETRY_COUNT`）
- 包名：全小写，不使用下划线（`com.ahogek.audit`）

---

## 🧠 记忆库结构

### memory-bank/projectbrief.md

```markdown
# 项目简述

## 核心目标

[一句话描述项目目的]

## 关键约束

- [约束1]
- [约束2]

## 利益相关者

- [用户/客户群体]
```

### memory-bank/techContext.md

```markdown
# 技术上下文

## 技术栈

- 语言:
- 框架:
- 数据库:
- 其他关键依赖:

## 架构图

[简要文字描述或ASCII图]

## 开发环境

- 启动命令:
- 测试命令:
- 构建命令:
```

### memory-bank/systemPatterns.md

```markdown
# 系统模式

## 设计决策

-

[决策1]: [理由]
-

[决策2]: [理由]

## 代码规范

- [规范1]
- [规范2]

## 组件字典

| ID | 组件 | 描述 | 状态 |
|---|---|---|---|
| C001 | [名称] | [描述] | stable |
```

### memory-bank/activeContext.md

```markdown
# 当前上下文

## 正在处理

- [当前任务/问题]

## 待解决

- [待办1]
- [待办2]

## 最近变更

- [时间] - [变更描述]

## 错误/障碍

- [错误信息或阻塞点]
```

### memory-bank/progress.md

```markdown
# 项目进度

## 已完成 ✅

- [x] [任务1]

## 进行中 🔄

- [ ] [任务2] - 进度: 50%

## 待开始 ⏳

- [ ] [任务3]

## 已知问题 🐛

- [问题描述]
```

---

## ⚡ 执行流程

### 阶段 1: 会话开始（每条消息第 1 步）

1. 检查 `memory-bank/` 目录是否存在，不存在则创建
2. 检查上述 5 个 .md 文件是否存在，不存在则按模板创建
3. 读取所有记忆文件到上下文

### 阶段 2: 处理请求（每条消息第 2 步）

1. 基于记忆文件理解项目背景
2. 执行用户请求的任务
3. 进行代码修改或解答问题

### 阶段 3: 记忆更新（每条消息第 3 步 - 自动执行）

**必须自问**: 这次交互产生了哪些值得记忆的信息？

如果需要更新：

- 直接写入相应的 .md 文件
- 保持简洁，避免冗余
- 使用 Markdown 格式

如果无需更新：

- 继续下一条消息（无需告知用户）

---

## 📝 更新示例

### 示例 1: 完成一个任务

用户: "实现用户登录功能"

响应后自动更新:

- `progress.md`: 将"用户登录功能"移到 ✅ 已完成
- `activeContext.md`: 清空该任务，写下"需要实现 JWT token 刷新"

### 示例 2: 确立架构决策

用户: "我们决定用 Zustand 代替 Redux"

响应后自动更新:

- `systemPatterns.md`: 添加"状态管理: 使用 Zustand，禁止使用 Redux"
- `techContext.md`: 更新技术栈

### 示例 3: 遇到错误

用户: "修复这个报错 [错误信息]"

响应后自动更新:

- `activeContext.md`: 记录错误原因和解决方案
- `progress.md`: 如果有相关任务，更新状态

---

## ⚠️ 约束与边界

1. **禁止要求用户操作**: 所有文件读写由 AI 自主完成
2. **保持精简**: 每个 .md 文件控制在 200 行以内
3. **事实优先**: 只记录已发生的代码变更，不猜测未来
4. **格式统一**: 严格使用上述模板结构
5. **实时性**: 变更必须在同一会话内更新到记忆文件
6. **无 /init 指令**: 首次对话自动初始化
7. **无 /save 指令**: 每次交互自动评估并执行更新

---

## 🔧 工具使用规范

### 读取记忆

```
Read: memory-bank/activeContext.md
Read: memory-bank/progress.md
...
```

### 更新记忆

```
Edit: memory-bank/progress.md
[具体修改]
```

### 创建记忆文件

```
Write: memory-bank/newfile.md
[内容]
```

---

*最后更新: [自动维护，无需手动修改]*
