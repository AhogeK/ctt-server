# Security Architecture

## Overview

CTT Server implements a defense-in-depth security architecture following OWASP guidelines. The system uses stateless JWT-based authentication, cookie-based token delivery, CSRF protection via Double Submit Cookie, and a declarative rate-limiting framework backed by Redis.

### Design Principles

| Principle         | Implementation                                                                   |
|-------------------|----------------------------------------------------------------------------------|
| Secure by Default | All endpoints require authentication unless explicitly marked `@PublicApi`       |
| Least Privilege   | Token scoping, cookie path isolation, CORS origin allowlisting                   |
| Defense in Depth  | Multiple overlapping controls: rate limiting, account lockout, CSRF, CSP         |
| Fail Secure       | Authentication failures return 401; authorization failures return 403            |
| Zero Trust        | Every request validated independently via stateless JWT; no server-side sessions |

### Security Layer Stack

```
┌─────────────────────────────────────────────────────────┐
│  Layer 1: Network / Transport                            │
│  - HSTS (1 year, includeSubDomains)                      │
│  - Secure flag on all auth cookies                       │
├─────────────────────────────────────────────────────────┤
│  Layer 2: Request Filtering                              │
│  - CORS origin allowlist                                 │
│  - CSRF Double Submit Cookie (public endpoints exempt)   │
│  - Security headers (CSP, X-Frame-Options, etc.)         │
├─────────────────────────────────────────────────────────┤
│  Layer 3: Authentication                                 │
│  - JWT (access token) via oauth2ResourceServer           │
│  - Cookie-based token delivery (HttpOnly, Secure)        │
│  - Refresh token rotation                                │
├─────────────────────────────────────────────────────────┤
│  Layer 4: Authorization                                  │
│  - @PublicApi annotation (deny by default)               │
│  - Terms acceptance filter                               │
│  - Account status enforcement (ACTIVE required)          │
├─────────────────────────────────────────────────────────┤
│  Layer 5: Rate Limiting & Abuse Prevention               │
│  - Per-IP, Per-User, Per-Email dimensions               │
│  - Global per-instance rps ceiling (GlobalRequestGuard) │
│  - Account lockout (brute-force protection)              │
│  - hCaptcha integration                                  │
├─────────────────────────────────────────────────────────┤
│  Layer 6: Audit & Monitoring                             │
│  - Structured audit events (five-tuple model)            │
│  - Log message desensitization (two-layer defense)     │
│  - Trace ID correlation                                  │
└─────────────────────────────────────────────────────────┘
```

---

## Authentication

### JWT Token Architecture

The system uses JSON Web Tokens (JWT) as the primary authentication mechanism. Sessions are fully
stateless; no session data is stored server-side. The "sessions" a user sees and revokes in the
account UI are not server-side authentication state: each one is the chain of refresh-token rows one
login produced, grouped by the `session_id` it carries.

#### Token Types

| Token                  | Purpose                                  | TTL        | Storage                                             |
|------------------------|------------------------------------------|------------|-----------------------------------------------------|
| Access Token           | Authenticate API requests                | 15 minutes | Cookie (`ctt_access_token`, HttpOnly)               |
| Refresh Token (Web)    | Obtain new access tokens                 | 30 days    | Cookie (`ctt_refresh_token`, HttpOnly, path-scoped) |
| Refresh Token (Plugin) | Obtain new access tokens for IDE plugins | 14 days    | Response body                                       |

#### JWT Claims

The access token contains the following claims:

| Claim          | Type    | Description                                 |
|----------------|---------|---------------------------------------------|
| `sub`          | UUID    | User ID                                     |
| `email`        | String  | User email address                          |
| `status`       | String  | User status enum (e.g., `ACTIVE`, `LOCKED`) |
| `authorities`  | String  | Comma-separated authority strings           |
| `termsVersion` | String  | Accepted terms version                      |
| `iss`          | String  | Issuer (`ctt-identity-provider`)            |
| `iat`          | Instant | Issued at                                   |
| `exp`          | Instant | Expiration time                             |

#### Token Lifecycle

```
Login/Refresh
      │
      ▼
┌─────────────────────┐
│ Generate JWT         │
│ (access + refresh)   │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ Set Cookies          │
│ - ctt_access_token   │  HttpOnly, Secure, SameSite=Lax, Path=/
│ - ctt_refresh_token  │  HttpOnly, Secure, SameSite=Strict, Path=/api/v1/auth/refresh
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ Client sends request │
│ with Cookie header   │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ JwtToCurrentUser     │
│ Converter extracts   │
│ claims → CurrentUser │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ Access token expires │
│ (15 min)             │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ POST /auth/refresh   │
│ (refresh token       │
│  rotated on use)     │
└─────────────────────┘
```

#### Refresh Token Rotation

Each refresh token is single-use. On refresh:

1. The current refresh token is revoked (`revoked_at` set)
2. A new access token and refresh token pair is issued
3. New cookies are set in the response

This prevents token replay attacks. A replay only counts as theft when the session it belongs to is
still alive: the replayed token was then superseded by a rotation, so the server treats it as a
breach and revokes every token the user holds (Kill Switch). A token whose session has no live row —
the whole session was revoked by a logout, a password reset, or the user kicking it out of the
session list — is answered `401 AUTH_003` for that session alone, because a client that does not yet
know it was signed out would otherwise take every other session down with it. The account's sessions
are listed and revoked through `GET` / `DELETE /api/v1/users/me/sessions`; revoking one is serialized
against token rotation for that session, so it cannot be kept alive by a refresh racing it.

### Cookie Security

Authentication tokens are delivered exclusively via cookies with security-hardened attributes.

#### Cookie Attributes

