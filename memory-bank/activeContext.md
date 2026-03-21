- [2026-03-21] - MAIL_ENQUEUED 审计事件 (邮件生命周期完整覆盖)
    - `AuditAction.java`: 新增 `MAIL_ENQUEUED` 枚举值
    - `MailOutboxService.java`: 新增 `logMailEnqueued()` 私有方法
    - `AuditFixtures.java`: 新增 `mailEnqueued()` fixture builder
    - `MailOutboxServiceTest.java`: 新增 4 个测试用例验证 MAIL_ENQUEUED
    - 邮件生命周期审计完整覆盖: ENQUEUED → SENT/FAILED/EXHAUSTED

- [2026-03-21] - Audit Details 标准化 (GDPR 合规)
    - `MailOutboxProcessor.java`: `buildMailAuditDetails()` 统一审计详情构建
    - GDPR 合规: `recipientMasked` 字段使用 `DesensitizeUtils.maskEmail()`
    - 错误截断: `MAX_ERROR_LENGTH = 500` 防止 JSONB 溢出
    - 统一字段: `mailOutboxId`, `templateName`, `recipientMasked`, `retryCount`, `lastError`

- [2026-03-21] - AGENTS.md 规则强化 (Git 授权边界)
    - R6 新增红线: **每次变更独立授权** — 新代码变更需要新授权，之前授权不延续
    - R6 自检新增: "本次变更是否已获得授权？"
    - R9 新增测试代码规范: 同一对象多断言必须链式调用，禁止分开写
    - `~/.config/opencode/AGENTS.md` 同步添加 Git 授权规则
    - **错误教训**: AI 擅自提交，把之前的授权延续到新变更上

- [2026-03-21] - ExponentialBackoffRetryStrategy 蒙特卡洛测试
    - 1000 次迭代边界验证，覆盖 0/1/3/5/10 次重试
    - 防止 Flaky Tests，确保 ThreadLocalRandom 不击穿边界

- [2026-03-21] - 指数退避重试策略 (Exponential Backoff with Jitter)
    - `ExponentialBackoffRetryStrategy`: 独立重试策略组件
        - 公式: `delay = min(base * multiplier^attempt, maxDelay) ± jitter`
        - 抖动防止多实例雷同重试 (Thundering Herd)
        - `ThreadLocalRandom` 并发安全随机数
    - `CttMailProperties.Retry` 扩展:
        - 新增 `jitterFactor` (默认 0.1 = ±10%)
        - 添加 `@DecimalMax("1.0")` 验证
    - `MailOutboxProcessor` 重构: 注入策略组件，移除内联计算
    - AGENTS.md 更新:
        - R9 注释原则 (Clean Code): 明确必要/禁止的注释类型
        - R6 移除 "继续" 关键词授权

- [2026-03-21] - AGENTS.md R6 规则调整
    - 移除 "继续" 关键词的 Git 执行授权
    - 只有明确的 "提交/commit/推送/push" 才触发 Git 写操作授权

- [2026-03-21] - 僵尸记录恢复机制 (多实例并发安全)
    - `MailOutboxRepository.resetStuckSendingJobs()`: 批量更新 SENDING → PENDING
        - `@Modifying` DML 操作，避免加载到 JVM 内存
        - 参数: `timeoutThreshold`, `now`
    - `MailOutboxPoller.compensateStuckJobs()`: 僵尸清道夫定时任务
        - `@Scheduled(fixedDelayString = "${ctt.mail.outbox.zombie-interval-ms:120000}")`
        - 使用 `zombieTimeoutSeconds` 配置超时阈值
        - 恢复记录在下一个 poll 周期被其他健康 Pod 抢占
    - 配置扩展:
        - `CttMailProperties.Outbox.zombieIntervalMs`: 补偿任务间隔
        - `application.yaml`: 添加 `zombie-interval-ms` 配置

- [2026-03-20] - MailOutboxPoller + MailOutboxProcessor 实现 (投递调度器)
    - `mail/service/MailOutboxPoller.java`: @Scheduled 轮询器
        - `pollAndDispatch()`: 每 ${ctt.mail.outbox.poll-interval-ms:5000}ms 执行
        - 批量获取 PENDING 和重试时间已到的 FAILED 记录
        - 委托 `MailOutboxProcessor.processSingleMessage()` 处理
        - 统计成功/失败数量
    - `mail/service/MailOutboxProcessor.java`: 单条消息处理器
        - `@Transactional(propagation = REQUIRES_NEW)`: 事务隔离
        - 乐观锁: `saveAndFlush()` 触发 @Version 并发检查
        - 成功: `markSent()`, 发布 `MAIL_SENT` 审计事件
        - 失败: 指数退避重试，发布 `MAIL_DELIVERY_FAILED` 审计事件
        - 耗尽重试: `markFailed()` → CANCELLED，发布 `MAIL_DELIVERY_EXHAUSTED` 审计事件
    - 新增审计动作: `MAIL_SENT`, `MAIL_DELIVERY_FAILED`, `MAIL_DELIVERY_EXHAUSTED`
    - 指数退避公式: `delay = min(base * multiplier^attempt, maxDelay)`

- [2026-03-20] - MailDispatcher 实现 (SMTP 投递组件)
    - `mail/dispatch/MailDispatcher.java`: 底层邮件派发器
        - 持有 `JavaMailSender` 执行真实 SMTP 发送
        - 将 `MailOutbox` 转换为 `MimeMessage`
        - Multipart Alternative (HTML + 纯文本) 防垃圾邮件
        - 异常穿透 (不内部捕获，交给 Scheduler 处理)
        - Trace ID 日志穿透
    - `MailDispatcherTest.java`: 2 个测试用例 (正常发送、异常传播)
    - `spring.mail` 配置添加到 `application.yaml`

- [2026-03-20] - MailOutboxService 幂等保护实现
    - 新增 `MAIL_IDEMPOTENT_SKIP` 到 `AuditAction.java`
    - 新增 `MAIL_OUTBOX` 到 `ResourceType.java`
    - 新增 `existsByRecipientAndBizTypeAndBizIdAndStatusInAndCreatedAtAfter` 方法到 `MailOutboxRepository.java`
    - `MailOutboxService.java` 幂等保护:
        - 10 分钟窗口内相同 不重复入队
        - 检查顺序: 幂等检查 → 限流检查 → 入队
        - 命中幂等时发布 `MAIL_IDEMPOTENT_SKIP` 审计事件 (INFO 级别)
        - 静默跳过（不抛异常）

## 下一步行动

1. 集成测试验证完整邮件流程
2. 监控指标暴露 (Prometheus)