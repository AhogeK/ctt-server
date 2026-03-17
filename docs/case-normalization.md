# Case Normalization Strategy

## Overview

This document defines the defensive case normalization strategy for CTT Server to
eliminate "account ghost conflicts" (e.g., `Admin@domain.com` vs `admin@domain.com`)
and prevent unauthorized account takeovers in enterprise identity systems.

## The Problem

Email case sensitivity is a security minefield:

- **RFC 5321**: Local part of email CAN be case-sensitive (theoretically)
- **Real World**: 99.9% of email providers treat emails case-insensitively
- **Attack Vector**: Malicious users exploit case differences to create duplicate
  accounts or bypass registration checks

**Example Attack**:
1. Attacker registers `Alice@Example.COM`
2. Legitimate user tries to register `alice@example.com`
3. Without normalization: two separate accounts → confusion and potential takeover

## Three-Layer Defense

### Layer 1: DTO Boundary (Input Normalization)

Use Record Compact Constructor to normalize at deserialization:

```java
public record UserRegisterRequest(
    @NotBlank @Email String email,
    String displayName,
    @StrongPassword String password
) {
    public UserRegisterRequest {
        email = (email == null) ? null : email.trim().toLowerCase();
    }
}
```

**Why here?**:
- First line of defense
- Jackson deserialization happens automatically
- Zero performance overhead

### Layer 2: Entity Lifecycle (Persistence Guard)

JPA lifecycle hooks provide final defense for entities created outside DTO flow:

```java
@Entity
public class User {
    // ...

    @PrePersist
    @PreUpdate
    protected void normalizeEmail() {
        if (this.email != null) {
            this.email = this.email.trim().toLowerCase();
        }
    }
}
```

**Why here?**:
- Catches admin scripts, data imports, direct entity manipulation
- Schema-on-write principle: data is clean when persisted
- Defensive against future developers bypassing DTOs

### Layer 3: Repository Queries (Index-Optimized Lookup)

PostgreSQL function index + Spring Data IgnoreCase:

```sql
-- Database index for case-insensitive unique constraint
CREATE UNIQUE INDEX uk_users_email_lower ON users ((LOWER(email)));
```

```java
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Case-sensitive lookup (for internal use with pre-normalized data).
     */
    Optional<User> findByEmail(String email);

    /**
     * Case-insensitive lookup (for login, registration checks).
     * SQL: WHERE LOWER(email) = LOWER(?)
     * Index: uk_users_email_lower (O(log N))
     */
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
```

**Why IgnoreCase?**:
- Spring Data generates `LOWER(email) = LOWER(?)`
- Perfectly matches `uk_users_email_lower` function index
- No table scan, O(log N) performance

## Migration Guidelines

When adding email fields to new entities:

1. **DTO**: Add compact constructor normalization
2. **Entity**: Add `@PrePersist/@PreUpdate` hook
3. **Repository**: Add `IgnoreCase` methods for public APIs
4. **Database**: Create function index: `CREATE INDEX idx_xxx_email_lower ON table ((LOWER(email)))`

## Best Practices

### Do

- Normalize at entry points (DTO constructors)
- Use `IgnoreCase` repository methods for user-facing queries
- Create function indexes for performance
- Document which layer handles normalization

### Don't

- Rely solely on database constraints (too late in flow)
- Use `LOWER()` in application code (bypasses index, O(N) scan)
- Trust frontend normalization (security boundary violation)
- Mix case-sensitive and insensitive comparisons inconsistently

## Verification

Test case normalization with:

```java
@Test
void emailIsNormalizedToLowercase() {
    UserRegisterRequest request = new UserRegisterRequest(
        "  Alice@EXAMPLE.COM  ",
        "Alice",
        "SecurePass123!"
    );

    assertThat(request.email()).isEqualTo("alice@example.com");
}
```

## References

- [RFC 5321 - Simple Mail Transfer Protocol](https://tools.ietf.org/html/rfc5321)
- [PostgreSQL Functional Indexes](https://www.postgresql.org/docs/current/indexes-expressional.html)
- [Spring Data JPA - Query Methods](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#jpa.query-methods)
