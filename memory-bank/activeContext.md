# Active Context
- [2026-05-01] - 密码字符白名单 REGEX_PASSWORD_CHARS 补充约束
    - 变更: ValidationConstants.java 新增 REGEX_PASSWORD_CHARS = "^[!-~]+$"（所有可打印 ASCII 非空格字符）
    - 变更: ValidationConstants.java 新增 MSG_PASSWORD_CHARS 错误消息
    - 变更: StrongPassword.java 新增 @Pattern(regexp=REGEX_PASSWORD_CHARS) 约束
    - 说明: NIST SP 800-63B 对齐的补充约束，仅限制字符范围（可打印 ASCII），不引入复杂度要求
    - 测试: ValidationConstantsTest 新增 validPasswordCharsProvider (3 种合法字符组合) + rejects_invalid_chars (6 种: 空格/制表/拉丁扩展/中文/emoji)
    - 文件: ValidationConstants.java, StrongPassword.java, ValidationConstantsTest.java
    - 版本: 0.24.1-SNAPSHOT → 0.24.2-SNAPSHOT (PATCH: 约束行为细化)

- [2026-05-01] - 密码校验规则 NIST SP 800-63B 对齐 + ParameterizedTest 合并
    - 变更: ValidationConstants.java 移除 REGEX_PASSWORD (复杂度正则)，新增 PASSWORD_MIN_LENGTH=8 / PASSWORD_MAX_LENGTH=64
    - 变更: StrongPassword.java @Pattern(regex=REGEX_PASSWORD) → @Size(min=PASSWORD_MIN_LENGTH, max=PASSWORD_MAX_LENGTH)
    - 变更: MSG_PASSWORD_WEAK "8-32, including uppercase/lowercase/digit/special" → "8-64 characters"
    - 变更: 3 个 DTO @Schema(password) description 更新（UserRegisterRequest, LoginRequest, ResetPasswordRequest）
    - 测试: UserRegisterRequestTest 移除复杂度测试，新增长度 ParameterizedTest（@CsvSource 3 种: blank/too short/too long），边界值(64字符)、null密码均已覆盖
    - 测试: ValidationConstantsTest 移除正则测试，新增长度常量测试
    - 审查: 3 concurrent review agents 审查通过 + 测试覆盖验证通过
    - 验证: 编译通过 + 全量测试通过 + LSP diagnostics clean
    - 版本: 0.23.2-SNAPSHOT → 0.24.1-SNAPSHOT (PATCH, 约束行为变更 + 测试重构)

- [2026-04-28] - 前端端口配置修正（5175 → 5173）
    - 变更: application.yaml 默认端口 5175 → 5173（两处）
    - 变更: application-test.yaml 默认端口 5175 → 5173（两处）
    - 文件: src/main/resources/application.yaml, src/test/resources/application-test.yaml
    - 版本: 0.23.2-SNAPSHOT (PATCH, 配置修正)

- [2026-04-28] - @ExampleObject JSON字符串Text Block转换（代码风格优化）
    - 变更: AuthController.java 16个@ExampleObject JSON字符串转换为Text Block格式（避免单行溢出）
    - 变更: DeviceController.java 2个@ExampleObject JSON字符串转换为Text Block格式
    - 变更: LogoutController.java 2个@ExampleObject JSON字符串转换为Text Block格式
    - 格式: `value = """\n{JSON}\n"""`（遵循EmailVerificationController.java模式）
    - 文件: AuthController.java, DeviceController.java, LogoutController.java
    - 版本: 0.23.1-SNAPSHOT (PATCH, 代码风格重构)

- [2026-04-28] - Code Review优化项实现（@author统一 + docs补充）
    - 修复: EmailVerificationService.java + JpaAuditingConfig.java `@author Auto-generated` → `@author AhogeK [ahogek@gmail.com]`
    - 补充: developer-handbook.md 新增 "Idempotent Skip Behavior" 章节（10min窗口、EmptyResponse.ok(true)、MAIL_IDEMPOTENT_SKIP）
    - 补充: api-governance.md 新增 "Idempotency Strategies Comparison" 章节（@Idempotent vs 业务层幂等窗口对比）
    - 文件: EmailVerificationService.java, JpaAuditingConfig.java, developer-handbook.md, api-governance.md
    - 版本: 0.23.0-SNAPSHOT (不变，非功能修改)

- [2026-04-28] - PasswordResetService 适配 MailOutboxService EmptyResponse 返回值
    - 变更: `MailOutboxService.enqueuePasswordResetEmail()` 返回类型 `void` → `EmptyResponse`
    - 变更: Idempotent skip 时返回 `EmptyResponse.ok(true)`（idempotentSkip=true）
    - 变更: 正常处理时返回 `EmptyResponse.ok(false)`（idempotentSkip=false）
    - 变更: `PasswordResetService.requestReset()` 返回类型 `void` → `EmptyResponse`，透传 mailOutboxService 返回值
    - 变更: `AuthController.requestPasswordReset()` 和 `forgotPassword()` 使用 service 返回的 EmptyResponse
    - 测试: PasswordResetServiceTest 新增 `shouldReturnIdempotentSkipWhenMailOutboxDeduplicates` 测试
    - 测试: 现有 5 个 RequestReset 测试适配 EmptyResponse 返回值断言
    - 文件: MailOutboxService.java, PasswordResetService.java, AuthController.java, PasswordResetServiceTest.java
    - 版本: 0.23.0-SNAPSHOT (不变，功能完善)

- [2026-04-28] - MailOutboxService enqueue 方法返回 EmptyResponse 支持 idempotent skip 标记
    - 变更: `enqueueVerificationEmail()` 和 `enqueuePasswordResetEmail()` 返回类型 `void` → `EmptyResponse`
    - 变更: Idempotent skip 时返回 `EmptyResponse.ok(true)`（idempotentSkip=true, message="Operation successful"）
    - 变更: 正常处理时返回 `EmptyResponse.ok("Email queued successfully")`（idempotentSkip=null）
    - 变更: 调用方（EmailVerificationService, PasswordResetService, UserService）忽略返回值，无需修改
    - 测试: MailOutboxServiceTest 新增 2 个测试验证 idempotent skip 返回值，现有测试适配返回值断言
    - 文件: MailOutboxService.java, MailOutboxServiceTest.java
    - 版本: 0.23.0-SNAPSHOT (不变，功能完善)

