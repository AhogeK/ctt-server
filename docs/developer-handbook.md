# Developer Handbook: Five Standard Operations

This handbook provides step-by-step instructions for five common development tasks in the CTT Server codebase.

## Table of Contents

1. [Adding Error Codes](#adding-error-codes)
2. [Adding Audit Events](#adding-audit-events)
3. [Adding Public Exceptions](#adding-public-exceptions)
4. [Adding Protected Interfaces](#adding-protected-interfaces)
5. [Using Mail Template Renderer](#using-mail-template-renderer)

---

## Adding Error Codes

### Design Principles

Error codes follow the format `{GROUP}_{NNN}` where:
- `GROUP`: Domain group (COMMON, AUTH, USER, MAIL, RATE_LIMIT, SECURITY, SYSTEM)
- `NNN`: Sequential three-digit number within the group

HTTP status codes are decoupled from error codes - multiple business error codes can share the same HTTP status.

### Standard Steps

**Step 1**: Add to `ErrorCode.java`

```java
// src/main/java/com/ahogek/cttserver/common/exception/ErrorCode.java
public enum ErrorCode {
    // ... existing codes ...

    // =========================================================================
    // AUTH - Add new authentication error
    // =========================================================================
    AUTH_013("OAuth token expired", HttpStatus.UNAUTHORIZED),
}
```

**Step 2**: Update `CONVENTIONS.md` Error Code Registry

```markdown
| Error Code | HTTP Status | Description           | Added In |
|------------|-------------|----------------------|----------|
| AUTH_013   | 401         | OAuth token expired   | Week 3   |
```

**Important**: Do NOT use HTTP status codes as error codes. Frontend needs precise error handling (e.g., distinguish "password wrong" vs "account locked" - both return 403).

---

## Adding Audit Events

### Audit Event Categories

| Category | Description                    | Examples                                 |
|----------|--------------------------------|------------------------------------------|
| IAM      | Identity and Access Management | LOGIN_SUCCESS, ACCOUNT_LOCKED            |
| EMAIL    | Email Verification             | EMAIL_VERIFICATION_SENT                  |
| CRED     | Credential Management          | PASSWORD_CHANGED, API_KEY_REVOKED        |
| DEVICE   | Device Management              | DEVICE_LINKED, DEVICE_UNLINKED           |
| SECURITY | Security and Defense           | RATE_LIMIT_EXCEEDED, UNAUTHORIZED_ACCESS |

### Standard Steps

**Step 1**: Add to `AuditAction.java`

```java
// src/main/java/com/ahogek/cttserver/audit/enums/AuditAction.java
public enum AuditAction {
    // ... existing actions ...

    /** New device registration completed. */
    DEVICE_REGISTERED("New device successfully registered");

    private final String description;

    AuditAction(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
```

**Step 2**: Publish Event in Business Code

```java
// In DeviceService.java
@Transactional
public Device registerDevice(RegisterDeviceRequest request) {
    var device = deviceRepository.save(buildDevice(request));

    // Publish audit event (async, non-blocking)
    eventPublisher.publishEvent(
        new SecurityAuditEvent(
            AuditAction.DEVICE_REGISTERED,
            ResourceType.DEVICE,
            SecuritySeverity.INFO,
            RequestContext.current().orElse(null),
            AuditDetails.builder()
                .put("deviceId", device.getId())
                .put("platform", device.getPlatform())
                .build()));

    return device;
}
```

**Step 3**: Add to `AuditFixtures.java`

```java
// src/test/java/com/ahogek/cttserver/fixtures/AuditFixtures.java
public static Builder deviceRegistered(UUID deviceId) {
    return builder()
            .action(AuditAction.DEVICE_REGISTERED)
            .resourceType(ResourceType.DEVICE)
            .resourceId(deviceId.toString())
            .severity(SecuritySeverity.INFO);
}
```

---

## Adding Public Exceptions

### Exception Hierarchy

```
RuntimeException
  └── BusinessException (sealed)
        ├── ValidationException      ← 400
        ├── UnauthorizedException    ← 401
        ├── ForbiddenException       ← 403
        ├── ConflictException        ← 409
        ├── TooManyRequestsException ← 429
        └── NotFoundException        ← 404
```

### BusinessException (Base Class)

```java
// src/main/java/com/ahogek/cttserver/common/exception/BusinessException.java
public abstract sealed class BusinessException extends RuntimeException
        permits ValidationException,
                UnauthorizedException,
                ForbiddenException,
                ConflictException,
                TooManyRequestsException,
                NotFoundException {

    private final ErrorCode errorCode;
    private final String customMessage;

    protected BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    protected BusinessException(ErrorCode errorCode, String customMessage) {
        super(customMessage != null ? customMessage : errorCode.message());
        this.errorCode = errorCode;
        this.customMessage = customMessage;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public ErrorResponse toErrorResponse() {
        return customMessage != null
                ? ErrorResponse.of(errorCode, customMessage)
                : ErrorResponse.of(errorCode);
    }
}
```

### Adding a New Exception

**Step 1**: Check if ErrorCode Exists

Verify the error code exists in `ErrorCode.java`. If not, add it first.

**Step 2**: Create Exception Class

Option A - Generic Exception (for simple cases):

```java
// Reuse existing ConflictException
throw new ConflictException(ErrorCode.USER_001, "Email already registered: " + email);
```

Option B - Domain-Specific Exception (for complex cases):

```java
// src/main/java/com/ahogek/cttserver/user/exception/AccountLockedException.java
package com.ahogek.cttserver.user.exception;

import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;

/**
 * Thrown when user account is locked due to security violations.
 */
public class AccountLockedException extends ForbiddenException {
    public AccountLockedException(String username) {
        super(ErrorCode.AUTH_004,
              String.format("Account [%s] is locked. Please try again in 30 minutes.", username));
    }
}
```

**Step 3**: Use in Service Layer

```java
// In UserService.java
public User authenticate(String email, String password) {
    User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new NotFoundException(ErrorCode.USER_004, "User not found"));

    if (user.isLocked()) {
        throw new AccountLockedException(user.getEmail());
    }

    // ... authentication logic
}
```

**Step 4**: No Handler Registration Required

`GlobalExceptionHandler` already handles all `BusinessException` subtypes. New exceptions automatically get:
- Structured WARN level logging
- Proper HTTP status mapping
- Trace ID correlation
- Error response serialization

**Step 5**: Write Tests

```java
@Test
@DisplayName("authenticate when account locked throws AccountLockedException")
void authenticate_whenAccountLocked_throwsException() {
    // Given
    var lockedUser = PersistedFixtures.lockedUser(entityManager);

    // When & Then
    assertThatThrownBy(() -> authService.authenticate(lockedUser.getEmail(), "password"))
            .isInstanceOf(AccountLockedException.class)
            .hasMessageContaining("locked");
}
```

---

## Adding Protected Interfaces

### Security Level Classification

| Level         | Access Pattern          | Typical Usage           |
|---------------|-------------------------|-------------------------|
| Public        | `@PublicApi`            | Login, register, health |
| Authenticated | Default (no annotation) | User profile, sync      |
| Role-based    | `@PreAuthorize`         | Admin operations        |

### Standard Steps

**Step 1**: Use `@PublicApi` for Public Endpoints

```java
// src/main/java/com/ahogek/cttserver/auth/AuthController.java
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @PublicApi(reason = "User login endpoint")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // ... implementation
    }
}
```

**Step 2**: No Annotation for Protected Endpoints

Protected endpoints require no annotation - Spring Security blocks them by default:

```java
// src/main/java/com/ahogek/cttserver/user/UserController.java
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    // This endpoint requires authentication (no @PublicApi = protected)
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(CurrentUser user) {
        return ResponseEntity.ok(userService.findById(user.id()));
    }
}
```

**Step 3**: Add Security Tests

```java
// src/test/java/com/ahogek/cttserver/user/UserControllerTest.java
@BaseControllerSliceTest(UserController.class)
class UserControllerTest {

    @Autowired MockMvcTester mvc;

    @MockitoBean UserService userService;

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("GET /me - authenticated user returns profile")
    void getMe_whenAuthenticated_returnsProfile() {
        given(userService.findById(any())).willReturn(mockUserResponse());

        assertThat(mvc.get().uri("/api/v1/users/me")).hasStatusOk();
    }

    @Test
    @DisplayName("GET /me - unauthenticated returns 401")
    void getMe_whenUnauthenticated_returns401() {
        assertThat(mvc.get().uri("/api/v1/users/me")).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
```

---

## Using Mail Template Renderer

### Overview

The Mail Template Renderer provides type-safe email template rendering using Thymeleaf with strongly-typed DTOs (sealed interface + records).

### Architecture

```
MailTemplateData (sealed interface)
├── EmailVerificationTemplateData (record)
└── PasswordResetTemplateData (record)

MailTemplateRenderer (interface)
└── ThymeleafMailTemplateRenderer (implementation)
```

### Standard Steps

**Step 1**: Create Template Data

```java
import com.ahogek.cttserver.mail.template.EmailVerificationTemplateData;

var data = new EmailVerificationTemplateData(
    "username",
    "https://example.com/verify?token=abc123",
    Duration.ofMinutes(15)
);
```

**Step 2**: Inject and Use Renderer

```java
@Service
public class AuthService {
    private final MailTemplateRenderer templateRenderer;
    private final JavaMailSender mailSender;
    
    public void sendVerificationEmail(User user, String token) {
        var data = new EmailVerificationTemplateData(
            user.getEmail(),
            buildVerificationLink(token),
            Duration.ofMinutes(15)
        );
        
        String html = templateRenderer.renderHtml(data);
        String text = templateRenderer.renderText(data);
        
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setTo(user.getEmail());
        helper.setSubject("Verify Your Email");
        helper.setText(html, text);
        
        mailSender.send(message);
    }
}
```

### Template Files

Templates are located in `src/main/resources/mail-templates/`:

```
mail-templates/
├── layout/base.html              # Shared layout (header, footer, brand colors)
├── email-verification.html       # Verification email HTML
├── email-verification.txt        # Verification email plain text
├── password-reset.html           # Password reset email HTML
└── password-reset.txt            # Password reset email plain text
```

### Adding New Template Types

**Step 1**: Create Template Data Record

```java
public record WelcomeEmailTemplateData(String username, String welcomeLink) implements MailTemplateData {
    public WelcomeEmailTemplateData {
        Objects.requireNonNull(username);
        Objects.requireNonNull(welcomeLink);
    }
    
    @Override
    public String getTemplateName() {
        return "welcome-email";
    }
    
    @Override
    public Map<String, Object> getVariables() {
        return Map.of("username", username, "welcomeLink", welcomeLink);
    }
}
```

**Step 2**: Create Template Files

Create `welcome-email.html` and `welcome-email.txt` in `src/main/resources/mail-templates/`.

**Step 3**: Use in Service

```java
var data = new WelcomeEmailTemplateData(user.getName(), welcomeLink);
String html = templateRenderer.renderHtml(data);
```

### Testing

```java
@SpringBootTest(classes = {MailTemplateConfig.class, ThymeleafMailTemplateRenderer.class})
class MailTemplateRendererTest {
    @Autowired private MailTemplateRenderer renderer;
    
    @Test
    void shouldRenderEmail() {
        var data = new EmailVerificationTemplateData("user", "https://example.com", Duration.ofMinutes(15));
        String html = renderer.renderHtml(data);
        
        assertThat(html).contains("user").contains("https://example.com");
    }
}
```

---

## Quick Reference

### Operation Entry Points

| Operation       | Entry Point        | Checklist                                                                  |
|-----------------|--------------------|----------------------------------------------------------------------------|
| Add Error Code  | `ErrorCode.java`   | 1. Add to enum 2. Update CONVENTIONS.md                                    |
| Add Audit Event | `AuditAction.java` | 1. Add to enum 2. Publish in Service 3. Add to Fixtures                    |
| Add Exception   | Module package     | 1. Verify ErrorCode 2. Extend BusinessException 3. Write tests             |
| Add Interface   | Controller class   | 1. Use @PublicApi for public 2. No annotation for protected 3. Write tests |

### File Locations

```
src/main/java/com/ahogek/cttserver/
├── common/exception/ErrorCode.java           # Error codes
├── common/exception/BusinessException.java   # Base exception
├── audit/enums/AuditAction.java              # Audit events
├── auth/infrastructure/security/PublicApi.java # Public API annotation
└── common/config/SecurityConfig.java         # Security rules

src/test/java/com/ahogek/cttserver/
└── fixtures/AuditFixtures.java               # Test fixtures
```

---

Built with consistency in mind. Follow these patterns to maintain code quality and predictability.