| Cookie              | HttpOnly | Secure | SameSite | Path                   | Max-Age              |
|---------------------|----------|--------|----------|------------------------|----------------------|
| `ctt_access_token`  | true     | true   | Lax      | `/`                    | 900s (15 min)        |
| `ctt_refresh_token` | true     | true   | Strict   | `/api/v1/auth/refresh` | 1,209,600s (14 days) |

#### Cookie Scoping

The refresh token cookie uses a narrow `Path` attribute scoped to the refresh endpoint. This means:

- The browser only sends the refresh token cookie to `POST /api/v1/auth/refresh`
- Other API endpoints never receive the refresh token
- If the access token cookie is leaked via another path, the refresh token remains isolated

The path is configurable via `ctt.security.cookie.refresh-token-path` (default: `/api/v1/auth/refresh`).

#### CookieHelper Implementation

```java
// src/main/java/com/ahogek/cttserver/auth/util/CookieHelper.java
public static Cookie createAccessTokenCookie(String token) {
    return buildCookie(ACCESS_TOKEN_COOKIE, token, ACCESS_TOKEN_PATH, ACCESS_TOKEN_MAX_AGE, "Lax");
}

public static Cookie createRefreshTokenCookie(String token, String refreshTokenPath) {
    return buildCookie(REFRESH_TOKEN_COOKIE, token, refreshTokenPath, REFRESH_TOKEN_MAX_AGE, "Strict");
}

private static Cookie buildCookie(String name, String value, String path, int maxAge, String sameSite) {
    Cookie cookie = new Cookie(name, value);
    cookie.setHttpOnly(true);
    cookie.setSecure(true);
    cookie.setPath(path);
    cookie.setMaxAge(maxAge);
    cookie.setAttribute("SameSite", sameSite);
    return cookie;
}
```

#### Backward Compatibility

In addition to cookies, the access token is also returned in the response body for clients that cannot use cookies (e.g., mobile apps, automated scripts). The `Authorization: Bearer <token>` header is also accepted.

### Public Endpoint Registry

Endpoints are marked public using the `@PublicApi` annotation. At startup, `PublicApiEndpointRegistry` scans all controllers and collects URLs annotated with `@PublicApi`. These URLs are then used by Spring Security to configure the public whitelist.

```java
@PublicApi(reason = "User login endpoint")
@PostMapping("/login")
public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) { ... }
```

The registry also includes system endpoints by default:
- `/error`
- `/actuator/health`, `/actuator/info`
- `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`

---


## Two-Factor Authentication

TOTP is a possession factor layered on top of the password (or OAuth) login. It is not a
replacement for the session: a stolen access token remains valid until it expires (at most 15
minutes), and API keys are machine credentials that bypass the interactive challenge by design.

**Secret storage.** A 20-byte secret is encrypted with AES-256-GCM using
`TOTP_ENCRYPTION_KEY`; the stored value is `v1:<iv>:<ciphertext>`. The key must be backed up
separately from the database: losing it makes every enabled secret undecryptable. It is validated
at startup, and a missing or malformed key stops the application instead of degrading to an
unencrypted or default value — the production profile deliberately has no fallback, while local
development uses a public placeholder so a fresh clone starts without extra setup. Pending,
unconfirmed secrets live in Redis for ten minutes and are encrypted with the same key.

The 20-byte secret is Base32-encoded **exactly once**, producing 32 characters without padding.
Enrollments created before v0.89.1 used a double-encoded value: 52 Base32 characters plus four `=`
padding (56 characters total); those accounts must be re-enrolled (`disable`, then `start` +
`confirm`) before iOS Google Authenticator can scan them.

**Replay.** Verification accepts the current 30-second step and one step either side. The accepted
step is written to `users.totp_last_used_step` with a conditional update, so the same step cannot
issue two sessions. A challenge is claimed with `GETDEL` and restored with an incremented attempt
counter after a failed code; the fifth failure ends it with `429`.

**Recovery codes.** Ten codes are generated from ten random bytes each, shown as four Base32
groups, and stored only as SHA-256 hashes. Consumption is an atomic `UPDATE ... WHERE used_at IS
NULL`; regeneration replaces the whole batch.

**Session and key boundaries.** Enabling or disabling 2FA revokes every other session of the
account (the current session is kept). Creating an API key requires a browser session; when 2FA is
enabled it also requires a TOTP or unused recovery code. Otherwise a stolen session could mint a
permanent machine credential and bypass the factor entirely.

**OAuth.** A GitHub callback checks the linked user's 2FA state before issuing any CTT token. The
challenge is delivered in the URL fragment (`#challengeToken=...`), never in the query string, and
no cookies are set on that response.

**Explicitly out of scope.** SMS/email codes, WebAuthn/passkeys as a second-factor switch,
trusted-device cookies, admin reset of a user's 2FA, and 2FA on every API-key request.

## Passkeys (WebAuthn)

Passkeys are an additional **discoverable credential** login path and an optional **second factor**
for an existing 2FA challenge. A primary passkey assertion is complete on its own and does not enter
the TOTP challenge; the second-factor endpoint is account-bound and is only available when the login
flow already issued a 2FA challenge. Password, TOTP, recovery codes and OAuth remain available for
recovery.

**Relying party.** `PASSKEY_RP_ID` must be the effective domain used by the browser and is a
long-lived commitment: changing it invalidates every registered credential. `PASSKEY_ALLOWED_ORIGINS`
is an exact origin allowlist. Both default to localhost for local development; a production
deployment must set them explicitly. `PASSKEY_RP_ID` must not include a scheme, port or path.

**Registration.** The browser session must be recent (`AUTH_031`), and API-key callers are rejected
(`AUTH_025`). The creation options require a resident key and user verification. `attestation=none`
is deliberate: this public service does not use attestation as an access-control decision.

