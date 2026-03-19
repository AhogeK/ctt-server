- [2026-03-20] - 测试覆盖率提升 (87% → 92% 指令, 76% → 84% 分支)
    - 新增测试文件：
        - `UserValidatorTest.java`: 18 个领域规则测试（邮箱唯一性/登录限制/邮箱验证/用户存在）
        - `UserServiceTest.java`: 4 个业务逻辑测试（注册流程/密码编码/审计事件）
        - `SpelExpressionResolverTest.java`: 6 个工具类测试（参数解析/属性访问/null处理）
        - `AuthControllerTest.java`: 6 个 REST API 测试（请求验证/错误响应）
    - 扩展 `GlobalExceptionHandlerTest.java`: +4 异常处理测试
    - 修复 `AuthController.java`: 添加 @PublicApi 注解到 register() 端点
    - 完善 `InternalServerErrorException.java`: 添加 Javadoc
    - 更新 `AGENTS.md`: 添加 R12 依赖管理规则

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

## 下一步行动

1. 实现 MailOutboxService 业务逻辑
2. 实现邮件发送调度器
3. 集成 Resend API 生产环境发送