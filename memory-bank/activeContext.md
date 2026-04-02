- [2026-04-03] - 版本号更新：traceId observability feature
    - 文件：`gradle/libs.versions.toml`
    - 变更：`0.4.0-SNAPSHOT` → `0.4.1-SNAPSHOT` (MINOR increment)
    - 提交：`ecc0ce0` chore: bump version to 0.4.1-SNAPSHOT for traceId observability feature
    - 状态：✅ 已提交，等待推送授权

- [2026-04-03] - Audit Logs traceId 全链路追踪修复 ✅ 完成
    - 架构决策（全部按推荐选项 A）：
        1. 创建共享 TraceIdUtils 工具类（DRY 原则）
        2. 遵循 W3C Trace Context 规范（VARCHAR(32)）
        3. 最小化实现（仅添加 trace_id，span_id/trace_flags 延期）
    - 实体层：
        - `src/main/java/com/ahogek/cttserver/audit/entity/AuditLog.java` (修改)
        - 添加 `traceId` 字段 + Javadoc + Fluent Setter
    - 事件层：
        - `src/main/java/com/ahogek/cttserver/audit/SecurityAuditEvent.java` (修改)
        - 添加 `traceId` 字段 + 修复语义错误（便捷构造函数误用 traceId 作为 resourceId）
    - 服务层：
        - `src/main/java/com/ahogek/cttserver/audit/service/AuditLogService.java` (修改)
        - 从 RequestContext 提取 traceId
    - 监听器层：
        - `src/main/java/com/ahogek/cttserver/audit/listener/AuditEventListener.java` (修改)
        - 映射 traceId 到实体
    - 测试工具：
        - `src/test/java/com/ahogek/cttserver/fixtures/AuditLogFixtures.java` (新建)
        - Object Mother + Builder 模式 + W3C 格式 traceId 常量
    - 测试覆盖：
        - 单元测试：SecurityAuditEventTest, AuditLogServiceTest, AuditEventListenerTest
        - 集成测试：AuditEventListenerIntegrationTest (验证 end-to-end 持久化)
    - 验证：
        - 编译通过：`./gradlew compileJava` ✅
        - 全量测试通过：`./gradlew build` ✅
        - Spotless 格式化：`./gradlew spotlessApply` ✅
        - 覆盖率验证：`./gradlew jacocoTestCoverageVerification` ✅
    - 状态：✅ 等待提交授权（需要用户明确说"提交"）

- [2026-04-03] - LogoutService 登出功能完整实现 + 测试覆盖 + 文档更新 ✅ 完成
    - 事故原因：
        1. master 分支被强制与 develop 同步，导致 AI 文件（memory-bank/, .agents/, .opencode/, AGENTS.md）被带入生产分支
        2. 未遵循 master 分支清理规则，直接使用 `git reset --hard develop` 导致污染
        3. 多次 cherry-pick 操作冲突处理不当，导致提交历史混乱
    - 修复方案：
        1. master 重置到安全起点 `02b83bd`（2026-03-24）
        2. 添加删除 AI 文件的提交：`chore: remove all AI-related files from production branch`
        3. 从 develop 的 `7b01f5e` 开始按顺序 cherry-pick 所有非 AI 提交（排除 `docs(memory-bank)`）
        4. 冲突处理：memory-bank 文件 → `git rm -f`，版本号 → `git checkout --theirs`
        5. 验证：`git ls-files master -- | grep -E "^(\.agents/|\.opencode/|AGENTS\.md|memory-bank/)"` 必须无输出
    - 新增规则（AGENTS.md R17）：
        - master 是生产分支，永远保持干净（无 AI 文件）
        - develop 是开发分支，允许包含 AI 文件
        - 同步 master 必须使用 cherry-pick 过滤，禁止直接 merge 或 reset
        - 事故恢复流程：停止 → 确认污染 → 重置到安全点 → 重新构建 → 强制推送 → 记录事故
    - 验证结果：
        - master 无任何 AI 文件：✅
        - master 包含所有非 AI 提交（23 个）：✅
        - develop 保持完整（含 AI 文件）：✅
        - 双分支已推送到远程：✅
    - 文件：
        - `AGENTS.md` (修改) - 添加 R17: 分支管理（强制 - 防止生产事故）
        - `memory-bank/activeContext.md` (修改) - 记录事故与修复方案

        - `1b98081` docs(memory-bank): record refresh token implementation and AGENTS.md rules
        - `42d3119` chore: bump version to 0.3.3 for refresh token rotation feature
        - `938f5d3` feat(auth): implement refresh token rotation with reuse detection
    - 提交历史 (master - 2 commits, 排除 AI):
        - `c16ebf2` chore: bump version to 0.3.3 for refresh token rotation feature
        - `37b08ee` feat(auth): implement refresh token rotation with reuse detection
    - 版本号：0.3.2-SNAPSHOT → 0.3.3-SNAPSHOT (新功能 MINOR)
    - 状态：✅ develop + master 均已推送到远程
    - 工作区：干净

    - 版本：0.3.1-SNAPSHOT → 0.3.2-SNAPSHOT
    - 状态：✅ develop + master 均已推送到远程

