# 系统模式

## 设计决策

### 架构风格: Package-by-Feature

**决策**: 采用按功能分包而非按层分包
**理由**:

- 高内聚：相关逻辑内聚在一起
- 可拆分为微服务：未来需要拆分微服务时，直接把整个包拎出去即可
- 清晰边界：每个功能模块独立维护自己的 controller/service/repository

### 同步策略: LWW (Last-Write-Wins) + 软删除

**决策**: 多设备双向同步采用基于时间戳的 LWW 策略
**理由**:

- 简单可靠：冲突解决逻辑清晰
- 数据安全：软删除机制防止数据丢失
- 设备无关：不依赖设备在线状态

### 认证方式: JWT + API Key 双轨制

**决策**: 网站用户用 JWT，插件设备用 API Key
**理由**:

- 场景适配：Web 端适合 Session-based JWT，插件端适合长期有效的 API Key
- 设备管理：API Key 与设备强绑定，支持单独撤销
- 安全隔离：不同客户端使用不同认证机制

### 时间策略: UTC 一把梭 (UTC-First Strategy)

**决策**: 全链路使用 UTC 绝对时间，彻底消除时区上下文依赖
**理由**:

- 绝对一致性：所有时间戳表示时间轴上的唯一刻度，与服务器、客户端时区无关
- 分布式安全：跨时区、多端协同场景下消除时区转换错误
- 类型安全：强制使用 Instant（机器时间），禁止 LocalDateTime（无时区上下文）
- 防御性设计：JVM 启动时强制设置 UTC，Jackson 序列化 ISO-8601 格式

**实施层级**:

1. JVM Foundation: `main()` 中 `TimeZone.setDefault(UTC)`
2. Jackson: `time-zone: UTC` + ISO-8601 序列化
3. Domain Model: `Instant` 为黄金标准，`OffsetDateTime` 按需使用
4. Database: PostgreSQL `TIMESTAMPTZ` → Hibernate 6 `Instant`
5. API Validation: `@Future` / `@Past` 配合 `Instant`

**参考**: [docs/time-strategy.md](../docs/time-strategy.md)

### 大小写规范: 邮箱防御性归一化 (Defensive Case Normalization)

**决策**: 邮箱在入库前强制转小写，查询使用 IgnoreCase 方法
**理由**:

- 防止账号幽灵冲突：`Admin@domain.com` 与 `admin@domain.com` 视为同一账号
- 消除越权接管风险：攻击者无法通过大小写变体创建重复账号
- 三层防御架构：DTO 归一化 → 实体钩子兜底 → Repository 索引优化查询

**实施层级**:

1. **DTO 边界**: Record Compact Constructor 进行 `trim().toLowerCase()`
2. **领域实体**: `@PrePersist/@PreUpdate` 钩子最终兜底
3. **Repository**: `findByEmailIgnoreCase()` 利用 `uk_users_email_lower` 函数索引

**参考**: [docs/case-normalization.md](../docs/case-normalization.md)

### 安全底座: CurrentUserProvider 防腐层 (Anti-Corruption Layer)

**决策**: 通过 CurrentUserProvider 接口解耦业务逻辑与安全框架实现
**理由**:

- 业务层不直接访问 SecurityContextHolder，消除 Spring Security 升级爆炸半径
- 统一身份模型 CurrentUser 屏蔽 JWT/API Key/OAuth2 差异
- 单元测试可 O(1) 模拟，无需启动 Spring 容器
- 为限流 (@RateLimit) 和幂等 (@Idempotent) 提供干净身份上下文

**架构分层**:

1. **Domain Model**: `CurrentUser` record (id, email, status, authorities, authType)
2. **Application Contract**: `CurrentUserProvider` 接口
3. **Infrastructure Adapter**: `SpringSecurityCurrentUserProvider` (唯一接触 SecurityContextHolder)

**使用方式**:

```java
@Service
public class SomeService {
    private final CurrentUserProvider currentUserProvider;

    public void doSomething() {
        // 隐式完成：未登录拦截 + 未激活拦截 + 身份提取
        CurrentUser user = currentUserProvider.getActiveUserRequired();
        // 执行业务...
    }
}
```

### 代码复用: SpEL 表达式解析器共享组件 (DRY Principle)

**决策**: 提取 `SpelExpressionResolver` 作为共享工具类，供限流和幂等框架复用
**理由**:

- **消除重复代码**: `RateLimitAspect` 和 `IdempotentAspect` 都有相同的 SpEL 解析逻辑（12 行重复代码）
- **单一职责**: 将 SpEL 解析逻辑集中到一处，便于维护和测试
- **可复用性**: 未来其他需要 SpEL 解析的切面可以直接使用此组件

