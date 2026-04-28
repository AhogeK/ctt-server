# API Governance & Security Classification

## Overview

This document categorizes the API endpoints in the CTT Server into security tiers.
This taxonomy dictates the required authentication, rate limiting, and idempotency
strategies for any new or existing endpoint.

## Classification Tiers

### Tier 1: Public / Anonymous
Endpoints accessible without any authentication. High risk of abuse.

*   **Examples**:
    *   `POST /api/v1/auth/register`
    *   `POST /api/v1/auth/login`
    *   `POST /api/v1/auth/refresh`
    *   `GET  /actuator/health/liveness` (Spring Boot Actuator)
*   **Authentication**: None.
*   **Rate Limiting**: Strictly enforced by **IP Address**.
    *   Low capacity (e.g., 5 requests / minute for login).
*   **Idempotency**: Recommended for mutating endpoints (e.g., register) using
    client-provided correlation IDs or strict database constraints.

### Tier 2: Authenticated User (Web)
Endpoints accessed via browser using JWT. Standard user operations.

*   **Examples**:
    *   `GET  /api/v1/users/me`
    *   `POST /api/v1/apikeys`
    *   `GET  /api/v1/stats/dashboard`
*   **Authentication**: Required (`CurrentUserProvider.getCurrentUserRequired()`).
    Token must be `WEB_SESSION`.
*   **Rate Limiting**: Enforced by **User ID**.
    *   Medium capacity (e.g., 60 requests / minute).
*   **Idempotency**: Strongly recommended for state-changing operations
    (e.g., creating an API key, changing password).

### Tier 3: High-Privilege User (Web)
Endpoints requiring a fully active and verified account.

*   **Examples**:
    *   `DELETE /api/v1/users/me` (Account deletion)
    *   `PUT /api/v1/users/me/password`
*   **Authentication**: Required (`CurrentUserProvider.getActiveUserRequired()`).
*   **Rate Limiting**: Enforced by **User ID**.
    *   Strict capacity (e.g., 5 requests / hour).
*   **Idempotency**: Mandatory.
*   **Additional**: May require Re-authentication (password confirmation) or
    MFA step-up (Future).

### Tier 4: Device / Machine (Plugin)
Endpoints accessed by JetBrains IDE plugins or automated scripts.

*   **Examples**:
    *   `POST /api/v1/sync/push`
    *   `GET  /api/v1/sync/pull`
*   **Authentication**: Required. Token must be `API_KEY`.
*   **Rate Limiting**: Enforced by **User ID** or **API** (global endpoint limit).
    *   High capacity per user, but optimized for burst traffic (e.g., Token Bucket with
        large bucket size but slow refill rate).
*   **Idempotency**: Mandatory for Push syncs (`server_version` or `change_id` as SpEL key).

## Rate Limiting Dimensions

The `@RateLimit` annotation supports four isolation dimensions via `RateLimitType`:

| Type      | Description                                                                 | Use Case                                    |
|:----------|:----------------------------------------------------------------------------|:--------------------------------------------|
| `IP`      | Limits by client IP address. No authentication required.                    | Public endpoints (login, registration)      |
| `USER`    | Limits by authenticated user ID. Requires valid JWT.                        | Business APIs (sync, profile update)        |
| `EMAIL`   | Limits by email extracted via SpEL `keyExpression`.                         | Email verification bombing prevention       |
| `API`     | Global limit for the endpoint regardless of caller.                         | Expensive operations, 3rd party API proxies |

**Note**: `EMAIL` type requires `keyExpression` parameter to extract email from request (e.g., `#request.email`).

## Multi-Dimensional Rate Limiting

The `@RateLimit` annotation is repeatable (`@Repeatable(RateLimits.class)`), allowing multiple rate limits on the same endpoint for defense in depth.

### Why Multi-Dimensional Limiting?

Single-dimension rate limiting has inherent vulnerabilities:

- **IP-only**: NAT/proxy aggregation causes collateral throttling (multiple users share one IP)
- **User-only**: Attackers can create multiple accounts to bypass limits
- **Email-only**: Attackers can target multiple emails from one IP

Multi-dimensional limiting applies independent constraints on different axes, forcing attackers to overcome multiple barriers simultaneously.

### Example: Password Reset Endpoint

The `/forgot-password` endpoint uses both IP and EMAIL limits:

```java
@PostMapping("/forgot-password")
@RateLimit(
    type = RateLimitType.EMAIL,
    keyExpression = "#request.email",
    limit = 3,
    windowSeconds = 600)  // 3 requests per 10 minutes per email
@RateLimit(
    type = RateLimitType.IP,
    limit = 30,
    windowSeconds = 3600)  // 30 requests per hour per IP
public ResponseEntity<RestApiResponse<EmptyResponse>> forgotPassword(
    @Valid @RequestBody ForgotPasswordRequest request) {
    // ...
}
```

**Attack scenarios this prevents**:

1. **Single attacker, single email**: EMAIL limit (3/10min) stops repeated requests
2. **Single attacker, multiple emails**: IP limit (30/hour) caps total requests across all emails
3. **Distributed attack (multiple IPs)**: Each email still has its own 3/10min limit

