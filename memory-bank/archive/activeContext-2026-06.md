# Active Context 归档 — 2026-06

> 冷数据归档（R13）。内容为该月已完成的条目，按时间倒序；仅供回溯，不再更新。

- [2026-06-28] - OAuth BIND 流程（修复已登录用户绑定 GitHub 时被强制登出 bug）
    - 新增: OAuthStatePayload.Action.BIND + currentUserId(UUID) + redirectUrl 字段；canonical constructor 校验 BIND 必须 currentUserId + LOGIN 必须 currentUserId == null
    - 新增: OAuthLoginOrRegisterService.attachToExistingUser(currentUserId, provider, accessToken, userInfo) — 校验 user ACTIVE + providerUserId 未被其他用户占用 (AUTH_016) + (user, provider) 未绑定 (AUTH_016) + UserOAuthAccount 插入 + 审计 OAUTH_ACCOUNT_LINKED；**不发 token**
    - 修改: OAuthCallbackController.authorize 接受 ?action=login|bind；BIND 需 JWT，从 SecurityContext 取 currentUserId 写入 state payload
    - 修改: OAuthCallbackController.callback 分支处理 BIND：成功 302 → /settings/profile?linked=github；失败 302 → /settings/profile?linked=github&error={code}；LOGIN 流程完全不变
    - 修改: OAuthCallbackController callback 加 state UUID 格式校验（defense-in-depth + 解决 Path Traversal taint analyzer 警告）
    - 修改: OAuthCallbackController.handleOAuthBusinessException 用 HandlerMethod 区分 authorize (JSON 401) vs callback (302 redirect)
    - 修改: OAuthAccountBinding.fromEntity 加 providerLogin 三级兜底（trim + login → email local-part → providerUserId）
    - 修改: GlobalExceptionHandler uk_user_oauth_provider_uid + uk_user_oauth_user_provider 错误码从 USER_001 改为 AUTH_016
    - 安全: BIND 失败时用户 session 完全不变（不发新 token、不动 refresh_token）；3 处显式声明 "Session invariant"
    - 测试: 22 新增（OAuthStatePayloadTest 9 + OAuthStateServiceTest round-trip 2 + OAuthLoginOrRegisterServiceTest 6 + OAuthCallbackController MockMvc 14 含 token-invariance 验证 + OAuthAccountQueryServiceTest fallback 5）；OAuth 模块 97/97 PASS；全量 852/852 PASS（之前 819）
    - 字段兼容性: 旧 JSON {"action":"LOGIN","redirectUri":"...","userId":"..."} 反序列化 → (LOGIN, null, null)；Jackson 默认忽略未知字段名
    - 文档: dev-docs/oauth/frontend-integration.md 新增 BIND 流程章节 + error code → toast 映射表；README OAuth 端点表更新 ?action=bind 说明
    - 规则: AGENTS.md R8.5 新增 OAuthStatePayload.Action 枚举使用约定子规则（独立 commit，AI 内容不混入业务 commit）
    - 版本: 0.27.0 → 0.28.0 (MINOR: 新 BIND 流程 + BUG 修复)

- [2026-06-28] - OAuth 绑定状态查询 API (ctt-web /settings/profile "Connected Accounts" 改造支撑)
    - 新增: OAuthAccountsResponse + OAuthAccountBinding records (auth.oauth.dto)
    - 新增: OAuthAccountController (GET /api/v1/auth/oauth/accounts)
    - 新增: OAuthAccountControllerMockMvcTest (4 个测试: 空列表/字段映射/敏感字段不泄露/未鉴权 401)
    - 设计: 注入 CurrentUserProvider + UserOAuthAccountRepository (与 OAuthCallbackController 解耦)
    - 安全: 响应不含 accessToken/refreshToken/providerUserId (验证通过 doesNotHavePath 断言)
    - 安全: @SecurityRequirement(bearerAuth) 标记鉴权要求
    - 测试: 4/4 通过，MockMvcTester + @MockitoBean + @WithMockUser 模式
    - 版本: 0.26.2 → 0.27.0 (MINOR: 新查询端点)

