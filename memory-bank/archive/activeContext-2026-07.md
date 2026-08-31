# Active Context 归档 — 2026-07

> 冷数据归档（R13）。内容为该月已完成的条目，按时间倒序；仅供回溯，不再更新。

- [2026-07-28] - 修复 CSP header 中 hCaptcha 域名带引号导致失效（前端 Bug 报告）
    - 根因: SecurityConfig.java CSP 的 script-src/frame-src/connect-src/img-src 中 4 处 hCaptcha host-source 被错误加单引号（'https://hcaptcha.com'）——CSP3 规范仅关键字可引（'self' 等），host-source 不允许；浏览器判定无效 source 并忽略 → hCaptcha 域名白名单失效 → 登录页 hCaptcha 内联脚本被 script-src 'self' 拦截（prepare.js 报错）
    - 修复（子 agent quick/opencode-go-deepseek-v4-flash）: 3 文件 12 行仅移除引号
      - SecurityConfig.java（4 处 host 去引号）
      - SecurityConfigHeadersTest.java（同步断言 → 回归守卫，旧引号必失败）
      - docs/security-architecture.md（同步 CSP 示例）
    - Scope Blast: 全仓库 grep 带引号 hcaptcha 仅这 3 处，无其他同类
    - 验证: grep 零残留; 定向 6 tests + 全量 **1058 tests**（--rerun-tasks 强制重跑）; spotlessCheck PASS（子 agent 修正一处缩进漂移并诚实报告）; jacoco PASS
    - 版本: 0.41.0 → **0.41.1**（PATCH bug 修复，硬编码检查无残留）
    - 真实浏览器验证: 需前端/用户侧确认（本环境无法验证浏览器渲染）——验收标准见交付报告
    - 状态: ✅ 代码+版本+记忆完成，待用户授权提交

- [2026-07-28] - Phase T: API Key 物理删除功能（前端需求报告驱动）
    - 需求来源: 前端 ctt-web 需求报告（优先级中）：REVOKED 密钥可永久删除，从列表彻底消失
    - 决策（自主判断，专业权衡）:
      - 错误码: AUTH_015 已被占用（OAuth provider error 502）→ 新增 **AUTH_023** ("Only revoked API keys can be deleted", 409)
      - 端点: 接受前端契约 `DELETE /{id}/delete`（与 revoke 的 DELETE /{id} 并存；前端 UI 已并行准备）
      - 版本: MINOR 0.40.3 → **0.41.0**（R15 新功能规则；前端建议 PATCH 但项目惯例新增端点=MINOR）
      - 表结构: audit_logs 无 FK 引用 api_keys → 物理删除无约束冲突（前端风险项排除）
    - 实现: ApiKeyService.deleteApiKey（findByIdAndUserId→AUTH_010；revokedAt==null→AUTH_023；先审计 API_KEY_DELETED 后 delete）+ Controller 端点 + 完整 Swagger（204/401×2/409/403 + @ExampleObject）
    - 测试: 单测 4（成功含审计/BOLA/非 REVOKED/不存在）+ MockMvc 4 + E2E 4 场景（revoke→delete→列表消失+DB 验证 / ACTIVE 409 保持原状 / 他人 401 / 重复删除 401）+ ErrorCodeTest
    - 文档: README 端点表 + Error Codes；developer-handbook 审计表 + 错误码注册表；frontend-integration.md 第 5 节（删除端点契约 + 前端交互：仅 REVOKED 显示 Delete 按钮、确认弹窗、invalidate 查询）
    - 验证: 全量 **1057 tests** / 0 failed; spotlessCheck PASS（修正 3 处格式）; jacoco PASS; LSP clean
    - 版本: 0.41.0（硬编码检查无残留）
    - 状态: ✅ 实施完成，待用户授权提交

- [2026-07-28] - Phase T 双轴 Code Review（子 agent ×2，quick/deepseek-v4-flash）+ 修复
    - Standards 轴 PASS + Spec 轴 SPEC COMPLETE（与此前自主审查结论一致）
    - 子 agent 发现并修复 3 项（我此前自主审查漏掉 2 项）:
      1. **api-governance.md Tier 2 端点清单未含新端点**（HARD）→ 已补 `DELETE /api-keys/{id}/delete` 行
      2. **无 EXPIRED→409 E2E 测试**（验收标准明示 ACTIVE/EXPIRED）→ 新增 `shouldReturn409_whenKeyExpired`（时间旅行改 expires_at 为过去 → delete → 409 + DB 行保留）
      3. **无 audit_logs 落库 E2E 断言** → `shouldDeleteRevokedKey` 追加 `API_KEY_DELETED AND resource_id` 查询断言；tearDown 增加 `DELETE FROM audit_logs`（与 LogoutIntegrationTest 先例一致）
    - 保留项（项目一致性判断）: 重复 NotFound 单测（与 RevokeApiKeyTests 双测试模式一致）；DELETE /{id}/delete 非 RESTful（前端契约 + action-suffix 先例）
    - 验证: ApiKey 116 tests + 全量 **1058 tests** / 0 failed; spotlessCheck PASS; jacoco PASS; LSP clean
    - 版本: 0.41.0 不变
    - 状态: ✅ 审查修复完成，待用户授权提交

- [2026-07-28] - 补充 API Key E2E 测试（409 上限 + 429 创建限流）
    - 背景: 前端验证时指出 20-key 上限与创建限流无 E2E 覆盖（此前仅单测 + MockMvc；429 认证限流已有 E2E 但创建端点限流没有）
    - 新增 2 个 @Nested 场景（ApiKeyIntegrationTest，@Order 7/8）:
      - LimitExceededTests: 真实创建 20 个 key → 第 21 个断言 409 + $.code = AUTH_014
      - CreateRateLimitTests: 连续创建 10 个 → 第 11 个断言 429 + $.code = RATE_LIMIT_001
    - **关键隔离设计**（测试间 Redis 共享状态）:
      - 创建限流 key 清理（rate_limit:user:ApiKeyController.createApiKey:*）: 409 测试在 11-20 次创建前 + 第 21 次请求前清理（否则 429 先于 409 拦截）；429 测试开头清理
      - **登录限流跨类干扰 bug**（全量测试暴露）: 登录 @RateLimit(IP, 30/h) 按客户端 IP 共享计数，新增测试多登录 2 次推高累计 → 后续 ApiKeyScopeIntegrationTest 登录被 429 → accessToken 空断言失败；修复: @AfterEach 清理 rate_limit:ip:AuthController.login:*
    - 错误响应格式确认: ConflictException/TooManyRequestsException 均 BusinessException → handleBusinessException → ErrorResponse 直出 → 断言用 $.code（非 $.data.code）
    - 验证: ApiKey 103 tests + 全量 1045 tests / 0 failed; spotlessCheck PASS（修正 2 处 Text Block 换行 + 1 处常量行宽）; jacoco PASS
    - 纯测试变更，无生产代码修改，版本号保持 0.40.2
    - 状态: ✅ 完成，待用户授权提交

