- [2026-03-22] - EmailVerificationToken Entity 字段补全
    - 添加 `email`, `purpose`, `sentAt`, `requestIp`, `userAgent` 字段
    - 添加 `PURPOSE_REGISTER_VERIFY`, `PURPOSE_CHANGE_EMAIL` 常量
    - 匹配数据库 schema (V20260303210000__init_base_schema.sql)
    - `TokenFixtures.java`: 更新 `EmailVerificationTokenBuilder` 添加新字段

- [2026-03-22] - EmailVerificationTokenRepository 方法扩展
    - `findByUserIdAndPurpose(UUID, String)`: 按用户和用途查询
    - `existsByUserIdAndPurposeAndConsumedAtIsNull(UUID, String)`: 快速检查未消费 token
    - 利用复合索引 `idx_email_verification_lookup` (leftmost prefix)
    - 新增 6 个测试用例覆盖新方法

- [2026-03-22] - TokenUtils 重构消除重复代码
    - 将 `createVerificationToken()` 从 UserService/EmailVerificationService 移至 TokenUtils
    - `TokenPair` record 移至 TokenUtils 作为 public static record
    - 方法签名: `static TokenPair createVerificationToken(userId, ttl, tokenRepository)`

- [2026-03-22] - EmailVerificationService 改进
    - 注入 `UserValidator` 依赖
    - 使用 `userValidator.assertCanVerifyEmail(user)` 替代内联检查
    - 完善审计日志 `EMAIL_VERIFICATION_SENT`
    - 修复 Controller rate limit: `windowSeconds = 300` → `60` (3/1min)

- [2026-03-22] - 本地开发配置优化
    - `application-local.yaml`: `format_sql: false` 关闭 SQL 格式化
    - 减少定时任务 SQL 日志刷屏，保留 SQL 可见性

- [2026-03-22] - 文档更新
    - `README.md`: 添加 API Endpoints 表格 (注册/验证/重发)
    - `docs/developer-handbook.md`: 添加"邮件验证实现"章节

- [2026-03-22] - AGENTS.md 规则优化
    - R2 增强: 添加详细更新流程和格式规范
    - R13 新增: 记忆文件维护规则 (行数限制 + 修剪规则)
    - R14 新增: AGENTS.md 自更新机制

- [2026-03-22] - MailOutboxService 集成测试完成
    - `MailOutboxServiceIntegrationTest.java`: 4 个集成测试用例
    - 测试结果：4/4 通过，完整构建通过

- [2026-03-22] - AGENTS.md R8 新增变更溯源原则
    - 发现与预期不一致时，优先猜想"是否被用户修改了"
    - 三步流程：检查 git history → 验证业务逻辑 → 更新记忆适应新逻辑
    - 禁止揣测"AI 忘了改"或"这应该是错的"

## 架构决策 (保留)

- [2026-03-21] - 邮件生命周期审计完整覆盖: ENQUEUED → SENT/FAILED/EXHAUSTED
- [2026-03-21] - Audit Details 标准化 (GDPR 合规): `recipientMasked` 字段脱敏
- [2026-03-21] - 指数退避重试策略: `delay = min(base * multiplier^attempt, maxDelay) ± jitter`
- [2026-03-21] - 僵尸记录恢复机制: `resetStuckSendingJobs()` 批量更新 SENDING → PENDING
- [2026-03-20] - MailOutboxPoller + MailOutboxProcessor: @Scheduled 轮询 + REQUIRES_NEW 事务隔离

## 下一步行动

1. 监控指标暴露 (Prometheus) - 待开始