**Assertion.** The anonymous options contain no `allowCredentials`, so the authenticator can select
any discoverable credential for the relying party. The server requires `UV` and `UP`, validates the
RP ID hash, origin, challenge and signature, and resolves the account from the WebAuthn user handle
(the account UUID). All assertion failures return the same `AUTH_033` response to avoid account
enumeration.

**Challenge storage.** A challenge is random, operation-bound (registration, discoverable assertion,
or second factor), stored in Redis and claimed with `GETDEL`; it expires after five minutes. A
consumed or mismatched challenge is rejected with `AUTH_032`.

**Second factor.** The options endpoint validates the login `challengeToken` without consuming it,
then stores an account-bound WebAuthn challenge whose payload includes the SHA-256 hash of that
login token. The assertion is accepted only when the WebAuthn challenge, account and token hash all
match. The login challenge is consumed only after a successful assertion; a failed passkey attempt
consumes the WebAuthn challenge but leaves TOTP/recovery code usable. The new session created by the
successful completion is recent by construction.

**Credential record.** The table stores the base64url credential ID, COSE public key, signature
counter, transports, AAGUID and backup flags. If the stored counter is zero, an observed zero is
accepted (synced passkeys); once a non-zero counter is stored, a zero or non-increasing observation
is rejected. Backup eligibility/state are display metadata only and are not an authorization gate.
No private key material reaches the server.

**Recovery and revocation.** Password + TOTP and password + recovery code remain valid. Deleting a
passkey requires recent authentication; deleting the last passkey is allowed only while the account
still has a password or an OAuth binding. Every registration sends a security notification email
containing only the label.

## Credential and request hardening

**API keys are machine credentials, not interactive identities.** Password set/change, email-change
request/resend/cancel/status and account deletion require `WEB_SESSION`; an API key receives
`AUTH_025`. `getActiveUserRequired()` alone proves account state, not that the caller is a browser.

**Password changes revoke other credentials.** The current browser session survives; every other live
refresh-token session and every API key are revoked, matching the credential-change rule used by
password reset.

**Forwarded IPs are untrusted by default.** `X-Forwarded-For` / `X-Real-IP` are honored only when the
direct socket peer is listed in `ctt.security.trusted-proxy-addresses`; the rightmost untrusted hop is
used. The default is the socket address, so rotating a client-supplied header cannot bypass IP rate
limits or forge audit attribution. A trusted TLS proxy that sets `X-Forwarded-Proto: https` also gets
the HSTS header.

**OAuth state and token placement.** The authorize endpoint sets an HttpOnly, SameSite=Lax state
cookie, with Secure when the request is directly TLS or arrives through a configured trusted proxy
that sets `X-Forwarded-Proto: https`; the callback rejects a state that does not match it. Successful
login puts access/refresh tokens in the URL fragment, never in the query string, so they do not
enter proxy or access logs.

**Management surface.** Actuator exposes only `health` and `info`; `metrics`, `loggers` and other
management endpoints are not reachable through the public API.

**Mail retention and account deletion.** `mail_outbox` rows are tied to `users` with an FK cascade;
terminal rows older than seven days are purged by a scheduled job. Rendered verification/reset links
therefore do not remain indefinitely, and account deletion removes queued/historical mail.

**Audit inputs.** Trace IDs are accepted only as 32 hex characters (the persisted column width) and
client IPs are length/charset bounded before audit persistence; malformed values fall back to a
generated trace ID/socket IP instead of causing a silently dropped audit row.

## CSRF Protection

### Double Submit Cookie Pattern

CSRF protection uses Spring Security's `CookieCsrfTokenRepository` with `HttpOnly=false`. The CSRF token is stored in a cookie that JavaScript can read, and the server expects the token in a request header.

#### How It Works

1. Spring Security sets a `XSRF-TOKEN` cookie on the response (HttpOnly=false, so JS can read it)
2. The frontend reads the cookie and sends the value in the `X-XSRF-TOKEN` header
3. Spring Security compares the cookie value with the header value
4. If they match, the request is allowed; otherwise, 403 Forbidden

#### Why HttpOnly=false for CSRF Cookie

The CSRF cookie must be readable by JavaScript to implement the Double Submit Cookie pattern. This is by design and does not introduce a security risk because:

- The CSRF token is not a secret; it protects against CSRF, not authentication
- The token must be readable by the frontend to send it in the header
- The actual authentication tokens (access/refresh) remain HttpOnly=true

#### Public Endpoint Exemption

Public endpoints (those marked with `@PublicApi`) are exempt from CSRF checks. This prevents CSRF errors on login, registration, and other unauthenticated endpoints where no session cookie exists.

```java
csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
    .ignoringRequestMatchers(publicApiRegistry.getPublicUrls())
```

### Frontend Integration

#### Reading XSRF-TOKEN Cookie

```javascript
// The XSRF-TOKEN cookie is set automatically by the server.
// Read it with standard cookie access:
function getCsrfToken() {
    const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
    return match ? decodeURIComponent(match[1]) : null;
}
```

#### Sending X-XSRF-TOKEN Header

Include the CSRF token in the `X-XSRF-TOKEN` header for all mutating requests (POST, PUT, DELETE, PATCH):

```javascript
// Using fetch API
fetch('/api/v1/auth/login', {
    method: 'POST',
    credentials: 'include',  // Required for cookies
    headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': getCsrfToken()
    },
    body: JSON.stringify({ email, password })
});

// Using Axios (auto-attaches XSRF-TOKEN if configured)
axios.defaults.xsrfCookieName = 'XSRF-TOKEN';
axios.defaults.xsrfHeaderName = 'X-XSRF-TOKEN';
axios.defaults.withCredentials = true;
```

#### Handling CSRF Errors

When CSRF validation fails, the server returns `403 Forbidden`. The frontend should:

1. Refresh the page (or re-fetch the CSRF token)
2. Retry the original request

---

## CORS Configuration

### Policy

CORS is configured via `SecurityProperties.Cors` and applied to all `/api/**` endpoints through `CorsConfig`.

#### Default Configuration

| Property                | Value                                                         |
|-------------------------|---------------------------------------------------------------|
| `allowedOrigins`        | `http://localhost:5173` (dev)                                 |
| `allowedOriginPatterns` | `http://localhost:5173`, `https://ctt.example.com`            |
| `allowedMethods`        | GET, POST, PUT, DELETE, PATCH, OPTIONS                        |
| `allowedHeaders`        | Authorization, Content-Type, X-Requested-With, Accept, Origin |
| `exposedHeaders`        | Authorization                                                 |
| `allowCredentials`      | true                                                          |
| `maxAge`                | 3600 seconds (1 hour)                                         |

#### Development vs Production

- **Development**: `http://localhost:5173` (Vue.js dev server)
- **Production**: Configure `ctt.security.cors.allowed-origins` and `ctt.security.cors.allowed-origin-patterns` to your production domain

#### Credentials Support

When `allowCredentials` is `true`, browsers disallow wildcard (`*`) origins. Each origin must be explicitly listed. This is required because the system uses cookies for authentication.

### Configuration

```yaml
# application.yaml
ctt:
  security:
    cors:
      allowed-origins:
        - "https://ctt.example.com"
      allowed-origin-patterns:
        - "https://ctt.example.com"
      allowed-methods:
        - "GET"
        - "POST"
        - "PUT"
        - "DELETE"
        - "PATCH"
        - "OPTIONS"
      allowed-headers:
        - "Authorization"
        - "Content-Type"
        - "X-Requested-With"
        - "Accept"
        - "Origin"
      exposed-headers:
        - "Authorization"
      allow-credentials: true
      max-age: 3600
```

---

## Security Headers

### OWASP Recommended Headers

All responses include the following security headers, configured in `SecurityConfig.securityFilterChain()`:

| Header                      | Value                                 | Purpose                          |
|-----------------------------|---------------------------------------|----------------------------------|
| `X-Content-Type-Options`    | `nosniff`                             | Prevents MIME type sniffing      |
| `X-XSS-Protection`          | `1; mode=block`                       | Enables browser XSS filter       |
| `X-Frame-Options`           | `DENY`                                | Prevents clickjacking            |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | Forces HTTPS for 1 year          |
| `Content-Security-Policy`   | See below                             | Restricts resource loading       |
| `Referrer-Policy`           | `no-referrer`                         | Prevents referrer header leakage |

### CSP Configuration

The Content Security Policy restricts which resources can be loaded and from where:

```
default-src 'self';
script-src 'self' https://hcaptcha.com https://*.hcaptcha.com;
frame-src 'self' https://*.hcaptcha.com;
connect-src 'self' https://*.hcaptcha.com;
img-src 'self' data: https://*.hcaptcha.com;
style-src 'self' 'unsafe-inline';
font-src 'self' data:;
object-src 'none';
base-uri 'self';
form-action 'self';
frame-ancestors 'none'
```

| Directive         | Sources                       | Rationale                                               |
|-------------------|-------------------------------|---------------------------------------------------------|
| `default-src`     | `'self'`                      | Only load resources from same origin by default         |
| `script-src`      | `'self'` + hCaptcha           | Allow scripts from self and hCaptcha for bot protection |
| `frame-src`       | `'self'` + hCaptcha           | Allow hCaptcha challenge iframe                         |
| `connect-src`     | `'self'` + hCaptcha           | Allow XHR/fetch to hCaptcha API                         |
| `img-src`         | `'self'` + `data:` + hCaptcha | Allow data URIs and hCaptcha images                     |
| `style-src`       | `'self'` + `'unsafe-inline'`  | Allow inline styles (Thymeleaf email templates)         |
| `font-src`        | `'self'` + `data:`            | Allow data URI fonts                                    |
| `object-src`      | `'none'`                      | Block plugins (Flash, Java)                             |
| `base-uri`        | `'self'`                      | Prevent base tag injection                              |
| `form-action`     | `'self'`                      | Forms can only submit to same origin                    |
| `frame-ancestors` | `'none'`                      | Prevent framing (reinforces X-Frame-Options)            |

---

## Rate Limiting

### Framework

Rate limiting is implemented as a declarative AOP framework using the `@RateLimit` annotation. Limits are enforced via Redis using atomic Lua scripts for distributed consistency.

### Rate Limit Dimensions

| Dimension | Key Source                                       | Use Case                                 |
|-----------|--------------------------------------------------|------------------------------------------|
| `IP`      | Client IP address from request context           | Public endpoints (login, registration)   |
| `USER`    | Authenticated account: JWT principal or API-key owner | Business APIs (profile, sync, stats) |
| `EMAIL`   | Email extracted via SpEL expression              | Email verification, password reset       |

The `API` dimension (one bucket shared by every caller of an endpoint) was removed on 2026-11-05:
it measured deployment size rather than abuse, and capped the installation at roughly 320–430
devices at the measured plugin polling rate. What replaced it is the installation-wide ceiling
below, which applies to every endpoint rather than to one.

### Multi-Dimensional Rate Limiting

Endpoints can have multiple `@RateLimit` annotations for defense in depth:

```java
@PostMapping("/password-reset/request")
@RateLimit(type = RateLimitType.EMAIL, keyExpression = "#request.email", limit = 3, windowSeconds = 600)
@RateLimit(type = RateLimitType.IP, limit = 30, windowSeconds = 3600)
public ResponseEntity<RestApiResponse<EmptyResponse>> requestReset(...) { ... }
```