### When to Use Multi-Dimensional Limiting

| Endpoint Type              | Recommended Limits                                    | Rationale                                    |
|:---------------------------|:------------------------------------------------------|:---------------------------------------------|
| Password reset             | EMAIL + IP                                            | Prevent bombing + distributed attacks        |
| Email verification resend  | EMAIL + IP                                            | Same as password reset                       |
| Login                      | IP only                                               | User ID not available before authentication  |
| API key creation           | USER only                                             | Authenticated context, no email involved     |

## Implementation Matrix

When adding a new controller method, consult this matrix to apply the correct annotations:

| Tier               | Auth Required?             | `@RateLimit` Type      | `@Idempotent` Required? |
|:-------------------|:---------------------------|:-----------------------|:------------------------|
| Tier 1 (Public)    | No                         | `RateLimitType.IP`     | Yes (if POST/PUT)       |
| Tier 2 (Web Auth)  | `getCurrentUserRequired()` | `RateLimitType.USER`   | Optional                |
| Tier 3 (High Priv) | `getActiveUserRequired()`  | `RateLimitType.USER`   | Yes                     |
| Tier 4 (Device)    | `getCurrentUserRequired()` | `RateLimitType.USER`   | Yes (for data sync)     |

**Note**: For Tier 1 endpoints involving email operations (password reset, verification resend), consider adding EMAIL-based limits alongside IP limits for multi-dimensional protection.

## Idempotency Strategies Comparison

The project uses two complementary idempotency mechanisms at different layers:

### `@Idempotent` Annotation (Controller Layer)

| Aspect            | Description                                                                  |
|-------------------|------------------------------------------------------------------------------|
| Layer             | Controller (Redis-based distributed lock)                                    |
| Mechanism         | Redis key with SpEL expression + user ID                                     |
| Duplicate Request | Throws `ConflictException` → HTTP 409                                        |
| Response Body     | `ErrorResponse` with error code `COMMON_005`                                 |
| Use Case          | Prevent concurrent duplicate submissions (e.g., sync push, API key creation) |
| Key Scope         | Request-level (correlation ID, operation ID)                                 |

### Business-Layer Idempotent Window (Service Layer)

| Aspect            | Description                                                            |
|-------------------|------------------------------------------------------------------------|
| Layer             | Service (DB query-based)                                               |
| Mechanism         | `MailOutboxService.isIdempotentSkip()` checks DB for recent records    |
| Duplicate Request | Returns `EmptyResponse.ok(true)` with `idempotentSkip=true` → HTTP 200 |
| Response Body     | `RestApiResponse<EmptyResponse>` (success response)                    |
| Use Case          | Prevent email bombing from repeated user clicks                        |
| Key Scope         | Business-level (user + business type + recipient)                      |
| Window            | 10 minutes sliding window                                              |
| Audit             | `MAIL_IDEMPOTENT_SKIP` logged on skip                                  |

### Response Difference

| Scenario                    | `@Idempotent` Response          | Business-Layer Idempotent Skip Response |
|-----------------------------|---------------------------------|-----------------------------------------|
| Duplicate within window     | 409 Conflict + error message    | 200 OK + `idempotentSkip=true`          |
| User Experience             | Error shown to user             | Silent success, no error                |
| Frontend Handling           | Error toast/alert               | Normal success flow                     |

### When to Use Each

| Endpoint Type                | Recommended Strategy      | Rationale                                 |
|------------------------------|---------------------------|-------------------------------------------|
| Sync push, API key creation  | `@Idempotent` annotation  | Prevent concurrent race conditions        |
| Email resend, password reset | Business-layer idempotent | Better UX, prevent email bombing silently |
| Payment, order creation      | `@Idempotent` annotation  | Explicit error for duplicate transactions |

## Example: Applying Governance

### Single-Dimension Rate Limiting (Tier 4)

```java
@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    // Tier 4: Device API - USER-based limiting
    @PostMapping("/push")
    @RateLimit(type = RateLimitType.USER, limit = 100, windowSeconds = 60)
    @Idempotent(
        prefix = "SYNC_PUSH",
        keyExpression = "#request.syncCursor",
        includeUserId = true,
        expireSeconds = 30,
        message = "Sync request is being processed")
    public ApiResponse<Void> pushData(@RequestBody SyncPayload request) {
        CurrentUser user = currentUserProvider.getActiveUserRequired();
        // business logic...
    }
}
```

### Multi-Dimensional Rate Limiting (Tier 1 - Email Operations)

```java
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    // Tier 1: Public API - Multi-dimensional limiting
    @PostMapping("/forgot-password")
    @RateLimit(
        type = RateLimitType.EMAIL,
        keyExpression = "#request.email",
        limit = 3,
        windowSeconds = 600)  // Prevent email bombing
    @RateLimit(
        type = RateLimitType.IP,
        limit = 30,
        windowSeconds = 3600)  // Prevent distributed attacks
    public ResponseEntity<RestApiResponse<EmptyResponse>> forgotPassword(
        @Valid @RequestBody ForgotPasswordRequest request) {
        // business logic...
    }
}
```