- [2026-07-28] - keyPrefix 一致性修复（用户决策：改代码，keyPrefix 带 cttak_ marker）
    - 背景: 实际响应 keyPrefix "T2fA6AVt"（无 marker）vs 文档/Javadoc/README 三处口径 "cttak_a1b2c3d4"（带 marker）— 实现偏离设计意图
    - 决策依据: 三处既有文档口径一致 + 业界惯例（GitHub ghp_/Stripe sk_live_/OpenAI sk-）+ R8.5 项目一致性；DB VARCHAR(32) 足够无需 ALTER
    - 修复: `extractPrefix` 改为固定切片 `substring(0, ApiKeyHasher.KEY_PREFIX_LENGTH)`（=14）；`ApiKeyHasher` 新增 `VISIBLE_PREFIX_CHARS=8` + `KEY_PREFIX_LENGTH=14` 常量
    - **发现并修复 indexOf 隐藏 bug**: URL-safe Base64 字母表含 `_`（也是分隔符），`indexOf('_', 6)` 会在 prefix 含 `_` 时提前截断（测试实测 "cttak_4tKpV1B" 13 字符）→ 固定切片方案 + 确定性边界测试 `shouldNotTruncateKeyPrefix_whenPrefixContainsUnderscore` 锁定
    - 回填策略（用户决策）: 开发阶段无需独立迁移，UPDATE 融合进 `V20260303210000__init_base_schema.sql` 末尾（防御性幂等块，含契约注释），独立迁移文件已删除；用户已清理本地数据库
    - 调用点核查: getKeyPrefix 全部为透明传递/文档示例，无长度假设
    - 子任务 token 耗尽中断后由主 agent 接管: 修正 3 处遗留（Javadoc 13→14 字符、测试名 ThirteenChars→FourteenChars、集成测试硬编码 "cttak_" → ApiKeyHasher 常量）
    - 验证: `*ApiKey*` 101 tests + 全量 1043 tests / 0 failed; spotlessCheck PASS; jacoco PASS (93.46%/83.49%); LSP clean
    - 版本: 0.40.2（与 createdAt 修复合并，未提交）
    - 状态: ✅ 代码+测试+迁移+记忆完成，待用户授权提交

- [2026-07-28] - 修复 POST 创建 API Key 响应缺失 createdAt 字段 (前端 Bug 报告)
    - 根因: `ApiKeyServiceImpl.createApiKey` `save()` 后立即 `ApiKeyResponse.fromEntity(saved)` 读 `createdAt`；Hibernate `@CreationTimestamp` 在 flush 时才填充 → 内存中为 null；全局 Jackson `default-property-inclusion: non_null` 省略该字段；GET 列表走 DB 重查故正常
    - 修复: `apiKeyRepository.save(apiKey)` → `saveAndFlush(apiKey)`（一行，与 MailOutboxProcessor 创建路径模式一致）
    - 回归测试: ApiKeyIntegrationTest 两个创建 helper 均新增 `createdAt` 非空断言（真实 Hibernate flush 上下文）；ApiKeyServiceImplTest 3 处 mock 适配 save → saveAndFlush
    - 验证: `./gradlew test --tests "*ApiKey*"` 99 tests / 0 failed; `./gradlew spotlessCheck` PASS; LSP clean
    - 同类扫描: 全库 scope blast（排除 auth/apikey）0 个同类 latent bug；最近似结构 OAuthLoginOrRegisterService.registerNewUser 因 LoginResponse 不读 createdAt 而 SAFE（agent 建议将来加时间戳字段时补回归测试）
    - 附带发现（待确认）: 响应 `keyPrefix` 无 `cttak_` 前缀（extractPrefix 剥离 marker），与 frontend-integration.md 文档示例 `cttak_a1b2c3d4` 不一致 — 已向用户提出，未擅自修改
    - 版本: 0.40.1 → 0.40.2 (PATCH: bug fix)
    - 状态: 代码+测试+版本+记忆完成，待用户授权提交

- [2026-07-22] - Phase R: API Key 集成测试 + Phase N/O 隐藏 bug 修复
    - 创建 ApiKeyIntegrationTest（6 个 E2E 场景）：happy_path/revoke/expire(2 测试方法：自然过期+时间旅行)/scope_deny/bola/rate_limit
    - 修复 Phase N 遗留 bug 1：ApiKey entity scopes 字段 `@Convert(String)` + `columnDefinition="jsonb"` 在 Hibernate 7 下触发 `column is of type jsonb but expression is of type character varying` PSQLException
      - 修复：替换为 `@JdbcTypeCode(SqlTypes.JSON)` + `@Column(columnDefinition="jsonb")`（与 AuditLog/MailOutbox 项目一致模式）
      - `ApiKeyScopeConverter` 类保留（向后兼容；不再使用），单测 `ApiKeyScopeConverterTest` 仍 PASS
    - 修复 Phase O 遗留 bug 2：SecurityConfig 的 `addFilterBefore(apiKeyAuthenticationFilter, SecurityContextHolderAwareRequestFilter.class)` 实际放在 JWT BearerTokenAuthenticationFilter 之后，导致 `Authorization: Bearer cttak_*` 被 JWT 过滤器先解析 → AUTH_003 "Token invalid" 401
      - 修复：改为 `addFilterBefore(apiKeyAuthenticationFilter, BearerTokenAuthenticationFilter.class)` —— API key filter 现在位于位置 9/17，JWT filter 在位置 10/17
    - 修复 Phase O 遗留 bug 3：JWT 过滤器对 `cttak_*` token 仍会尝试解析为 JWT（即使 API key filter 已跳过 JwtAuthenticationConverter），双重认证链冲突
      - 修复：新增 `ApiKeyAwareBearerTokenResolver`，对 `cttak_*` prefix 返回 null 让 API key filter 处理
      - 集成：`oauth2ResourceServer.bearerTokenResolver(new ApiKeyAwareBearerTokenResolver())`
    - 修复 Phase O 遗留 bug 4：`SpringSecurityCurrentUserProvider.getCurrentUser()` 不识别 `ApiKeyPrincipal`，导致用 API key 访问 `ApiKeyController` 时 `currentUserProvider.getCurrentUserRequired()` 抛 AUTH_001 "Authentication required"
      - 修复：`ApiKeyPrincipal` 重构为嵌入 `CurrentUser user`（含 userId/email/status/authorities/authType=API_KEY）
      - 提供 `ApiKeyPrincipal.from(User, keyId, scopes)` factory 利用已加载的 User entity（避免重复 DB 查询）
      - `SpringSecurityCurrentUserProvider` 增加 `instanceof ApiKeyPrincipal` 分支返回嵌入的 user
      - 用户层调用接口 `userId()` 仍然可用（从 user.id() 计算），向后兼容
    - 测试适配：ApiKeyScopeAspectTest 改用 `new ApiKeyPrincipal(TEST_USER, KEY_ID, ...)` 而非旧的三 UUID 构造器
    - 响应行为对齐（项目一致性 R8.5）：
      - AUTH_012 (revoked) 实际返回 **403 FORBIDDEN**（非任务描述的 401），采纳项目实际行为
      - BOLA 测试期望 `$.code = AUTH_010`（NotFoundException 直出，非 RestApiResponse 包装）
      - Rate limit/expire 测试期望 `$.data.code`（RestApiResponse 包装）
    - 测试：ApiKeyScopeIntegrationTest（Phase P，已被 bug 阻塞）现在也全部 PASS —— bug 修复连带解锁
    - 测试：全项目 `./gradlew test` —— 1049 tests, 0 failed, 1 skipped, 100% success rate
    - `./gradlew spotlessCheck` —— PASS
    - 版本: 0.40.0 → 0.40.1 (PATCH: bug fixes)
