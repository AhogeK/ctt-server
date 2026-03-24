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

## 进行中 🔄

Week 1 基础设施搭建 - 进度: 85%
- [x] Flyway 数据库迁移
- [x] 代码规范文件
- [x] 构建工具配置
- [x] 包结构拆分
- [x] 统一响应/错误码/异常模型
- [x] 全局 Jackson 配置
- [x] TraceId 透传机制
- [x] RequestContext 体系
- [x] 三层日志规范
- [x] 安全基础架构 (UTC/大小写/CurrentUserProvider/Token状态机/User状态机)
- [x] 接口治理框架 (@RateLimit + @Idempotent)
- [x] 接口安全分类 (@PublicApi + 动态白名单)
- [x] 客户端身份上下文 (ClientIdentity)
- [x] OWASP 安全 Headers
- [x] 配置分层架构 (12-Factor App)
- [x] 安全配置规范 (@ConfigurationProperties)
- [x] 测试基线脚手架 (完整)
- [x] 测试数据 Fixture 工具包
- [x] 测试覆盖率达标 (指令 92%, 分支 84%)
- [ ] Spring Boot 项目结构
- [ ] Redis 缓存配置
- [ ] JWT 认证实现
- [ ] API Key 管理

## 开发计划 (8周)

### Week 1-2: 基础设施与认证
- [ ] 配置 Redis 缓存
- [ ] 实现 JWT 用户认证
- [ ] 实现 API Key 管理

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