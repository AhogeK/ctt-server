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
    *   `GET  /api/v1/probe/health`
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

## Implementation Matrix

When adding a new controller method, consult this matrix to apply the correct annotations:

| Tier               | Auth Required?             | `@RateLimit` Type      | `@Idempotent` Required? |
|:-------------------|:---------------------------|:-----------------------|:------------------------|
| Tier 1 (Public)    | No                         | `RateLimitType.IP`     | Yes (if POST/PUT)       |
| Tier 2 (Web Auth)  | `getCurrentUserRequired()` | `RateLimitType.USER`   | Optional                |
| Tier 3 (High Priv) | `getActiveUserRequired()`  | `RateLimitType.USER`   | Yes                     |
| Tier 4 (Device)    | `getCurrentUserRequired()` | `RateLimitType.USER`   | Yes (for data sync)     |

## Example: Applying Governance

```java
@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    // Tier 4: Device API
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