This prevents:
- Single attacker, single email: EMAIL limit (3/10min) stops repeated requests
- Single attacker, multiple emails: IP limit (30/hour) caps total requests
- Distributed attack: Each email still has its own 3/10min limit

### Endpoint Rate Limits

| Endpoint                                   | Dimensions | Limit  | Window          |
|--------------------------------------------|------------|--------|-----------------|
| `POST /auth/register`                      | IP         | 60     | 1 hour          |
| `POST /auth/login`                         | IP         | 30     | 1 hour          |
| `POST /auth/resend-verification`           | EMAIL      | 3      | 1 minute        |
| `POST /auth/password-reset/request`        | EMAIL + IP | 3 / 30 | 10 min / 1 hour |
| `POST /auth/password-reset/confirm`        | IP         | 15     | 10 minutes      |
| `POST /auth/logout-all`                    | USER       | 5      | 1 minute        |
| `POST /auth/oauth/github/callback`         | IP         | 60     | 1 hour          |
| `POST /users/me/email/change-request`      | EMAIL      | 3      | 10 minutes      |
| `POST /users/me/email/change-confirm`      | IP         | 15     | 10 minutes      |
| `POST /users/me/email/resend-verification` | USER       | 1      | 60 seconds      |
| `POST /auth/login/2fa`                     | IP         | 10     | 5 minutes       |
| `POST /users/me/2fa/totp/start`            | USER       | 5      | 1 hour          |
| `POST /users/me/2fa/totp/confirm`          | USER       | 5      | 5 minutes       |
| `POST /users/me/2fa/disable`               | USER       | 5      | 1 hour          |
| `POST /users/me/2fa/recovery-codes/regenerate` | USER   | 3      | 1 hour          |
| `POST /api/v1/sync/pull`                  | USER       | 600    | 1 minute        |
| `POST /api/v1/sync/push`                  | USER       | 120    | 1 minute        |

The table lists the limits that are most often looked up; the codebase carries more. The sync
limits are per **account**, so one account draining its history (about 106 pages at the
1000-change page size) never consumes another account's budget.

### Installation-Wide Request Ceiling

`GlobalRequestGuard` caps rate-limited requests per second across the whole instance, ahead of
every dimension bucket. The configured value is `ctt.security.rate-limit.global-max-requests-per-second`
(default 200) and it returns the same `429 RATE_LIMIT_001` as a dimension bucket, with a
`retryAfter` one second out.

It is an in-process fixed-window counter, not a shared Redis counter: it protects the instance it
runs in, and an N-instance deployment has an effective ceiling of N times the configured value. A
true fleet-wide ceiling belongs at the edge proxy, which is the only layer that sees the traffic
this application never receives.

### Rate Limit Response

When rate limited, the server returns:

```json
{
    "success": false,
    "message": "Too many requests, please try again later.",
    "data": {
        "code": "RATE_LIMIT_001",
        "message": "Too many requests, please try again later.",
        "httpStatus": 429,
        "timestamp": "2026-07-04T..."
    }
}
```

### Redis Implementation

Rate limiting uses a Lua script for atomic increment-and-expire operations:

```lua
local current = redis.call('get', KEYS[1])
if current and tonumber(current) >= tonumber(ARGV[1]) then
    return 0
end
current = redis.call('incr', KEYS[1])
if tonumber(current) == 1 then
    redis.call('expire', KEYS[1], tonumber(ARGV[2]))
end
return 1
```

---

## Account Lockout

### Brute-Force Protection

The system implements automatic account lockout to prevent brute-force password attacks.

| Parameter           | Default    | Configurable Via                               |
|---------------------|------------|------------------------------------------------|
| Max failed attempts | 5          | `ctt.security.password.max-failed-attempts`    |
| Sliding window      | 15 minutes | `ctt.security.password.failure-window-seconds` |
| Lockout duration    | 30 minutes | `ctt.security.password.lock-duration`          |
| Storage             | DB         | `ctt.security.password.storage`                |

### Lockout Flow

```
Failed login attempt
        │
        ▼
┌─────────────────────┐
│ Record failure       │
│ (SHA-256 hashed      │
│  email + IP)         │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ Count failures in    │
│ sliding window       │
└──────────┬──────────┘
           │
     ┌─────┴─────┐
     │ >= max?   │
     └─────┬─────┘
      Yes  │  No
       ▼   │   ▼
┌──────────┐  ┌──────────┐
│ Lock user│  │ Continue │
│ (30 min) │  │          │
└──────────┘  └──────────┘
```

### Auto-Unlock (Hybrid Model)

1. **Lazy unlock**: On next login attempt, checks if earliest attempt + lockDuration < now, then reactivates user
2. **Scheduled sweep**: Hourly background task unlocks accounts with no recent attempts in the failure window

### Locked Response

```json
{
    "code": "AUTH_004",
    "message": "Account locked",
    "httpStatus": 403,
    "retryAfter": "2026-07-04T12:30:00Z"
}
```

The response also includes `Retry-After` header (RFC 7231) with seconds until unlock.

### Kill Switch

Password reset automatically unlocks the account and revokes all active sessions:

```java
// PasswordResetService.resetPassword()
loginAttemptService.recordSuccess(email);  // Unlock
refreshTokenRepository.revokeAllUserTokens(userId, Instant.now());  // Kill Switch
```

---

## Audit Logging

### Security Event Model

All security-relevant events are recorded using a five-tuple model:

| Field       | Type                      | Description                                         |
|-------------|---------------------------|-----------------------------------------------------|
| User        | UUID                      | Subject who triggered the event (null if anonymous) |
| Action      | AuditAction               | What was done                                       |
| Resource    | ResourceType + ID         | Target resource                                     |
| Severity    | INFO / WARNING / CRITICAL | Risk level for alerting                             |
| Environment | IP, UA, TraceId           | Request context                                     |

