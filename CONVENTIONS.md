# Project Conventions & Standards

> Engineering scaffold for long-term maintainability

---

## 1. Git Workflow

### Branch Naming

| Type     | Pattern                            | Example                        |
|----------|------------------------------------|--------------------------------|
| Feature  | `feat/{ticket-id}-{short-desc}`    | `feat/CTT-42-jwt-auth`         |
| Bugfix   | `fix/{ticket-id}-{short-desc}`     | `fix/CTT-55-null-pointer`      |
| Hotfix   | `hotfix/{version}-{short-desc}`    | `hotfix/v1.2.3-security-patch` |
| Release  | `release/v{major}.{minor}.{patch}` | `release/v1.3.0`               |
| Docs     | `docs/{short-desc}`                | `docs/api-examples`            |
| Refactor | `refactor/{short-desc}`            | `refactor/extract-service`     |
| Scaffold | `scaffold/{short-desc}`            | `scaffold/conventions`         |

### Commit Message Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:**

- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation
- `style`: Code style (formatting, no logic change)
- `refactor`: Code refactoring
- `perf`: Performance improvement
- `test`: Adding/updating tests
- `chore`: Build, tooling, dependencies
- `ci`: CI/CD changes

**Examples:**

```
feat(auth): implement JWT token generation

- Add JwtTokenProvider with RSA key pair
- Configure Spring Security filter chain
- Add token refresh endpoint

Closes: CTT-42
```

```
fix(sync): resolve concurrent modification exception

Use ConcurrentHashMap instead of HashMap for session cache
in SyncPullService to prevent race conditions during high
concurrency scenarios.

Fixes: CTT-78
```

---

## 2. Code Style

### Java / Kotlin

**General:**

- Indent: 4 spaces (no tabs)
- Line length: 120 characters max
- Encoding: UTF-8
- Line endings: LF (Unix-style)

**Naming:**

```java
// Classes: PascalCase
public class UserAuthenticationService {
}

// Methods: camelCase
public String generateApiKey() {
}

// Variables: camelCase
private final String apiKeyPrefix;

// Constants: UPPER_SNAKE_CASE
private static final int MAX_RETRY_COUNT = 3;

// Database columns: snake_case
@Column(name = "created_at")
private Instant createdAt;
```

**Package Structure:**

```
com.ahogek.ctt/
├── common/          # Shared utilities (no business logic)
│   ├── config/
│   ├── exception/
│   └── util/
├── auth/            # Authentication feature
│   ├── controller/
│   ├── service/
│   ├── repository/
│   └── dto/
├── apikey/          # API Key management feature
├── sync/            # Sync engine feature
├── stats/           # Statistics feature
└── leaderboard/     # Ranking feature
```

**Imports Order:**

1. `java.*`
2. `javax.*`
3. Third-party libraries
4. Project internal packages
5. Static imports

**Comments:**

- Use English for all comments
- Javadoc for public APIs
- Inline comments only when logic is non-obvious

---

## 3. Testing Standards

### Test Structure

```java

@ExtendWith(SpringExtension.class)
@AutoConfigureMockMvc
@Transactional
class UserServiceTest {

    @Test
    @DisplayName("Should create user with valid email and password")
    void shouldCreateUserWithValidCredentials() {
        // Given
        var request = new CreateUserRequest("test@example.com", "SecurePass123!");

        // When
        var result = userService.create(request);

        // Then
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        assertThat(result.getId()).isNotNull();
    }

    @Test
    @DisplayName("Should throw exception when email already exists")
    void shouldThrowExceptionWhenEmailExists() {
        // Given
        var existingEmail = "existing@example.com";
        userService.create(new CreateUserRequest(existingEmail, "Pass123!"));

        // When & Then
        assertThatThrownBy(() ->
            userService.create(new CreateUserRequest(existingEmail, "Pass456!"))
        )
            .isInstanceOf(DuplicateEmailException.class)
            .hasMessageContaining("Email already registered");
    }
}
```

