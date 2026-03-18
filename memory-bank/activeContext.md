- [2026-03-18] - MailOutboxRepository 最终版本实现
    - 修复 `findPendingJobs` 方法（原 `findDispatchable`）：
        - 问题：`@Param("limit") int limit` 参数在 JPQL 中未使用，完全失效
        - 修复：改用 `Pageable pageable` 参数，由 Spring Data 自动注入 LIMIT/OFFSET
        - 收益：调用方可动态调整批次大小和排序，无需修改 Repository
        - 实现细节：
          - 使用全限定枚举类名 `com.ahogek.cttserver.mail.enums.MailOutboxStatus.PENDING`
          - 移除 JPQL 中的 `ORDER BY`，由 `Pageable` 的 `Sort` 驱动，保持可组合性
          - 覆盖两种情况：
            - `status = PENDING`：新邮件，始终可投递
            - `status = FAILED AND nextRetryAt <= now AND retryCount < maxRetries`：重试邮件
    - 新增 `countDuplicates` 方法（防重复投递频率校验）：
        - 用途：enqueue 前的 rate-guard，防止短时间向同一收件人发送过多相同类型邮件
        - 参数：
          - `recipient`：目标邮箱地址
          - `bizType`：业务类型（如 "REGISTER_VERIFY"）
          - `statuses`：状态列表（通常 `[PENDING, SENDING, SENT]`）
          - `windowStart`：滚动时间窗口起始点
        - 使用场景示例：
          ```java
          long recent = repo.countDuplicates(
              recipient, "REGISTER_VERIFY",
              List.of(PENDING, SENDING, SENT),
              Instant.now().minus(10, ChronoUnit.MINUTES)
          );
          if (recent >= 3) throw new TooManyRequestsException(...);
          ```
        - 为什么不用派生方法名：
          - `countByRecipientAndStatusAndCreatedAtAfter` 只能绑定单个 `status`
          - 防重复需要同时匹配多个状态（`IN` 查询），必须用 `@Query`
    - 保留方法：
        - `findByTraceId(String traceId)`：按 OpenTelemetry trace ID 查询
        - `countByStatus()`：按状态分组统计（监控仪表盘用）
    - 索引依赖：
        - `findPendingJobs` 依赖 `idx_mail_outbox_dispatch (status, next_retry_at, created_at)`
        - 该索引已在初始化脚本中创建，无需额外 DDL
    - 验证：
        - 编译成功
        - 全部测试通过（269 tests）
        - Spotless 格式化通过

- [2026-03-18] - 代码审查修复（Mail Outbox 最终版本）
    - 修复 AGENTS.md 规则 10 违规（Medium）：
        - 问题：MailOutboxStatus 枚举 description 使用中文
        - 修复：改为英文描述
          - "待投递" → "Pending delivery"
          - "投递中" → "Sending"
          - "已送达" → "Delivered"
          - "投递失败" → "Delivery failed"
          - "已取消" → "Cancelled"
        - 影响：符合 AGENTS.md 规则 10（代码中禁止使用中文）
    - 修复 `cancel()` 方法状态验证（Low）：
        - 问题：Javadoc 声称"从非终态转换"但未校验，允许对 SENT 记录调用 cancel()
        - 修复：添加 `if (status.isTerminal()) throw new IllegalStateException(...)`
        - 影响：状态机完整性得到保障，防止非法状态转换
    - 修复 DDL 注释不一致（Low）：
        - 问题：max_retries 注释说"标记为 FAILED"但实际转为 CANCELLED
        - 修复：改为"Maximum number of delivery retries before auto-cancellation"
        - 影响：注释与实际行为一致
    - 精简 activeContext.md（Low）：
        - 问题：文件 334 行，违反 AGENTS.md 200 行限制
        - 修复：只保留最近 Mail Outbox 变更记录，删除较旧历史
        - 影响：符合 200 行限制，聚焦最近变更
    - 验证：
        - 编译成功
        - 全部测试通过（269 tests）
        - Spotless 格式化通过
        - activeContext.md 降至 41 行

- [2026-03-18] - Mail Outbox 最终版本强化（架构师审查）
    - 增强 MailOutboxStatus 枚举：
        - 添加 description 字段（中英文描述）
        - 添加 isTerminal() 方法（判断是否为终态）
        - 优化 Javadoc：添加状态机转换图
        - 影响：API 响应可序列化描述文案，状态机逻辑更清晰
    - 增强 MailOutbox 实体：
        - 添加 @Version 乐观锁字段（Long 类型）
          - 用途：防止多调度节点并发时重复投递
          - 机制：PENDING → SENDING 状态跃迁时由 Hibernate 自动校验版本号
          - 收益：彻底消除并发场景下的重复发送问题
        - 优化 canRetry() 方法语义：
          - 旧版：`retryCount < maxRetries && status != CANCELLED`
          - 新版：`!status.isTerminal() && retryCount < maxRetries`
          - 影响：使用枚举的 isTerminal() 方法，语义更清晰
        - 优化 markFailed() 方法：
          - 逻辑：先 retryCount++，后判断 canRetry()
          - 分支：可重试 → FAILED + nextRetryAt；不可重试 → 调用 cancel()
          - 影响：边界条件精确，retryCount = maxRetries 时直接取消
        - 增强 setPayload() 防御性：
          - 旧版：直接赋值
          - 新版：`payload != null ? payload : new HashMap<>()`
          - 影响：防止 JSON 列存入 null 触发 Hibernate 类型转换异常
        - 添加 @Column(length = 255) 约束：
          - recipient 和 subject 字段显式声明 length
          - 影响：与 DDL VARCHAR(255) 对齐，消除 Hibernate ddl-auto 警告
        - 优化 Javadoc：
          - 类级别：说明预渲染策略和乐观锁用途
          - 字段级别：每个字段的业务含义和使用场景
          - 方法级别：状态机转换逻辑和边界条件
    - 更新 Flyway 初始化脚本（V20260303210000）：
        - 添加 version 列（BIGINT NOT NULL DEFAULT 0）
        - 添加字段注释说明乐观锁用途
        - 添加 id 字段注释
        - 说明：开发阶段直接修改初始化脚本，无需增量迁移
    - 验证：
        - 编译成功
        - 全部测试通过（269 tests）
        - Spotless 格式化通过
        - Flyway 脚本语法正确