### Audit Event Categories

| Category        | Actions                                                                                                                                  |
|-----------------|------------------------------------------------------------------------------------------------------------------------------------------|
| **IAM**         | `LOGIN_SUCCESS`, `LOGIN_FAILURE`, `LOGOUT`, `LOGOUT_ALL`, `ACCOUNT_LOCKED`, `ACCOUNT_UNLOCKED`                                           |
| **Email**       | `EMAIL_VERIFICATION_SENT`, `EMAIL_VERIFIED`, `EMAIL_CHANGE_REQUESTED`, `EMAIL_CHANGED`, `EMAIL_CHANGE_CANCELLED`, `MAIL_IDEMPOTENT_SKIP` |
| **Credentials** | `PASSWORD_CHANGED`, `PASSWORD_SET`, `PASSWORD_RESET_REQUESTED`, `PASSWORD_RESET_EMAIL_NOT_FOUND`, `API_KEY_REVOKED`                      |
| **OAuth**       | `OAUTH_LOGIN_SUCCESS`, `OAUTH_REGISTER_SUCCESS`, `OAUTH_BIND_SUCCESS`, `OAUTH_UNBIND_SUCCESS`, `OAUTH_LOGIN_FAILURE`                     |
| **Security**    | `RATE_LIMIT_EXCEEDED`, `UNAUTHORIZED_ACCESS`, `CSRF_VIOLATION`                                                                           |
| **Terms**       | `TERMS_ACCEPTED`                                                                                                                         |

### Log Desensitization (Two-Layer Defense)

| Layer     | Component                       | Mechanism                                         |
|-----------|---------------------------------|---------------------------------------------------|
| 1. Filter | `DesensitizeUtils.maskHeader()` | Masks Authorization headers in HTTP logging       |
| 2. Global | `MaskingMessageConverter` (console) / `MaskingJsonEncoder` (production JSON) | Regex-based fallback for hardcoded sensitive data. The console path masks the formatted message; the JSON path masks the formatted message plus the string values of key/value pairs (`kvpList`), which a pattern never sees |

Protected patterns: `password`, `passwd`, `token`, `authorization`, `cookie`, `secret`.

The production profile logs JSON, where a pattern-based converter never runs — there is no pattern to walk. `MaskingJsonEncoder` masks the formatted message and the string values of key/value pairs (for example `query_string`, which carries `token=` on verification links), then delegates to Logback's `JsonEncoder`. One difference from the console path: once text was masked, `arguments` is omitted, because an argument cannot be told apart from data and the rendered text already contains it. Events that need no masking are emitted by the stock encoder, so their schema is unchanged. MDC properties and throwable messages are not masked.

### Audit Storage Boundary

The audit trail never stores request or response bodies, in any environment. A row carries the
action, the resource type and identifier, the severity and outcome, environment metadata, and
structured `details` (`reason`, `errorCode`, `attemptCount`, `stateBefore`, `stateAfter`, plus an
optional extension map) — see `AuditDetails`.

A former `ctt.security.audit.log-payloads` switch had no production consumer (only a binding test)
and was removed in v0.93.7 rather than left as a misleading knob. Body capture was evaluated and
rejected: audit rows survive account deletion, the only masking is text-oriented rather than
JSON-aware, one sync push may carry up to 1000 sessions, and ASVS 16.3.2 requires logging access
decisions without the sensitive data itself. The full argument, alternatives and revisit trigger
are in [ADR-0001](decisions/ADR-0001-audit-payload-boundary.md). If diagnostics ever need more,
record shape only (field names, sizes, validation codes), never values.


---

## Password Security

### Hashing Strategy

Passwords are hashed using BCrypt with configurable strength:

```yaml
ctt:
  security:
    password:
      bcrypt-rounds: 12  # Minimum: 10
```

BCrypt strength (log rounds) determines computation time. At 12 rounds, each hash takes ~250ms, making rainbow table attacks expensive.

### Password Policy

| Rule                 | Value                               | Source                       |
|----------------------|-------------------------------------|------------------------------|
| Minimum length       | 8 characters                        | NIST SP 800-63B              |
| Maximum length       | 64 characters                       | Storage protection           |
| Allowed characters   | Printable ASCII non-space (`!`-`~`) | `REGEX_PASSWORD_CHARS`       |
| Same as old password | Rejected on reset                   | `PASSWORD_SAME_AS_OLD` error |

### Password Reset Flow

```
User requests reset
        │
        ▼
POST /auth/password-reset/request {"email": "..."}
        │
        ▼
┌─────────────────────┐
│ Anti-enumeration:    │
│ Always returns 200   │
│ regardless of email  │
│ existence            │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ Generate 64-byte     │
│ random token         │
│ Hash with SHA-256    │
│ Store hash only      │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ Queue email via      │
│ mail_outbox          │
└──────────┬──────────┘
           │
           ▼
User clicks reset link
           │
           ▼
POST /auth/password-reset/confirm {"token": "...", "newPassword": "..."}
           │
           ▼
┌─────────────────────┐
│ 1. Validate token    │
│ 2. Update password   │
│ 3. Kill Switch:      │
│    revoke all tokens │
│ 4. Unlock account    │
│    (if locked)       │
└─────────────────────┘
```

Token properties:
- Size: 64 bytes random
- Hash: SHA-256 (raw tokens never stored)
- TTL: 1 hour
- Consumption: One-time use with optimistic locking

---

## OAuth Integration

### GitHub OAuth

The system supports GitHub OAuth for login, registration, and account binding.

#### Authorization Flow