- [2026-04-02] - JwtAuthenticationEntryPoint 实现 + 5 原子提交完成
    - 文件：
        - `src/main/java/com/ahogek/cttserver/auth/infrastructure/security/JwtAuthenticationEntryPoint.java` (新建)
        - `src/main/java/com/ahogek/cttserver/common/config/jackson/JacksonConfig.java` (新建)
        - `src/main/java/com/ahogek/cttserver/common/config/SecurityConfig.java` (修改)
        - `src/test/java/com/ahogek/cttserver/auth/infrastructure/security/JwtAuthenticationEntryPointTest.java` (新建)
    - 变更：
        - 实现 AuthenticationEntryPoint 接口，处理 JWT 认证失败（token 无效/过期）
        - 返回统一 RestApiResponse 格式，错误码 AUTH_003
        - 配置 SecurityConfig：OAuth2 + 全局异常处理双入口点
        - 新建 JacksonConfig 提供全局 ObjectMapper Bean
    - 影响：JWT 认证失败现在返回标准 JSON 响应而非默认 401 空白响应
    - 验证：
        - JwtAuthenticationEntryPointTest: 3/3 通过
        - 完整构建：`./gradlew build` 通过
        - 代码审查：逻辑 PASS + 风格 PASS（修复导入顺序）
        - 全量测试：524/524 通过
        - 覆盖率：Instructions 93%, Lines 93%, Branches 84%, Methods 91%
    - 版本：0.3.0-SNAPSHOT → 0.3.1-SNAPSHOT (Bug 修复)
    - 提交历史 (develop):
        - `7c36769` feat(auth): add JWT authentication entry point with unified error response
        - `b520761` feat(config): add Jackson configuration for global ObjectMapper bean
        - `af55d7d` feat(security): integrate JWT authentication entry point into security filter chain
        - `0e9a2ef` chore: bump version to 0.3.1 for JWT auth failure response fix
        - `af888a0` docs(memory-bank): record JwtAuthenticationEntryPoint implementation (AI only)
    - 合并到 master: ✅ (排除 AI commit，只合并前 4 个)
        - `649ccab` feat(auth): add JWT authentication entry point with unified error response
        - `566dfe0` feat(config): add Jackson configuration for global ObjectMapper bean
        - `3bc26f5` feat(security): integrate JWT authentication entry point into security filter chain
        - `54a55cd` chore: bump version to 0.3.1 for JWT auth failure response fix
    - 状态：✅ develop + master 均已推送到远程

- [2026-04-02] - 登录接口实现完成 + 代码审查修复 (POST /api/v1/auth/login)
    - 文件：`src/main/java/com/ahogek/cttserver/auth/AuthController.java` + `AuthControllerTest.java`
    - 端点：POST /api/v1/auth/login (@PublicApi, @RateLimit: 30/小时/IP)
    - 变更：
        - 注入 UserLoginService 依赖到 AuthController
        - 添加 login 端点方法，返回 ResponseEntity<ApiResponse<LoginResponse>>
        - 添加 5 个 TDD 测试用例（成功场景 + 4 个验证失败场景）
    - 代码审查修复（ultrabrain + code-reviewer skill）：
        - 修复类级别 Javadoc：`{@link UserService}` → `application services`
        - 修复 malformed JSON 测试：尾随逗号 → 真正无效 JSON（缺少闭合括号）
    - 影响：暴露 UserLoginService.login() 用于 Web 认证，完整审计日志记录
    - 验证：AuthControllerTest 11 个测试全部通过，编译成功，Spotless 通过
    - 版本：0.2.1-SNAPSHOT → 0.3.0-SNAPSHOT (新增功能)
    - 状态：✅ 审查通过，等待提交授权

- [2026-04-01] - 分支同步：develop → master (依赖版本更新) ✅
    - 操作：Cherry-pick commits 7b01f5e + 20ad578 到 master
    - 文件：`gradle/libs.versions.toml`, `build.gradle.kts`
    - 变更：
        - 添加 ben-manes 依赖版本管理插件 (v0.53.0)
        - 添加 jacoco 插件
        - 添加依赖版本稳定性检查配置
    - 影响：master 分支包含最新依赖配置，develop 分支保持不变
    - 验证：编译成功，已推送到远程

## 架构决策 (保留)

- [2026-03-21] - 邮件生命周期审计完整覆盖：ENQUEUED → SENT/FAILED/EXHAUSTED
- [2026-03-21] - Audit Details 标准化 (GDPR 合规): `recipientMasked` 字段脱敏
- [2026-03-21] - 指数退避重试策略：`delay = min(base * multiplier^attempt, maxDelay) ± jitter`
- [2026-03-21] - 僵尸记录恢复机制：`resetStuckSendingJobs()` 批量更新 SENDING → PENDING
- [2026-03-20] - MailOutboxPoller + MailOutboxProcessor: @Scheduled 轮询 + REQUIRES_NEW 事务隔离

## 下一步行动

1. JWT 认证基础设施 Phase E: Token 刷新机制
2. JWT 认证基础设施 Phase F: Controller 端点暴露