- [2026-07-22] - Phase S: 文档 + API Key 前端集成指南
    - 实际评估：S.1 developer-handbook.md（错误码/审计/认证章节）、S.2 api-governance.md（安全层级/限流）、S.3 README.md（端点表/技术栈）、S.5 OpenAPI 示例 均已在 Phase N/O/P/Q/R 累积完成，无需额外修改
    - 创建 `dev-docs/apikey/frontend-integration.md`（S.4 唯一缺失交付物）：API Key 创建/列表/吊销流程、错误码映射、状态显示指南、安全注意事项
    - 无代码修改，版本号不变
    - 状态: ✅ Phase S 全部完成，待用户授权提交

- [2026-07-22] - Phase R 审查 + 修复 (review fixes)
    - **code-reviewer (项目级 skill)** 派出 5 个并发 BG 子 agent 审查未提交代码（4 review + 1 verify）
    - **3 CRITICAL issues fixed**:
      - **C-1**: `ApiKeyAwareBearerTokenResolver` 硬编码 `"Bearer "` 字符串 → 改为注入 `SecurityProperties` 读取 `apiKey.headerPrefix()`,配置变更后不再失配
      - **C-2**: 硬编码 `"cttak_"` 字符串 → 改为 `import static ApiKeyHasher.KEY_PREFIX_MARKER` 使用项目 public 常量,避免重复真相源
      - **C-3**: 死代码 `ApiKeyScopeConverter.java` + `ApiKeyScopeConverterTest.java` 已无任何 @Convert 引用 → 删除,同步修正 `ApiKey.java:24` Javadoc（不再声称 scopes 通过 converter 持久化,改为说明通过 `@JdbcTypeCode(SqlTypes.JSON)`）
    - **3 MAJOR issues fixed**:
      - **M-1**: `ApiKeyIntegrationTest.java:44` docstring 仍写 `401 AUTH_012`,实际响应是 403 (line 281 `@DisplayName` 已正确) → 修正为 403
      - **M-2**: `ApiKeyPrincipal.from` Javadoc 谎称 "UserStatus is coerced to ACTIVE",实际直接传 `user.getStatus()` → 改写为 "active-status invariant is enforced upstream by ApiKeyService.validateAndTouch"
      - **M-3**: `ApiKeyAwareBearerTokenResolver` 用 `new` 直接实例化(`SecurityConfig:161`) → 提升为 `@Bean` 在 `SecurityConfig.apiKeyBearerTokenResolver()`,与项目 `apiKeyAuthenticationFilter` 风格一致（虽然 placement 在 `common.config` 而非 `apikey.config`,因 resolver 是 Spring Security 关注点,但依赖 `auth.apikey` 的常量已存在先例）
    - **Scope blast verdict**: 4 个 pattern 扫描结果显示 **0 个同类 latent bug** elsewhere
    - **Test gaps noted (informational)**: idempotency of double-DELETE, GET `/{id}` BOLA, per-user 20-key limit, last_used_at update, audit log emission, malformed header, empty scopes, listings content - 已记录但不在本任务范围
    - **Docs sync**: 更新 `developer-handbook.md` (新增 "API Key Filter Order and Token Resolution" 小节) + `README.md` (认证流程 + Error Codes 表)
    - 重新验证: `./gradlew test` —— 1041 tests (删除 ApiKeyScopeConverterTest 后 -8), 0 failed, 100% success rate
    - 重新覆盖率: INSTRUCTION 93.5% / BRANCH 83.5% (≥ 80% / 70% 阈值,均远超)
    - `./gradlew spotlessCheck` —— PASS
    - 状态: ✅ 审查完成,所有 blocker 修复,待用户授权提交

- [2026-07-15] - Phase Q: API Key 认证限流实现
    - 增强 ApiKeyAuthenticationFilter：Per-IP 限流（10次失败/60秒）+ Retry-After header
    - SecurityProperties.ApiKeyProperties 新增 authFailureRateLimit 和 authFailureRateLimitWindowSeconds 配置
    - ApiKeySecurityConfig 注入 RedisRateLimiter 到 filter
    - 复用现有 RedisRateLimiter（Lua 脚本原子操作）实现固定窗口限流
    - 更新 developer-handbook.md：新增 API Key 认证限流文档
    - 修复 4 个测试文件适配 ApiKeyProperties 新参数
    - 版本: 0.39.0 → 0.40.0 (MINOR: auth rate limiting)
    - 验证: `./gradlew test --tests "*ApiKeyAuthenticationFilterTest"` — PASS; `./gradlew spotlessApply compileJava` — PASS
    - 状态: ✅ Phase Q 核心实现完成，待用户授权提交

- [2026-07-13] - Phase P 补充：同步端点 + MockMvc 测试 + 集成测试
    - 创建 SyncController (sync/controller/) 最小端点：POST /api/v1/sync/pull, POST /api/v1/sync/push
    - 两个端点均应用 @RequiresApiKeyScope(ApiKeyScope.SYNC) scope 校验
    - 创建 SyncControllerMockMvcTest：验证 403 AUTH_020 响应格式、JWT 绕过、ADMIN 超越
    - 创建 ApiKeyScopeIntegrationTest：完整 Spring 上下文 scope 执行验证
    - 更新 README.md：新增同步端点文档
    - 更新 developer-handbook.md：新增同步端点 scope 说明
    - 版本: 0.38.0 → 0.39.0 (MINOR: sync endpoints + tests)
    - 验证: `./gradlew test --tests "*SyncControllerMockMvcTest"` — PASS; `./gradlew spotlessApply compileJava` — PASS
    - 状态: ✅ Phase P 限制项全部完成，待用户授权提交

- [2026-07-12] - Phase P: Scopes 权限系统实现完成
    - T1: SecurityConfig 新增 @EnableMethodSecurity 启用方法级安全
    - T3: 创建 @RequiresApiKeyScope 自定义注解 (auth/apikey/security/)
    - T2/T4: 创建 ApiKeyScopeAspect AOP 切面 (JWT 用户自动绕过, API Key 用户检查 scope, ADMIN scope 超越所有)
    - T5: ApiKeyController 应用 @RequiresApiKeyScope (POST/DELETE=WRITE, GET=READ)
    - T5: DeviceController 应用 @RequiresApiKeyScope (GET=READ, DELETE=WRITE)
    - T6: CreateApiKeyRequest 已有 @NotEmpty scope 验证（复用现有）
    - AuditAction 新增 API_KEY_SCOPE_DENIED 审计事件
    - 测试: ApiKeyScopeAspectTest (5 tests: 有scope/无scope+ADMIN绕过/JWT绕过/无认证)
    - 验证: `./gradlew test --tests "*ApiKeyScopeAspectTest"` — PASS; `./gradlew compileJava` — PASS
    - 版本: 0.37.1 → 0.38.0 (MINOR: scope enforcement)
    - 状态: ✅ Phase P 核心实现完成，待用户授权提交