```
Client → GET /auth/oauth/github/authorize?action=login
         │
         ▼
Server generates CSRF state, returns GitHub authorization URL
         │
         ▼
Client redirects user to GitHub
         │
         ▼
User authorizes on GitHub
         │
         ▼
GitHub redirects to /auth/oauth/github/callback?code=xxx&state=xxx
         │
         ▼
Server validates state → exchanges code → gets user info
         │
         ▼
Server 302 redirects to {frontendUrl}/oauth/callback#accessToken=...&refreshToken=...
```

#### OAuth Actions

| Action  | Endpoint                      | Auth Required | Description                          |
|---------|-------------------------------|---------------|--------------------------------------|
| `login` | `GET /authorize?action=login` | No            | Login or register via GitHub         |
| `bind`  | `GET /authorize?action=bind`  | Yes           | Link GitHub account to existing user |

#### Token Storage (Encrypted)

OAuth access tokens are encrypted at rest using AES-256-GCM:

| Property  | Value                            |
|-----------|----------------------------------|
| Algorithm | AES/GCM/NoPadding                |
| Key size  | 256 bits (32 bytes)              |
| IV size   | 12 bytes (random per encryption) |
| Tag size  | 128 bits                         |
| Encoding  | Base64                           |

The encryption key is configured via `ctt.security.oauth.token-encryption-key` (Base64-encoded 32 bytes).

```java
// AesGcmTokenEncryptor.java
byte[] iv = new byte[12];
secureRandom.nextBytes(iv);
Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
byte[] cipherTextWithTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
```

Encryption is transparent via JPA `AttributeConverter` (`OAuthTokenConverter`), so entity code does not need to handle encryption explicitly.

#### Account Binding/Unbinding

| Endpoint                          | Method | Description               |
|-----------------------------------|--------|---------------------------|
| `/auth/oauth/accounts`            | GET    | List bound OAuth accounts |
| `/auth/oauth/accounts/{provider}` | DELETE | Unbind OAuth provider     |

Unbinding fails with 409 Conflict if it would leave the user with no login method (no password set and no other OAuth providers).

---

## Email Security

### Email Verification

Email verification uses one-time tokens with SHA-256 hashing.

| Property       | Value                               |
|----------------|-------------------------------------|
| Token size     | Random bytes                        |
| Hash algorithm | SHA-256                             |
| TTL            | 24 hours                            |
| Storage        | `email_verification_tokens` table   |
| Purpose        | `REGISTER_VERIFY` or `CHANGE_EMAIL` |

Key security decisions:
- Raw tokens never stored in database (only SHA-256 hashes)
- Token status derived dynamically from timestamps (`consumedAt`, `revokedAt`, `expiresAt`)
- One-time use: token consumed on first verification, all other user tokens revoked

### Email Change

The email change flow requires password verification (unless the user is OAuth-only):

```
POST /users/me/email/change-request {newEmail, password?}
        │
        ▼
System sends verification to new email address
        │
        ▼
User clicks verification link
        │
        ▼
POST /users/me/email/change-confirm {token}
        │
        ▼
Email changed, verification reset
```

Security measures:
- Password required if user has password set
- Token expires in 1 hour
- Max 5 verification attempts per token
- Rate limited: 3 requests per 10 minutes per email
- Only one pending email change at a time

---

## Idempotency

### Two-Layer Strategy

The system uses two complementary idempotency mechanisms:

#### Layer 1: `@Idempotent` Annotation (Controller Layer)

Uses Redis SETNX for distributed locking. Returns 409 Conflict on duplicate requests.

```java
@PostMapping("/sync/push")
@Idempotent(
    prefix = "SYNC_PUSH",
    keyExpression = "#request.syncCursor",
    includeUserId = true,
    expireSeconds = 30,
    message = "Sync request is being processed")
public ApiResponse<Void> pushData(@RequestBody SyncPayload request) { ... }
```

#### Layer 2: Business-Layer Idempotent Window (Service Layer)

Checks DB for recent records with same business key. Returns 200 OK with `idempotentSkip=true` on duplicates.

Used for email operations (resend verification, password reset) to prevent email bombing while providing a better user experience.

| Mechanism      | Duplicate Response   | Use Case                     |
|----------------|----------------------|------------------------------|
| `@Idempotent`  | 409 Conflict         | Sync push, API key creation  |
| Business-layer | 200 OK (silent skip) | Email resend, password reset |

---

## Terms Acceptance

Users must accept the current terms version before accessing protected endpoints.

### Enforcement

A `TermsCheckFilter` runs after JWT authentication and checks the `termsVersion` claim in the JWT against the configured current version.

- If versions match: request proceeds
- If versions mismatch: 403 Forbidden with `AUTH_019` error code
- Public endpoints are exempt from terms checking

### Configuration

```yaml
ctt:
  terms:
    current-version: "1.0.0"
    terms-accept-path: "/api/v1/auth/terms/accept"
```

---

## Configuration Reference

### SecurityProperties

All security configuration is consolidated in `SecurityProperties` (`ctt.security.*`):

```yaml
ctt:
  security:
    jwt:
      secret-key: ${JWT_SECRET_KEY}          # Required, 256+ bits
      issuer: "ctt-identity-provider"
      access-token-ttl: 15m
      refresh-token-ttl-plugin: 14d
      refresh-token-ttl-web: 30d

    password:
      bcrypt-rounds: 12                      # Min: 10
      max-failed-attempts: 5
      lock-duration: 30m
      failure-window-seconds: 900            # 15 minutes
      retention-duration: PT720H             # 30 days
      storage: DB                            # or REDIS

    rate-limit:
      enabled: true
      global-max-requests-per-second: 200

    cors:
      allowed-origins: ["https://ctt.example.com"]
      allowed-origin-patterns: ["https://ctt.example.com"]
      allowed-methods: [GET, POST, PUT, DELETE, PATCH, OPTIONS]
      allowed-headers: [Authorization, Content-Type, X-Requested-With, Accept, Origin]
      exposed-headers: [Authorization]
      allow-credentials: true
      max-age: 3600

    oauth:
      frontend-url: "${FRONTEND_BASE_URL}"
      token-encryption-key: "${OAUTH_TOKEN_ENCRYPTION_KEY}"  # Base64, 32 bytes
      github:
        client-id: "${GITHUB_CLIENT_ID}"
        client-secret: "${GITHUB_CLIENT_SECRET}"

    cookie:
      refresh-token-path: /api/v1/auth/refresh

    hcaptcha:
      site-key: "${HCAPTCHA_SITE_KEY}"
      secret-key: "${HCAPTCHA_SECRET_KEY}"
      verify-url: https://api.hcaptcha.com/siteverify
      timeout: 5s
```