- [2026-04-28] - EmptyResponse 新增 idempotentSkip 字段（TDD 流程）
    - 背景: Idempotent Skip 静默响应问题，需要让 API 返回可区分响应
    - 新增: EmptyResponse 第 4 个 record 组件 `Boolean idempotentSkip`
    - 新增: factory 方法 `ok(boolean idempotentSkip)` 和 `ok(String message, boolean idempotentSkip)`
    - 保留: `ok()` 和 `ok(String message)` 保持不变（idempotentSkip=null）
    - 测试: EmptyResponseTest 新增 5 个测试（shouldX_whenY 模式）
    - TDD 流程: RED（测试编译失败）→ GREEN（实现通过）→ 全量测试无回归
    - 文件: EmptyResponse.java, EmptyResponseTest.java
    - 版本: 0.22.1-SNAPSHOT → 0.23.0-SNAPSHOT (MINOR: 新功能)

- [2026-04-28] - resend-verification 邮件未发送根因调查
    - 问题: API 返回成功但用户未收到邮件
    - 根因: **Idempotent Skip** 触发（MailOutboxService.java:94-96）
    - 机制: 10分钟幂等窗口内重复请求静默跳过，不创建新 mail_outbox 行
    - 证据: 用户确认 mail_outbox 表有10分钟内记录 + 日志有 "Idempotent skip" 消息
    - 结论: 预期行为（防骚扰设计），无需代码修改
    - 测试建议: 清空 mail_outbox 或等待10分钟后再测试
    - 调查方法: investigate skill (Phase 1-4)，并行 fire 3 agent（explore/librarian）

- [2026-04-28] - Refresh Token Device FK 约束内联到 init schema
    - 变更: 删除独立迁移文件 V20260428060000__remove_refresh_token_device_fk.sql
    - 变更: init schema 中 refresh_tokens.device_id 移除 REFERENCES devices (id) ON DELETE SET NULL
    - 原因: 开发阶段清理，WEB 登录不需要 devices 表前置记录
    - 影响: device_id 保留为可选追踪字段，无 FK 约束
    - 文件: V20260303210000__init_base_schema.sql (line 229), V20260428060000__*.sql (deleted)
    - 版本: 0.22.0-SNAPSHOT (不变，重构清理)

- [2026-04-28] - 设备管理模块提交
    - 问题: src/main/java/com/ahogek/cttserver/device/ 目录下有未提交代码
    - 根因: 2026-04-28 AI 生成的设备管理模块，从未提交
    - 内容: Device entity + DeviceController + DeviceService + DeviceRepository + DeviceResponse DTO
    - 功能: IDE 插件客户端注册/追踪，GET /api/v1/devices（设备列表），DELETE /api/v1/devices/{deviceId}（吊销设备）
    - 文件: device/entity/, device/controller/, device/service/, device/repository/, device/dto/
    - 版本: 0.21.2-SNAPSHOT → 0.22.0-SNAPSHOT (MINOR: 新功能)

- [2026-04-28] - 登录失败修复 + 约束感知错误处理
    - 问题: `/api/v1/auth/login` 返回 USER_001/SYSTEM_001 错误
    - 根因: `refresh_tokens.device_id` FK 约束指向 `devices` 表，WEB 登录传递的 deviceId 不存在于 devices 表
    - 修复: Flyway migration 删除 `refresh_tokens_device_id_fkey` 约束，device_id 保持为可选追踪字段
    - 新增: AUTH_014 "Token creation failed" (HTTP 409) 错误码
    - 增强: GlobalExceptionHandler 解析 PostgreSQL 约束名返回上下文相关错误码
        - uk_users_email_lower, uk_user_oauth_* → USER_001
        - uk_refresh_tokens_token_hash, uk_*_token_hash → AUTH_014
        - 未知约束 → SYSTEM_001
    - 测试: GlobalExceptionHandlerTest 新增 3 个约束解析测试
    - 测试: IDE 警告修复 — .satisfies() 链式调用改为直接断言
    - 文件: ErrorCode.java, GlobalExceptionHandler.java, GlobalExceptionHandlerTest.java, V20260428060000__*.sql
    - 版本: 0.21.1-SNAPSHOT → 0.21.2-SNAPSHOT (PATCH bug fix)

- [2026-04-25] - 邮件链接路径可配置化（修复前端 404）
    - 问题: 邮件中生成的 `/verify-email` 和 `/reset-password` 路径与前端路由 `/auth/verify-email`、`/auth/reset-password` 不匹配
    - 修复: `CttMailProperties.Frontend` record 新增 `verifyEmailPath` 和 `resetPasswordPath` 字段（原仅 `baseUrl`）
    - 修复: `MailOutboxService.buildVerificationLink()` 和 `buildPasswordResetLink()` 使用配置路径替代硬编码
    - 配置: application.yaml / application-test.yaml 新增 `verify-email-path: /auth/verify-email` 和 `reset-password-path: /auth/reset-password`
    - 测试: MailOutboxServiceTest / MailOutboxPollerTest / MailDispatcherTest / ExponentialBackoffRetryStrategyTest 全部适配新构造函数
    - 文件: CttMailProperties.java, application.yaml, application-test.yaml, MailOutboxService.java, 4 个测试文件
    - 版本: 0.21.0-SNAPSHOT → 0.21.1-SNAPSHOT (PATCH bug fix)

- [2026-04-22] - OAuthCallbackController 拟人测试 + 配置修复
    - 配置: .env 补齐 GitHub OAuth 凭据 + 缺失字段 (MAIL_FROM_*, FRONTEND_BASE_URL, APP_PORT 等)
    - 配置: application.yaml 新增 ctt.security.oauth.frontend-url 映射
    - 修复: OAuthProvider 枚举支持大小写不敏感路径绑定 (github/GITHUB → GITHUB)
    - 新增: OAuthProviderConverter Spring Converter 实现路径变量转换
    - 修复: callback 端点 code/state 参数改为可选，先检查 error 再检查缺失参数
    - 手动 QA 验证:
        - ✅ GET /authorize → 200 + 有效 GitHub URL (含 client_id, scope, state)
        - ✅ GET /authorize (invalid provider) → 302 到错误页
        - ✅ GET /callback?error=access_denied → 302 + OAUTH_PROVIDER_ERROR
        - ✅ GET /callback (missing code) → 302 + MISSING_OAUTH_PARAMS
        - ✅ GET /callback (invalid state) → 302 + AUTH_013
        - ✅ GET /callback (missing state) → 302 + MISSING_OAUTH_PARAMS
        - ✅ Swagger UI: OAuth tag 下 2 个端点文档完整
    - 验证: 全量测试通过、覆盖率通过、服务启动正常
    - 文件: .env, application.yaml, OAuthProvider.java, OAuthProviderConverter.java, OAuthCallbackController.java
    - 版本: 0.20.0-SNAPSHOT → 0.21.0-SNAPSHOT (MINOR: 新功能)