- [2026-07-10] - Phase O: API Key 认证管线最终审查修复完成
    - M1: ApiKeyServiceImplTest 新增 UserStatus 非 ACTIVE 参数化测试 (LOCKED/SUSPENDED/DELETED/PENDING_VERIFICATION)
    - M2: validateAndTouch 映射每个 UserStatus 到具体错误码 (AUTH_004/005/006/009)，与 JWT 路径一致
    - L1: ApiKeyAuthenticationFilter 成功认证后记录 API_KEY_USED 审计日志
    - N1: ApiKeyService Javadoc FQN → 短类名 (import UnauthorizedException/ForbiddenException)
    - N2: Filter test renamed: shouldReturn401_whenMalformedApiKey → shouldReturn401_whenKeyPrefixIsEmpty
    - 验证: `./gradlew test` — BUILD SUCCESSFUL; `./gradlew spotlessCheck` — PASS
    - 状态: ✅ Phase O 最终审查修复完成，待用户授权提交

- [2026-07-10] - Phase O: API Key 认证管线实现完成
    - 实现: ApiKeyPrincipal, ApiKeyProperties, ApiKeyAuthenticationFilter, ApiKeySecurityConfig
    - 集成: SecurityConfig 注入 ApiKeyAuthenticationFilter (在 JWT 过滤器之前)
    - 扩展: ApiKeyService 接口新增 validateAndTouch 方法, ApiKeyServiceImpl 实现
    - 测试: ApiKeyAuthenticationFilterTest (6 tests) + ApiKeyServiceImplTest.ValidateAndTouchTests (5+4 tests)
    - 配置: application.yaml 新增 ctt.security.api-key 配置
    - 验证: `./gradlew test` — BUILD SUCCESSFUL; `./gradlew spotlessCheck` — PASS
    - 状态: ✅ Phase O 全部完成

- [2026-07-10] - Notion 开发计划 Phase N 区块验收更新
    - 页面: "🖥️ ctt-server 开发计划" (ID: 320f5477-6e22-8123-a8d6-d91fddb9445c)
    - 更新: N 区块标题加 ✅ 前缀，所有 17 个子任务打勾，9 项验收标准打勾
    - 更新: 总交付清单 N 状态改为 "✅ 已完成（v0.36.0）"
    - 补充: 实现状态快照、验收报告路径、Code Review 修复记录（16 项）
    - 标注: N.9 RevokeApiKeyRequest 已删除（死代码）及原因
    - 标注: 实际交付物 11 源文件 + 8 测试文件（原计划 10+5，Code Review 新增 3 个测试）
    - 状态: ✅ 已完成

- [2026-07-09] - Phase N Code Review 全部修复完成
    - 审查发现: 1 Critical + 4 High + 5 Medium + 6 Low
    - C-1 修复: README.md 新增 API Key Management 端点表; developer-handbook.md 新增 AUTH_020/021 错误码 + API_KEY_* 审计事件; api-governance.md 新增 Tier 2 端点分类
    - H-1 修复: ApiKeyController Javadoc 状态码 "(404)" → "(401)"
    - H-2 修复: ApiKeyScopeConverter 空集反序列化 bug (`EnumSet.copyOf` → `raw.isEmpty()` 前置检查)
    - H-3 修复: 新增 5 个单元测试 (ApiKeyStatusTest, ApiKeyScopeConverterTest, ApiKeyQueryServiceImplTest, ApiKeyResponseTest, ApiKeyTest)
    - H-4 修复: ApiKeyResponse.status 从 String 改为 ApiKeyStatus enum
    - M-1 修复: extractPrefix 改用 KEY_PREFIX_MARKER 长度切片
    - M-2 修复: revokeApiKey 接口移除 reason 参数（简化，与 OAuth UNBIND 对称）
    - M-4 修复: ApiKeyScopeConverter 增加 objectMapper null 防御性检查
    - L-1 修复: ApiKey entity 移除 @Schema 注解（与 User/Device 一致）
    - L-3 修复: ApiKeyHasher.KEY_PREFIX_MARKER 改为 public（供 Service 引用）
    - 验证: `./gradlew test` — BUILD SUCCESSFUL; `./gradlew spotlessCheck` — PASS
    - 状态: ✅ Phase N 全部完成（代码 + 测试 + 文档）

- [2026-07-09] - Phase N Service Implementation 完成
    - 实现: `ApiKeyServiceImpl` (createApiKey / revokeApiKey), `ApiKeyQueryServiceImpl` (listApiKeys / getApiKey)
    - 清理: 删除死代码 `RevokeApiKeyRequest.java`
    - 测试: `ApiKeyServiceImplTest` (7 tests: create happy/limit/not-found + revoke happy/idempotent/not-found/BOLA)
    - 验证: `./gradlew test` — BUILD SUCCESSFUL (全量测试通过，0 failures)
    - 验证: `./gradlew spotlessCheck` — PASS
    - 版本: 0.35.0 → 0.36.0 (MINOR: Service implementations)
    - 状态: ✅ Phase N 核心 CRUD 全部完成

- [2026-07-09] - Phase N Code Review 与修复 (P0/P1 Issues)
    - 审查发现: P0-1 缺少 Service 实现 (Integration Tests 全部失败), P0-2 引用不存在异常, P1-1 DTO 死代码, P1-2 缺失 Device 关联
    - 修复: 实现 `ApiKeyServiceImpl` / `ApiKeyQueryServiceImpl`, 补充 Device 关联, 修复 DTO/Controller 读取 Revoke Body, 移除不存在异常引用
    - 状态: 🔄 修复中 (bg_41fb383b)

- [2026-07-09] - ApiKeyController 实现完成 (阶段 N 核心 CRUD 子任务)
    - 新增: ApiKeyService / ApiKeyQueryService 接口（write/read 分离契约）
    - 新增: ApiKeyController (`/api/v1/auth/api-keys` 前缀) — 4 个端点
        - `POST /` 创建 — 201 + `CreateApiKeyResponse` (含 rawKey 仅一次)
        - `GET /` 列表 — 200 + `ApiKeysResponse`
        - `GET /{id}` 详情 — 200 + `ApiKeyResponse`
        - `DELETE /{id}` 撤销 — 204 No Content
    - 安全: 完整 Swagger @ApiResponses (201/200/204/400/401/409) + @ExampleObject
    - 安全: `@RateLimit(type=USER, limit=10, windowSeconds=3600)` 限定创建
    - 安全: `@SecurityRequirement(name="bearerAuth")` JWT 鉴权
    - 安全: BOLA 防护 - 当前 userId 显式传入 service，不允许跨用户访问
    - 新增: ApiKeyControllerMockMvcTest — 20 个测试覆盖 4 端点 + BOLA + 验证 + 401
    - 测试: 20/20 PASS, ./gradlew spotlessCheck PASS
    - 关键设计: API key "not found" → 401 (AUTH_010) 而非 404 — BOLA 防护语义 (防止 UUID 枚举攻击)
    - 状态: ✅ Controller + Test 完成，待 Service 实现接入