### Environment Variables

| Variable                     | Required   | Default                 | Description                                     |
|------------------------------|------------|-------------------------|-------------------------------------------------|
| `JWT_SECRET_KEY`             | All profiles | none                    | JWT signing key (256+ bits)                     |
| `OAUTH_TOKEN_ENCRYPTION_KEY` | All profiles | none                    | AES-256 key for OAuth tokens (Base64, 32 bytes) |
| `TRUSTED_PROXY_ADDRESSES`    | Behind proxy | empty                   | Exact proxy IPs allowed to supply `X-Forwarded-For` |
| `GITHUB_CLIENT_ID`           | OAuth      | empty                   | GitHub OAuth app client ID                      |
| `GITHUB_CLIENT_SECRET`       | OAuth      | empty                   | GitHub OAuth app client secret                  |
| `HCAPTCHA_SITE_KEY`          | Production | empty                   | hCaptcha site key (blank = captcha disabled)    |
| `HCAPTCHA_SECRET_KEY`        | Production | empty                   | hCaptcha secret key                             |
| `FRONTEND_BASE_URL`          | Production | `http://localhost:5173` | Frontend URL for OAuth redirects                |

---

## Frontend Integration Guide

### Required Headers

All authenticated requests must include:

```
Cookie: ctt_access_token=<jwt>
X-XSRF-TOKEN: <csrf-token-from-cookie>
Content-Type: application/json
```

### Cookie Handling

The server sets authentication cookies automatically on login/refresh. The frontend must:

1. Include `credentials: 'include'` in fetch requests (or `withCredentials: true` in Axios)
2. Read `XSRF-TOKEN` cookie and send it as `X-XSRF-TOKEN` header
3. Handle cookie expiration by calling `POST /auth/refresh` when access token expires

### Error Handling

| HTTP Status | Error Code       | Meaning                | Frontend Action                   |
|-------------|------------------|------------------------|-----------------------------------|
| 401         | `AUTH_001`       | Missing or invalid JWT | Redirect to login                 |
| 401         | `AUTH_002`       | Token expired          | Call refresh endpoint             |
| 401         | `AUTH_003`       | Invalid token          | Redirect to login                 |
| 403         | `AUTH_004`       | Account locked         | Show countdown with `retryAfter`  |
| 403         | `AUTH_019`       | Terms version expired  | Show terms acceptance dialog      |
| 403         | `USER_008`       | Terms not accepted     | Show terms acceptance dialog      |
| 409         | `COMMON_003`     | Duplicate request      | Show "already processing" message |
| 429         | `RATE_LIMIT_001` | Rate limited           | Show retry message                |

### Refresh Token Flow

```javascript
async function refreshAccessToken() {
    const response = await fetch('/ctt-server/api/v1/auth/refresh', {
        method: 'POST',
        credentials: 'include',  // Send refresh token cookie
        headers: {
            'X-XSRF-TOKEN': getCsrfToken()
        }
    });

    if (!response.ok) {
        // Refresh failed, redirect to login
        window.location.href = '/login';
        return;
    }

    // New access token cookie is set automatically
    return response.json();
}
```

---

## Security Best Practices

### For Developers

- **Never store raw tokens**: All tokens (email verification, password reset, refresh) are SHA-256 hashed before storage
- **Use `@PublicApi` sparingly**: Every public endpoint is an attack surface. Document the reason.
- **Apply rate limiting**: Use `@RateLimit` on all endpoints. Prefer multi-dimensional limits for sensitive operations.
- **Validate CSRF**: Include `X-XSRF-TOKEN` header on all mutating requests from browser clients.
- **Mask sensitive data**: Never log passwords, tokens, or secrets, and never put them or request bodies into audit details. See [Audit Storage Boundary](#audit-storage-boundary).
- **Test authorization**: Verify that users cannot access other users' resources (BOLA protection).
- **Use `CurrentUserProvider`**: Never extract user identity directly from JWT in business code.

### For Operations

- **Rotate secrets**: Rotate `JWT_SECRET_KEY` and `OAUTH_TOKEN_ENCRYPTION_KEY` periodically. Invalidates all existing tokens.
- **Monitor audit logs**: Watch for `ACCOUNT_LOCKED`, `RATE_LIMIT_EXCEEDED`, and `UNAUTHORIZED_ACCESS` events.
- **Set HSTS in production**: The default HSTS header is set by the application. Ensure your reverse proxy does not strip it.
- **Use HTTPS**: All cookies have `Secure=true`. The application must be served over HTTPS in production.
- **Configure CORS restrictively**: Never use wildcard origins with `allowCredentials=true`.
- **Backup encryption keys**: Loss of `OAUTH_TOKEN_ENCRYPTION_KEY` makes all stored OAuth tokens unrecoverable.

---

## Related Documentation

- [Developer Handbook](developer-handbook.md) - Adding error codes, audit events, exceptions, and protected APIs
- [API Governance](api-governance.md) - API security tiers, rate limiting, and idempotency guidelines
- [Audit Boundary Spec](audit-boundary-spec.md) - Audit event boundaries and specifications
