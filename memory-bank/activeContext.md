- [2026-03-20] - MailOutboxService 幂等保护实现
    - 新增 `MAIL_IDEMPOTENT_SKIP` 到 `AuditAction.java`
    - 新增 `MAIL_OUTBOX` 到 `ResourceType.java`
    - 新增 `existsByRecipientAndBizTypeAndBizIdAndStatusInAndCreatedAtAfter` 方法到 `MailOutboxRepository.java`
    - `MailOutboxService.java` 幂等保护:
        - 10 分钟窗口内相同 不重复入队
        - 检查顺序: 幂等检查 → 限流检查 → 入队
        - 命中幂等时发布 `MAIL_IDEMPOTENT_SKIP` 审计事件 (INFO 级别)
        - 静默跳过（不抛异常）
    - `MailOutboxServiceTest.java`: +5 个幂等测试用例 (共 17 个测试)
    - 更新 `ResourceTypeTest.java` 和 `AuditActionTest.java` 以包含新增枚举值

- [2026-03-20] - MailOutboxService 完成 (Transactional Outbox 写入侧)
    - `MailOutboxService.java`: 邮件入队服务
        - `enqueueVerificationEmail(userId, username, email, token)`: 注册验证邮件
        - `enqueuePasswordResetEmail(userId, username, email, token)`: 密码重置邮件
        - 限流防护: 1分钟/5分钟窗口，最多3次
        - 预渲染模板存储 (bodyHtml/bodyText)
        - 显式初始化 status=PENDING, retryCount=0, nextRetryAt=now()
    - 扩展 `CttMailProperties`: 添加 `Frontend` record (baseUrl)
    - 更新 `application.yaml`/`application-test.yaml`: 添加 `ctt.mail.frontend.base-url`

## 下一步行动

1. 实现邮件发送调度器 (MailOutboxScheduler)
2. 集成 Resend API 生产环境发送