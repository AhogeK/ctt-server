# auth-lifecycle — practices

## Login metadata: which flows write what

| Flow | `lastLoginAt` | `lastLoginIp` | Where |
| --- | --- | --- | --- |
| Password login | `Instant.now()` | `RequestContext.current().map(RequestInfo::clientIp)` | `UserLoginService.login()` |
| OAuth login, existing binding | `Instant.now()` | from the state payload (captured at authorize) | `OAuthLoginOrRegisterService.handleExistingBinding()` |
| OAuth first-time registration | `Instant.now()` | from the state payload | `OAuthLoginOrRegisterService.registerNewUser()` |
| BIND | not set (not a login) | not set | — |
| UNBIND | not set (not a login) | not set | — |

**Checklist for any new login/registration flow**: does it authenticate a user? If yes, set both
fields. The OAuth callback captures the client IP into the state payload at authorize time, because
the callback request arrives from GitHub, not from the user.

## OAuth state payload

```java
record OAuthStatePayload(Action action, String nonce, UUID currentUserId) {
    // canonical constructor: BIND requires currentUserId, LOGIN forbids it
}
```

The state is stored in Redis with a short TTL and consumed once. Both the authorize and callback
endpoints switch on `action`; a new action must appear in both.

## Password ordering rules

Order matters — each check assumes the previous one passed:

1. User exists → else `USER_004`
2. User has a password → else `USER_015` (defensive: the set-password endpoint owns that case)
3. Current password matches → else `USER_014`
4. New password differs from the old → else `PASSWORD_SAME_AS_OLD`
5. Encode, save, audit `PASSWORD_CHANGED`

Passwords arrive base64-encoded from the frontend and are stored/compared as-is; do not "helpfully"
decode them server-side.

## Lockout mechanics

- Attempts are recorded in `login_attempts` with **hashed** email and IP (SHA-256) — never plaintext.
- A locked response is `403` carrying `retryAfter` (ISO-8601) and a `Retry-After` header, so the UI can
  count down with the same code path as rate limiting.
- The hourly sweep unlocks accounts whose window has fully drained; it complements, never replaces,
  the lazy check.

## Captcha

`verifyCaptcha(token)` runs **before** any persistence work. Unconfigured secret → warn and pass.
Configured: blank token → `SECURITY_006`; failed verification → `SECURITY_006` with the provider's
error codes; provider unreachable → `BadGateway`, not a validation error (the user did nothing wrong).
For local end-to-end probes, the provider's documented always-pass test key pair works without
touching code.

## Test recipe

1. Flow ordering: a test per guard (each error code reachable independently).
2. Anti-enumeration: same response for existing and non-existent addresses.
3. Token single-use: consume twice, second attempt fails.
4. Kill switch: after reset, a previously issued refresh token is rejected.
5. Action coverage: adding an enum value without its controller branch must fail a test — assert the
   unhandled case explicitly rather than relying on the happy path.
