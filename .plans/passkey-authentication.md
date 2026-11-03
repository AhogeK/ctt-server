# 通行密钥（Passkey / WebAuthn）后端实施计划

- 日期：2026-11-03
- 状态：implemented（并入 v0.91.0；develop 6432bc1）
- 需求来源：ctt-web 前端《通行密钥（Passkey）与现有验证码并存》（草案）
- 关联契约：`.omp/reports/2026-10-31/two-factor-delivery-frontend-report.md`、`.omp/reports/2026-11-02/totp-secret-length-fix-report.md`
- 目标版本：0.91.0（基础实现与第二步同批发布）
- 分支：develop；本计划只进 develop

## 依赖选型（多维评估，2026-11-03）

| 候选 | 版本/维护 | 兼容性 | 安全/许可 | 结论 |
|---|---|---|---|---|
| `com.yubico:webauthn-server-core` | **2.10.1**（Maven Central 最新稳定，2026-10；Yubico 维护） | Java 11+ bytecode，在 Java 25 上可用；使用 Jackson 2.21.x，与本项目现有对象映射一致；无 Spring 强耦合，适合自定义 REST + Redis 挑战 | BSD-2；OSV 查询无已知漏洞；传递依赖 Guava/HttpClient5/CBOR，需纳入依赖审查 | **采用** |
| `org.springframework.security:spring-security-webauthn` | 7.1.2（与当前 Spring Security 对齐；最新稳定线） | 第一方框架集成，但 POM 固定 WebAuthn4J 0.31.10 + Jackson 3.2.1；Boot 4.1.2 BOM 将 Jackson 3 解析为 3.1.7（强制降级），且 API 以 Authentication/filter/session 为中心 | Apache-2 | 不采用：当前自定义无状态 Redis 契约需额外适配，且存在 Jackson 代际/降级风险 |
| `com.webauthn4j:webauthn4j-core` | 0.32.0.RELEASE（最新，活跃） | 0.x API，Jackson 3 (`tools.jackson`)；与 Spring Security WebAuthn 已封装能力重叠 | Apache-2；OSV 无已知漏洞 | 不直接采用：重复能力、API 稳定性与版本对齐成本更高 |

选择 Yubico 的原因不是“能编译”，而是它在**最新稳定、专用领域、维护者、许可证、已知漏洞、Jackson/Java 兼容、自定义 REST 适配**上综合最优。Spring Security WebAuthn 仍是备选；若未来需要完全交给 Spring Security filter 链、且接受其挑战存储模型，可重新评估。

## 目标

在不改变现有密码、TOTP、恢复码、GitHub OAuth 的前提下，新增一条**发现型通行密钥的单步登录路径**，并提供凭据的注册、列表、重命名、删除能力。通行密钥登录成功后视为近期认证，直接签发现有 `LoginResponse`（Cookie + Authorization + refresh session）。

## 非目标

- 不做 attestation 白名单/证明力登记。
- 不替换或删除现有认证因子。
- 不实现短信、推送、邮件验证码。
- 本轮不做“通行密钥作为第二步”的开关（方案 C）；该能力已由 v0.91.0 的独立子计划实现，见 [`.plans` 主计划](../.plans/ctt-server-development-plan.md) 与本地 `.omp/plans/2026-11-03/passkey-second-factor-plan.md`（不入库）。
- 不改插件端；API key 仍是插件机器凭据。

## 已拍板的设计决策

1. **库**：`com.yubico:webauthn-server-core:2.10.1`。
   - BSD-2；成熟、无 attestion 依赖；使用 Jackson 2，和本项目当前对象映射一致。
   - 不使用 WebAuthn4J 0.32：其 POM 依赖 Jackson 3 (`tools.jackson`)，会与现有 Jackson 2 生态并存，增加冲突面。
