# Terms 同意状态功能 - 实施计划

> **日期**: 2026-05-02
> **状态**: 待实施
> **版本**: 0.25.0-SNAPSHOT

---

## 1. 背景与目标

### 1.1 为什么需要这个功能

- **法律合规**: GDPR/CCPA 要求能够证明用户同意了服务条款
- **版本管理**: 条款更新时需要检查用户的 `terms_version` 是否过期
- **审计追溯**: 记录用户同意的具体版本和时间

### 1.2 前端需求

```json
// 注册时前端发送
{
  "email": "user@example.com",
  "displayName": "User",
  "password": "...",
  "termsVersion": "1.0.0"
}

// 登录时后端返回
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "d4f5e6a7b8c9d0e1f2a3b4c5d6e7f8a9",
  "expiresIn": 3600,
  "tokenType": "Bearer",
  "termsExpired": false
}
```

---

## 2. 架构设计

### 2.1 条款内容存储

| 内容 | 存放位置 | 原因 |
|------|----------|------|
| **条款内容** | 前端 i18n 文件 (`locales/zh.json`, `locales/en.json`) | 多语言支持，前端可控 |
| **当前版本号** | 后端 `application.yaml` (`ctt.terms.current-version`) | 后端控制版本，避免前端传错 |
| **版本查询** | `GET /api/v1/config/public` → `{ termsVersion }` | 前端启动时获取当前版本 |
| **同意状态** | 后端 `users.terms_version` + `users.terms_accepted_at` | 审计合规 |

### 2.2 条款过期拦截机制

```
┌─────────────────────────────────────────────────────────────┐
│ 前端 (Vue.js)                                                │
│                                                              │
│  1. 登录成功 → 检查 termsExpired 字段                         │
│     - 如果 true → 弹出同意框                                 │
│                                                              │
│  2. 所有 API 请求 → 拦截 403 + TERMS_EXPIRED                 │
│     - 缓存失败请求 → 弹出同意框 → 调用 POST /terms/accept    │
│     - 获取新 JWT → 替换 localStorage → 重放缓存请求          │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│ 后端 (Spring Boot)                                           │
│                                                              │
│  1. LoginResponse 新增 termsExpired 字段                     │
│     - 登录成功后返回当前用户的条款是否过期                     │
│                                                              │
│  2. JWT 编码 termsVersion claim                              │
│     - 签发 JWT 时包含用户当前同意的条款版本                   │
│                                                              │
│  3. TermsCheckFilter (JWT 过滤器)                            │
│     - 解析 JWT 后比对 termsVersion vs 当前版本               │
│     - 如果过期 → 返回 403 + TERMS_EXPIRED                    │
│                                                              │
│  4. POST /terms/accept                                       │
│     - 更新 terms_accepted_at = NOW()                         │
│     - 更新 terms_version = 当前版本                          │
│     - 签发新 JWT（含新 termsVersion）→ 返回 accessToken + refreshToken │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. P0: 核心实现

### 3.1 数据库迁移

**文件**: `src/main/resources/db/migration/V20260502000000__add_terms_fields.sql`

```sql
-- ==========================================
-- Add Terms Acceptance Fields
-- ==========================================

ALTER TABLE users
    ADD COLUMN terms_accepted_at TIMESTAMPTZ,
    ADD COLUMN terms_version VARCHAR(20);

COMMENT ON COLUMN users.terms_accepted_at IS 'Timestamp when user accepted terms (UTC)';
COMMENT ON COLUMN users.terms_version IS 'Terms version accepted by user (e.g., 1.0.0)';
```

**状态**: ✅ 已融合进 init schema (`V20260303210000__init_base_schema.sql`)

### 3.2 User 实体修改

**文件**: `src/main/java/com/ahogek/cttserver/user/entity/User.java`

```java
@Column(name = "terms_accepted_at")
private Instant termsAcceptedAt;

@Column(name = "terms_version", length = 20)
private String termsVersion;

