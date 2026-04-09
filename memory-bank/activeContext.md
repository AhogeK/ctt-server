# Active Context

## Recent Changes (Last 30 Days)

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