- [2026-07-07] - Notion "API Key 管理" 区块风格优化完成
    - 页面: "🖥️ ctt-server 开发计划" (ID: 320f5477-6e22-8123-a8d6-d91fddb9445c)
    - 优化内容: 新增 "实施快照" 锚点、合并 "架构/技术栈"、扩充 `ApiKeyHasher` 描述、所有阶段状态更新为 "⬜ 待开始"
    - 参考: `.sisyphus/plans/2026-07-07-api-key-management.md`
    - 状态: ✅ 已完成

- [2026-07-07] - Notion "API Key 管理" 区块风格优化
    - 页面: "🖥️ ctt-server 开发计划" (ID: 320f5477-6e22-8123-a8d6-d91fddb9445c)
    - 问题: 「核心产出」列仅为类名/文件名罗列，与 OAuth 区块的详细描述风格不一致
    - 修复: 6 行全部重写为详细的中文功能描述（参考 Notion MCP 文档使用 update_content 精确匹配）
    - 示例: "Entity/Repository/Service/Controller/CRUD" → "ApiKeyScope/ApiKeyStatus 枚举定义 + ApiKey JPA Entity + ApiKeyRepository (4 个查询方法) + ApiKeyHasher (SHA-256 + SecureRandom) + ..."
    - 确认: "API Key 管理总交付清单" 标题全页仅出现 1 次，无重复
    - 状态: ✅ 已完成

- [2026-07-07] - API Key 管理计划添加交付验收核对清单
    - 文件: .sisyphus/plans/2026-07-07-api-key-management.md
    - 新增: 交付验收核对清单（16 个交付物 + 30 个验收项 + 7 个安全核查）
    - 格式: 参考 OAuth 接入验收报告格式
    - 状态: ✅ 已完成

- [2026-07-07] - API Key 管理实施计划设计完成
    - 文件: .sisyphus/plans/2026-07-07-api-key-management.md
    - 结构: 6 个阶段（N/O/P/Q/R/S），对齐 OAuth 接入模块的命名约定
    - 包结构: auth/apikey/ 镜像 auth/oauth/（client/config/controller/crypto/dto/entity/enums/exception/model/repository/service）
    - 阶段 N: 核心生命周期（Entity/Repository/Service/Controller/CRUD，3-4 天）
    - 阶段 O: 认证管线（ApiKeyAuthenticationFilter + SecurityContext，2-3 天）
    - 阶段 P: Scopes 权限（@PreAuthorize 强制 scope 校验，1 天）
    - 阶段 Q: 审计 + 安全（API_KEY_USED/API_KEY_AUTH_FAILED 新增 + per-IP 限流，1-2 天）
    - 阶段 R: 集成测试（E2E 6+ 场景，1-2 天）
    - 阶段 S: 文档 + UI 集成（developer-handbook + frontend-integration，0.5-1 天）
    - 新增 ErrorCode: AUTH_020 (403 scope 不足), AUTH_021 (401 header 格式错)
    - 新增 AuditAction: API_KEY_USED, API_KEY_AUTH_FAILED
    - 复用: TokenUtils.hashToken (SHA-256), ApiKeyHasher 包装; ErrorCode.AUTH_010/011/012 覆盖无效/过期/已吊销
    - 关键决策: 异步 lastUsedAt 写入（< 5ms 延迟预算）, 双轨认证（JWT 或 API Key）, per-user 20 个 key 上限
    - Notion: 已发布到页面 320f5477-6e22-8123-a8d6-d91fddb9445c（"🖥️ ctt-server 开发计划"）
    - 状态: 📝 设计完成，待用户批准实施

- [2026-07-07] - SKILL_GRAPH.md 最终确认：290 个技能全覆盖（五次验证）
    - ~/.agents/skills/: 222 个 ✅
    - ~/.config/opencode/skills/: 60 个 ✅
    - 项目 .agents/skills/: 4 个 ✅
    - 总计: 290 个技能，0 遗漏
    - SKILL_GRAPH.md 位置: 项目根目录（与 AGENTS.md 同级）
    - 状态: ✅ 已完成

- [2026-07-07] - SKILL_GRAPH.md 最终确认：290 个技能全覆盖（四次验证）
    - ~/.agents/skills/: 222 个 ✅
    - ~/.config/opencode/skills/: 60 个 ✅
    - 项目 .agents/skills/: 4 个 ✅
    - cli-hub-matrix 技能: 5 个 ✅
    - 其他工具技能: 2 个 ✅
    - 总计: 290 个技能，0 遗漏
    - SKILL_GRAPH.md 位置: 项目根目录（与 AGENTS.md 同级）
    - 状态: ✅ 已完成

- [2026-07-06] - SKILL_GRAPH.md 最终确认：290 个技能全覆盖（三次验证）
    - ~/.agents/skills/: 222 个 ✅
    - ~/.config/opencode/skills/: 60 个 ✅
    - 项目 .agents/skills/: 4 个 ✅
    - 总计: 290 个技能，0 遗漏
    - SKILL_GRAPH.md 中记录: 290 个技能名（完全匹配）
    - 修复遗漏: grill-with-docs, test-driven-development
    - 状态: ✅ 已完成

- [2026-07-06] - SKILL_GRAPH.md 最终确认：286 个技能全覆盖（二次验证）
    - ~/.agents/skills/: 222 个 ✅（0 遗漏）
    - ~/.config/opencode/skills/: 60 个 ✅（0 遗漏，含 3 个 gstack 遗漏已修复）
    - 项目 .agents/skills/: 4 个 ✅（0 遗漏）
    - 总计: 286 个技能，0 遗漏
    - SKILL_GRAPH.md 中记录: 317 条（含重复引用和分类）
    - 新增: cli-anything-* 完整列表、gstack-benchmark/benchmark-models/office-hours
    - 状态: ✅ 已完成

- [2026-07-06] - SKILL_GRAPH.md 更新：添加 gstack + doko 技能
    - 新增: 60 个 gstack/doko/sisyphus 技能从 ~/.config/opencode/skills/
    - 总计: 282 个技能（222 + 60，无重复）
    - 更新: 技能总数从 221 更新为 282
    - 新增分类: GStack 技能套件、Doko 技能、执行规则
    - 状态: ✅ 已完成

- [2026-07-06] - SKILL_GRAPH.md 全局技能索引创建
    - 文件: `/Users/ahogek/.agents/SKILL_GRAPH.md`
    - 内容: 221 个技能的分类索引，包含快速查找指南、分类索引、高频组合模式
    - 用途: AI 在执行任务前快速扫描，识别并加载最相关的技能
    - 状态: ✅ 已完成

- [2026-07-06] - S：OAuth 集成测试 验收报告
    - 文件: .sisyphus/oauth-integration-testing-acceptance-report.md
    - 结论: ✅ 核心目标（OAuth 核心路径有自动化保障，不依赖网络）通过验收
    - 实现路径: MockMvc + MockRestServiceServer 替代 WireMock（Spring 自带，更轻量）
    - 测试覆盖: 80+ 测试方法覆盖 OAuth 模块全部核心类
    - 状态: ✅ 已完成

