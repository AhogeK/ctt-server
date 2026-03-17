# Unified Time Strategy Specification

## Overview

This document defines the UTC-first time handling strategy for CTT Server to ensure
absolute consistency across distributed systems, multiple devices, and different
geographic regions.

## Core Principle

**Absolute Time on the Epoch**: All timestamps are reduced to a single point on the
absolute timeline, completely eliminating timezone context dependencies from servers,
databases, and client environments.

```
T_UTC = T_Local - Delta_Offset
```

## Implementation Layers

### Layer 1: JVM Foundation (UTC Enforcement)

The application forces UTC timezone at startup via `@PostConstruct` in
`CttServerApplication`:

```java
@PostConstruct
public void init() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
}
```

This prevents discrepancies caused by varying host server timezone configurations
(e.g., `Asia/Shanghai` vs `America/New_York`).

### Layer 2: Jackson Serialization (ISO-8601)

Configured in `application.yaml`:

```yaml
spring:
  jackson:
    time-zone: UTC
```

Spring Boot 4.x uses Jackson 3.x which defaults to ISO-8601 string format.

**Output Format**: `"2026-03-16T12:00:00.000Z"`

The trailing `Z` indicates Zulu/UTC time, providing O(1) parsing compatibility
across JavaScript, Python, and Go.

### Layer 3: Domain Model Types

| Scenario                            | Type             | Usage                                                                                                                         |
|-------------------------------------|------------------|-------------------------------------------------------------------------------------------------------------------------------|
| **Machine Time / Absolute Records** | `Instant`        | **Golden Standard**. Use for `createdAt`, `expiresAt`, `lastLoginAt`. Immutable point on timeline, immune to timezone shifts. |
| **Local-Aware Calendar Time**       | `OffsetDateTime` | Use when business logic requires "what time was it locally when this occurred" (e.g., timezone-specific billing reports).     |

**Prohibited Types**:

- `java.util.Date` (legacy, mutable)
- `java.sql.Timestamp` (JDBC legacy)
- `LocalDateTime` (no timezone context, dangerous for distributed systems)

### Layer 4: Database Storage

PostgreSQL uses `TIMESTAMPTZ` (Timestamp with Time Zone):

```sql
CREATE TABLE example (
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ
);
```

Hibernate 6 automatically maps `TIMESTAMPTZ` to `Instant`:

```java
@CreationTimestamp
@Column(name = "created_at", nullable = false, updatable = false)
private Instant createdAt;
```

### Layer 5: API Validation

Use Jakarta Validation constraints with `Instant`:

```java
public record TokenCreateRequest(
    @NotNull
    @Future(message = "Expiration time must be in the absolute future")
    Instant expiresAt
) {}
```

## Client Contract

### Client to Server (Request)

Clients MUST convert local time to UTC ISO-8601 before sending:

```javascript
// JavaScript example
const utcString = new Date().toISOString();  // "2026-03-16T12:00:00.000Z"
```

### Server to Client (Response)

Server always returns UTC time with `Z` suffix. Clients render using local timezone:

```javascript
// Using day.js
const localTime = dayjs(utcString).format('YYYY-MM-DD HH:mm:ss');
```

## Migration Guidelines

When adding new timestamp fields:

1. **Database**: Use `TIMESTAMPTZ` in Flyway migrations
2. **Entity**: Use `Instant` type
3. **DTO**: Use `Instant` type with `@Future` or `@Past` if applicable
4. **API**: Document the ISO-8601 format in OpenAPI annotations

## Examples

### Entity Definition

```java
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
public class Session {

    @Id
    private UUID id;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @Column
    private Instant expiresAt;

    // Getters and business methods...
}
```

### DTO Definition

```java
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record SessionRequest(
    @NotNull
    @Future
    Instant expiresAt,

    String projectName
) {}
```

## References

- [ISO-8601 Standard](https://en.wikipedia.org/wiki/ISO_8601)
- [PostgreSQL TIMESTAMPTZ](https://www.postgresql.org/docs/current/datatype-datetime.html)
- [Hibernate 6 Java Time Support](https://docs.jboss.org/hibernate/orm/6.0/userguide/html_single/Hibernate_User_Guide.html#datetime)
- [Jackson JavaTimeModule](https://github.com/FasterXML/jackson-modules-java8/tree/master/datetime)
