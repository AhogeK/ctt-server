# 项目进度

## 已完成 ✅

- [x] AGENTS.md 项目记忆配置
- [x] 开发者手册 (docs/developer-handbook.md)
- [x] 版本升级: 0.0.1-SNAPSHOT → 0.1.0-SNAPSHOT
- [x] memory-bank/ 目录结构
- [x] Flyway 数据库迁移脚本
- [x] 代码规范：CONVENTIONS.md、.editorconfig、.gitmessage
- [x] 构建工具：Spotless + JaCoCo
- [x] 包结构拆分 (common, auth, user, device, audit, mail)
- [x] 统一响应模型 (ApiResponse, ErrorResponse, PagedResponse, EmptyResponse)
- [x] 审计事件模型与安全事件模型 (强类型枚举 + 五元组)
- [x] 测试基线脚手架 (BaseControllerSliceTest, BaseRepositoryTest, BaseIntegrationTest)
- [x] 邮件基础设施 Phase A (GreenMail 内嵌 SMTP)
- [x] 邮件基础设施 Phase B (Mail Outbox 事务性邮件队列 + 测试基础设施)
- [x] 邮件基础设施 Phase C (MailOutboxService 写入侧服务 + 10 tests)
- [x] 邮件基础设施 Phase D (MailOutboxPoller + MailOutboxProcessor + 调度投递)
- [x] 邮件基础设施 Phase E (ExponentialBackoffRetryStrategy + Jitter + Monte Carlo 测试)
- [x] 测试覆盖率提升 (指令 87%→92%, 分支 76%→84%)
- [x] MailOutboxProcessor Detached Entity 修复 (REQUIRES_NEW 事务隔离)
- [x] JPA Auditing 配置独立化 (解决切片测试冲突)
- [x] 审计资源类型扩展 (MAIL_OUTBOX 数据库约束)
- [x] MailOutboxService 集成测试 (4 个测试用例全部通过)
- [x] AGENTS.md R8 变更溯源原则 (防止 AI 揣测行为)
- [x] AGENTS.md R9 代码规范执行 (中文注释/测试名清理)
- [x] EmailVerificationToken Entity 字段补全 (email, purpose, sentAt, requestIp, userAgent)
- [x] EmailVerificationTokenRepository 方法扩展 (findByUserIdAndPurpose, existsBy...)
- [x] TokenUtils 重构消除重复代码
- [x] EmailVerificationService 集成 UserValidator
- [x] 邮件验证文档更新 (README.md, developer-handbook.md)
- [x] JWT 认证基础设施 Phase A (依赖 + TokenUtils 扩展)
- [x] JWT 认证基础设施 Phase B (JwtEncoder/JwtDecoder Bean 注册)
- [x] JWT 认证基础设施 Phase C (JwtTokenProvider 签发服务)
- [x] JWT 认证基础设施 Phase D (UserLoginService 登录服务)
- [x] 分支同步：develop → master (依赖版本更新 - ben-manes 插件 + jacoco 配置)
- [x] 登录接口实现 (POST /api/v1/auth/login - @PublicApi, @RateLimit)
- [x] JWT 认证失败统一响应 (JwtAuthenticationEntryPoint + 5 原子提交)
- [x] LoginAttemptService 门面提取
    - 解耦失败计数逻辑，统一异常处理 (AUTH_004)
    - 消除 UserValidator 中的重复检查
    - 客户端 IP 从 RequestContext 获取 (审计日志完善)
    - 15个单元测试 + 集成测试覆盖完整锁流程
- [x] LoginAttemptCleanupScheduler 定时清理任务
    - 定期清理过期 login_attempts 记录，防止表无限增长
    - 可配置 retention-duration (默认 30 天) 和 cleanup-interval (默认 1 小时)
- [x] Login lockout 集成到 UserLoginService + 事务传播修复
    - REQUIRES_NEW 传播确保失败记录不被外层事务回滚
    - checkLockStatus 返回刷新后的 User 实体
    - PasswordResetService 密码重置后自动解锁账户
- [x] 批量自动解锁定时任务 (AccountUnlockScheduler)
    - 在 LoginAttemptCleanupScheduler 中追加 unlockExpiredAccounts()
    - 遍历 LOCKED 用户，滑动窗口内无尝试 → reactivate()
    - 混合模式：登录时懒解锁（精准）+ 定时扫表（数据底座整洁）
- [x] 账号锁定/解锁审计事件 (ACCOUNT_LOCKED / ACCOUNT_UNLOCKED)
    - 登录失败超阈值时落 ACCOUNT_LOCKED 审计
    - 3 条解锁路径均落 ACCOUNT_UNLOCKED 审计（懒解锁/密码重置/定时扫表）
    - 清理 recordSuccess() 中冗余的 reactivate（调用方已处理）
    - 39 个测试通过，覆盖全部审计调用点

## 进行中 🔄

Week 1 基础设施搭建 - 进度: 100% ✅
- [x] Spring Boot 项目结构
- [x] Redis 缓存配置
- [x] JWT 认证实现
- [x] API Key 管理
- [x] 账号锁定策略（含 login_attempts 表 + 定时清理 + 批量解锁）

## 开发计划 (8周)

### Week 1-2: 基础设施与认证 ✅ 完成
- [x] 配置 Redis 缓存
- [x] 实现 JWT 用户认证
- [x] 实现 API Key 管理
- [x] 账号防暴破体系（配置 → 防腐层 → 懒解锁 → 隐私日志 → 定时清理）

### Week 3-4: 双向同步引擎
- [ ] CodingSession 数据模型
- [ ] SyncCursor 设备同步状态
- [ ] SyncPullService / SyncPushService
- [ ] ConflictResolver (LWW)

### Week 5-6: 插件端集成
- [ ] 升级 SQLite 表结构
- [ ] 同步调度逻辑

### Week 7: 统计与排行榜
- [ ] 多维时序聚合查询
- [ ] Redis ZSet 排行榜

### Week 8: 测试与上线
- [ ] 压力测试
- [ ] Sentry 监控
- [ ] 部署配置