**实现方式**:

```java
@Component
public class SpelExpressionResolver {
    public String resolve(ProceedingJoinPoint joinPoint, 
                         MethodSignature signature, 
                         String expression) { ... }
}
```

**收益**:

- 代码行数减少 ~24 行（2 个 Aspect 各 12 行）
- 修改 SpEL 解析逻辑只需改一处
- 可以单独为 SpEL 解析逻辑编写单元测试

### 接口治理: 限流与幂等框架 (Rate Limiting & Idempotency)

**决策**: 引入声明式的 `@RateLimit` 和 `@Idempotent` 框架
**理由**:

- 声明式优于编程式：业务代码只需添加注解，无需关心 Redis 锁和限流算法细节
- 细粒度控制：支持四种维度的限流 (`IP`, `USER`, `EMAIL`, `API`)
- 安全集成：直接依赖 `CurrentUserProvider` 和 `RequestContext` 提取干净的身份上下文
- 防止资损与脏数据：通过 `@Idempotent` + 分布式锁拦截重复提交流程

**架构分层**:

1. **声明层**: `@RateLimit(type, keyExpression, limit, windowSeconds)` 注解
2. **策略层**: `RateLimitKeyFactory` 根据维度类型生成 Redis Key
3. **执行层**: `RedisRateLimiter` 使用 Lua 脚本实现原子性固定窗口算法
4. **切面层**: `RateLimitAspect` AOP 拦截 + SpEL 表达式解析
5. **共享组件层**: `SpelExpressionResolver` SpEL 表达式解析器（供限流和幂等复用）

**技术亮点**:

- **Redis Lua 原子脚本**: 避免竞态条件，保证 `get -> +1 -> set` 的原子性
- **SpEL 动态提取**: 支持从请求参数动态提取 Key（如邮箱防轰炸）
- **审计集成**: 限流触发时自动记录 `RATE_LIMIT_EXCEEDED` 安全审计事件
- **代码复用**: 通过 `SpelExpressionResolver` 消除重复代码，遵循 DRY 原则

**参考**: [docs/api-governance.md](../docs/api-governance.md)

### 接口安全: Secure by Default (Default Deny)

**决策**: 采用"默认拒绝"安全模型，所有接口必须显式标记才能公开访问
**理由**:

- 消除漏配风险：传统"集中式白名单"随业务迭代易腐化，导致安全漏洞
- 分布式声明优于集中配置：业务开发人员通过 `@PublicApi` 注解声明，无需触碰安全配置类
- 强制安全审计：每个公开接口必须提供 `reason` 说明，便于代码审查
- 云原生最佳实践：符合 Zero Trust 架构原则，默认不信任任何请求

**实施层级**:

1. **声明层**: `@PublicApi(reason = "...")` 注解（类/方法级别）
2. **扫描层**: `PublicApiEndpointRegistry` 启动时 O(N) 遍历收集公开 URL
3. **配置层**: `SecurityConfig` 动态白名单 + `.anyRequest().authenticated()` 兜底

**使用示例**:

```java
// 默认受保护，无需额外声明
@GetMapping("/api/v1/sessions/sync")
public void syncData() { ... }  // 401 if unauthenticated

// 显式标记公开接口
@PublicApi(reason = "Registration endpoint")
@PostMapping("/api/v1/auth/register")
public void register() { ... }  // 允许匿名访问
```

### 客户端上下文: ClientIdentity 多端身份标准化

**决策**: 在流量接入层完成客户端身份标准化，提供统一领域模型
**理由**:

- **关注点分离**: 禁止业务层解析 `HttpServletRequest` Header，避免 HTTP 协议污染
- **O(1) 扩展性**: 新增终端类型（Web/IDE插件/OpenAPI）无需修改业务代码
- **强类型安全**: `ClientIdentity` Record 替代散落字符串，消除运行时错误
- **架构就绪**: 为 Device 注册、Refresh Token 绑定、API Key 设备风控提供标准上下文

**实施层级**:

1. **契约层**: `ClientHeaderConstants` 定义 X-Device-ID, X-Platform, X-IDE-Name 等标准 Header
2. **提取层**: `RequestContextInitializerFilter` 在 MVC 路由前 O(1) 提取并灌入线程上下文
3. **领域层**: `ClientIdentity` Record 封装 deviceId, platform, ideName 等结构化数据
4. **访问层**: `RequestInfo.client()` 提供不可空访问，业务层拿到的是干净领域对象

**HTTP Header 契约**:

| Header | 说明 | 示例 |
|--------|------|------|
| X-Device-ID | 设备唯一标识 (UUID) | `550e8400-e29b-41d4-a716-446655440000` |
| X-Device-Name | 设备友好名称 | `Alice's MacBook Pro` |
| X-Platform | 操作系统平台 | `macOS`, `Windows`, `Linux`, `Web` |
| X-IDE-Name | 宿主 IDE 名称 | `IntelliJ IDEA`, `VSCode` |
| X-IDE-Version | IDE 版本 | `2024.1` |
| X-App-Version | 插件/应用版本 | `1.2.3` |

**使用方式**:

```java
// 业务层无需接触 HttpServletRequest
@Transactional
public LoginResponse login(LoginRequest request) {
    ClientIdentity client = RequestContext.currentRequired().client();

    // 注册或更新设备
    if (client.deviceId() != null) {
        deviceService.registerOrUpdateDevice(user.getId(), client, ...);
    }

    // 签发 Token，动态决定生命周期
    String issuedFor = client.isPluginClient() ? "PLUGIN" : "WEB";

    RefreshToken rt = tokenService.createRefreshToken(user.getId(), client.deviceId(), issuedFor);

    return new LoginResponse(jwt, rt.getTokenHash());
}
```

### 传输安全: OWASP 安全 Header 集合 (Defense in Depth)

**决策**: 在 Spring Security 过滤器链中注入 OWASP 推荐的安全 Header 集合
**理由**:

- **纵深防御**: 作为第一道物理防线，阻止 XSS、点击劫持、MIME 嗅探等攻击
- **标准化**: 遵循 OWASP Cheat Sheet 最佳实践
- **透明化**: 通过 Spring Security 声明式配置，业务代码零侵入

**实施层级**:

1. **X-Content-Type-Options**: `nosniff` - 禁止 MIME 类型嗅探
2. **X-XSS-Protection**: `1; mode=block` - 启用 XSS 过滤器（旧版浏览器）
3. **X-Frame-Options**: `DENY` - 禁止页面被嵌入 iframe（点击劫持防护）
4. **Strict-Transport-Security**: `max-age=31536000; includeSubDomains` - 强制 HTTPS
5. **Content-Security-Policy**: `default-src 'self'` - 限制资源加载源

**配置方式**:

```java
.headers(headers -> headers
    .contentTypeOptions(contentType -> {})
    .xssProtection(xss -> xss.headerValue(ENABLED_MODE_BLOCK))
    .frameOptions(frame -> frame.deny())
    .httpStrictTransportSecurity(hsts -> hsts
        .includeSubDomains(true)
        .maxAgeInSeconds(31536000))
    .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
)
```

## 代码规范

### 项目结构规范

```
com.ahogek.ctt/
├── common/          # 全局共享模块（不包含业务逻辑）
│   ├── config/      # 全局配置
│   ├── exception/   # 全局异常处理
│   ├── response/    # 统一响应结构
│   └── utils/       # 工具类
├── auth/            # 认证模块
├── apikey/          # API Key 模块
├── sync/            # 数据同步模块
├── stats/           # 统计模块
└── leaderboard/     # 排行榜模块
```

### 模块内部规范

每个功能模块独立包含：

- `dto/` - 请求/响应数据传输对象
- `entity/` - JPA 实体类
- `repository/` - Spring Data JPA 接口
- `service/` - 业务逻辑
- `*Controller.java` - REST API 端点
- 如有特殊需要：`filter/`, `config/`, `mapper/`

### 命名规范

- Controller: `XxxController.java`
- Service: `XxxService.java` + `XxxServiceImpl.java`
- Repository: `XxxRepository.java`
- DTO: `XxxRequest.java` / `XxxResponse.java`
- Entity: `Xxx.java` (表名使用下划线命名)
- Test: `XxxTest.java` (方法命名: `methodName_whenCondition_expectedBehavior`)

### 测试规范 (Spring Boot 4)

#### 四层测试边界

| Layer | 加载范围 | 依赖策略 | 基类注解 |
|-------|---------|---------|---------|
| Controller Slice | 仅 Web 层 Bean | Mock Service | @BaseControllerSliceTest |
| Service Test | 纯 POJO / Spring DI | Mock Repository | @ExtendWith(MockitoExtension) |
| Repository Test | JPA + 真实 DB Slice | Testcontainers | @BaseRepositoryTest |
| Integration Test | 完整 ApplicationContext | 真实全栈 | @BaseIntegrationTest |

#### Spring Boot 4 包路径变更

Spring Boot 4 模块化后，测试注解包路径发生变化：

