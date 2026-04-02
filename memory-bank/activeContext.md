- [2026-04-02] - JwtAuthenticationEntryPoint 实现 - JWT 认证失败统一响应
    - 文件：
        - `src/main/java/com/ahogek/cttserver/auth/infrastructure/security/JwtAuthenticationEntryPoint.java` (新建)
        - `src/main/java/com/ahogek/cttserver/common/config/SecurityConfig.java` (修改)
        - `src/test/java/com/ahogek/cttserver/common/TestConfig.java` (修改 - 添加 ObjectMapper Bean)
    - 变更：
        - 实现 AuthenticationEntryPoint 接口，处理 JWT 认证失败（token 无效/过期）
        - 返回统一 RestApiResponse 格式，错误码 AUTH_003
        - 配置 SecurityConfig：OAuth2 + 全局异常处理双入口点
        - 添加 ObjectMapper Bean 到 TestConfig 修复测试上下文
    - 影响：JWT 认证失败现在返回标准 JSON 响应而非默认 401 空白响应
    - 验证：
        - JwtAuthenticationEntryPointTest: 3/3 通过
        - 完整构建：`./gradlew build` 通过
        - 代码审查：逻辑 PASS + 风格 PASS（修复导入顺序）
    - 版本：0.3.0-SNAPSHOT → 0.3.1-SNAPSHOT (Bug 修复)

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

- [2026-03-24] - JWT 认证基础设施 Phase D (UserLoginService)
    - `LoginRequest.java`: 登录请求 DTO (email, password, deviceId)
    - `LoginResponse.java`: 登录响应 DTO (userId, accessToken, refreshToken, expiresIn)
    - `UserLoginService.java`: 登录服务
        - 防枚举：用户不存在返回与密码错误相同的提示
        - 状态机屏障：登录前验证用户状态
        - 防爆破：使用 UserValidator.assertLoginAttemptsNotExceeded()
        - 成功后签发 Access Token + Refresh Token
        - 审计日志：LOGIN_SUCCESS / LOGIN_FAILED
    - `UserLoginServiceTest.java`: 11 个单元测试覆盖核心场景
    - `User.java`: 补全字段匹配数据库 schema
        - `emailVerifiedAt`, `lastLoginAt`, `lastLoginIp`, `lockedUntil`
        - `recordFailedLogin(maxAttempts, lockDuration)`: 设置 `lockedUntil`
        - `recordSuccessfulLogin()`: 设置 `lastLoginAt`, 清除 `lockedUntil`

- [2026-03-23] - JWT 认证基础设施 Phase C (JwtTokenProvider)
    - `JwtTokenProvider.java`: 创建 JWT Access Token 签发服务
        - `generateAccessToken(User)`: 使用 JwtClaimsSet 构建 Claims
        - 标准 Claims: iss, sub, iat, exp
        - 自定义 Claims: email
    - `JwtTokenProviderTest.java`: 6 个单元测试覆盖核心功能
    - 安全设计：Payload 不加密，禁止放入敏感数据

- [2026-03-23] - JWT 认证基础设施 Phase B (JWT Bean 注册)
    - `build.gradle.kts`: 替换 `spring-security-oauth2-jose` → `spring-boot-starter-oauth2-resource-server`
    - `JwtConfig.java`: 创建独立配置类 (auth/config/)
        - `JwtEncoder` Bean: NimbusJwtEncoder (HMAC-SHA256)
        - `JwtDecoder` Bean: NimbusJwtDecoder (HMAC-SHA256)
    - `SecurityConfig.java`: 启用 `oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))`

- [2026-03-23] - JWT 认证基础设施 Phase A (依赖与 Token 工具)
    - 添加 `spring-security-oauth2-jose` 依赖 (libs.versions.toml + build.gradle.kts)
    - `RefreshToken.java`: 补全 `issuedFor`, `lastUsedAt` 字段匹配数据库 schema
    - `RefreshTokenRepository.java`: 创建仓储接口
        - `findByTokenHash(String)`: 根据 hash 查找 token
        - `revokeAllUserTokens(UUID, Instant)`: 批量吊销用户所有有效 token
        - `revokeDeviceTokens(UUID, UUID, Instant)`: 吊销特定设备的 token
    - `TokenUtils.java`: 扩展支持 Refresh Token
        - 重命名 `TokenPair` → `EmailVerificationTokenPair`
        - 新增 `RefreshTokenPair` record
        - 新增 `createRefreshToken()` 方法

- [2026-03-22] - EmailVerificationToken 完整实现
    - Entity 字段补全：`email`, `purpose`, `sentAt`, `requestIp`, `userAgent`
    - Repository 方法扩展：`findByUserIdAndPurpose`, `existsBy...`
    - TokenUtils 重构：`createVerificationToken()` 统一实现
    - EmailVerificationService 改进：注入 UserValidator，完善审计日志
    - 文档更新：README.md API Endpoints, developer-handbook.md 验证流程
    - AGENTS.md 规则优化：R2/R8/R13/R14/R15 新增

- [2026-03-23] - Bug 修复：审计事件竞态条件
    - 问题：`@EventListener` 在事务内立即触发，异步线程尝试插入 audit_logs 时 FK 违规
    - 修复：`@EventListener` → `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true)`
    - 新增 `RaceConditionPreventionTests` 测试嵌套类，覆盖真实场景
    - 新增 `AuditLogRepository.findByUserId(UUID)` 方法

- [2026-03-23] - Bug 修复：Token 创建缺少 email 字段
    - 问题：`TokenUtils.createVerificationToken()` 没有设置 email，导致 DB 约束违规
    - 修复：添加 email 参数，更新所有调用方

- [2026-03-23] - Bug 修复：PublicApiEndpointRegistry 初始化顺序
    - 问题：`@PostConstruct` 在 SecurityFilterChain 创建后执行，导致 publicUrls 为空
    - 修复：改用 `@PostConstruct` 确保在 Bean 初始化时填充 URL

- [2026-03-23] - 版本号更新
    - 0.1.1-SNAPSHOT → 0.2.0-SNAPSHOT
    - 变更类型：新增功能 (JWT 认证基础设施 Phase A)

## 架构决策 (保留)

- [2026-03-21] - 邮件生命周期审计完整覆盖：ENQUEUED → SENT/FAILED/EXHAUSTED
- [2026-03-21] - Audit Details 标准化 (GDPR 合规): `recipientMasked` 字段脱敏
- [2026-03-21] - 指数退避重试策略：`delay = min(base * multiplier^attempt, maxDelay) ± jitter`
- [2026-03-21] - 僵尸记录恢复机制：`resetStuckSendingJobs()` 批量更新 SENDING → PENDING
- [2026-03-20] - MailOutboxPoller + MailOutboxProcessor: @Scheduled 轮询 + REQUIRES_NEW 事务隔离

## 下一步行动

1. JWT 认证基础设施 Phase E: Token 刷新机制
2. JWT 认证基础设施 Phase F: Controller 端点暴露