2. **挑战存储**：新增 `PasskeyChallengeService`，不把 WebAuthn 载荷塞进 `TwoFactorChallengeService`。
   - Redis key = `passkey:challenge:<SHA-256(challenge)>`；
   - 值 = 操作类型（REGISTER/ASSERT）、userId（断言可空）、创建时间、请求 JSON；
   - `GETDEL` 一次一用，TTL 5 分钟；
   - 挑战本身由 WebAuthn 库生成，服务端不自行拼 PublicKeyCredential。
3. **ID/字段**：表用 `UUID` 主键与 `UUID user_id`，与项目其他表一致；`credential_id` 存 base64url 字符串并唯一；公钥存 COSE `BYTEA`；`transports` 用 JSONB。
4. **登录**：发现型断言，`allowCredentials` 为空；匿名 options；成功用 `UserLoginService.completeLogin(user, null)` 签发与密码登录相同的令牌封装；`userVerification=REQUIRED`，服务端仍校验 UV/UP。
5. **注册/管理**：仅浏览器会话；近期认证复用从 `TwoFactorService` 抽出的 `RecentAuthenticationService`；`attestation=none`，`residentKey=required`，`excludeCredentials` 带上用户已有凭据。
6. **恢复码/密码**：不改；删除通行密钥需要近期认证；删除最后一把时仍允许，只要账号还有密码或 OAuth 绑定；否则拒绝（项目目前所有账号都至少有一种，保留防御性检查）。
7. **冷却期**：`ctt.security.passkey.new-credential-delay` 默认 `0`；>0 时注册后在该时间内禁止用新凭据登录，但允许删除/重命名。符合自托管单团队场景。
8. **通知**：新增 mail outbox 业务类型与模板，注册成功后异步入队；不新增 SMTP 依赖。
9. **反枚举**：`/auth/passkey/*` 的 options 永远 200；assert 的未知凭据、签名错误、UV 失败统一 `AUTH_033`；不返回账号是否存在。
10. **RP 配置**：`ctt.security.passkey.rp-id`、`allowed-origins`、`timeout`、`challenge-ttl`、`new-credential-delay`；本地默认 `localhost` + `http://localhost:5173`；生产使用 `PASSKEY_RP_ID` / `PASSKEY_ALLOWED_ORIGINS`，启动时校验非空。`rp-id` 是长期承诺，文档明确修改等于作废现有凭据。

## 数据模型

```sql
CREATE TABLE user_passkeys (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    credential_id     VARCHAR(1364) NOT NULL,
    public_key_cose   BYTEA NOT NULL,
    sign_count        BIGINT NOT NULL DEFAULT 0,
    transports        JSONB,
    aaguid            UUID,
    backup_eligible   BOOLEAN NOT NULL DEFAULT FALSE,
    backup_state      BOOLEAN NOT NULL DEFAULT FALSE,
    name              VARCHAR(64) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at      TIMESTAMPTZ,
    CONSTRAINT uq_user_passkeys_credential UNIQUE (credential_id)
);
CREATE INDEX idx_user_passkeys_user ON user_passkeys (user_id);
```

## API 契约

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| POST | `/api/v1/auth/passkey/options` | 匿名 | 断言 options（无 allowCredentials） |
| POST | `/api/v1/auth/passkey/assert` | 匿名 | 完成断言；成功 200 LoginResponse + Cookie |
| POST | `/api/v1/users/me/passkey/options` | WEB_SESSION + 近期认证 | 注册 options |
| POST | `/api/v1/users/me/passkey` | WEB_SESSION + 近期认证 | 完成注册；201 摘要 |
| GET | `/api/v1/users/me/passkey` | WEB_SESSION | 列表 |
| PATCH | `/api/v1/users/me/passkey/{id}` | WEB_SESSION | 重命名 |
| DELETE | `/api/v1/users/me/passkey/{id}` | WEB_SESSION + 近期认证 | 删除 |

错误码：`AUTH_032` 挑战无效/过期（401）、`AUTH_033` 断言校验失败（401）、`AUTH_034` 凭据已注册（409）、`AUTH_035` 凭据不存在/不属于当前用户（404）；近期认证不足 `AUTH_031`；API key 调用 `AUTH_025`。