- `@WebMvcTest` → `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`
- `@AutoConfigureMockMvc` → `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`
- `@DataJpaTest` → `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`
- `@AutoConfigureTestDatabase` → `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase`
- `@MockitoBean` 替代已废弃的 `@MockBean`（Spring Framework 7 新增）

#### Controller Slice Test

```java
@BaseControllerSliceTest(UserController.class)
@DisplayName("User Controller Tests")
class UserControllerTest {
    @Autowired MockMvcTester mvc;
    @MockitoBean UserService userService;

    @Test
    @WithMockUser
    void getUser_whenExists_returns200() {
        assertThat(mvc.get().uri("/api/users/1")).hasStatusOk();
    }
}
```

#### Repository Test

```java
@BaseRepositoryTest
@DisplayName("UserRepository Tests")
class UserRepositoryTest {
    @Autowired TestEntityManager em;
    @Autowired UserRepository userRepository;

    @Test
    void findByEmail_whenExists_returnsUser() {
        var user = new User();
        user.setEmail("test@example.com");
        em.persistAndFlush(user);

        assertThat(userRepository.findByEmailIgnoreCase("TEST@EXAMPLE.COM")).isPresent();
    }
}
```

#### Integration Test

```java
@BaseIntegrationTest
@DisplayName("User API Integration Tests")
class UserIntegrationTest {
    @Autowired MockMvcTester mvc;

    @Test
    void createUser_endToEnd_success() {
        assertThat(mvc.post().uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\"}"))
                .hasStatus(HttpStatus.CREATED);
    }
}
```

#### Context 复用原则

集成测试必须维护 Spring ApplicationContext 缓存：

- 禁止使用 `@DirtiesContext`
- 禁止类级别 `@TestConfiguration` 覆盖 Bean
- 禁止修改静态状态影响 Context
- 所有测试类共享同一个 Context 以保证 CI 性能

#### Testcontainers 配置

测试容器配置固定版本，防止 CI 环境不可复现：

```java
// TestcontainersConfiguration.java
@Bean
@ServiceConnection
PostgreSQLContainer postgresContainer() {
    // CI 环境自动禁用复用，本地开发启用复用
    return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.3"))
        .withReuse(!"true".equalsIgnoreCase(System.getenv("CI")));
}
```

本地开发需启用复用（加速测试周期）：

```properties
# ~/.testcontainers.properties
testcontainers.reuse.enable=true
```

#### 测试 Profile 配置

`application-test.yaml` 关键配置：

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop  # Repository tests use Hibernate auto-ddl
  flyway:
    enabled: true  # Integration tests use Flyway migrations

ctt:
  security:
    password:
      bcrypt-rounds: 4  # Test speedup: 2ms vs 250ms (production 12)
```

### Java 新特性规范 (JDK 25)

由于项目使用 JDK 25，应优先使用现代 Java 特性：

- **Record**: 替代冗余的 immutable 数据类（如 `FieldError`、`DTO`）
- **Sealed Class**: 替代枚举用于有限状态集
- **Pattern Matching**: 简化 instanceof + cast 模式
- **Switch 表达式**: 优先使用 modern switch 语法
- **Virtual Threads**: IO 密集型操作使用虚拟线程

**示例**:

```java
// ❌ 冗余写法
public static final class FieldError {
    private final String field;
    private final String message;

    public FieldError(String field, String message) { ...}

    public String field() {
        return field;
    }

    public String message() {
        return message;
    }
}

// ✅ 使用 Record (JDK 25+)
public record FieldError(String field, String message) {
}
```

### Javadoc 注释规范

所有类文件必须包含 Javadoc 注释，模板如下：

```java
package com.ahogek.cttserver.xxx;

import xxx;

/**
 * [简短描述类的功能]
 *
 * <p>[详细描述，包含使用场景、注意事项等]</p>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-14
 */
public class Xxx {
}
```

**注意**:

- Javadoc 必须在 `package` + `import` 之后，类声明之前
- `@since` 使用简化格式：`YYYY-MM-DD`（无需具体时间）
- 保持一行简短描述在 80 字符以内
- 使用 HTML 标签 `<p>`、`<ul>` 等进行结构化描述
- **绝对禁止**在 Javadoc 中使用 emoji（🛡️、✅、🔴 等）
- **绝对禁止**在代码中使用中文注释或中文变量名

**错误示例**（已发生AI越界）:

```java
// ❌ 错误：Javadoc 在 import 上面
package com.ahogek.cttserver.xxx;

/**
 * Class description
 */

import xxx;

/

public class Xxx {
}

// ❌ 错误：中文注释
// 检查用户是否存在
if(user !=null){...}

// ❌ 错误：emoji
/** 🛡️ Audit log entity */

