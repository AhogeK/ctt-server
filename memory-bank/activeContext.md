- [2026-03-19] - MailOutboxRepository 测试基础设施重构
    - 创建 `MailOutboxFixtures.java`：
        - Object Mother + Builder 模式，与 UserFixtures/AuditFixtures 风格对齐
        - 预设方法：pending(), retryableFailed(), notYetRetryableFailed(), exhaustedFailed(), sending(), sent(), cancelled()
    - 更新 `PersistedFixtures.java`：
        - 添加 mailOutbox(), pendingMail(), retryableFailedMail(), sentMail() 方法
    - 更新 `BaseRepositoryTest.java`：
        - 添加 `@EnableJpaAuditing` 支持 `@CreatedDate/@LastModifiedDate`
        - MailOutbox 使用 Spring Data JPA 审计注解而非 Hibernate @CreationTimestamp
    - 重写 `MailOutboxRepositoryTest.java`：
        - 从 `@BaseIntegrationTest` 改为 `@BaseRepositoryTest` (DataJpa slice)
        - 使用 `@Nested` 组织测试（findPendingJobs / findByTraceId / countDuplicates / countByStatus）
        - 使用 `em.persistFlushFind()` 触发 JPA 生命周期回调
        - 测试覆盖：13 个测试用例，覆盖 4 个查询方法
    - 添加数据库索引：
        - `idx_mail_outbox_dedup (recipient, biz_type, status, created_at)` 支持 countDuplicates 查询
    - 验证：
        - 全部测试通过（269 tests）
        - 符合 AGENTS.md 规则 10（代码使用英文）

- [2026-03-18] - MailOutboxRepository 最终版本实现
    - `findPendingJobs(Instant, Pageable)`：调度器轮询，支持 PENDING + 可重试 FAILED
    - `findByTraceId(String)`：按 OpenTelemetry trace ID 查询
    - `countDuplicates(...)`：防重复投递频率校验 (rate-guard)
    - `countByStatus()`：按状态分组统计（监控仪表盘）
    - 索引：idx_mail_outbox_dispatch, idx_mail_outbox_dedup, idx_mail_outbox_trace_id, idx_mail_outbox_retry

- [2026-03-18] - MailOutbox 实体强化
    - 添加 @Version 乐观锁字段，防止并发重复投递
    - 优化状态机方法：canRetry(), markSending(), markSent(), markFailed(), cancel()
    - MailOutboxStatus 枚举添加 description 和 isTerminal()

## 下一步行动

1. 实现 MailOutboxService 业务逻辑
2. 实现邮件发送调度器
3. 集成 Resend API 生产环境发送