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
- [x] 锁定登录响应包含 retryAfter 时间戳
    - 新增 AccountLockedException 携带 retryAfter 字段
    - ErrorResponse 新增 retryAfter 字段，GlobalExceptionHandler 设置 Retry-After HTTP Header
    - LockoutStrategyPort.getRetryAfter() 基于最早尝试时间 + lockDuration 计算
    - 前端可渲染倒计时 UI，网关层可通过 Retry-After header 拦截高频重试
- [x] 认证闭环 E2E 集成测试 (RegistrationAndVerificationIntegrationTest)
    - 完整注册 → GreenMail/Mailpit 收邮件 → 提取 token → 验证 → users.status = ACTIVE
    - 重复注册同邮箱 → 409 USER_001
    - Token 过期 → 401 MAIL_005 → 重发验证 → 旧 token 失效新 token 可用
    - 注册输入验证：无效邮箱/弱密码/空邮箱 → 400 COMMON_003
    - 6 个测试全部通过，覆盖注册验证完整生命周期 + 边界验证
    - 审查修复：移除冗余断言、增强 token 不等式 DB 验证
- [x] LogoutController NPE Bug 修复
    - 根因: @AuthenticationPrincipal Jwt jwt 为 null（JwtToCurrentUserConverter 将 principal 转为 CurrentUser）
    - 修复: 改为 @AuthenticationPrincipal CurrentUser currentUser
    - 同步更新 LogoutControllerTest 使用 authentication(createAuth()) 替代 jwt()
    - 审查修复：合并分裂注释、修正全路径类名为 import
- [x] 登录与令牌 E2E 集成测试 (LoginAndTokenIntegrationTest)
    - 正常登录 → 获取双 Token → 用 Access Token 访问受保护接口 200
    - 错误密码 5 次 → 第 6 次触发锁定 (403 AUTH_004 + retryAfter) → DB 状态 LOCKED
    - 刷新令牌轮换 → 重放旧 Token → 403 AUTH_009 → 验证事务回滚行为
    - 3 个测试全部通过，覆盖登录、暴破防护、令牌重用检测
    - 技术要点: MockMvc 提取响应体 + MockMvcTester 断言受保护接口
    - 技术要点: UserRepository.saveAndFlush() 替代 TestEntityManager 避免事务问题
    - 修复全路径类名 → import（LogoutControllerTest 反射调用）
    - 修复版本号为 PATCH bump（0.15.0 → 0.15.1-SNAPSHOT）
    - 新增 developer-handbook.md Logout Behavior 章节
    - 新增注册输入验证测试（3 个用例）
- [x] 密码重置 E2E 集成测试 (PasswordResetIntegrationTest)
    - 完整密码重置链路: 登录 → 请求重置 → mail_outbox 提取 token → 确认重置 → 验证 session 全部吊销 → 新密码登录
    - Token 过期时间旅行测试: JdbcClient 修改 expires_at → 401 AUTH_002
    - 2 个测试全部通过，覆盖密码重置完整生命周期 + 过期边界
    - 手动 QA: Swagger UI 全链路 18 个测试用例 100/100 通过
    - 技术要点: mail_outbox 表查询 + 正则提取 token, JdbcClient 时间旅行
- [x] 登出与会话吊销 E2E 集成测试 (LogoutIntegrationTest)
    - Single Session Logout (7 测试): 正常登出、幂等登出、空白/null token 验证、BOLA 防护、不存在 token 幂等、过期 token 幂等、审计日志验证
    - Global Logout / Kill Switch (2 测试): 多设备全量吊销、无活跃 token 幂等
    - Unauthenticated Access (2 测试): 无 JWT 返回 401
    - 11 个测试全部通过，覆盖登出完整生命周期 + 安全边界 + 审计追踪
    - 手动 QA: 注册→验证→登录→单设备登出→全设备登出 全链路验证通过
    - 审查修复: 4 agent 并行审查，修复 P0/P1/P2 全部发现
    - 文档: LogoutRequest.java 补充 Swagger @Schema 注解 + ValidationConstants, developer-handbook.md 更新测试覆盖表
    - 修复: LogoutController @ApiResponse 错误代码, countActiveTokensForUser 语义匹配
- [x] LoginAndTokenIntegrationTest 补充 Refresh Token 重放 E2E 断言
    - 原有测试只验证 DB 状态，缺少实际重放 rt1 → 403 AUTH_009 的 E2E 断言
    - 新增 4 步断言链：重放返回 403 → rt1 revoked → rt2 active → rt2 可刷新
    - 揭示架构事实：revokeAllUserTokens 因事务回滚无效，rt2 仍可继续使用
- [x] Docker Compose PostgreSQL 18+ 兼容性修复
    - volume 挂载点从 /var/lib/postgresql/data 改为 /var/lib/postgresql
    - 支持 PostgreSQL 18+ Docker 镜像的 pg_ctlcluster 管理和 pg_upgrade
- [x] application-local.yaml.template mail 配置环境变量化
    - mail.host/mail.port 改为 \${MAIL_SMTP_HOST:localhost}/\${MAIL_SMTP_PORT:1025}
- [x] Docker 化部署：Dockerfile + docker-compose 集成 app 服务
    - 多阶段构建: eclipse-temurin:25-jdk (build) → 25-jre-noble (runtime)
    - app 服务含 healthcheck、depends_on、环境变量自动注入
    - 所有外部端口可配置 (POSTGRES, REDIS, MAIL_SMTP, MAIL_UI, APP)
    - Dockerfile APP_PORT 动态化: 默认 8080，.env 注入覆盖，ARG + ENV + EXPOSE 联动
    - Dockerfile BuildKit 缓存: --mount=type=cache,target=/root/.gradle 加速构建
    - docker-compose.yaml: image: ctt-server-test, container_name 动态化
- [x] Jenkins CI/CD 测试流水线
    - Jenkinsfile: checkout → configure → infra up → deploy → health check
    - APP_PORT 8004，docker compose 自动构建部署
    - CONTAINER_NAME=ctt-server-test 动态容器名，Health Check 联动

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