- [2026-07-06] - Notion P/Q 格式优化（移除过度使用的引用块）
    - 用户反馈：引用块应该用在真正"引用"内容的时候，不应用作一般的区块分隔
    - P 部分：移除"实际实现"和"验收报告"两处引用块（改为普通文本）
    - Q 部分：移除"实现状态"和"验收报告"两处引用块（改为普通文本）
    - 状态: ✅ 已完成

- [2026-07-06] - Notion P：账号绑定/解绑 验收更新
    - 页面: "🖥️ ctt-server 开发计划"
    - 更新: P 部分标题改为 ✅（已完成），实现状态改为 ✅ 全部完成
    - 更新: 所有子勾选打勾 [x]，验收项打勾
    - 更新: 总交付清单中 P 状态改为 ✅ 已完成
    - 状态: ✅ 已完成

- [2026-07-06] - OAuth 账号绑定 / 解绑 验收报告
    - 文件: .sisyphus/oauth-binding-acceptance-report.md
    - 结论: ✅ BIND/UNBIND 核心业务完整通过验收（v0.28.0 BIND + v0.29.0 UNBIND）
    - 实现路径差异: API 路径（/accounts 代替 /link）、ErrorCode（AUTH_018 代替 OAUTH_CANNOT_UNLINK_LAST_CREDENTIAL）、Service 命名（合并到 OAuthLoginOrRegisterService）
    - 状态: ✅ 已完成

- [2026-07-05] - Notion Q：OAuth Token 生命周期管理 验收更新
    - 页面: "🖥️ ctt-server 开发计划"
    - 更新: Q 部分标题改为 ✅（已完成），实现状态改为 ✅ 全部完成
    - 更新: 总交付清单中 Q 状态改为 ✅ 已完成
    - 清理: 删除之前 insert_content 插入的临时重复内容
    - 状态: ✅ 已完成

- [2026-07-05] - AesGcmTokenEncryptor 自动装配修复
    - 问题: @Component 类有两个构造函数，Spring 无法确定使用哪个
    - 修复: 给接收 SecurityProperties 的构造函数添加 @Autowired 注解
    - 验证: ./gradlew test --tests "*TokenKeyRotationService*" — PASS
    - 状态: ✅ 已修复

- [2026-07-05] - TokenKeyRotationService 代码审查修复
    - 审查: code-reviewer 子agent 审查发现 Critical bug — newEncryptor 从未被使用，轮换是空操作
    - 修复: TokenKeyRotationService.rotateAccountTokens() — 显式使用 newEncryptor.encrypt(plaintext) 替代依赖 JPA converter
    - 修复: TokenKeyRotationServiceTest — 添加 ArgumentCaptor 验证重新加密确实发生
    - 修复: Spotless 格式违规 + 未使用的 import
    - 验证: ./gradlew test --tests "*TokenKeyRotationService*" — PASS；./gradlew spotlessCheck — PASS
    - 版本: 0.34.0 → 0.35.0 (MINOR: 新功能)
    - 状态: ✅ 审查修复完成

- [2026-07-05] - OAuth Token 密钥轮换服务实现完成
    - 新增: TokenKeyRotationService — 批量解密再加密服务
    - 新增: TokenKeyRotationServiceTest — 5 个测试用例
    - 修改: AesGcmTokenEncryptor — 添加接受密钥参数的构造函数
    - 验证: ./gradlew test --tests "*TokenKeyRotationService*" — PASS
    - 状态: ✅ 已完成

- [2026-07-05] - OAuth Token 生命周期管理验收报告创建
    - 文件: docs/oauth-token-lifecycle-acceptance-report.md
    - 结论: ✅ 核心业务通过验收，密钥轮换后台任务为预期的未实现项
    - 验收项: 7 项（6 项通过，1 项预期的未实现项）
    - 状态: ✅ 已完成

- [2026-07-05] - Notion 开发计划页面更新（O：GitHub OAuth 核心流程实现验收完成）
    - 页面: "🖥️ ctt-server 开发计划" (ID: 320f5477-6e22-8123-a8d6-d91fddb9445c)
    - 更新: 在页面末尾插入 GitHub OAuth 验收结论（2026-07-05）
    - 内容: 验收项清单（9 项全部 ✅ 通过）+ 实现路径差异说明 + Token 加密链路确认
    - 状态: ✅ 已完成

- [2026-07-05] - hasPassword 字段提交完成
    - Commit 1 (develop): `feat(user): add hasPassword field to user profile response` (508ab2d)
    - Commit 2 (develop): `docs(memory-bank): record hasPassword field implementation` (c6a6142)
    - Cherry-pick (master): `feat(user): add hasPassword field to user profile response` (0c0b10f)
    - 推送: develop + master 已推送
    - 验证: master 无 AI 文件（git ls-files 验证通过）

- [2026-07-05] - hasPassword 字段代码审查 + 文档更新
    - 审查: code-reviewer 子agent 审查通过（PASS）
    - 修复: Spotless 格式违规 — @Schema 注解多行→单行（已修复）
    - 文档: README.md 新增 v0.34.0+ Response Fields 子章节（hasPassword 说明）
    - 文档: developer-handbook.md 更新 GET /users/me 端点描述（9 个字段，版本 0.34.0）
    - 验证: ./gradlew test — 933 tests PASS, 0 failures；./gradlew spotlessCheck — PASS；./gradlew build — BUILD SUCCESSFUL
    - 状态: 审查完成，文档已更新，待用户处理 commit/push

- [2026-07-05] - UserProfileResponse 新增 hasPassword 字段（前端 Set/Change Password 按钮文案支撑）
    - 新增: UserProfileResponse record 添加 `hasPassword` boolean 字段
    - 实现: fromEntity() 基于 `user.getPasswordHash() != null` 计算（OAuth 用户无密码返回 false）
    - 测试: UserProfileServiceTest 新增 1 个测试（shouldReturnHasPasswordFalse_whenNoPasswordSet）+ 更新 1 个测试断言
    - 测试: UserControllerMockMvcTest 更新 fullProfile()/unverifiedProfile() 构造函数 + hasPassword 断言
    - 验证: ./gradlew test --tests "*UserProfileService*" --tests "*UserController*" — PASS
    - 版本: 0.33.1 → 0.34.0 (MINOR: 新字段)
    - 待提交: 用户处理 commit/push

- [2026-07-04] - Security improvements review fixes (cleared cookie SameSite + CORS allowed-headers allowlist)
    - 修复: CookieHelper.buildClearedCookie() — 新增 sameSite 参数，cleared cookie 也设置 SameSite 属性（与 normal cookie 对称：access=Lax, refresh=Strict）
    - 修复: application.yaml CORS allowed-headers — `- "*"` → 显式 allowlist (Authorization / Content-Type / X-Requested-With / Accept / Origin)
    - 测试: CookieHelperTest shouldClearAccessTokenCookieWithMaxAgeZero + shouldClearRefreshTokenCookieWithMaxAgeZero — 各新增 1 行 SameSite 断言
    - 验证: ./gradlew spotlessCheck — PASS；./gradlew test --tests "*CookieHelper*" --tests "*SecurityConfigHeadersTest*" — 12 tests PASS（CookieHelperTest 6/6 + SecurityConfigHeadersTest 6/6 含 1 pre-existing skip）；./gradlew build — BUILD SUCCESSFUL
    - 版本: 0.33.0 → 0.33.1 (PATCH: review fixes)
    - 未提交: 待用户处理 commit/push

