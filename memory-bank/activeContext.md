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