- [2026-04-22] - OAuthCallbackController 审查修复
    - 修复: callback 端点补充 @PublicApi（原缺失导致 401）
    - 修复: callback 端点补充 @RateLimit(IP, 60/3600s) 防洪
    - 修复: 处理 GitHub 错误参数 (?error=access_denied) → 302 到前端错误页
    - 修复: 验证 state payload action == LOGIN，防止 BIND 动作滥用
    - 修复: @ExceptionHandler FQN → 使用已导入形式
    - 修复: 302 @ApiResponse 补充 content schema
    - 修复: 删除冗余 Javadoc（@param response 等）
    - 新增: @ExceptionHandler(Exception.class) 兜底所有未预期异常 → 302 到错误页
    - 新增: redirectError() 私有方法消除重复代码
    - 新增: OAuthCallbackControllerTest (9 annotation tests, pure unit, no Spring context)
    - 修复: application-test.yaml 补充 OAuth github.* 和 frontend-url 配置
    - 验证: spotlessApply 通过、全量测试通过、覆盖率通过
    - 文件: OAuthCallbackController.java, OAuthCallbackControllerTest.java, application-test.yaml
    - 版本: 0.20.0-SNAPSHOT → 0.21.0-SNAPSHOT (MINOR: 新功能)

- [2026-04-22] - OAuthCallbackController 实现
    - 新增: `OAuthCallbackController` — OAuth 回调入口，连接 GitHub Provider 与内部认证系统
    - 新增: `GET /{provider}/authorize` — 生成 CSRF state，返回 GitHub 授权 URL（JSON `{authUrl: "..."}`）
    - 新增: `GET /{provider}/callback` — 校验 state → 换 token → 取用户信息 → 登录/注册 → 302 重定向前端
    - 新增: `AuthorizeResponse` DTO — 授权 URL 响应载体
    - 新增: `SecurityProperties.OAuthProperties.frontendUrl` — 前端基础 URL 配置项
    - 设计: 成功重定向 `{frontendUrl}/oauth/callback?accessToken=...&refreshToken=...`
    - 设计: 失败重定向 `{frontendUrl}/oauth/error?code={errorCode}`
    - 设计: `@ExceptionHandler(BusinessException)` 统一捕获回调异常并 302
    - 设计: authorize 端点 `@RateLimit(IP, 30/3600s)` 防 state 滥用
    - 修复: AesGcmTokenEncryptorTest / GitHubOAuthClientTest 适配 OAuthProperties 新构造函数
    - 验证: compileJava 通过、spotlessApply 通过、全量测试通过
    - 文件: OAuthCallbackController.java, AuthorizeResponse.java, SecurityProperties.java, 2 测试文件
    - 版本: 0.20.0-SNAPSHOT → 0.21.0-SNAPSHOT (MINOR: 新功能)

- [2026-04-22] - Notion 开发计划页面更新（O 阶段 OAuthLoginOrRegisterService）
    - 更新: O 阶段 checkbox 勾选状态 — OAuthLoginOrRegisterService（❌）→（✅）
    - 更新: 所有登录/注册核心服务的子 checkbox 已勾选
    - 更新: 方法签名 AuthTokenResponse → LoginResponse
    - 更新: 总交付清单表格同步状态
    - 页面: "🖥️ ctt-server 开发计划" (ID: 320f5477-6e22-8123-a8d6-d91fddb9445c)

- [2026-04-22] - OAuthLoginOrRegisterServiceTest 移除无用 eq() matcher
    - 问题: Sonar/IntelliJ 警告 "Remove this and every subsequent useless 'eq(...)' invocation"
    - 修复: 移除 `eq(OAuthProvider.GITHUB)` → 直接传递枚举值
    - 修复: 移除 `eq(String.valueOf(GITHUB_USER_ID))` → 直接传递字符串表达式
    - 修复: 移除 `eq(TEST_EMAIL)` → 直接传递常量字符串
    - 修复: 移除 verify(auditLogService) 中大部分 eq() 调用
    - 保留: 混用 any() matcher 时其他参数必须用 eq()（Mockito 规则）
    - 格式: spotlessApply 修复缩进
    - 验证: 12 tests 通过、spotlessCheck 通过
    - 文件: OAuthLoginOrRegisterServiceTest.java
    - 版本: 0.20.0-SNAPSHOT (版本号不变，代码质量修复)

- [2026-04-22] - OAuthLoginOrRegisterService Critical 修复
    - 问题: 方法签名缺少 accessToken 参数，导致 userInfo.toString() 被误用作 accessToken 存储
    - 影响: 数据库存储无效的 toString() 输出而非真正的 OAuth token，无法调用 GitHub API
    - 修复: 修改 process() 方法签名为 `process(OAuthProvider, String accessToken, GitHubUserInfo)`
    - 修复: 所有 setAccessToken() 调用改为使用正确的 accessToken 参数
    - 修复: 测试文件添加 ReflectionTestUtils import，移除全路径类名
    - 修复: 测试文件适配新方法签名，添加 TEST_GITHUB_ACCESS_TOKEN 常量区分 GitHub token 和 CTT token
    - 验证: 编译通过、13 tests 通过、全量测试无回归、Spotless 通过
    - 文件: OAuthLoginOrRegisterService.java, OAuthLoginOrRegisterServiceTest.java
    - 版本: 0.20.0-SNAPSHOT (版本号不变，PATCH 修复)

- [2026-04-22] - OAuthLoginOrRegisterService 实现
    - 新增: `OAuthLoginOrRegisterService` — OAuth 登录/注册核心服务，身份协调器
    - 实现: `process(OAuthProvider, GitHubUserInfo): LoginResponse` — 处理 OAuth 回调
    - 分支1: 已有绑定 → 状态校验 + Token 更新 + OAUTH_LOGIN_SUCCESS 审计 + 返回凭据
    - 分支2: 邮箱合并 → 插入 OAuth 绑定 + OAUTH_ACCOUNT_LINKED 审计 + 调用已有绑定流程
    - 分支3: 新用户注册 → verifyEmail() 激活（OAuth 跳过邮箱验证）+ 插入绑定 + 返回凭据
    - 设计: OAuth 用户调用 `verifyEmail()` 设置 ACTIVE + emailVerified + emailVerifiedAt
    - 设计: GitHub token 永不过期 → `tokenExpiresAt = null`
    - 测试: `OAuthLoginOrRegisterServiceTest` (13 tests) — 已有绑定/邮箱合并/新用户注册/状态校验
    - 清理: 删除 docs/plans/, docs/superpowers/, ~/.local/share/opencode/plans/ (Plan agent 误创建)
    - 文件: OAuthLoginOrRegisterService.java, OAuthLoginOrRegisterServiceTest.java
    - 版本: 0.19.0-SNAPSHOT → 0.20.0-SNAPSHOT (MINOR: 新功能)