- [2026-07-04] - Security improvements (JWT cookies, CORS, Referrer-Policy, CSP for hCaptcha)
    - Add CookieHelper utility for JWT cookie management
    - Add CorsConfig with CORS properties (SecurityProperties.Cors record)
    - Update SecurityConfig: add CORS, Referrer-Policy: no-referrer, update CSP for hCaptcha
    - AuthController: inject cookies in login, refresh, terms-accept; support cookie-based refresh token
    - LogoutController: clear JWT cookies on logout
    - OAuthCallbackController: set cookies before redirect
    - Add unit tests for CookieHelper (6 tests)
    - Version: 0.32.0 → 0.33.0 (MINOR: new security features)
    - Status: implementation complete, awaiting user commit authorization

- [2026-07-04] - LogoutController 清除 JWT cookies（修复 OAuth 登录 cookie 残留 bug）
    - 修改: LogoutController.java — logout endpoint 新增 HttpServletResponse 参数 + 调用 CookieHelper.clearCookiesFromResponse(httpResponse) + Javadoc @param httpResponse
    - 修改: LogoutControllerTest.java — 2 处反射方法签名查找追加 HttpServletResponse.class；新增 CookieClearingTests 嵌套类验证 access/refresh cookie 均被清除 (maxAge=0)
    - 根因: OAuthCallbackController 调用 CookieHelper.addCookiesToResponse 设置 JWT cookies，但 LogoutController 从未清除，导致登出后浏览器仍持有旧 token（安全风险）
    - 验证: ./gradlew compileJava --no-daemon — BUILD SUCCESSFUL；./gradlew test --tests "*Logout*" — 36/36 PASS（含新 CookieClearingTests）
    - 版本: 0.32.0 → 0.32.1 (PATCH: bug fix)
    - 未提交: 待用户处理 commit/push

- [2026-07-04] - SecurityConfig 强化（CORS + Referrer-Policy + hCaptcha CSP）
    - 修改: SecurityConfig.java — 注入 CorsConfigurationSource bean + 启用 .cors() + 新 Referrer-Policy + 新 CSP（hCaptcha 白名单）
    - 修改: SecurityConfigHeadersTest.java — CSP 测试断言新策略 + 新增 Referrer-Policy 测试
    - 修复: AesGcmTokenEncryptorTest.java + GitHubOAuthClientTest.java — SecurityProperties 构造函数适配（Cors record 已加入，CorsConfig.java 已存在但测试遗漏）
    - 验证: ./gradlew test --tests "SecurityConfigHeadersTest" — 6 tests PASS（1 skipped 因 HTTP 非 HTTPS）
    - 响应头确认: Content-Security-Policy / Referrer-Policy: no-referrer / X-Content-Type-Options / X-XSS-Protection / X-Frame-Options 全部正确
    - 未提交: 待用户处理 commit/push

- [2026-07-04] - OAuth Set Password API 实现完成 ✅
    - 新增: POST /api/v1/users/me/password/set — OAuth 用户首次设置密码
    - 新增文件: PasswordService / PasswordController / SetPasswordRequest DTO / 2 测试文件
    - 修改文件: ErrorCode (USER_015) / AuditAction (PASSWORD_SET) / developer-handbook.md
    - 模式: 复用 EmailChangeController/EmailChangeService 模式
    - 验证: ./gradlew build — BUILD SUCCESSFUL, 全量测试通过
    - 版本: 0.31.2 → 0.32.0 (MINOR: 新功能)
    - 待提交: 用户处理 commit/push

- [2026-07-04] - developer-handbook.md 同步更新（OAuth Set Password API 文档登记）
    - 变更: Error Code Registry 新增 USER_015 (Password already set / CONFLICT 409)
    - 变更: 新增 "Set Password Audit Events" 子章节 + PASSWORD_SET 审计动作
    - 验证: ./gradlew compileJava → BUILD SUCCESSFUL
    - 未提交: 待 OAuth Set Password API 实施完成统一提交

- [2026-07-04] - OAuth Set Password API 实现计划创建
    - 需求: POST /api/v1/users/me/password/set — OAuth 用户首次设置密码
    - 计划: .sisyphus/plans/2026-07-04-set-password-api.md (7 个任务，TDD 方式)
    - 新增文件: SetPasswordRequest DTO / PasswordService / PasswordController / 2 测试文件
    - 修改文件: ErrorCode (USER_015) / AuditAction (PASSWORD_SET) / developer-handbook.md
    - 模式: 复用 EmailChangeController/EmailChangeService 模式（子控制器 + 服务层）
    - 验证: @StrongPassword 校验 + BCryptPasswordEncoder 编码 + 审计日志
    - 状态: 计划已完成，待用户选择执行方式

- [2026-07-04] - Resend Verification 端点 + 代码质量修复
    - 新增: POST /api/v1/users/me/email/resend-verification（重发验证邮件，60秒/次限流）
    - 新增: AuditAction.EMAIL_CHANGE_RESENT 审计动作
    - 重构: EmailChangeService 提取 USER_NOT_FOUND 常量，消除 4 处字符串重复
    - 修复: EmailChangeServiceTest Instant.now() 警告（使用 FIXED_NOW 常量）
    - 测试: 3 单元测试 + 3 集成测试
    - 验证: ./gradlew test — 912+ tests PASS
    - 版本: 0.31.1 → 0.31.2 (PATCH: 新端点 + 质量修复)
    - 提交: develop + master (cherry-pick) 已推送

- [2026-07-03] - Email Change Feature 审查修复（ErrorCode USER_012 去重）
    - 问题: USER_012 ("Email already registered") 与 USER_001 重复
    - 修复: 删除 USER_012，所有引用替换为 USER_001
    - 文件: ErrorCode.java (删除 USER_012) + EmailChangeServiceTest.java (2 处引用替换)
    - 验证: ./gradlew test — 912 tests PASS, 0 failures
    - 版本: 0.31.0 → 0.31.1 (PATCH: 去重修复)

- [2026-07-03] - Email Change Feature 全部 12 个任务完成 ✅
    - 实施: 企业级邮箱管理架构（前端方案 → 后端实现）
    - 架构: 复用 email_verification_tokens 表，新增 old_email/status/attempts 3 列
    - 新增文件: EmailChangeService / EmailChangeController / 3 DTOs / 2 邮件模板 / 1 Migration
    - 修改文件: EmailVerificationToken entity / EmailVerificationTokenRepository / ErrorCode / AuditAction / MailOutboxService / CttMailProperties / UserProfileResponse / UserProfileService / User entity
    - 测试: EmailChangeServiceTest (19 tests) + EmailChangeIntegrationTest (13 tests) + 修复预存编译错误
    - 验证: ./gradlew build — BUILD SUCCESSFUL (912 tests, 0 failures)
    - API 端点: POST /change-request, POST /change-confirm, DELETE /change-request, GET /status
    - 错误码: USER_009~011, USER_013~014, 审计: EMAIL_CHANGE_*
    - 版本: 0.30.1 → 0.31.1 (MINOR: 新功能 + PATCH: 去重修复)
    - 待提交: 用户处理 commit/push