### Test Coverage Requirements

- Unit tests: > 80% coverage
- Integration tests: Critical paths only
- E2E tests: Happy paths only

---

## 4. API Response Conventions

### Response Envelope Structure

All API responses must use one of the following wrapper types:

| Response Type                 | Wrapper Class      | Use Case                           |
|-------------------------------|--------------------|------------------------------------|
| Success with data             | `ApiResponse<T>`   | Single resource or custom response |
| Success with list (paginated) | `PagedResponse<T>` | List endpoints with pagination     |
| Success without data          | `EmptyResponse`    | DELETE, UPDATE without return      |
| Error                         | `ErrorResponse`    | All error responses                |

### Success Response Structure

**ApiResponse<T> - Single resource:**

```json
{
    "success": true,
    "message": "Operation successful",
    "data": {
        "id": "uuid",
        "email": "user@example.com"
    },
    "timestamp": "2026-03-14T12:00:00Z"
}
```

**PagedResponse<T> - Paginated list:**

```json
{
    "items": [
        ...
    ],
    "page": 1,
    "size": 20,
    "totalItems": 150,
    "totalPages": 8,
    "hasNext": true,
    "hasPrevious": false
}
```

**EmptyResponse - No content:**

```json
{
    "success": true,
    "message": "Resource deleted successfully",
    "timestamp": "2026-03-14T12:00:00Z"
}
```

### Error Response Structure

All errors must return `ErrorResponse`:

```json
{
    "code": "AUTH_001",
    "message": "Invalid credentials",
    "httpStatus": 401,
    "details": [
        {
            "field": "email",
            "message": "Invalid email format"
        }
    ],
    "traceId": "abc-123-def",
    "timestamp": "2026-03-14T12:00:00Z"
}
```

### Field Naming Convention

| Context     | Convention | Example                 |
|-------------|------------|-------------------------|
| JSON / API  | snake_case | `user_id`, `created_at` |
| Java fields | camelCase  | `userId`, `createdAt`   |
| Database    | snake_case | `user_id`, `created_at` |

### Time Format

All timestamps must use **ISO 8601** format with UTC timezone:

- **Format**: `YYYY-MM-DDTHH:MM:SSZ`
- **Timezone**: UTC (Z suffix)
- **Java Type**: `java.time.Instant`
- **Example**: `"2026-03-14T12:00:00Z"`

### Trace Field Position

The `traceId` field must be included in:

- **Error responses**: Always present for debugging
- **Success responses**: Optional (can be omitted)

Trace ID position in JSON: root level, after `details`.

### HTTP Status Code Mapping

| Status Code | Use Case              | Exception Type             |
|-------------|-----------------------|----------------------------|
| 200         | Success               | -                          |
| 201         | Created               | -                          |
| 204         | No Content            | -                          |
| 400         | Validation Error      | `ValidationException`      |
| 401         | Unauthorized          | `UnauthorizedException`    |
| 403         | Forbidden             | `ForbiddenException`       |
| 404         | Not Found             | `NotFoundException`        |
| 409         | Conflict              | `ConflictException`        |
| 429         | Too Many Requests     | `TooManyRequestsException` |
| 500         | Internal Server Error | Generic Exception          |

### Error Code Format

```
{GROUP}_{CODE}

Examples:
- COMMON_001, COMMON_002, ...
- AUTH_001, AUTH_002, ...
- USER_001, USER_002, ...
```

### Validation Error Details

For validation errors (400), include field-level details:

```json
{
    "code": "COMMON_003",
    "message": "Validation error",
    "httpStatus": 400,
    "details": [
        {
            "field": "email",
            "message": "Invalid email format"
        },
        {
            "field": "password",
            "message": "Password must be at least 8 characters"
        }
    ],
    "traceId": "abc-123",
    "timestamp": "2026-03-14T12:00:00Z"
}
```

### RESTful URL Conventions