- [2026-04-21] - Notion 开发计划页面更新（O 阶段状态同步）
    - 更新: 标题 "O：GitHub OAuth 核心流程实现 ⚠️（未实现）" → "⚠️（部分实现）"
    - 更新: 实现状态描述，详细列出已完成/未完成项
    - 更新: GitHub API 客户端复选框（6项全部勾选）
    - 更新: 总交付清单表格状态 "❌ 未实现" → "⚠️ 部分完成"
    - 页面: "🖥️ ctt-server 开发计划" (ID: 320f5477-6e22-8123-a8d6-d91fddb9445c)

- [2026-04-21] - GitHub OAuth 客户端基础设施
    - 新增: `BadGatewayException` — 502 异常类型，扩展 BusinessException sealed permits
    - 新增: `GitHubTokenResponse` / `GitHubUserInfo` / `GitHubEmail` — OAuth API 响应 record
    - 新增: `GitHubOAuthClient` — RestClient 实现 code→token 交换、用户信息获取、邮箱回退
    - 扩展: `SecurityProperties.OAuthProperties` 嵌套 `GitHubProperties`（clientId, clientSecret, URIs, scope）
    - 新增: `GitHubOAuthClientTest` (9 tests) — 正常链路、邮箱回退、无主邮箱异常、API 错误映射
    - 新增: application.yaml github 配置 + .env.example GITHUB_CLIENT_ID/SECRET
    - 修复: AesGcmTokenEncryptorTest OAuthProperties 构造函数适配
    - 设计: 邮箱回退 — `/user` email=null 时自动调用 `/user/emails` 取 primary+verified
    - 版本: 0.18.4-SNAPSHOT → 0.19.0-SNAPSHOT (MINOR: 新功能)

- [2026-04-21] - OAuthStateService 单元测试补充
    - 新增: `OAuthStateServiceTest` (10 tests, 2 nested groups)
    - 覆盖: generateAndSaveState 正常生成/序列化失败/BIND payload, consumeState 正常消费/重放攻击/过期/无效 state/反序列化失败
    - 模式: MockitoExtension + ValueOperations mock + @Nested + shouldX_whenY 命名
    - 文件: OAuthStateServiceTest.java
    - 版本: 0.18.3-SNAPSHOT → 0.18.4-SNAPSHOT