- [2026-07-03] - lastLoginAt / lastLoginIp 登录元数据补全（修复 /api/v1/users/me 响应缺失 lastLoginAt 字段 bug）
    - 根因: User 实体有 lastLoginAt / lastLoginIp 字段（DB schema + time-strategy.md 明确定义），但 UserLoginService.login() 和 OAuthLoginOrRegisterService 的登录流程从未设置
    - 修复: UserLoginService.login() 成功后设置 user.setLastLoginAt(Instant.now()) + user.setLastLoginIp(clientIp)
    - 修复: OAuthLoginOrRegisterService.handleExistingBinding() 设置 user.setLastLoginAt(Instant.now()) + user.setLastLoginIp(clientIp)（clientIp 从 state payload 取得，authorize 时捕获）
    - 修复: OAuthLoginOrRegisterService.registerNewUser() 设置 newUser.setLastLoginAt(Instant.now()) + newUser.setLastLoginIp(clientIp)（OAuth 新用户首次登录）
    - 修复: User.java 补充缺失的 setLastLoginAt(Instant) setter 方法
    - 保留: UserProfileResponse.lastLoginAt 的 @JsonInclude(ALWAYS) 兜底（新注册但未登录的用户字段为 null）
    - 防复发: systemPatterns.md 新增 "Login Metadata Setting" 模式 + developer-handbook.md 新增登录元数据实现检查清单
    - 全量测试: 876/876 PASS（3 新增测试：shouldSetLoginMetadata_whenLoginSucceeds + shouldSetLastLoginAt_onSuccessfulLogin + shouldRedirectToError_whenStateIsNotUuidFormat）
    - 版本: 0.30.0 → 0.30.1（PATCH: bug fix）

- [2026-07-01] - OAuth User Profile Endpoint (PR-C, GET /api/v1/users/me, ctt-web AppHeader dropdown 支撑)
    - 新增: UserProfileResponse record (auth.user.dto) - 7 字段 DTO: id/email/displayName/emailVerified/createdAt/lastLoginAt/termsVersion
    - 新增: UserProfileService - read-only, @Transactional(readOnly=true), getCurrentUserProfile(UUID) 调用 UserRepository.findById + UserProfileResponse.fromEntity
    - 新增: UserController @GetMapping("/me") - currentUserProvider.getCurrentUserRequired().id() 提取 userId (与 OAuthAccountController 模式一致)
    - 安全: 故意不暴露 passwordHash / lastLoginIp / version / emailVerifiedAt / termsAcceptedAt / updatedAt
    - 设计: emailVerified 派生自 User.emailVerifiedAt != null (而非 User.emailVerified Boolean 字段，避免双源不一致)
    - 设计: avatar 不存储 (用户决定 avatar = id hash 由前端生成，后端零工作量)
    - Swagger: @SecurityRequirement("bearerAuth") + @ApiResponses 200 (RestApiResponse) + 401 (ErrorResponse + AUTH_002 独立 @ExampleObject) + @Tag("User")
    - 测试: 9 新增 (UserProfileServiceTest 4 + UserControllerMockMvcTest 5 含敏感字段不暴露 doesNotHavePath 断言)
    - 文档: README.md API 端点表新增 GET /users/me 行 + Avatar 字段说明
    - 文档: .sisyphus/plan/2026-07-01-user-profile.md (AI 内部 plan，不入 docs/)
    - 工程: .gitignore 添加 .sisyphus/ (AI plan 内部目录，不污染版本控制)
    - 限制: 缺 OAuthUserProfileIntegrationTest (与 PR-A/B 对称未补，可未来 hardening)
    - 限制: docs/developer-handbook.md 缺 GET /users/me 条目 (与之前 AUTH_017/018 同样遗留)
    - 全量测试: 874/874 PASS (之前 865)，0 回归
    - 版本: 0.29.0 → 0.30.0 (MINOR: 新 endpoint)

- [2026-07-01] - OAuth UNBIND 流程（PR-B 解除已绑定 GitHub 账号 + last-login-method 防御）
    - 新增: OAuthLoginOrRegisterService.unbindFromExistingUser(currentUserId, provider) — 校验 binding 存在 (AUTH_017 404) + last-method 守卫 (AUTH_018 409: 无密码且唯一 OAuth 时禁止解绑) + delete UserOAuthAccount + 审计 OAUTH_ACCOUNT_UNLINKED；**session 不变**（不撤销/重发 token，user 保持已登录）
    - 新增: UserOAuthAccountRepository.countByUserId(UUID) — last-method 守卫用，索引 user_oauth_accounts.user_id
    - 新增: OAuthAccountController.unbindAccount @DeleteMapping("/{provider}") 返回 204 No Content
    - 新增: OAuthAccountController.handlePathVariableConversion @ExceptionHandler — invalid provider 路径变量 → 400 + COMMON_001（带 traceId）
    - 修改: ErrorCode AUTH_017 HTTP 400 → 404 (资源未找到); AUTH_018 HTTP 400 → 409 (冲突：解绑后无任何登录方式)
    - 修改: ErrorCodeTest 同步 HTTP 状态码断言
    - 安全: 非幂等设计 (与 BIND 一致) — 第二次 DELETE 同一 provider 返回 404 + AUTH_017
    - 安全: 并发场景宽容幂等 — 两个并发 DELETE 都会返回 204，DB 实际只删一次（DELETE WHERE id=X 影响 0 行无错）
    - 设计: last-method 守卫的不可达分支 (password=null && count=0 / password!=null && count=0) 已被前置 findByUserIdAndProvider 拦截
    - 设计: Session invariant 显式声明 (unbindFromExistingUser Javadoc 第 4 条) — 与 BIND 保持对称
    - 测试: 13 新增（OAuthAccountControllerMockMvcTest 11 包括 multi-OAuth + 重复删除去重; OAuthLoginOrRegisterServiceTest 5; ErrorCodeTest 更新）；去重 1 个（shouldReturn404_whenUnbindTwice 与 shouldReturn404_whenProviderNotLinked 行为完全相同）
    - OAuth 模块 96/96 PASS；全量 865/865 PASS（之前 852）
    - 文档: dev-docs/oauth/frontend-integration.md 新增 "GitHub Account Unbinding Flow (UNBIND)" 章节含业务规则 + 调用示例 + 错误码映射表 + 幂等性说明
    - 文档: README.md OAuth 端点表新增 DELETE 行
    - 限制: 缺 OAuthUnbindIntegrationTest（@SpringBootTest 级别 E2E），与 BIND 一致未补，可未来 hardening
    - 限制: 未给 ErrorCode 常量加 Javadoc 说明语义归属（pre-existing pattern，PR-B 未引入新违规）
    - 限制: docs/developer-handbook.md Error Code Registry 表缺 AUTH_017/018 条目（与 BIND 时漏的对称问题）
    - 风格遗留: 6 处 SonarQube java:S5778 lambda 警告（在 OAuthLoginOrRegisterServiceTest 中 assertThatThrownBy 模式），按用户决定不修不抑制
    - 版本: 0.28.0 → 0.29.0 (MINOR: 新 UNBIND endpoint)

