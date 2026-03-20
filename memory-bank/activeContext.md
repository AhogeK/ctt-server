- [2026-03-20] - MailDispatcher 实现 (SMTP 投递组件)
    - `mail/dispatch/MailDispatcher.java`: 底层邮件派发器
        - 持有 `JavaMailSender` 执行真实 SMTP 发送
        - 将 `MailOutbox` 转换为 `MimeMessage`
        - Multipart Alternative (HTML + 纯文本) 防垃圾邮件
        - 异常穿透 (不内部捕获，交给 Scheduler 处理)
        - Trace ID 日志穿透
    - `MailDispatcherTest.java`: 2 个测试用例 (正常发送、异常传播)

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

1. 实现 MailOutboxScheduler (调度器)
    - 轮询 PENDING 记录
    - 调用 MailDispatcher 发送
    - 状态转换: PENDING → SENDING → SENT/FAILED
    - 指数退避重试
2. 多实例锁 (分布式锁)