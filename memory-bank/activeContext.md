# Active Context

## Recent Changes (Last 30 Days)

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
