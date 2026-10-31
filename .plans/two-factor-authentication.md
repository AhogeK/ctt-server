# 两步验证（TOTP + 恢复码）实施计划

> **状态**：已完成（v0.89.0，已交付 develop） · 2026-10-31

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` (native) or
> `superpowers:subagent-driven-development` to implement task-by-task. Steps use checkbox (`- [ ]`)
> syntax for tracking. **Do not commit without the user's explicit authorization in that turn**
> (project R6); each task ends with a verification report, not an automatic commit.

**Goal:** 为 ctt-server 增加 TOTP + 恢复码的两步验证，覆盖密码登录、GitHub OAuth、启用/关闭/轮换与 API key 创建检查，同时不破坏插件现有的 API key 同步。

**Architecture:** 登录先验证密码，再根据 `users.totp_confirmed_at` 决定签发 token 还是返回 202 挑战；挑战用 Redis 存一次性 token，TOTP 用 `otp-java` 校验并以 `totp_last_used_step` 防重放；恢复码哈希存独立表，使用原子占用；启用/关闭 2FA 时撤销其他会话，API key 创建要求网页会话并在启用 2FA 时二次验证。

**Tech Stack:** Java 25 · Spring Boot 4.1.2 · Spring Security 7 · PostgreSQL/Flyway · Redis · `com.github.bastiaanjansen:otp-java:2.2.0` · JUnit 6 + Testcontainers.

**Spec:**
- `.omp/reports/2026-10-30/two-factor-reply-to-frontend.md`（最终契约，含 3 条采纳建议）
- `.omp/reports/2026-10-30/two-factor-decisions.md`（两项拍板与 3 项加固）
- `.omp/reports/2026-10-30/two-factor-requirement-confirmation.md`（原始契约与现状核对）

---

## Global Constraints

- 分支 `develop`；`.plans/`、`memory-bank/` 属 AI/开发内容，**禁止 cherry-pick 进 master**。
- 版本：新功能 → **0.89.0**；版本号只在 `gradle/libs.versions.toml` 的 `appVersion`，版本提交独立且晚于功能提交。
- 迁移：**绝不修改** `V20260303210000__init_base_schema.sql`；新迁移用 `V20261030120000__...`、`V20261030120100__...`。
- 代码/注释/日志英文；`docs/` 面向用户文档英文；计划/记忆中文。
- 错误码固定：`AUTH_025`(403 API key)、`AUTH_026`(401 挑战/setup 失效)、`AUTH_027`(401 码/恢复码无效)、`AUTH_028`(409 已启用)、`AUTH_029`(409 未启用)、`AUTH_030`(403 需要 2FA 复核)、`AUTH_031`(403 需要重新登录)；密码错误复用 `USER_013`(403 缺失)/`USER_014`(401 错误)。
- 限流固定：start 5/h、confirm 5/5min、disable 5/h、regenerate 3/h（USER）；`/auth/login/2fa` 10/5min（IP）+ 每挑战 5 次失败。
- TOTP：SHA1 / 6 位 / 30 秒 / 窗口 ±1；secret 20 字节；恢复码 10 个 × 10 字节（Base32 16 字符，展示 `XXXX-XXXX-XXXX-XXXX`）。
- `TOTP_ENCRYPTION_KEY` 为 32 字节 base64，生产必须配置；测试用固定测试键。任何日志/审计/导出不得出现 secret、TOTP 码、恢复码明文或哈希。
- 插件不改；API key 仍是 2FA 旁路（已接受的权衡），但**新建 API key 必须网页会话 + 2FA 复核**。
- 工作区已有未提交的 `SKILL_GRAPH.md`（上一任务）；本计划的任何提交都不得包含它。

---

## Verified Facts

| 维度 | 实测值 | 来源 |
|---|---|---|
| 登录返回 | `RestApiResponse<LoginResponse>`；成功同时写 Cookie + `Authorization` 头 | `AuthController.login` |
| 登录限流 | 30/h，默认 `RateLimitType.IP` | `@RateLimit(limit=30, windowSeconds=3600)`；`RateLimit.type()` 默认 IP |
| API key 身份 | `getActiveUserRequired()` 接受 `ApiKeyPrincipal`；全仓仅 `DELETE /users/me` 显式判 `WEB_SESSION` | `SpringSecurityCurrentUserProvider`、`UserController:916` |
| API key 创建 | 只要求 `getActiveUserRequired()` + `WRITE` scope ⇒ API key 可再铸 key | `ApiKeyController.createApiKey` |
| 插件认证 | 纯 API key：`SyncApiServiceImpl` 每个方法传 `bearerToken`；无密码登录 | `code-time-tracker` 只读核对 |
| 错误码上限 | `AUTH_025` 为当前最大 | `ErrorCode.java` |
| 审计动作 | 无 TOTP 相关 | `AuditAction.java` |
| 用户表 | 无 2FA 列 | `V20260303210000__init_base_schema.sql` |
| Redis 模式 | `StringRedisTemplate.opsForValue().getAndDelete` 已用于一次性 state | `OAuthStateService` |
| 会话身份 | JWT 带 `sid`；`CurrentUser.sessionId` 可用；refresh token 有 `session_id` | `JwtToCurrentUserConverter`、`RefreshTokenRepository` |
| OAuth 回调 | 当前把 access/refresh token 放前端跳转 query 并写 Cookie | `OAuthCallbackController.callback` |
| 导出 | `UserExportService` 流式写 JSON；新增字段默认不会自动进入 DTO | `UserExportService` |
| TOTP 依赖 | 项目当前无 TOTP/commons-codec 声明 | `build.gradle.kts` / `libs.versions.toml` |

**UNVERIFIED（执行时先读源码/字节码再落代码）：**
- `otp-java 2.2.0` 的精确 API（`TOTP`/`Generator`/`Verifier` 方法名、窗口参数）——下载 jar 后 `javap` 或读 javadoc 确认。
- 当前会话 `createdAt` 的查询方法（用于 OAuth-only 的 10 分钟再验证）——读 `RefreshTokenRepository` 的会话行查询后确定；不可用 `lastLoginAt` 代替。
- `OAuthLoginOrRegisterService` 的最小重构边界（在签发 token 前插入 2FA 判断）——读现有事务与测试后确定。

---

## Decision Ledger

| 项 | 状态 | 依据 |
|---|---|---|
| 202 + 信封、OAuth fragment、错误码 026–031 | ✅ decided | 前端回函全部接受；项目侧拍板 |
| TOTP 库 `otp-java:2.2.0` | ✅ decided | Maven Central latest/release 2.2.0；MIT；Java 17；仅 commons-codec |
| 启用/关闭撤销其他会话 | ✅ decided | 防旧 refresh token 绕过；关闭防他人在你的机器上关闭 |
| 启用再验证：密码或会话 ≤10 分钟 | ✅ decided | 前端建议 + 改用会话创建时间，不用 `lastLoginAt` |
| API key 创建检查 | ✅ decided | 修复“API key 铸 API key”绕过 |
| 执行方式（native / subagent-driven） | 🔸 needs user ruling | 任务间接口耦合高，建议 native；由用户选择 |

---

## Affected Files

### 新增
- `src/main/resources/db/migration/V20261030120000__add_two_factor_to_users.sql`
- `src/main/resources/db/migration/V20261030120100__create_user_recovery_codes.sql`
- `src/main/java/com/ahogek/cttserver/common/config/properties/TwoFactorProperties.java`
- `src/main/java/com/ahogek/cttserver/auth/WebSessionGuard.java`
- `src/main/java/com/ahogek/cttserver/auth/twofactor/entity/UserRecoveryCode.java`
- `src/main/java/com/ahogek/cttserver/auth/twofactor/repository/UserRecoveryCodeRepository.java`
- `src/main/java/com/ahogek/cttserver/auth/twofactor/service/TotpSecretCipher.java`
- `src/main/java/com/ahogek/cttserver/auth/twofactor/service/TotpService.java`
- `src/main/java/com/ahogek/cttserver/auth/twofactor/service/RecoveryCodeService.java`
- `src/main/java/com/ahogek/cttserver/auth/twofactor/service/TwoFactorChallengeService.java`
- `src/main/java/com/ahogek/cttserver/auth/twofactor/service/TwoFactorService.java`
- `src/main/java/com/ahogek/cttserver/auth/twofactor/service/TwoFactorSetupStore.java`
- `src/main/java/com/ahogek/cttserver/auth/twofactor/service/LoginTwoFactorService.java`
- `src/main/java/com/ahogek/cttserver/auth/twofactor/controller/TwoFactorController.java`
- `src/main/java/com/ahogek/cttserver/auth/dto/LoginOutcome.java`
- `src/main/java/com/ahogek/cttserver/auth/dto/LoginChallengeResponse.java`
- `src/main/java/com/ahogek/cttserver/auth/dto/LoginTwoFactorRequest.java`
- `src/main/java/com/ahogek/cttserver/auth/twofactor/dto/TwoFactorStatusResponse.java`
- `src/main/java/com/ahogek/cttserver/auth/twofactor/dto/StartTotpRequest.java`
- `src/main/java/com/ahogek/cttserver/auth/twofactor/dto/StartTotpResponse.java`
- `src/main/java/com/ahogek/cttserver/auth/twofactor/dto/ConfirmTotpRequest.java`
- `src/main/java/com/ahogek/cttserver/auth/twofactor/dto/RecoveryCodesResponse.java`
- `src/main/java/com/ahogek/cttserver/auth/twofactor/dto/DisableTwoFactorRequest.java`
- `src/main/java/com/ahogek/cttserver/auth/twofactor/dto/RegenerateRecoveryCodesRequest.java`
- 测试：`TotpServiceTest`、`TotpSecretCipherTest`、`RecoveryCodeServiceTest`、`TwoFactorChallengeServiceTest`、`TwoFactorSetupStoreTest`、`TwoFactorPropertiesTest`、`TwoFactorSchemaIntegrationTest`、`SessionAgeAndReplayTest`、`TwoFactorEndpointsIntegrationTest`、`LoginTwoFactorIntegrationTest`（包路径按现有测试惯例）
  - **实测与计划差异**：计划里的 `OAuthTwoFactorIntegrationTest` / `ApiKeyTwoFactorIntegrationTest` / `UserTwoFactorMappingTest` 未按原名建立，覆盖落到既有测试类中：OAuth 两条路径（含 email-merge + 2FA）在 `OAuthLoginOrRegisterServiceTest`，fragment 跳转在 `OAuthCallbackControllerMockMvcTest`，API key 创建校验在 `TwoFactorEndpointsIntegrationTest`，三列读写由 `TwoFactorSchemaIntegrationTest` + 端点用例覆盖。按项目一致性（R8.5）以既有测试类承载，而非为对齐计划名新建文件。

### 修改
- `gradle/libs.versions.toml`、`build.gradle.kts`：`otp-java:2.2.0`
- `.env.example`、`src/main/resources/application.yaml`、`src/test/resources/application-test.yaml`：`TOTP_ENCRYPTION_KEY` 与 `ctt.security.two-factor.*`
- `src/main/java/com/ahogek/cttserver/user/entity/User.java`：三个 2FA 列
- `src/main/java/com/ahogek/cttserver/common/exception/ErrorCode.java`：`AUTH_026`–`AUTH_031`
- `src/main/java/com/ahogek/cttserver/audit/enums/AuditAction.java`：4 个 TOTP 动作
- `src/main/java/com/ahogek/cttserver/auth/AuthController.java`：登录 202 分支 + `/login/2fa`
- `src/main/java/com/ahogek/cttserver/auth/service/UserLoginService.java`：`LoginOutcome`、`completeLogin`
- `src/main/java/com/ahogek/cttserver/auth/oauth/controller/OAuthCallbackController.java`、`.../service/OAuthLoginOrRegisterService.java`：2FA 分支
- `src/main/java/com/ahogek/cttserver/auth/apikey/controller/ApiKeyController.java`、`.../dto/CreateApiKeyRequest.java`、`.../service/ApiKeyServiceImpl.java`：创建检查
- `src/main/java/com/ahogek/cttserver/auth/repository/RefreshTokenRepository.java`：`revokeAllUserTokensExceptSession`
- `src/main/java/com/ahogek/cttserver/user/controller/UserController.java`：删除账号复用 `WebSessionGuard`（消除重复的 authType 判断）
- 文档：`docs/api-reference.md`、`docs/api-governance.md`、`docs/security-architecture.md`、`docs/developer-handbook.md`、`README.md`
- 记忆：`memory-bank/activeContext.md`、`progress.md`、`domains/auth-lifecycle/*`、`domains/api-contract/references.md`
- 主计划：`.plans/ctt-server-development-plan.md`（2FA 条目状态）

---

## Progress Ledger（唯一进度源）

| Task | 状态 | 验证证据 |
|---|---|---|
| 1 基础：依赖/配置/迁移/错误码/审计 | ✅ done | `./gradlew test --tests "*TwoFactorPropertiesTest" --tests "*TwoFactorSchemaIntegrationTest" --tests "*ErrorCodeTest" --tests "*AuditActionTest"` → BUILD SUCCESSFUL (29s) |
| 2 TOTP/加密/恢复码服务 | ✅ done | `./gradlew test --tests "*TotpSecretCipherTest" --tests "*TotpServiceTest" --tests "*RecoveryCodeServiceTest" --tests "*TwoFactorSetupStoreTest"` → BUILD SUCCESSFUL (16s) |
| 3 挑战与防重放 | ✅ done | `./gradlew test --tests "*TwoFactorChallengeServiceTest" --tests "*SessionAgeAndReplayTest"` → BUILD SUCCESSFUL (17s) |
| 4 用户 2FA 端点 | ✅ done | `./gradlew test --tests "*TwoFactorEndpointsIntegrationTest"` → BUILD SUCCESSFUL (18s) |
| 5 登录 202 + `/login/2fa` | ✅ done | `./gradlew test --tests "*LoginTwoFactorIntegrationTest" --tests "*AuthControllerTest" --tests "*AuthControllerMalformedInputTest" --tests "*UserLoginServiceTest" --tests "*UserLoginServiceIssueTokensTest"` → BUILD SUCCESSFUL (22s) |
| 6 OAuth 2FA 分支 | ✅ done | `./gradlew test --tests "*OAuthCallbackControllerMockMvcTest" --tests "*OAuthLoginOrRegisterServiceTest" --tests "*OAuthLoginOrRegisterServiceTermsTest"` → BUILD SUCCESSFUL (7s) |
| 7 API key 创建检查 | ✅ done | `./gradlew test --tests "*TwoFactorEndpointsIntegrationTest" --tests "*ApiKeyControllerMockMvcTest"` → BUILD SUCCESSFUL (19s) |
| 8 会话撤销/导出/审计 | ✅ done | `./gradlew test --tests "*TwoFactorEndpointsIntegrationTest"` → BUILD SUCCESSFUL (18s) |
| 9 文档/记忆/版本/全量验证 | ✅ done | `./gradlew spotlessCheck jacocoTestCoverageVerification build` → BUILD SUCCESSFUL (3m08s)；**1618 tests / 0 failures / 1 skipped** |

## Delivery Summary（2026-10-31）

- 交付：密码登录与 GitHub OAuth 均接入 202 挑战；新增 2FA 状态、启用、确认、关闭、恢复码轮换端点；API key 创建改为仅网页会话，并在 2FA 启用时要求 TOTP/恢复码。
- 安全：TOTP secret AES-256-GCM 加密；恢复码仅存 SHA-256 并单次消费；`totp_last_used_step` 条件更新防重放；启用/关闭撤销其他会话；审计与导出不含 secret、码或哈希。
- 复核修正：① Compose 不再透传空 `TOTP_ENCRYPTION_KEY`（Spring 空串不会回退，曾会导致本地启动失败）；② `completeLogin` 重新校验账号状态，挑战完成后若账号被 SUSPENDED/LOCKED/DELETED 仍拒绝签发 token；③ 文档/注释统一为「第五次失败 429」。
- 验证：`spotlessCheck` + `jacocoTestCoverageVerification` + 全量 `build` 通过；**1618 tests / 0 failures / 1 skipped**。
- 已知边界：OAuth email-merge 的 2FA 路径会先建立 provider 绑定再挑战；账号删除/密码/邮箱修改仍只要求密码或会话；关闭 2FA 后既有 access token 最长 15 分钟有效；以上均已在残余风险中记录，不在本批扩大。

**同步规则（强制）**：每完成一个 Task，必须在本表更新状态并在“验证证据”写命令 + 结果；同时更新主计划对应条目与 `memory-bank/activeContext.md`。计划与实现不一致时，**先改计划再改代码**，并记录“原写 X，实测 Y”。本文件只进 develop，不进 master。

**RED→GREEN（强制）**：每个 Task 的测试步骤必须先运行并记录失败（RED），再写实现；只记录通过结果不算完成。

---

## Task 1: 基础依赖、配置、迁移与枚举

**Files:** `libs.versions.toml`, `build.gradle.kts`, `.env.example`, `application.yaml`, `application-test.yaml`, 两个迁移, `TwoFactorProperties.java`, `User.java`, `ErrorCode.java`, `AuditAction.java`

**Interfaces produced:**
- `TwoFactorProperties(String issuer, String encryptionKey, int digits, int periodSeconds, int windowSteps, Duration pendingSetupTtl, Duration challengeTtl, Duration recentAuthenticationWindow, int recoveryCodeCount)`
- `User.getTotpSecretCiphertext()/getTotpConfirmedAt()/getTotpLastUsedStep()` + setters
- `ErrorCode.AUTH_026..AUTH_031`
- `AuditAction.TOTP_ENABLED/TOTP_DISABLED/TOTP_RECOVERY_CODES_REGENERATED/TOTP_CHALLENGE_FAILED`

- [ ] 读 `SecurityProperties` 的 `@ConfigurationProperties` 注册方式，确认新属性类无需额外 `@EnableConfigurationProperties`（若需，加到配置类）。
- [ ] 写迁移：users 三列（`TEXT`/`TIMESTAMPTZ`/`BIGINT`，全部 nullable）+ `user_recovery_codes`（id/user_id/code_hash/batch_id/created_at/used_at，FK cascade，唯一约束 `(user_id, code_hash)`，索引 `(user_id, used_at)`）。
- [ ] 写 `TwoFactorProperties`，`encryptionKey` 做 32 字节 base64 校验；测试配置提供固定键。
- [ ] 加 `otp-java` 到 catalog + `build.gradle.kts`；运行 `./gradlew dependencies --configuration runtimeClasspath | grep -i 'otp\|commons-codec'`，确认只新增预期依赖。
- [x] 写失败测试：`TwoFactorPropertiesTest`（非法 key 拒绝）、`TwoFactorSchemaIntegrationTest`（三列读写）、`ErrorCodeTest`（026–031 状态码）。
- [x] 实现最小代码让测试通过；运行 `./gradlew test --tests "*TwoFactorPropertiesTest" --tests "*TwoFactorSchemaIntegrationTest"`。
- [ ] 更新 Progress Ledger，报告验证输出；不提交。

## Task 2: TOTP、secret 加密、恢复码

**Files:** `TotpSecretCipher.java`, `TotpService.java`, `RecoveryCodeService.java`, `TwoFactorSetupStore.java`, `UserRecoveryCode.java`, `UserRecoveryCodeRepository.java` + 三个单测

**Interfaces produced:**
- `String TotpSecretCipher.encrypt(String plaintext)` / `String decrypt(String ciphertext)`（格式 `v1:<base64 iv>:<base64 ct>`，AES-256-GCM）
- `String TotpService.generateSecret()`（20 字节 → Base32）
- `String TotpService.otpauthUri(String secret, String email)`
- `OptionalLong TotpService.matchingStep(String secret, String code)`（窗口 ±1）
- `List<String> RecoveryCodeService.issue(UUID userId)`（删除旧批次、插入 10 条哈希）
- `boolean RecoveryCodeService.consume(UUID userId, String rawCode)`（原子 `used_at IS NULL`）
- `long RecoveryCodeService.countRemaining(UUID userId)`
- `void RecoveryCodeService.deleteAll(UUID userId)`
- `UserRecoveryCode` 字段：`UUID id, UUID userId, String codeHash, UUID batchId, Instant createdAt, Instant usedAt`
- `int UserRecoveryCodeRepository.consume(UUID userId, String codeHash, Instant now)`（`UPDATE ... WHERE used_at IS NULL`，返回 1 表示占用成功）
- `TwoFactorSetupStore.put(UUID userId, String encryptedSecret, Duration ttl)` / `Optional<String> get(UUID userId)` / `void delete(UUID userId)`

- [ ] 先 `javap`/读 `otp-java` API，确认 `TOTP`/`Generator`/`Verifier` 用法；把结论写入计划（替换 UNVERIFIED）。
- [ ] 写失败测试：AES-GCM 往返、篡改密文失败、密钥长度错误；RFC 6238 已知向量（固定时间/secret）；恢复码 10 个、格式、只存哈希、并发 consume 只有一个成功、旧码在 issue 后失效。
- [ ] 实现最小代码；运行 `./gradlew test --tests "*Totp*" --tests "*RecoveryCode*"`。
- [ ] 更新 Progress Ledger；不提交。

## Task 3: 挑战服务与防重放

**Files:** `TwoFactorChallengeService.java`, `RefreshTokenRepository.java`（会话年龄查询）, 单测/集成测试

**Interfaces produced:**
- `ChallengeTicket create(UUID userId, String deviceId)`（32 字节 token，Redis 存 SHA-256，TTL 300s，返回 `token/expiresIn`）
- `ChallengePayload consume(String token)`（`AUTH_026` 无效/过期；绑定 userId/deviceId）
- `void registerFailure(String token)`（第 5 次失败删除挑战并抛 429；不触碰账号锁定）
- `Instant sessionCreatedAt(UUID userId, UUID sessionId)`（供 OAuth-only 再验证；读 refresh token 会话最早 `created_at`）
- `void markTotpStepUsed(UUID userId, long step)`（条件更新 `step > last`，0 行 = 重放 `AUTH_027`）
- `record ChallengeTicket(String token, long expiresIn)` / `record ChallengePayload(UUID userId, String deviceId)`（`TwoFactorChallengeService` 的嵌套 record）

- [ ] 读 `RefreshTokenRepository` 现有会话查询，确定 `sessionCreatedAt` 的 JPQL；写失败测试（挑战单次、过期、5 次失败、并发 consume、同 step 重放）。
- [ ] 实现 Redis 原子操作（优先 `getAndDelete`/Lua；若用 Lua，测试并发）；运行定向测试。
- [ ] 更新 Progress Ledger；不提交。

## Task 4: 用户 2FA 端点

**Files:** `TwoFactorController.java`, 6 个 DTO, `TwoFactorService.java`, `TwoFactorSetupStore.java`, `WebSessionGuard.java`, `TwoFactorEndpointsIntegrationTest.java`

**Interfaces produced:**
- `TwoFactorStatusResponse status(UUID userId, UUID sessionId)` → `enabled/confirmedAt/recoveryCodesRemaining/hasPassword/recentAuthentication`
- `StartTotpResponse start(UUID userId, String currentPassword, UUID sessionId)`（有密码必填 `USER_013/014`；OAuth-only 会话 ≤10min 否则 `AUTH_031`；`AUTH_028` 已启用）
- `RecoveryCodesResponse confirm(UUID userId, String code)`（`AUTH_027` 错码；返回 10 个码仅一次）
- `void disable(UUID userId, String code, String recoveryCode, UUID sessionId)`（`AUTH_029` 未启用；204）
- `RecoveryCodesResponse regenerate(...)`（旧码全部失效）
- `void requireCodeForSensitiveAction(UUID userId, String code)`（2FA 已启用时：缺失 `AUTH_030`、错误/重放 `AUTH_027`；未启用时 no-op）
- `CurrentUser WebSessionGuard.requireActiveWebSession()`（API key → `AUTH_025`；所有 2FA 端点与 API key 创建使用）

DTO 形状：
- `TwoFactorStatusResponse(boolean enabled, Instant confirmedAt, long recoveryCodesRemaining, boolean hasPassword, boolean recentAuthentication)`
- `StartTotpRequest(String currentPassword)`（nullable）；`StartTotpResponse(String secret, String otpauthUri, int digits, int period, String issuer, Instant expiresAt)`
- `ConfirmTotpRequest(String code)`；`RecoveryCodesResponse(List<String> recoveryCodes)`
- `DisableTwoFactorRequest(String code, String recoveryCode)` / `RegenerateRecoveryCodesRequest(String code, String recoveryCode)`（二选一，服务端校验恰好一个）

- [ ] 写失败集成测试：未启用状态、start 密码缺失/错误、OAuth-only 新鲜/过期、confirm 错码/成功、重复 confirm 409、disable 错码/成功、regenerate 旧码失效、API key 调用 403 `AUTH_025`、每个端点限流。
- [ ] 实现 `WebSessionGuard.requireActiveWebSession()` 并在所有 2FA 端点使用；实现 controller/service。
- [ ] `start` 将 AES-GCM 加密后的 secret 写入 `TwoFactorSetupStore`（TTL 10 分钟，覆盖旧 pending）；`confirm` 读取并校验，成功时持久化 `totp_secret_ciphertext` / `totp_confirmed_at`、标记 step、签发 10 个恢复码并删除 pending；错码保留 pending 供重试。
- [ ] 运行 `./gradlew test --tests "*TwoFactorEndpointsIntegrationTest"`。
- [ ] 更新 Progress Ledger；不提交。

## Task 5: 登录 202 与 `/login/2fa`

**Files:** `LoginOutcome.java`, `LoginChallengeResponse.java`, `LoginTwoFactorRequest.java`, `LoginTwoFactorService.java`, `AuthController.java`, `UserLoginService.java`, `LoginTwoFactorIntegrationTest.java`

**Interfaces produced:**
- `sealed interface LoginOutcome { record Success(LoginResponse response); record Challenge(LoginChallengeResponse challenge); }`
- `LoginOutcome UserLoginService.login(LoginRequest request)`
- `LoginResponse UserLoginService.completeLogin(User user, String deviceId)`（创建 session/refresh/audit）
- `LoginResponse LoginTwoFactorService.complete(String challengeToken, String code)`（挑战 + TOTP/恢复码 + 防重放）
- `LoginChallengeResponse(boolean twoFactorRequired, String challengeToken, long expiresIn)`；`LoginTwoFactorRequest(String challengeToken, String code)`

- [ ] 写失败集成测试：未启用仍 200 + Cookie/Authorization；已启用 202 + 信封 + **无 Cookie/Authorization**；错误码 401；正确码 200；同码重放 401；挑战过期 401；5 次失败 429；`termsExpired` 最终仍正确。
- [ ] 重构 `UserLoginService.login` 为 `LoginOutcome`；`completeLogin` 只在完整成功后更新 `lastLoginAt`、写 `LOGIN_SUCCESS`、签发 token。
- [ ] 在 `AuthController` 加 `/login/2fa`（`@PublicApi`、IP 10/5min），成功路径复用 Cookie/Authorization 写法。
- [ ] 运行定向测试；更新 Progress Ledger；不提交。

## Task 6: OAuth 2FA 分支

**Files:** `OAuthCallbackController.java`, `OAuthLoginOrRegisterService.java`, 测试落在 `OAuthLoginOrRegisterServiceTest` 与 `OAuthCallbackControllerMockMvcTest`

- [ ] 读现有 OAuth 测试与 `process` 事务，确定“先认证/注册、后签发”的最小拆分；把结论写入计划。
- [ ] 写失败测试：已启用 2FA 的 OAuth 用户回调 → 302 到 `frontendUrl/oauth/callback?twoFactorRequired=true#challengeToken=...`，**无 Set-Cookie、无 token query**；未启用用户保持现有行为；challenge 可完成 `/login/2fa`。
- [ ] 实现分支：2FA 检查发生在 `createRefreshToken` 之前；challenge 的 `deviceId=null`；fragment 用 URLEncoder 编码。
- [ ] 运行定向测试；更新 Progress Ledger；不提交。

## Task 7: API key 创建检查

**Files:** `ApiKeyController.java`, `CreateApiKeyRequest.java`, `ApiKeyServiceImpl.java`, `WebSessionGuard.java`, 测试落在 `TwoFactorEndpointsIntegrationTest`

- [ ] 写失败测试：API key 调用创建 → 403 `AUTH_025`；网页会话且未启用 2FA → 201；已启用缺 code → 403 `AUTH_030`；错码 → 401 `AUTH_027`；正确码 → 201 且同码重放 401。
- [ ] 在 `CreateApiKeyRequest` 加可选 `code`（`@Schema`，不破坏旧请求）；controller 用 `WebSessionGuard`；service 调用 `TwoFactorService.requireCodeForSensitiveAction`。
- [ ] 运行定向测试；更新 Progress Ledger；不提交。

## Task 8: 会话撤销、导出与审计

**Files:** `RefreshTokenRepository.java`, `TwoFactorService.java`, `UserExportService.java`（仅测试/必要白名单）, `UserController.java`, 集成测试

- [ ] 写失败测试：启用 2FA 后其他会话 refresh 401、当前会话仍可刷新；关闭 2FA 同样；access token 在 15 分钟内仍有效（文档化断言）；导出 JSON 不含 `totp`/`recovery` 字段；审计三条事件无 secret/码；`TOTP_CHALLENGE_FAILED` 不含码。
- [ ] 加 `revokeAllUserTokensExceptSession(userId, sessionId, now)`；启用/关闭在**同一事务内**执行（修正早前“事务提交后”的写法：回滚时撤销也应回滚，避免部分状态）。
- `int RefreshTokenRepository.revokeAllUserTokensExceptSession(UUID userId, UUID sessionId, Instant now)`
- [ ] 导出 DTO 白名单/测试；`AuditAction` 接线。
- [ ] 运行定向测试；更新 Progress Ledger；不提交。

## Task 9: 文档、记忆、版本与全量验证

**Files:** `docs/api-reference.md`, `docs/api-governance.md`, `docs/security-architecture.md`, `docs/developer-handbook.md`, `README.md`, `memory-bank/*`, `domains/*`, `.plans/ctt-server-development-plan.md`, `libs.versions.toml`

- [ ] 更新 API Reference：端点、202 信封、错误码 025–031、限流、API key 边界、OAuth fragment、会话撤销、`hasPassword/recentAuthentication`。
- [ ] 更新 Governance/Security：2FA 威胁模型、TOTP 重放、恢复码、加密键运维、API key 旁路与创建检查。
- [ ] 更新 README（安全模型、API 表）；Developer Handbook（错误码/审计动作）。
- [ ] 更新 `memory-bank/domains/auth-lifecycle/*` 与 `api-contract/references.md`；`activeContext` 一条、`progress` 版本里程碑。
- [ ] 版本 `0.89.0`（独立提交）；运行 `./gradlew spotlessCheck test jacocoTestCoverageVerification` 与一次全量 `build`，记录 tests/failures。
- [ ] 更新主计划 2FA 条目与 Progress Ledger；报告，不提交。

---

## Review Focus

1. **同一 TOTP step 并发重放**：两个请求同时提交同一 6 位码，必须只有一个成功（条件更新 `totp_last_used_step`）。
2. **挑战 token 并发/重放**：同一 `challengeToken` 被两个客户端同时使用，不能签发两组 token。
3. **恢复码并发消费**：同一恢复码两次提交，只能有一次成功；最后一条用完后 `recoveryCodesRemaining=0`。
4. **被盗会话启用 2FA**：无密码/无新鲜会话时 start 必须失败；有密码时缺/错密码必须失败；不能在失败路径写入 pending secret。
5. **OAuth 重定向泄露**：2FA 分支不得在 query/日志/Cookie 中出现 token；fragment 只在浏览器端读取。
6. **API key 铸造旁路**：API key 调用创建端点必须 403；2FA 用户缺码必须 403 `AUTH_030`；同码重放失败。
7. **启用后旧凭据**：其他 refresh token 立即失效；access token 的 15 分钟残留必须被文档明确，而不是假装即时失效。

---

## Risks and Counter-examples

| 风险 | 反例/已知边界 | 缓解 |
|---|---|---|
| `TOTP_ENCRYPTION_KEY` 丢失 | 所有 secret 无法解密，用户无法登录 | 密钥与 DB 备份分离保管；密文带 `v1:` 前缀；文档写明恢复流程 |
| Redis 不可用 | 挑战/pending setup 失败 | 返回 `SYSTEM_003`，绝不降级跳过 2FA |
| 时钟漂移 | 用户手机快/慢 30–60 秒 | 窗口 ±1；仍失败则提示校准时间 |
| OAuth 现有 token-in-query | 旧行为可能泄露 token | 2FA 分支绝不带 token；普通 OAuth 暂不改（单独安全项） |
| 启用 2FA 踢掉其他设备 | 用户在多设备登录 | 前端提示；当前会话保留；access token 15 分钟残留 |
| API key 创建行为变更 | 现有 API key 客户端若创建 key 会 403 | 已核实插件只列/校验；发布说明标注 |
| `otp-java` API 不符 | 方法名/窗口参数与预期不同 | 执行前 `javap` 确认；失败则回退 JDK HMAC 实现，契约不变 |
| 迁移锁表 | users 表大 | 加 nullable 列，PostgreSQL 快速；避免默认值重写 |

---

## Acceptance Criteria

- [x] `GET /users/me/2fa` 返回 5 个字段；API key 调用 403 `AUTH_025`。— `TwoFactorEndpointsIntegrationTest.shouldRejectApiKey_whenTwoFactorStatusIsRequested`
- [x] 有密码账号 start 缺/错密码分别 `USER_013`/`USER_014`；OAuth-only 旧会话 `AUTH_031`。— 生命周期用例 + `shouldRejectStaleSession_whenOAuthOnlyAccountStartsSetup` / `shouldAcceptFreshSession_...`
- [x] confirm 成功返回恰好 10 个恢复码，之后无法再次读取；重复 confirm 409。
- [x] 已启用登录返回 202 + 信封 `data.challengeToken`，无 Cookie/Authorization；`/login/2fa` 正确码 200 并写 Cookie/Authorization。
- [x] 挑战 5 分钟过期、单次使用；同一 TOTP step 不可重放；5 次失败 429。— 含端点级 `shouldReturn429_whenFifthCodeIsWrong`（服务层单测之外新增）
- [x] 恢复码只能用一次；轮换后旧码全部失效；关闭后登录回 200。
- [x] 启用/关闭撤销其他会话（refresh 401），当前会话保留。
- [x] OAuth 2FA 跳转 fragment，无 token/Cookie；未启用用户行为不变。— 含 `shouldReturnChallenge_whenMergedEmailOwnerHasTwoFactor`（email-merge 分支）
- [x] API key 创建：API key 403；2FA 缺码 403 `AUTH_030`；错码 401；正确 201。
- [x] 导出不含 2FA secret/恢复码；审计 4 个动作无明文。— `TOTP_CHALLENGE_FAILED` 由 `LoginTwoFactorIntegrationTest` 断言真落库（RED 可复现：加回 `@Transactional` 即失败）
- [x] `spotlessCheck`、`jacocoTestCoverageVerification`、全量 `build` 通过；记录 tests/failures。
- [x] 主计划/activeContext/progress/本计划 Progress Ledger 同步；工作区不包含 `SKILL_GRAPH.md` 的改动。

---

## Not Covered / Explicitly Not Doing

- 不做短信/邮件验证码作为第二因素；不做 WebAuthn/Passkey。
- 不做“记住此设备 N 天”；不做管理员强制重置 2FA。
- 不改插件；插件继续 API key。
- 不改普通 OAuth 的 token-in-query 行为（单独安全项，不在本计划）。
- 密码重置**不清除** TOTP（用户仍可用 TOTP/恢复码；忘记两者走支持流程）。
- 不做前端页面（ctt-web 子计划）；本计划只交付后端契约与测试。
- 不做 API key 全面 2FA 化；只守创建入口。
- 不顺手重构无关认证代码；`WebSessionGuard` 只替换已有重复判断。

---

## Execution Handoff

计划已保存到 `.plans/two-factor-authentication.md`。请审阅并选择执行方式：

- **Native（推荐）**：任务之间共享 schema、`LoginOutcome`、`TwoFactorService` 接口，顺序耦合高；由我在当前会话逐任务实现，最后一个独立审查。成本最低。
- **Subagent-driven**：每个 Task 由新子代理实现、独立审查后再进入下一个；更彻底，但每个任务都要重新建立上下文，成本更高。

无论哪种方式：先确认计划捕获了你的意图，再开始编码；**不自动提交**，每个 Task 完成后汇报并等待提交授权。