// getters and setters
public Instant getTermsAcceptedAt() { return termsAcceptedAt; }
public void setTermsAcceptedAt(Instant termsAcceptedAt) { this.termsAcceptedAt = termsAcceptedAt; }
public String getTermsVersion() { return termsVersion; }
public void setTermsVersion(String termsVersion) { this.termsVersion = termsVersion; }
```

**状态**: ✅ 已修改

### 3.3 配置项

**文件**: `src/main/resources/application.yaml`

```yaml
ctt:
  terms:
    current-version: "1.0.0"
```

**状态**: ✅ 已添加

### 3.4 UserRegisterRequest DTO 修改

**文件**: `src/main/java/com/ahogek/cttserver/auth/dto/UserRegisterRequest.java`

**修改**: 移除 `termsAccepted`，改为 `termsVersion`

```java
@Schema(description = "Terms version accepted by user", example = "1.0.0")
@NotBlank(message = "Terms version is required")
String termsVersion
```

**前端请求**:
```json
{
  "email": "user@example.com",
  "displayName": "User",
  "password": "...",
  "termsVersion": "1.0.0"
}
```

**状态**: ✅ 已修改

### 3.5 UserValidator 修改

**文件**: `src/main/java/com/ahogek/cttserver/user/validator/UserValidator.java`

**修改**: 移除 `assertTermsAccepted(Boolean)`，改为 `assertTermsVersionValid(String)`

```java
public void assertTermsVersionValid(String termsVersion) {
    String currentVersion = termsProperties.getCurrentVersion();
    if (!currentVersion.equals(termsVersion)) {
        throw new ValidationException(
            ErrorCode.TERMS_VERSION_MISMATCH,
            "Terms version mismatch. Please refresh the page and try again."
        );
    }
}
```

**状态**: ✅ 已修改

**文件**: `src/main/java/com/ahogek/cttserver/user/service/UserService.java`

**修改**: 注册时校验 version + 设置字段

```java
userValidator.assertTermsVersionValid(request.termsVersion());

newUser.setTermsAcceptedAt(Instant.now());
newUser.setTermsVersion(request.termsVersion());
```

**状态**: ✅ 已修改

### 3.7 LoginResponse 修改

**文件**: `src/main/java/com/ahogek/cttserver/auth/dto/LoginResponse.java`

**修改**: 新增 `boolean termsExpired` 字段

```java
@Schema(description = "Whether user's terms version is expired", example = "false")
boolean termsExpired
```

**状态**: ✅ 已修改

### 3.8 UserLoginService 修改

**文件**: `src/main/java/com/ahogek/cttserver/auth/service/UserLoginService.java`

**修改**: 登录时检查条款版本，返回 `termsExpired`

```java
boolean termsExpired = !currentVersion.equals(user.getTermsVersion());
return new LoginResponse(userId, accessToken, refreshToken, expiresIn, "Bearer", termsExpired);
```

**状态**: ✅ 已修改

### 3.9 OAuthLoginOrRegisterService 修改

**文件**: `src/main/java/com/ahogek/cttserver/auth/oauth/service/OAuthLoginOrRegisterService.java`

**修改**: 与邮箱登录统一，返回 `termsExpired`

**状态**: ✅ 已修改

### 3.10 公开配置接口

**文件**: `src/main/java/com/ahogek/cttserver/auth/AuthController.java`

**新增**: `GET /api/v1/config/public`

```java
@PublicApi
@GetMapping("/api/v1/config/public")
public ApiResponse<PublicConfigResponse> getPublicConfig() {
    return ApiResponse.ok(new PublicConfigResponse(termsProperties.getCurrentVersion()));
}