// ✅ 正确：package → import → Javadoc → class
    package com.ahogek.cttserver.xxx;

import xxx;

/**
 * Audit log entity for security events.
 */
public class Xxx {
}
```

### Clean Code 注释原则

**不要过度注释** - 代码应当自解释：

```java
// ❌ 过度注释：显而易见
/** Gets the user name. */
public String getUserName() {
    return userName;
}

// ✅ 必要注释：解释 "Why"
// Use ReentrantLock instead of synchronized for better throughput
// under high contention scenarios.
private final ReentrantLock lock = new ReentrantLock();
```

**注释准则**:

- **必须注释**: 类/接口职责、公共 API 参数和返回值、复杂算法原理
- **禁止注释**: 显而易见的代码（getter/setter）、实现细节（What）、中文注释
- **日志消息**: 必须使用英文，禁止 emoji（❌ `log.info("✅ 成功")`，✅ `log.info("Success")`）

## 组件字典

| ID   | 组件                 | 描述                                                      | 状态         |
|------|--------------------|---------------------------------------------------------|------------|
| C001 | JWT Auth           | JWT 用户认证 (登录/注册/Token刷新)                                | planned    |
| C002 | API Key Manager    | API Key 生成/校验/设备绑定/撤销                                   | planned    |
| C003 | Sync Engine        | 双向同步核心 (Pull/Push/冲突解决)                                 | planned    |
| C004 | Sync Cursor        | 设备同步状态追踪                                                | planned    |
| C005 | Stats Aggregator   | 时序数据聚合查询                                                | planned    |
| C006 | Leaderboard        | Redis ZSet 实时排行榜                                        | planned    |
| C007 | Rate Limiter       | API 限流保护                                                | planned    |
| C008 | Global Exception   | 全局异常处理器 (三级路由: ERROR/WARN/AUDIT)                        | stable     |
| C009 | Code Conventions   | 代码规范：Spotless + JaCoCo + Conventional Commits           | stable     |
| C010 | TraceId Filter     | TraceId 透传机制 (已废弃，由 C011 替代)                            | deprecated |
| C011 | Request Context    | 请求上下文 (RequestInfo + ScopedValue + IpUtils)             | stable     |
| C012 | Request Logging    | 请求层日志 (ACCESS_LOG + 慢请求检测)                              | stable     |
| C013 | Structured Logging | 结构化日志工具 (LogRecord + Fluent API)                        | stable     |
| C014 | Audit Event Model  | 审计事件模型 (五元组: User/Action/Resource/Severity/Environment) | stable     |
| C015 | Audit Enums        | 审计枚举体系 (AuditAction, ResourceType, SecuritySeverity)    | stable     |
| C016 | CurrentUserProvider| 安全底座防腐层 (CurrentUser + Provider Interface + Spring Security Adapter) | stable     |
| C017 | RateLimiter        | 声明式限流框架 (@RateLimit, RateLimitAspect, RedisRateLimiter, Lua脚本) | stable     |
| C018 | Idempotent         | 声明式幂等框架骨架 (@Idempotent, IdempotentAspect)               | stable     |
| C019 | PublicApi          | 接口安全分类模型 (@PublicApi + PublicApiEndpointRegistry + Secure by Default) | stable     |
| C020 | SpelExpressionResolver | SpEL 表达式解析器共享组件 (供限流和幂等框架复用) | stable     |
| C021 | ClientIdentity       | 客户端身份上下文模型 (支持 Web/IDE插件/OpenAPI 多端) | stable     |
| C022 | SecurityProperties   | 强类型安全配置类 (JWT/密码/限流/审计策略 @ConfigurationProperties) | stable     |
| C023 | Test Baseline        | 测试基线脚手架 (BaseControllerSliceTest, BaseRepositoryTest, BaseIntegrationTest, TestBaselineSmokeTest) | stable     |
| C024 | Test Fixtures         | 测试数据工厂 (UserFixtures, TokenFixtures, AuditFixtures, PersistedFixtures) | stable     |

## 测试分层策略

### Repository Slice vs Integration Schema 策略

**Repository Slice Test (@BaseRepositoryTest)**:
- 使用 `ddl-auto: create-drop`（来自 application-test.yaml）
- Hibernate 根据实体类自动创建 schema
- 快速迭代，适合 Repository 层单元测试
- 不执行 Flyway 迁移

**Integration Test (@BaseIntegrationTest)**:
- 覆盖为 `ddl-auto: validate`
- Flyway 迁移构建真实 schema
- Hibernate 仅验证实体与表结构一致性
- 及早发现迁移脚本与实体定义不匹配
