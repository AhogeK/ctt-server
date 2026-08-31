# Active Context 归档 — 2026-05

> 冷数据归档（R13）。内容为该月已完成的条目，按时间倒序；仅供回溯，不再更新。

- [2026-05-28] - OAuth 开发文档移至 dev-docs 目录
    - 移动: docs/qa/oauth-manual-test.md → dev-docs/oauth/oauth-manual-test.md
    - 移动: docs/oauth/github-app-setup.md → dev-docs/oauth/github-app-setup.md
    - 移动: docs/oauth/frontend-integration.md → dev-docs/oauth/frontend-integration.md
    - 修复: 相对链接更新为 ../../docs/ 前缀
    - 原因: 分离开发/测试文档与正式项目文档
    - 版本: 0.26.1 → 0.26.2

- [2026-05-27] - OAuth 测试增强 + 文档审查完成
    - 新增: OAuthCallbackControllerMockMvcTest.java (7 个 MockMvc HTTP 测试)
    - 增强: OAuthLoginOrRegisterServiceTest 3 个 ArgumentCaptor 断言 (accessToken, status=ACTIVE, displayName)
    - 审查: docs/oauth/* 3 个文档全部 PASS, 无需修正
    - 修复: MockMvc 测试 TermsCheckFilter exclude + @WithMockUser 适配
    - 测试: 814 全量测试通过
    - 版本: 0.26.0 (不变)

- [2026-05-23] - hCaptcha 后端集成完成
    - 新增: HcaptchaProperties.java (@ConfigurationProperties, ctt.security.hcaptcha prefix)
    - 新增: CaptchaService.java (RestClient 验证, 5s timeout, 优雅降级)
    - 新增: CaptchaServiceTest.java (7 个测试, MockRestServiceServer)
    - 新增: ErrorCode SECURITY_006 (captcha验证失败, 403) + SECURITY_007 (服务不可用, 502)
    - 修改: LoginRequest, UserRegisterRequest, ForgotPasswordRequest 新增 captchaToken 字段
    - 修改: AuthController register/login/forgotPassword 首行调用 verifyCaptcha()
    - 修改: ConfigController GET /api/v1/config/public 返回 captchaSiteKey
    - 修改: application.yaml + local + dev 配置 hCaptcha (dev/local 用官方测试密钥)
    - 修改: 7+ 测试文件适配 captchaToken 构造函数变更
    - 修复: application-dev.yaml 重复 ctt key 合并
    - 安全: secret-key 仅服务端使用, 优雅降级 (siteKey 为空跳过验证)
    - 版本: 0.25.2 → 待 bump

- [2026-05-23] - AuthController + ConfigController 集成 Captcha 验证
    - AuthController: 注入 CaptchaService，register/login/forgotPassword 首行调用 captchaService.verifyCaptcha()
    - ConfigController: 注入 HcaptchaProperties，PublicConfigResponse 新增 captchaSiteKey 字段
    - 未添加: refreshToken/logoutAll/confirmPasswordReset/acceptTerms（不需要人机验证）
    - CaptchaService 由并行任务创建，HcaptchaProperties 已存在
    - 文件: AuthController.java, ConfigController.java
    - 版本: 待 bump

- [2026-05-23] - TermsCheckFilter 修复 + OAuthCallbackController 修复
    - 根因: `authentication.getCredentials()` 在 Spring Security 认证成功后返回 null，导致 filter 放行
    - 修复: TermsCheckFilter 改为从 Authorization 请求头提取 JWT（BEARER_PREFIX 常量 + request.getHeader）
    - 修复: OAuthCallbackController OAuth 重定向补充 termsExpired 参数
    - 测试: TermsCheckFilterTest 6 个测试适配新 mock 方式
    - 文档: 新增 docs/terms-acceptance-frontend-guide.md 前端集成指南
    - 版本: 0.25.1 → 0.25.2 (PATCH: bug fix)

- [2026-05-08] - Terms Acceptance 功能完成（测试 + 文档 + 版本号）
    - 测试覆盖: 8个测试（JwtTokenProviderTest 2个 + UserServiceTest 2个 + UserValidatorTest 4个）
    - 文档完善: README.md 特性概述 + docs/developer-handbook.md USER_008/AUTH_019错误码 + docs/api-governance.md 端点分类
    - 版本号: 0.25.1-SNAPSHOT → 0.25.1（移除 -SNAPSHOT，功能完成）
    - 文件: 6个测试文件 + 3个文档文件 + 1个版本号文件
    - 测试状态: 全量测试通过

- [2026-05-07] - Terms Acceptance Code Review 修复 + 版本升级
    - 背景: Oracle/Momus 代码审查发现 16 个 P0/P1/P2 问题
    - 修复: P0(4项) SecurityConfig 注册 TermsCheckFilter + OAuth termsVersion + TokenRefreshService termsExpired 逻辑 + 测试覆盖
    - 修复: P1(5项) UserService acceptTerms 事务性 + 审计日志 + terms/accept 限流 + architecture 模式
    - 修复: P2(5项) Filter 异常处理 + null safety + Javadoc + test assertions + token comparison
    - 测试修复: AuthControllerTest 401 assertions ($.code → $.data.code, AUTH_002 → AUTH_003)
    - 版本: 0.25.0-SNAPSHOT → 0.25.1-SNAPSHOT (PATCH: code review fixes)
    - 文件: 14 main source files, 12+ test files
    - 测试状态: 792 tests passing

- [2026-05-03] - termsVersion 格式调整：日期→语义化版本号
    - 变更: 全局 "2026-05-02" → "1.0.0"（application.yaml, application-test.yaml, TermsProperties, UserRegisterRequest, 7个测试文件）
    - 变更: docs/plans/2026-05-02-terms-acceptance.md 示例同步更新
    - 原因: 前端建议，版本号格式更直观，日期信息由 lastUpdated 字段承载
    - 文件: 11 个文件
    - 版本: 0.25.0-SNAPSHOT (不变)

- [2026-05-04] - terms/accept 接口验证与测试修复
    - 变更: AuthControllerTest.java 新增 AcceptTermsTests，验证已认证用户重新同意条款流程
    - 修复: 修复 AuthControllerForgotPasswordTest 等 5 个测试文件，补全 UserRepository 与 TermsProperties 模拟 Bean
    - 说明: 计划 10.2，核心功能闭环验证完成
    - 文件: AuthControllerTest.java, 5 个相关测试文件
    - 版本: 0.25.0-SNAPSHOT (不变)

- [2026-05-04] - TermsCheckFilter 逻辑验证
    - 新增: TermsCheckFilterTest.java 实现 16 个测试用例，覆盖版本匹配、过期、跳过公开接口等场景
    - 说明: 计划 10.2，确保过滤器行为符合预期且不影响现有流程
    - 文件: TermsCheckFilterTest.java
    - 版本: 0.25.0-SNAPSHOT (不变)

- [2026-05-04] - LoginResponse 序列化验证
    - 新增: LoginResponseTest.java 验证 termsExpired 字段在 JSON 中的序列化与反序列化
    - 说明: 计划 10.1
    - 文件: LoginResponseTest.java
    - 版本: 0.25.0-SNAPSHOT (不变)

- [2026-05-04] - AuthController 实现 terms/accept API
    - 变更: AuthController.java 新增 POST /terms/accept 端点，支持重新同意条款并获取新 Token
    - 变更: UserLoginService.java 新增 issueTokens(User) 方法支持 Token 重发
    - 说明: 计划 4.3，打通条款同意闭环
    - 文件: AuthController.java, UserLoginService.java
    - 版本: 0.25.0-SNAPSHOT (不变)

- [2026-05-04] - UserService 新增 acceptTerms 方法
    - 新增: UserService.java public void acceptTerms(UUID, String) 实现，更新同意时间与版本
    - 说明: 计划 4.4，为 4.3 API 提供服务层支持
    - 文件: UserService.java
    - 版本: 0.25.0-SNAPSHOT (不变)

- [2026-05-03] - 修复 TermsCheckFilter 硬编码 URI
    - 变更: TermsProperties 新增 termsAcceptPath 字段（默认 /api/v1/auth/terms/accept）
    - 变更: TermsCheckFilter 移除 TERMS_ACCEPT_PATH 常量，使用配置属性
    - 变更: application.yaml + application-test.yaml 新增 terms-accept-path 配置
    - 说明: 修复 SonarQube "URIs should not be hardcoded" 问题
    - 文件: TermsProperties.java, TermsCheckFilter.java, application.yaml, application-test.yaml, 3个测试文件
    - 版本: 0.25.0-SNAPSHOT (不变)

- [2026-05-03] - 新增 TermsCheckFilter + 测试修复
    - 新增: TermsCheckFilter.java (@Component, OncePerRequestFilter)
    - 修复: 7个测试文件 excludeFilters 排除 TermsCheckFilter（@WebMvcTest 不加载 TermsProperties）
    - 说明: 计划 4.2，检查 JWT 中 termsVersion 与当前版本比对
    - 文件: TermsCheckFilter.java, 7个测试文件
    - 版本: 0.25.0-SNAPSHOT (不变)

- [2026-05-03] - JWT 编码 termsVersion claim
    - 变更: JwtTokenProvider.java generateAccessToken 新增 termsVersion claim（null-safe）
    - 说明: 计划 4.1，为 TermsCheckFilter 提供比对依据
    - 文件: JwtTokenProvider.java
    - 版本: 0.25.0-SNAPSHOT (不变)

- [2026-05-03] - ErrorCode 更新 USER_008 + 新增 AUTH_019
    - 变更: USER_008 消息 "Terms acceptance required" → "Terms version mismatch"
    - 新增: AUTH_019 "Terms version expired, please re-accept" (403 FORBIDDEN)
    - 说明: 计划 3.11，P0 错误码完成
    - 文件: ErrorCode.java
    - 版本: 0.25.0-SNAPSHOT (不变)

- [2026-05-03] - 新增 ConfigController 公开配置接口
    - 新增: ConfigController.java (@RestController, GET /api/v1/config/public)
    - 决策: 独立 Controller 而非放在 AuthController（路径 /api/v1/config 不属于 /api/v1/auth）
    - 说明: 计划 3.10，返回 PublicConfigResponse(termsVersion)
    - 文件: ConfigController.java
    - 版本: 0.25.0-SNAPSHOT (不变)
    - 变更: UserLoginServiceTest.java 增加 TermsProperties 参数
    - 变更: OAuthLoginOrRegisterServiceTest.java 增加 TermsProperties 参数
    - 说明: 适配 3.8/3.9 的构造函数变更
    - 文件: 2 个测试文件
    - 版本: 0.25.0-SNAPSHOT (不变)

- [2026-05-03] - OAuthLoginOrRegisterService termsExpired 逻辑
    - 变更: OAuthLoginOrRegisterService.java 注入 TermsProperties，createLoginResponse 比对 termsVersion
    - 说明: 计划 3.9，与邮箱登录统一
    - 文件: OAuthLoginOrRegisterService.java
    - 版本: 0.25.0-SNAPSHOT (不变)

- [2026-05-03] - UserLoginService termsExpired 逻辑
    - 变更: UserLoginService.java 注入 TermsProperties，登录时比对 termsVersion，返回 termsExpired
    - 说明: 计划 3.8
    - 文件: UserLoginService.java
    - 版本: 0.25.0-SNAPSHOT (不变)

- [2026-05-03] - LoginResponse 新增 termsExpired
    - 变更: LoginResponse.java 新增 boolean termsExpired 字段（@Schema + Javadoc）
    - 变更: 便利构造函数默认 termsExpired=false
    - 说明: 计划 3.7，实际逻辑在 3.8 UserLoginService 中实现
    - 文件: LoginResponse.java
    - 版本: 0.25.0-SNAPSHOT (不变)

- [2026-05-03] - 测试修复 termsAccepted→termsVersion
    - 变更: UserRegisterRequestTest.java 15处 true → "2026-05-02"
    - 变更: RegistrationAndVerificationIntegrationTest.java 1处 true → "2026-05-02"
    - 变更: UserServiceTest.java 4处 true → "2026-05-02"
    - 变更: UserValidatorTest.java 构造函数增加 TermsProperties 参数
    - 说明: 适配 3.4/3.5/3.6 的 DTO 和 Validator 变更
    - 文件: 4 个测试文件
    - 版本: 0.25.0-SNAPSHOT (不变)

- [2026-05-03] - UserValidator + UserService 修改
    - 变更: UserValidator.java assertTermsAccepted → assertTermsVersionValid，注入 TermsProperties，版本比对逻辑
    - 变更: UserService.java assertTermsAccepted → assertTermsVersionValid，setTermsVersion 从 request 获取
    - 说明: 计划 3.5 + 3.6
    - 文件: UserValidator.java, UserService.java
    - 版本: 0.25.0-SNAPSHOT (不变)

- [2026-05-03] - UserRegisterRequest termsAccepted→termsVersion
    - 变更: UserRegisterRequest.java Boolean termsAccepted → String termsVersion (@NotBlank + @Schema)
    - 变更: AuthControllerTest.java "termsAccepted": true → "termsVersion": "2026-05-02" (2处)
    - 变更: UserService.java request.termsAccepted() → request.termsVersion()
    - 变更: UserValidator.java assertTermsAccepted 参数改为 String
    - 说明: 计划 3.4 DTO 修改
    - 文件: UserRegisterRequest.java, AuthControllerTest.java, UserService.java, UserValidator.java
    - 版本: 0.25.0-SNAPSHOT (不变)

- [2026-05-03] - TermsProperties 配置类创建 + yaml 配置添加
    - 新增: TermsProperties.java (@ConfigurationProperties(prefix = "ctt.terms"), record, currentVersion 字段)
    - 变更: application.yaml 新增 ctt.terms.current-version: "2026-05-02"
    - 说明: 计划 3.3 配置项，为后续 UserValidator.assertTermsVersionValid 提供版本来源
    - 文件: TermsProperties.java, application.yaml
    - 版本: 0.25.0-SNAPSHOT (不变)

- [2026-05-03] - Terms 迁移脚本融合进 init schema
    - 变更: 删除独立迁移文件 V20260502000000__add_terms_fields.sql
    - 变更: 将 terms_accepted_at (TIMESTAMPTZ) 和 terms_version (VARCHAR(20)) 两列融入 init_base_schema.sql 的 users 表 CREATE TABLE 语句
    - 变更: 将两列的 COMMENT ON COLUMN 追加到 users 表注释区块末尾
    - 原因: 项目仍在开发阶段，无需独立迁移文件，统一融入 init schema 简化迁移链
    - 文件: V20260303210000__init_base_schema.sql (modified), V20260502000000__add_terms_fields.sql (deleted)
    - 版本: 0.25.0-SNAPSHOT (不变，重构清理)

- [2026-05-02] - Terms 同意状态记录（GDPR/CCPA 合规）
    - 变更: Flyway 迁移 V20260502000000__add_terms_fields.sql 添加 users.terms_accepted_at (TIMESTAMPTZ) 和 users.terms_version (VARCHAR(20))
    - 变更: User.java 添加 termsAcceptedAt (Instant) 和 termsVersion (String) 字段及 getter/setter
    - 变更: UserRegisterRequest.java 添加 Boolean termsAccepted 字段（带 @NotNull 和 @Schema）
    - 变更: ErrorCode.java 新增 USER_008 "Terms acceptance required" (400 BAD_REQUEST)
    - 变更: UserValidator.java 新增 assertTermsAccepted(Boolean) 方法（null/false 抛 ValidationException）
    - 变更: UserService.registerUser() 添加 termsAccepted 校验 + 设置 termsAcceptedAt/termsVersion
    - 说明: GDPR/CCPA 合规要求，注册时必须同意条款，条款更新时检查 terms_version 是否过期
    - 文件: V20260502000000__add_terms_fields.sql, User.java, UserRegisterRequest.java, ErrorCode.java, UserValidator.java, UserService.java
    - 版本: 0.24.3-SNAPSHOT → 0.25.0-SNAPSHOT (MINOR: 新功能)

- [2026-05-02] - 邮件 From 显示名称修正（CTT Server → Code Time Tracker）
    - 变更: application.yaml 默认值 `CTT Server` → `Code Time Tracker`
    - 变更: CttMailProperties.java 注释示例 `"CTT Server"` → `"Code Time Tracker"`
    - 变更: OpenApiConfig.java API 标题 `"CTT Server API"` → `"Code Time Tracker API"`
    - 说明: 品牌一致性修正，邮件发送方显示名称与项目名称保持一致
    - 文件: application.yaml, CttMailProperties.java, OpenApiConfig.java
    - 版本: 0.24.2-SNAPSHOT → 0.24.3-SNAPSHOT (PATCH: 品牌名称修正)
- [2026-05-01] - 密码字符白名单 REGEX_PASSWORD_CHARS 补充约束
    - 变更: ValidationConstants.java 新增 REGEX_PASSWORD_CHARS = "^[!-~]+$"（所有可打印 ASCII 非空格字符）
    - 变更: ValidationConstants.java 新增 MSG_PASSWORD_CHARS 错误消息
    - 变更: StrongPassword.java 新增 @Pattern(regexp=REGEX_PASSWORD_CHARS) 约束
    - 说明: NIST SP 800-63B 对齐的补充约束，仅限制字符范围（可打印 ASCII），不引入复杂度要求
    - 测试: ValidationConstantsTest 新增 validPasswordCharsProvider (3 种合法字符组合) + rejects_invalid_chars (6 种: 空格/制表/拉丁扩展/中文/emoji)
    - 文件: ValidationConstants.java, StrongPassword.java, ValidationConstantsTest.java
    - 版本: 0.24.1-SNAPSHOT → 0.24.2-SNAPSHOT (PATCH: 约束行为细化)

- [2026-05-01] - 密码校验规则 NIST SP 800-63B 对齐 + ParameterizedTest 合并
    - 变更: ValidationConstants.java 移除 REGEX_PASSWORD (复杂度正则)，新增 PASSWORD_MIN_LENGTH=8 / PASSWORD_MAX_LENGTH=64
    - 变更: StrongPassword.java @Pattern(regex=REGEX_PASSWORD) → @Size(min=PASSWORD_MIN_LENGTH, max=PASSWORD_MAX_LENGTH)
    - 变更: MSG_PASSWORD_WEAK "8-32, including uppercase/lowercase/digit/special" → "8-64 characters"
    - 变更: 3 个 DTO @Schema(password) description 更新（UserRegisterRequest, LoginRequest, ResetPasswordRequest）
    - 测试: UserRegisterRequestTest 移除复杂度测试，新增长度 ParameterizedTest（@CsvSource 3 种: blank/too short/too long），边界值(64字符)、null密码均已覆盖
    - 测试: ValidationConstantsTest 移除正则测试，新增长度常量测试
    - 审查: 3 concurrent review agents 审查通过 + 测试覆盖验证通过
    - 验证: 编译通过 + 全量测试通过 + LSP diagnostics clean
    - 版本: 0.23.2-SNAPSHOT → 0.24.1-SNAPSHOT (PATCH, 约束行为变更 + 测试重构)