public record PublicConfigResponse(
    @Schema(description = "Current terms version", example = "1.0.0")
    String termsVersion
) {}
```

**状态**: ❌ 待新增

### 3.11 错误码

**文件**: `src/main/java/com/ahogek/cttserver/common/exception/ErrorCode.java`

**新增**:
```java
USER_008("Terms acceptance required", HttpStatus.BAD_REQUEST),
AUTH_019("Terms version expired, please re-accept", HttpStatus.FORBIDDEN)
```

**状态**: ⚠️ USER_008 已存在（消息待更新），AUTH_019 待新增

---

## 4. P1: 条款过期拦截

### 4.1 JWT 编码 termsVersion claim

**文件**: `src/main/java/com/ahogek/cttserver/auth/service/JwtTokenProvider.java`

**修改**: 签发 JWT 时编码 `termsVersion` claim

```java
claims.put("termsVersion", user.getTermsVersion());
```

**状态**: ❌ 待修改

### 4.2 TermsCheckFilter

**文件**: `src/main/java/com/ahogek/cttserver/auth/filter/TermsCheckFilter.java` (新增)

```java
@Component
@RequiredArgsConstructor
public class TermsCheckFilter extends OncePerRequestFilter {
    
    private final JwtTokenProvider jwtTokenProvider;
    private final CttProperties cttProperties;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) {
        // 1. 从 Authorization header 提取 JWT
        // 2. 解析 claims，获取 termsVersion
        // 3. 比对 termsVersion vs cttProperties.getTerms().getCurrentVersion()
        // 4. 如果过期 → 返回 403 + TERMS_EXPIRED
        // 5. 否则 → chain.doFilter(request, response)
    }
}
```

**状态**: ❌ 待新增

### 4.3 POST /terms/accept API

**文件**: `src/main/java/com/ahogek/cttserver/auth/AuthController.java`

**新增**: 同意新条款 API

```java
@PostMapping("/api/v1/auth/terms/accept")
@SecurityRequirement(name = "bearerAuth")
public ApiResponse<LoginResponse> acceptTerms(
        @AuthenticationPrincipal CurrentUser currentUser) {
    // 1. 获取当前用户
    // 2. 更新 terms_accepted_at = NOW()
    // 3. 更新 terms_version = 当前版本
    // 4. 签发新 JWT（含新 termsVersion）
    // 5. 返回新的 accessToken + refreshToken
}
```

**状态**: ❌ 待新增

### 4.4 UserService 新增 acceptTerms 方法

**文件**: `src/main/java/com/ahogek/cttserver/user/service/UserService.java`

```java
@Transactional
public void acceptTerms(UUID userId, String termsVersion) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException(ErrorCode.USER_004, "User not found"));
    
    user.setTermsAcceptedAt(Instant.now());
    user.setTermsVersion(termsVersion);
    userRepository.save(user);
}
```

**状态**: ❌ 待新增

---

## 5. P2: 优化

### 5.1 UTC 时区统一

**确认**: 当前项目已使用 `Instant` 类型存储时间戳（`emailVerifiedAt`、`lastLoginAt` 等），`termsAcceptedAt` 也应使用 `Instant`。

**状态**: ✅ 已确认（User entity 已使用 Instant）

### 5.2 集群配置同步

**当前阶段**: 滚动部署 + 短暂窗口容忍（1-2 分钟不一致，用户重试一次即可）

**后期优化**: 引入 Nacos/Apollo 配置中心（需要时再添加）

**状态**: ✅ 已确认（当前阶段无需额外实现）

---

## 6. 前端职责（前端团队负责）

| 职责 | 说明 |
|------|------|
| **Terms 内容** | i18n 文件 (`locales/zh.json`, `locales/en.json`) |
| **TermsDialog 组件** | 注册页展示条款 + 同意 checkbox |
| **注册流程** | 启动时调用 `GET /config/public` 获取版本，注册时传递 `termsVersion` |
| **Axios 拦截器** | 403 `TERMS_EXPIRED` → 弹出对话框 → 调用 `POST /terms/accept` → 重放请求 |
| **注册版本 mismatch** | 保留表单数据，只重置 checkbox |

---

## 7. 关键决策

| 决策 | 选择 | 原因 |
|------|------|------|
| **条款内容存储** | 前端 i18n | 多语言支持，前端可控 |
| **当前版本号** | 后端配置 | 后端控制版本，避免前端传错 |
| **是否建 terms 表** | ❌ 不建 | 个人项目，条款变更频率低 |
| **是否建 GET /terms/current API** | ❌ 不建 | 内容在 i18n，版本从 config/public 获取 |
| **termsAccepted 字段** | ❌ 移除 | termsVersion + 后端校验 = 同意 |
| **OAuth 统一响应** | ✅ 统一 | 避免 OAuth 用户漏掉条款更新 |
| **JWT claims** | ✅ 使用 | 无状态过滤器检查，避免每次请求查 DB |
| **POST /terms/accept 返回** | ✅ 完整 JWT 对 | 旧 JWT 含旧版本 claim，必须签发新 JWT |

---

## 8. 当前状态

| 项目 | 状态 |
|------|------|
| DB 迁移字段 | ✅ 已融合进 init schema (`V20260303210000__init_base_schema.sql`) |
| 独立迁移文件 | ✅ 已删除 (`V20260502000000__add_terms_fields.sql` + `V20260503__add_terms_fields.sql`) |
| User 实体 | ✅ 已修改 (`termsVersion` + `termsAcceptedAt`) |
| 版本号 | ✅ 已更新 (`0.25.0-SNAPSHOT`) |
| 测试 | ✅ 752 测试通过 |
| memory-bank | ✅ 已更新 |

---

## 9. 待实施任务

### P0 核心实现

| 任务 | 文件 | 优先级 |
|------|------|--------|
| ~~添加配置项~~ | ~~`application.yaml`~~ | ~~P0~~ ✅ |
| ~~修改 UserRegisterRequest~~ | ~~`UserRegisterRequest.java`~~ | ~~P0~~ ✅ |
| ~~修改 UserValidator~~ | ~~`UserValidator.java`~~ | ~~P0~~ ✅ |
| ~~修改 UserService~~ | ~~`UserService.java`~~ | ~~P0~~ ✅ |
| ~~修改 LoginResponse~~ | ~~`LoginResponse.java`~~ | ~~P0~~ ✅ |
| ~~修改 UserLoginService~~ | ~~`UserLoginService.java`~~ | ~~P0~~ ✅ |
| ~~修改 OAuthLoginOrRegisterService~~ | ~~`OAuthLoginOrRegisterService.java`~~ | ~~P0~~ ✅ |
| 新增公开配置接口 | `AuthController.java` | P0 |
| 新增错误码 | `ErrorCode.java` | P0 |

### P1 条款过期拦截

| 任务 | 文件 | 优先级 |
|------|------|--------|
| JWT 编码 termsVersion | `JwtTokenProvider.java` | P1 |
| 新增 TermsCheckFilter | `auth/filter/TermsCheckFilter.java` | P1 |
| 新增 POST /terms/accept | `AuthController.java` | P1 |
| 新增 UserService.acceptTerms | `UserService.java` | P1 |

---

## 10. 验证计划

### 10.1 单元测试

| 测试 | 说明 |
|------|------|
| `UserValidatorTest` | `assertTermsVersionValid` 正常/版本不匹配 |
| `UserServiceTest` | `registerUser` 设置 terms 字段 |
| `LoginResponseTest` | `termsExpired` 字段序列化 |

### 10.2 集成测试

| 测试 | 说明 |
|------|------|
| `RegistrationAndVerificationIntegrationTest` | 注册流程包含 `termsVersion` |
| `AuthControllerTest` | 注册 API 返回 `termsExpired` |
| `TermsCheckFilterTest` | 条款过期拦截 |

### 10.3 手动 QA

| 场景 | 预期 |
|------|------|
| 注册时 `termsVersion` 匹配 | 注册成功，200 |
| 注册时 `termsVersion` 不匹配 | 注册失败，400 `TERMS_VERSION_MISMATCH` |
| 登录时条款未过期 | 返回 `termsExpired: false` |
| 登录时条款已过期 | 返回 `termsExpired: true` |
| 条款过期后调用 API | 403 `TERMS_EXPIRED` |
| 调用 POST /terms/accept | 返回新 JWT，后续请求通过 |

---

## 11. 参考

- GDPR Article 7: Conditions for consent
- CCPA Section 1798.100: Consumer rights
- OWASP Authentication Cheat Sheet
- NIST SP 800-63B: Digital Identity Guidelines

---

**作者**: AhogeK
**审核**: 前端团队确认
**最后更新**: 2026-05-03
