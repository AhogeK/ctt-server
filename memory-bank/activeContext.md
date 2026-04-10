# Active Context

## Recent Changes (Last 30 Days)

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