| Method | URL Pattern       | Description         |
|--------|-------------------|---------------------|
| GET    | `/resources`      | List resources      |
| GET    | `/resources/{id}` | Get single resource |
| POST   | `/resources`      | Create resource     |
| PUT    | `/resources/{id}` | Full update         |
| PATCH  | `/resources/{id}` | Partial update      |
| DELETE | `/resources/{id}` | Delete resource     |

### Versioning

API versioning via URL path:

```
/api/v1/users
/api/v2/users
```

---

## 5. Documentation Standards

### API Documentation (OpenAPI)

```java

@Tag(name = "Authentication", description = "User authentication endpoints")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Operation(
        summary = "User login",
        description = "Authenticate user with email and password, returns JWT token"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials"),
        @ApiResponse(responseCode = "429", description = "Too many requests")
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        // implementation
    }
}
```

### Database Migration Comments

```sql
-- ==============================================================================
-- Migration: Add user audit columns
-- Description: Adds last_login tracking for security audit
-- Ticket: CTT-42
-- Author: developer@example.com
-- Date: 2026-03-15
-- ==============================================================================

ALTER TABLE users
    ADD COLUMN last_login_at TIMESTAMPTZ,
    ADD COLUMN last_login_ip VARCHAR(50);

COMMENT ON COLUMN users.last_login_at IS 'Timestamp of last successful login';
COMMENT ON COLUMN users.last_login_ip IS 'IP address of last successful login';
```

---

## 6. Database Conventions

### Naming

- Tables: `snake_case`, plural
    - ✅ `users`, `api_keys`, `coding_sessions`
    - ❌ `User`, `apiKey`, `codingSession`

- Columns: `snake_case`
    - ✅ `created_at`, `is_deleted`, `api_key_hash`
    - ❌ `createdAt`, `isDeleted`

- Constraints: descriptive prefix
    - `pk_{table}` - Primary key
    - `uk_{table}_{columns}` - Unique key
    - `fk_{table}_{ref_table}` - Foreign key
    - `chk_{table}_{condition}` - Check constraint

### Required Columns

Every table must have:

- `id` (UUID or SERIAL) - Primary key
- `created_at` - Creation timestamp
- `updated_at` - Last update timestamp

Soft-delete tables must have:

- `is_deleted` (BOOLEAN) - Soft delete flag
- `deleted_at` (TIMESTAMPTZ) - Deletion timestamp

---

## 7. Security Guidelines

### Secrets Management

- Never commit secrets to Git
- Use environment variables for configuration
- Use `.env` file for local development (added to `.gitignore`)
- Rotate API keys every 90 days

### API Security

- All endpoints (except health/login) require authentication
- Rate limiting: 100 req/min for authenticated users
- Input validation on all endpoints
- SQL injection prevention via parameterized queries
- XSS prevention via output encoding

---

## 8. Performance Guidelines

### Database

- Add indexes for frequently queried columns
- Use pagination for large result sets (limit 100)
- N+1 query prevention via JOIN FETCH
- Use EXPLAIN ANALYZE for slow queries

### Caching

- Cache frequently accessed data in Redis
- Set appropriate TTL based on data volatility
- Implement cache invalidation strategy

### API Response

- Keep response size under 1MB
- Use compression for large payloads
- Implement request/response logging selectively

---

## 9. Code Review Checklist

Before creating PR:

- [ ] All tests pass locally
- [ ] Code follows style conventions
- [ ] No secrets in code
- [ ] Documentation updated
- [ ] Commit messages follow convention
- [ ] Self-review completed

---

## 10. IDE Configuration

### IntelliJ IDEA / Android Studio

Import code style: `.idea/codeStyles/Project.xml`

### VS Code

Install extensions:

- EditorConfig for VS Code
- Spring Boot Extension Pack
- GitLens

---

*Last updated: 2026-03-03*
*Maintainers: Project maintainers*
*Questions? Open an issue with label `conventions`*