## 安全规则

- 注册/断言均由 Yubico `RelyingParty` 校验 `clientDataJSON`、RP ID hash、origin 白名单、签名、challenge、UP、UV。
- `userVerification=REQUIRED`，不接受 UV=false；不把 backup state 作为拒绝条件。
- `signCount`：存储值为 0 时接受任何值（含 0，覆盖同步型凭据）；存储值 > 0 时，新值必须严格大于存储值，新值为 0 视为回退并拒绝。
- 挑战一次一用；注册挑战绑定 userId；断言挑战不绑定用户（发现型）。
- 登录限流：options/assert 使用 IP 限流；已知用户断言失败写 `LoginAttempt`（未知凭据只做 IP 限流，避免枚举差异）；成功 `recordSuccess`。
- 新增凭据通知邮件不包含公钥、credential ID 或挑战。
- 审计：`PASSKEY_REGISTERED`、`PASSKEY_RENAMED`、`PASSKEY_DELETED`、`PASSKEY_LOGIN_SUCCESS`、`PASSKEY_LOGIN_FAILED`（不记录 challenge/credential 明文）。

## 测试策略

- 单元：`PasskeyChallengeService`（单次消费/过期/操作绑定）、凭据命令校验、signCount/backup/UV 分支、最后凭据删除防御。
- Web MVC：匿名 options/assert、登录态注册/列表/重命名/删除、API key 403、错误码/校验。
- 集成：使用 Yubico 测试工具或自建 ES256 虚拟认证器完成注册→断言全流程；验证 DB 落库、Cookie/Authorization、审计、邮件 outbox、旧 TOTP 仍可用。
- 全量：`spotlessCheck` + `test` + `jacocoTestCoverageVerification` + `build`；最后统一执行，避免并行任务互相污染。

## 实施结果（2026-11-03）

- 交付：发现型通行密钥登录；注册 options/完成、列表、重命名、删除；注册与删除要求近期认证；API key 调用 403。
- 安全：Yubico 2.10.1；Redis 一次性、操作绑定的 5 分钟挑战；RP ID/origin 校验；UV/UP 强制；签名计数器手动校验；备份标志仅展示；无 attestation；新增凭据邮件通知。
- 兼容：密码、TOTP、恢复码、OAuth 全部保留；通行密钥登录不进入 TOTP；旧 2FA 流程不变。
- 测试：新增 `PasskeyIntegrationTest` 与 `PasskeyChallengeServiceTest`；全量 `cleanTest test` **1629 tests / 0 failures / 1 skipped**，`build` 通过。
- 排障：导出限流用例的 `RestClient` 默认按 429 的 `Retry-After` 自动重试并睡满 1 小时；改用 `JdkClientHttpRequestFactory` 后整轮 3m3s 完成（生产 429 语义不变）。
- 前端契约：`.omp/reports/2026-11-03/passkey-frontend-report.md`（实施轮）。

## 任务分解

1. [x] 依赖、配置、迁移、实体/仓库、错误码/审计动作。
2. [x] Redis 挑战服务 + Yubico `CredentialRepository` 适配器。
3. [x] 注册/管理 endpoints + DTO + 服务。
4. [x] 发现型登录 options/assert + token 签发 + 登录限流/审计。
5. [x] 邮件通知/模板。
6. [x] 单元、Web MVC、集成测试。
7. [x] docs/README/domain memory/AGENTS 同步。
8. [ ] 版本 0.90.0 已写；原子提交、master cherry-pick、推送与 clean 验证待授权。

## 验收

- 已登录且近期认证 → 注册通行密钥 → 列表正确。
- 退出后登录页发现型凭据 → 直接进入，不经过 TOTP。
- 浏览器不支持条件式填充时按钮路径可用。
- 删除后不再列出；密码/TOTP/OAuth 仍可登录。
- 过期挑战、错误来源、签名错误、UV=false 均返回规定错误码且不泄露账号存在性。
- 重复注册 `AUTH_034`；新增凭据邮件通知；全量测试通过。