- [2026-04-21] - EmailVerificationController Text Block 重构
    - 变更: 6 处 @ExampleObject JSON 字符串转换为 Java Text Block (""")
    - 位置: AUTH_004, USER_007 (verify), COMMON_003, USER_003, USER_007 (resend), COMMON_002
    - 格式: 单行 JSON 内容，三引号包裹（遵循 Text Block 规范）
    - 文件: EmailVerificationController.java
    - 版本: 0.18.2-SNAPSHOT → 0.18.3-SNAPSHOT

- [2026-04-20] - USER_007 ErrorCode 创建（Email Verification Bug 修复）
    - 根因: OpenAPI 文档声明 USER_002 + 409（已验证），实际代码抛 COMMON_003 (400)，语义矛盾
    - 分析: EmailVerificationController @ApiResponse 错误声明 USER_002（实际代码不抛此 ErrorCode）
    - 分析: UserValidator.assertCanVerifyEmail() 抛 ConflictException(COMMON_003)，COMMON_003 定义为 BAD_REQUEST (400) 与 ConflictException 语义矛盾
    - 修复: 创建 USER_007("Email already verified", HttpStatus.CONFLICT) 替代 COMMON_003
    - 修复: UserValidator.assertCanVerifyEmail() 使用 USER_007 替代 COMMON_003 + 自定义消息
    - 修复: EmailVerificationController @ApiResponse 4处 USER_002 → USER_007
    - 测试: ErrorCodeTest 新增 USER_007.httpStatus() 断言
    - 测试: UserValidatorTest 4处消息断言更新 "not in pending verification" → "Email already verified"
    - 文件: ErrorCode.java, UserValidator.java, EmailVerificationController.java, ErrorCodeTest.java, UserValidatorTest.java
    - 版本: 0.18.1-SNAPSHOT → 0.18.2-SNAPSHOT

- [2026-04-20] - AUTH_014 HTTP status 设计错误修复（删除）
    - 根因: AesGcmTokenEncryptor.decrypt() 失败是服务端问题（DB损坏/密钥配置错误/JCE不可用），客户端无法解决
    - 分析: HTTP 401 (Unauthorized) 语义不符（表示"客户端需提供身份凭证"），decrypt 失败非客户端问题
    - 修复: UnauthorizedException(ErrorCode.AUTH_014) → InternalServerErrorException("OAuth token decryption failed")
    - 删除: AUTH_014 enum constant（decrypt 失败不应有专属 ErrorCode，与 encrypt 保持一致）
    - 设计: 500 Internal Server Error 更准确表达服务端故障，日志级别 ERROR（含完整堆栈便于排查）
    - 文件: ErrorCode.java, AesGcmTokenEncryptor.java, ErrorCodeTest.java, AesGcmTokenEncryptorTest.java
    - 版本: 0.18.0-SNAPSHOT → 0.18.1-SNAPSHOT

- [2026-04-13] - OAuth 错误码扩展（ErrorCode AUTH 分组）
    - 更新: AUTH_013 "OAuth state validation failed" + HttpStatus.FORBIDDEN（原 401→403，语义更精准）
    - 新增: AUTH_015 "OAuth provider error" + 502 BAD_GATEWAY（上游 Provider 异常）
    - 新增: AUTH_016 "OAuth account already linked" + 409 CONFLICT（账号绑定冲突）
    - 新增: AUTH_017 "OAuth account not linked" + 400 BAD_REQUEST（用户未绑定该 Provider）
    - 新增: AUTH_018 "Cannot unlink last credential" + 400 BAD_REQUEST（无密码用户解绑唯一 OAuth）
    - 删除: USER_007/008/009（与 AUTH_015/016/017 语义重复，保持 AUTH 分组统一）
    - 修复: OAuthStateService UnauthorizedException → ForbiddenException（匹配 AUTH_013 403）
    - 测试: ErrorCodeTest 更新所有 AUTH_013-018 HTTP status 断言
    - 文件: ErrorCode.java, OAuthStateService.java, ErrorCodeTest.java
    - 验证: 全量测试通过，Spotless 通过，覆盖率达标
    - 版本: 0.17.0-SNAPSHOT → 0.18.0-SNAPSHOT

- [2026-04-12] - OAuth 审计事件扩展（AuditAction + ResourceType）
    - 新增: AuditAction.java 新增 "OAuth Integration" 区块
    - 新增: 4 个 OAuth 事件 — OAUTH_LOGIN_SUCCESS / OAUTH_LOGIN_FAILED / OAUTH_ACCOUNT_LINKED / OAUTH_ACCOUNT_UNLINKED
    - 修复: ResourceType.java 添加 OAUTH_ACCOUNT（与 DB CHECK 约束一致性）
    - 设计: OAuth 区块放置在 IAM 区块末尾（ACCOUNT_UNLOCKED 之后），符合认证领域划分
    - 决策: OAUTH_TOKEN_REFRESHED 暂不添加（GitHub token 不过期，YAGNI）
    - 文件: AuditAction.java, ResourceType.java
    - 验证: ./gradlew compileJava 通过
    - 版本: 0.16.0-SNAPSHOT → 0.17.0-SNAPSHOT

- [2026-04-12] - OAuth Domain Objects 实现
    - 新增: `auth/oauth/enums/OAuthProvider.java` — 仅 GITHUB 枚举（YAGNI）
    - 新增: `auth/oauth/crypto/OAuthTokenConverter.java` — JPA AttributeConverter，自动加解密
    - 新增: `auth/oauth/entity/UserOAuthAccount.java` — Entity 映射 user_oauth_accounts 表，无感加密
    - 新增: `auth/oauth/repository/UserOAuthAccountRepository.java` — 4 查询方法
    - 设计: Java enum 仅 GITHUB（应用层 YAGNI），DB CHECK 保留多 Provider（Schema 扩展性）
    - 设计: Converter 使用 @Autowired setter 注入（现代 Hibernate 5+）
    - 设计: accessToken + refreshToken 双字段应用 @Convert（Defense in Depth）
    - 版本: 0.15.24-SNAPSHOT → 0.16.0-SNAPSHOT

- [2026-04-12] - OAuth 加密基础设施（AES-256-GCM）
    - 新增: `auth/oauth/crypto/OAuthTokenEncryptor.java` — 加密接口（DIP）
    - 新增: `auth/oauth/crypto/AesGcmTokenEncryptor.java` — AES-256-GCM 实现，IV 12 字节随机，AEAD 防篡改
    - 新增: `SecurityProperties.OAuthProperties` — tokenEncryptionKey 配置项
    - 新增: ErrorCode.AUTH_014 "OAuth token decryption failed"
    - 新增: AesGcmTokenEncryptorTest（4 个测试：加解密还原、IV 随机性、空值处理、防篡改）
    - 版本: 待 bump

- [2026-04-11] - OAuth State 存储（Redis 方案）
    - 新增: `auth/oauth/model/OAuthStatePayload.java` — record 含 Action 枚举（LOGIN/BIND）
    - 新增: `auth/oauth/service/OAuthStateService.java` — Redis SETEX + GETDEL 原子操作
    - 新增: ErrorCode.AUTH_013 "OAuth state invalid or expired"
    - 设计: Key 格式 `oauth:state:{uuid}`，TTL 10 分钟，`getAndDelete()` 防重放攻击
    - 移除: init schema 中的 oauth_states 表（改用 Redis，零清理成本）
    - 测试: OAuthStateServiceTest (10 tests) — 2026-04-21 补充

- [2026-04-11] - OAuth 阶段启动：数据模型与基础设施准备（收敛方案）
    - 复用: 已有 `user_oauth_accounts` 表（init schema 创建），不新建 `oauth_accounts`
    - 集成: 两项基础设施直接融入 init schema（V20260303210000__init_base_schema.sql）
        - audit_logs 的 chk_audit_resource_type 追加 'OAUTH_ACCOUNT'（第 418-428 行）
        - Section 14: oauth_states 表（CSRF state 持久化，第 540-559 行）
    - 设计: oauth_states 使用 JSONB payload 区分 Login/Bind 流，expires_at 索引支持定时清理
    - 状态: init schema 已更新，独立迁移文件已删除，待提交
    - 版本: 0.15.21-SNAPSHOT → 0.15.22-SNAPSHOT

- [2026-04-11] - AGENTS.md R9 补充 @ApiResponse content + ExampleObject 规则
    - 规则: 每个 @ApiResponse 必须带 content = @Content(schema = @Schema(implementation = ...))
    - 规则: 非 200 响应必须加 examples = @ExampleObject，每个错误码示例必须不同且包含真实 JSON
    - 规则: 200 响应使用 RestApiResponse schema，错误响应使用 ErrorResponse schema + 独立示例
    - 文件: AGENTS.md (R9 OpenAPI Schema 区块扩展 4 行)

- [2026-04-11] - Swagger @ApiResponse 补充独立 Example Value
    - 修复: 3 个 Controller 共 28 个 @ApiResponse 添加 content = @Content(schema + @ExampleObject)
    - 每个错误码(400/401/403/409/429)现在显示独立的 JSON 示例，不再重复相同 Schema
    - 文件: AuthController.java, LogoutController.java, EmailVerificationController.java
    - 版本: 0.15.19-SNAPSHOT → 0.15.20-SNAPSHOT

- [2026-04-10] - AGENTS.md R9 新增 OpenAPI Schema 强制规则
    - 规则: 所有 DTO/Response 类 + 字段必须加 @Schema(description + example)
    - 规则: 校验注解不可遗漏，禁止硬编码版本号
    - 文件: AGENTS.md (R9 新增 OpenAPI Schema 区块)

- [2026-04-10] - Swagger Schemas 补充 @Schema 注解
    - 修复: 4 个 DTO 缺少 @Schema — RefreshTokenRequest, PasswordResetRequest, ForgotPasswordRequest, ResetPasswordRequest
    - 修复: 4 个 Response 缺少 @Schema — EmptyResponse, RestApiResponse, ErrorResponse(+FieldError), PagedResponse
    - 所有类 + 字段均添加 description + example，Swagger UI 现在显示完整的 Schema 文档
    - 版本: 0.15.18-SNAPSHOT → 0.15.19-SNAPSHOT

- [2026-04-10] - AGENTS.md R15 新增版本号同步检查规则
    - 教训: OpenApiConfig 硬编码 "0.5.0-SNAPSHOT" 未随项目版本更新
    - 规则: 每次更新版本号后必须全局搜索 `0.\d+\.\d+`，确认所有硬编码版本同步
    - 排除: Javadoc @since、memory-bank 历史、依赖版本号、IP 地址、Flyway baseline
    - 红线: 代码中禁止硬编码项目版本号，必须用 @Value 或 @appVersion@ 注入
    - 文件: AGENTS.md (R15 新增「版本号同步检查」区块)

- [2026-04-10] - Swagger 版本号与项目版本同步
    - 修复: OpenApiConfig.java — 硬编码 `.version("0.5.0-SNAPSHOT")` → `@Value("${info.app.version}")` 动态注入
    - 版本号来源: application.yaml `info.app.version: @appVersion@` → gradle/libs.versions.toml
    - 版本: 0.15.17-SNAPSHOT → 0.15.18-SNAPSHOT

- [2026-04-10] - 清理 ProbeController 及全部引用
    - 删除: src/main/java/com/ahogek/cttserver/probe/ProbeController.java（整个 probe/ 包）
    - 重写: GlobalExceptionHandlerTest.java — 移除 @WebMvcTest + ProbeController，8 个 probe 端点测试转为直接 handler 方法调用，删除 2 个 happy-path 测试，最终 13 个测试全部通过
    - 更新: docs/api-governance.md — `/api/v1/probe/health` → `/actuator/health/liveness`（Spring Boot Actuator 原生探针）
    - 版本: 0.15.16-SNAPSHOT → 0.15.17-SNAPSHOT
    - 注意: application.yaml 中 management.health.probes 配置保留，属于 Spring Boot Actuator K8s 原生探针，与 ProbeController 无关


- [2026-04-10] - Dockerfile BuildKit 缓存 + 容器名动态化
    - Dockerfile: --mount=type=cache,target=/root/.gradle 加速依赖解析和 bootJar
    - docker-compose.yaml: 新增 image: ctt-server-test，container_name: ${CONTAINER_NAME:-ctt-server}
    - Jenkinsfile: Deploy 使用 CONTAINER_NAME=ctt-server-test，Health Check 动态读取容器名
    - 文件: Dockerfile, docker-compose.yaml, Jenkinsfile
    - 版本: 0.15.15-SNAPSHOT → 0.15.16-SNAPSHOT


## Recent Changes (Last 30 Days)

- [2026-04-10] - Dockerfile APP_PORT 动态化 + 审查修复
    - Dockerfile: ARG APP_PORT=8080 + ENV SERVER_PORT=${APP_PORT} + EXPOSE ${APP_PORT}
    - docker-compose.yaml: build.args 传入 APP_PORT，端口映射和 healthcheck 全部联动
    - docker-compose.yaml: healthcheck curl → wget → curl（安装 curl 到 JRE 镜像）
    - 默认 8080，Jenkins 通过 .env 注入 APP_PORT=8004 覆盖
    - Jenkinsfile: .env 生成新增 JWT_SECRET_KEY, MAIL_FROM_*, FRONTEND_BASE_URL, SPRING_PROFILES_ACTIVE
    - Jenkinsfile: 删除 environment 块中冗余的 APP_PORT
    - .gitignore: 恢复 .env.* 通配符，保留 !.env.example 排除
    - .env.example: 新增完整环境变量模板（APP_PORT, JWT_SECRET_KEY, MAIL_FROM_*, FRONTEND_BASE_URL 等）
    - README.md: Docker Compose 端口表新增 APP_PORT
    - 文件: Dockerfile, docker-compose.yaml, Jenkinsfile, .gitignore, .env.example, README.md
    - docker-compose.yaml: environment 注入 SERVER_PORT 和 APP_PORT，修复 healthcheck 容器内变量缺失
    - 版本: 0.15.12-SNAPSHOT → 0.15.15-SNAPSHOT
    - 审查: 4 agent 并行审查，修复 P0（healthcheck curl, JWT_SECRET_KEY 缺失）+ P1（版本号跳过, .gitignore, README 文档, Jenkinsfile 冗余）

- [2026-04-10] - AGENTS.md 新增 R17 禁止操作：严禁在 master 上做任何修改
    - 教训: 曾犯在 master 上直接修改 Jenkinsfile 再反向 cherry-pick 到 develop 的严重错误
    - 规则: 所有修改必须先在 develop 上完成，再从 develop cherry-pick 到 master
    - master 只接受 cherry-pick，绝不允许直接在 master 上改任何代码/配置/文档
    - 文件: AGENTS.md (R17 禁止操作新增两条)

- [2026-04-10] - Jenkins CI/CD 测试流水线
    - 新增: Jenkinsfile (原 Jenkinsfile-test 重命名)
    - checkout → configure (.env 自动生成 + application-local.yaml 模板) → infra up → deploy → health check
    - 使用 master 分支，docker compose 自动构建部署 app 服务
    - 健康检查轮询 + 超时日志输出
    - APP_PORT: 8080 → 8004
    - 文件: Jenkinsfile
    - 版本: 0.15.10-SNAPSHOT → 0.15.12-SNAPSHOT

- [2026-04-10] - Docker 化部署：Dockerfile + docker-compose 集成 app 服务
    - 新增: Dockerfile (多阶段构建: eclipse-temurin:25-jdk → 25-jre-noble)
    - 修改: docker-compose.yaml 新增 app 服务，含 healthcheck、depends_on、环境变量注入
    - 修改: Mailpit 端口动态化 (MAIL_SMTP_EXTERNAL_PORT, MAIL_UI_EXTERNAL_PORT)
    - 修改: APP_EXTERNAL_PORT 支持自定义宿主机端口 (默认 8080)
    - 修改: README.md 更新 Docker Compose 启动说明
    - 文件: Dockerfile, docker-compose.yaml, README.md
    - 版本: 0.15.8-SNAPSHOT → 0.15.9-SNAPSHOT

- [2026-04-10] - application-local.yaml.template mail 配置环境变量化
    - 修复: mail.host/mail.port 硬编码 → \${MAIL_SMTP_HOST:localhost}/\${MAIL_SMTP_PORT:1025}
    - 原因: 与 .env 变量命名保持一致，支持自定义 SMTP 地址
    - 文件: application-local.yaml.template
    - 版本: 0.15.7-SNAPSHOT → 0.15.8-SNAPSHOT

- [2026-04-10] - Docker Compose PostgreSQL 18+ 兼容性修复
    - 问题: postgres:latest (18+) 要求 volume 挂载到 /var/lib/postgresql 而非 /var/lib/postgresql/data
    - 修复: volumes 路径改为 postgres_data:/var/lib/postgresql
    - 原因: 18+ 镜像使用 pg_ctlcluster 管理，需要父目录以支持 pg_upgrade --link
    - 文件: docker-compose.yaml
    - 版本: 0.15.6-SNAPSHOT → 0.15.7-SNAPSHOT

- [2026-04-10] - LoginAndTokenIntegrationTest 补充 Refresh Token 重放 E2E 断言
    - 问题: 原有测试只验证 DB 状态（rt1 revoked, rt2 active = 1），未实际断言重放 rt1 返回 403 AUTH_009
    - 修复: 新增 4 步 E2E 断言链
        - Then 1: 重放 rt1 → 403 AUTH_009（reuse detected）
        - Then 2: DB 验证 rt1 仍为 revoked
        - Then 3: DB 验证 rt2 仍为 active（revokeAllUserTokens 随事务回滚）
        - Then 4: rt2 仍可成功刷新（kill switch 因事务回滚无效）
    - 文件: LoginAndTokenIntegrationTest.java
    - 验证: 全量测试通过
    - 版本: 0.15.4-SNAPSHOT（PATCH 修复，版本号不变）

- [2026-04-10] - LogoutIntegrationTest E2E 测试
    - 新增: LogoutIntegrationTest (11 个 E2E 测试，3 个 @Nested 组)
    - Single Session Logout (7 测试): 正常登出吊销 token、幂等登出、空白 token 验证 (400 COMMON_003)、BOLA 防护、不存在 token 幂等、null token 验证、过期 token 幂等
    - Global Logout / Kill Switch (2 测试): 多设备全量吊销、无活跃 token 幂等
    - Unauthenticated Access (2 测试): 无 JWT 访问 /logout 和 /logout-all 返回 401
    - 技术要点: MockMvcTester 统一断言模式，JdbcClient DB 状态核查
    - 技术要点: TokenUtils.hashToken() 验证 token_hash 列
    - 技术要点: @AfterEach 清理顺序 audit_logs → refresh_tokens → login_attempts → users (JDBC-only)
    - 技术要点: countActiveTokensForUser 添加 expires_at > NOW() 匹配 revokeAllUserTokens 查询语义
    - 技术要点: assertTokenRejected() helper 消除重复断言
    - 技术要点: 审计日志验证 (LOGOUT_SUCCESS, SECURITY_ALERT, LOGOUT_ALL_DEVICES)
    - 修复: LogoutRequest.java 补充 Swagger @Schema 注解 + ValidationConstants.MSG_NOT_BLANK
    - 修复: LogoutController.java @ApiResponse 错误代码 (AUTH_003 → COMMON_003, AUTH_001 → missing or invalid JWT)
    - 审查: 4 agent 并行审查 (逻辑 + 风格 + 架构 + 文档)，修复 P0/P1/P2 全部发现
    - 手动 QA: 注册→验证→登录→单设备登出→全设备登出 全链路验证通过
    - 文件: LogoutIntegrationTest.java, LogoutRequest.java, LogoutController.java, JacksonConfig.java, developer-handbook.md, techContext.md
    - 验证: 全量测试通过，Spotless 通过
    - 版本: 0.15.3-SNAPSHOT → 0.15.4-SNAPSHOT

- [2026-04-10] - PasswordResetIntegrationTest E2E 测试
    - 新增: PasswordResetIntegrationTest (2 个 E2E 测试场景)
    - 测试场景 1: 完整密码重置链路 → 登录产生 token → 请求重置 → mail_outbox 提取 token → 确认重置 → 验证所有 refresh_tokens 被吊销 → 旧密码失败 → 新密码登录成功
    - 测试场景 2: Token 过期时间旅行 → JdbcClient 修改 expires_at 为 1 小时前 → 确认重置返回 401 AUTH_002
    - 技术要点: mail_outbox 表查询 + 正则提取 token (body_html 列)
    - 技术要点: JdbcClient "时间旅行" 测试过期逻辑
    - 技术要点: countActiveRefreshTokens() 验证 Kill Switch 行为
    - 文件: PasswordResetIntegrationTest.java
    - 验证: 2 个测试全部通过
    - 手动 QA: Swagger UI 全链路验证 18 个测试用例 100/100 通过
    - 版本: 0.15.2-SNAPSHOT → 0.15.3-SNAPSHOT

- [2026-04-10] - LoginAndTokenIntegrationTest E2E 测试
    - 新增: LoginAndTokenIntegrationTest (3 个 E2E 测试场景)
    - 测试场景 1: 正常登录 → 获取双 Token → 用 Access Token 访问受保护接口 200
    - 测试场景 2: 错误密码 5 次 → 第 6 次触发锁定 (403 AUTH_004 + retryAfter) → DB 状态 LOCKED
    - 测试场景 3: 刷新令牌轮换 → 重放旧 Token → 403 AUTH_009 → 验证事务回滚后 rt2 仍活跃
    - 技术要点: MockMvc 提取响应体 + MockMvcTester 断言受保护接口
    - 技术要点: UserRepository.saveAndFlush() 替代 TestEntityManager 避免事务问题
    - 技术要点: 记录 TokenRefreshService 事务回滚导致 revokeAllUserTokens 失效的行为
    - 文件: LoginAndTokenIntegrationTest.java
    - 验证: 3 个测试全部通过
    - 版本: 0.15.1-SNAPSHOT (待更新)
    - 新增: RegistrationAndVerificationIntegrationTest (6 个 E2E 测试场景)
    - 测试场景 1-3: 完整注册/重复注册/Token过期+重发验证
    - 测试场景 4-6: 注册输入验证（无效邮箱/弱密码/空邮箱）
    - 修复: LogoutController NPE Bug (@AuthenticationPrincipal Jwt → CurrentUser)
    - 修复: LogoutControllerTest 同步更新 (jwt() → authentication(createAuth()))
    - 修复: 审查发现的全路径类名、冗余断言、注释分裂等问题
    - 文档: developer-handbook.md 新增 Logout Behavior 章节
    - 文件: RegistrationAndVerificationIntegrationTest.java, LogoutController.java,
      LogoutControllerTest.java, developer-handbook.md
    - 验证: 全量测试通过，Spotless 通过，覆盖率达标
    - 手动验证: 注册→验证→登录→刷新→登出 全链路 200，错误路径覆盖完整
    - 版本: 0.15.0-SNAPSHOT → 0.15.1-SNAPSHOT

- [2026-04-10] - Login lockout response includes retryAfter timestamp
    - 新增: AccountLockedException extends BusinessException with retryAfter field
    - 新增: ErrorResponse.retryAfter 字段（nullable Instant）+ withRetryAfter() 方法
    - 新增: LockoutStrategyPort.getRetryAfter() + DbLockoutStrategy 实现
    - 修改: LoginAttemptService 抛出 AccountLockedException 替代 ForbiddenException
    - 修改: GlobalExceptionHandler 处理 AccountLockedException，设置 Retry-After HTTP Header
    - 文件: ErrorResponse.java, AccountLockedException.java, BusinessException.java,
      LockoutStrategyPort.java, DbLockoutStrategy.java, LoginAttemptService.java,
      GlobalExceptionHandler.java, LoginAttemptServiceTest.java, GlobalExceptionHandlerTest.java
    - 测试: 全量通过，新增 2 个 GlobalExceptionHandler 测试
    - 影响: 前端可渲染倒计时 UI，网关层可通过 Retry-After header 拦截高频重试
    - 版本: 0.14.0-SNAPSHOT → 0.15.0-SNAPSHOT

- [2026-04-09] - Audit logging for account lock/unlock events
    - 新增: ACCOUNT_UNLOCKED 到 AuditAction enum
    - 注入: AuditLogService 到 LoginAttemptService, LoginAttemptCleanupScheduler
    - 审计点: recordFailure() 锁定账户时 logFailure(ACCOUNT_LOCKED)
    - 审计点: checkLockStatus() 自动解锁时 logSuccess(ACCOUNT_UNLOCKED)
    - 审计点: PasswordResetService.resetPassword() 解锁时 logSuccess(ACCOUNT_UNLOCKED)
    - 审计点: LoginAttemptCleanupScheduler.unlockExpiredAccounts() 批量解锁时 logSuccess(ACCOUNT_UNLOCKED)
    - 文件: AuditAction.java, LoginAttemptService.java, LoginAttemptCleanupScheduler.java, PasswordResetService.java
    - 测试: LoginAttemptServiceTest, PasswordResetServiceTest, LoginAttemptCleanupSchedulerTest (共 39 tests 通过)
    - 影响: 所有账户锁定/解锁操作均有审计日志，复用现有 SecurityAuditEvent + AuditEventListener 基础设施
    - 版本: 0.13.1-SNAPSHOT → 0.14.0-SNAPSHOT

- [2026-04-09] - Batch account unlock scheduler (hybrid: lazy + scheduled)
    - 问题: LOCKED 用户如不再登录则永久锁定，Admin 面板统计数据失真
    - 方案: 在现有 LoginAttemptCleanupScheduler 中追加 unlockExpiredAccounts() 定时任务
    - 实现: 遍历所有 LOCKED 用户，检查滑动窗口内无尝试记录 → user.reactivate()
    - 文件: LoginAttemptCleanupScheduler.java, UserRepository.java
    - 测试: LoginAttemptCleanupSchedulerTest (5 个新用例)
    - 影响: 后台数据底座定期清理，Admin 面板显示准确锁定状态
    - 验证: 全量测试通过，覆盖率达标

- [2026-04-09] - Login lockout integration + transaction propagation fix
    - 问题: @Transactional 回滚导致 recordFailure 写入的 login_attempts 记录被回滚，暴力破解防护失效
    - 修复: LoginAttemptService.checkLockStatus/recordFailure/recordSuccess 改为 REQUIRES_NEW 传播
    - checkLockStatus 返回 User 实体（auto-unlock 后外层使用刷新后的状态）
    - UserLoginService.validateUserStatus 使用返回的 checkedUser 进行状态检查
    - PasswordResetService.resetPassword 添加 user.reactivate() 确保外层事务感知状态变更
    - LockoutIntegrationTest 移除类级 @Transactional（与 REQUIRES_NEW 不兼容），改用 @AfterEach 清理
    - PasswordResetServiceTest 修正断言（LOCKED → ACTIVE）
    - 文件: LoginAttemptService.java, UserLoginService.java, PasswordResetService.java
    - 测试: LockoutIntegrationTest.java, UserLoginServiceTest.java, PasswordResetServiceTest.java
    - 影响: 安全记录独立于外层事务，密码错误不会回滚失败计数
    - 验证: 全量测试 662 tests 通过

- [2026-04-09] - Login lockout 迁移到 login_attempts 表
    - 新建: LoginAttempt entity + Repository + 迁移文件
    - 重写: DbLockoutStrategy 使用 LoginAttemptRepository 替代 User 实体字段
    - 删除: User 实体锁字段（failedLoginAttempts, lastFailureTime, lockedUntil）
    - 新增: User @Version 乐观锁 + TOCTOU 竞态修复（重试机制）
    - 新增: LoginAttemptCleanupScheduler 定时清理过期记录
    - API 改为 email-based: recordFailure(email, ip), recordSuccess(email), isLocked(email)
    - 文件: 23 files changed
    - 验证: 全量测试通过

- [2026-04-08] - 账号锁定策略完整实现
    - 功能: 防止暴力破解，失败计数超阈值自动临时锁定，锁定到期自动解锁
    - 配置项:
        - `ctt.security.password.max-failed-attempts` (默认 5)
        - `ctt.security.password.failure-window-seconds` (默认 900, 15分钟滑动窗口)
        - `ctt.security.password.lock-duration` (默认 30m)
        - `ctt.security.password.storage` (DB/Redis, 默认 DB)
    - 架构: Strategy Pattern (LockoutStrategyPort 接口 + DB/Redis 双实现)
    - 新建: LockoutStorageType, LockoutStrategyPort, DbLockoutStrategy, RedisLockoutStrategy, LockoutConfig
    - 修改: User.java, SecurityProperties.java, UserLoginService.java
    - 测试: DbLockoutStrategyTest (16个), SecurityPropertiesTest (27个), UserLoginServiceTest

## Architectural Decisions (Permanent)

- [2026-04-09] - Lockout 使用 login_attempts 滑动窗口而非 locked_until 时间戳
- [2026-04-09] - 混合解锁模式：登录时懒解锁（毫秒级精准）+ 定时扫表（数据底座整洁）
- [2026-04-09] - 安全记录使用 REQUIRES_NEW 传播，独立于外层事务回滚
- [2026-04-08] - 账号锁定采用 Strategy Pattern，支持 DB/Redis 双后端
- [2026-03-21] - 邮件生命周期审计完整覆盖：ENQUEUED → SENT/FAILED/EXHAUSTED
- [2026-03-21] - Audit Details 标准化 (GDPR 合规): `recipientMasked` 字段脱敏
- [2026-03-21] - 指数退避重试策略：`delay = min(base * multiplier^attempt, maxDelay) ± jitter`
- [2026-03-21] - 僵尸记录恢复机制：`resetStuckSendingJobs()` 批量更新 SENDING → PENDING
- [2026-03-20] - MailOutboxPoller + MailOutboxProcessor: @Scheduled 轮询 + REQUIRES_NEW 事务隔离
