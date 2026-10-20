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

## Profile writes stay out of the read-only projection

`UserProfileService` is read-only by contract (its class Javadoc says so) and stays that way: the
display-name update lives in `UserService`, whose job is already "validate + persist + audit +
transaction" — the shape `registerUser` and `acceptTermsAndIssueTokens` share. Any future profile
write follows the same split, and the controller composes the two: write first, then read the
projection from its single source, so the body a client syncs from matches what was persisted.

- A submission equal to the stored value is a successful no-op — no write, **no audit row** — so the
  trail contains only real changes.
- A real change emits one `PROFILE_UPDATED` (IAM group, `ResourceType.USER`) carrying `field`,
  `oldValue` and `newValue`. `audit_logs.action` has **no** CHECK constraint (only `resource_type`
  and `severity` do), so a new action needs no migration.
- Display names are **not unique**: the schema has no constraint and nothing resolves a user by name.
  `USER_005` ("Display name already taken") therefore stays unused until a real identifier — not a
  label — is introduced.

## A session is a login, and its audience is decided by the registry

`refresh_tokens.session_id` is minted once per login and carried by every row rotation produces, so
"one login = one session" survives refreshing. The access token names its session through the `sid`
claim, and `CurrentUser.sessionId` is null for API-key principals and for tokens minted outside a
session (the claim is then omitted entirely).

Audience (`WEB` / `PLUGIN`) and its TTL are decided **at issuance**, and the discriminator is the
device registry — not the presence of a device id. `LoginRequest.deviceId` is `@NotBlank`, so every
login carries one, and neither client sends the platform/IDE headers that `ClientIdentity` models.
`devices` is the server-side statement of "this client is an IDE plugin", which is why
`refreshTokenTtlPlugin` exists at all.

**Rotation and every later re-issuance continue the session instead of re-deciding.** Rotation
inherits audience, device binding, TTL and client User-Agent from the token being rotated, and
`UserLoginService.issueTokens` — the path behind terms acceptance and OAuth callbacks — looks up the
session's live row and carries the same four attributes. Only a session with no live row (a fresh
login) decides from the registry. The regression that forced this rule: terms acceptance used to
re-issue with a null device id, so a plugin session silently became a `WEB`/30-day row on the same
`session_id`, which would have shown up in the session list as a browser session. Covered by
`shouldKeepPluginAudience_whenAcceptingTerms`.

Consequence worth knowing: a plugin that logs in **before** registering its device is recorded as a
web session, and every later issuance on that session keeps that audience.

### Revoking a session, and what it must not do

`DELETE /users/me/sessions/{id}` revokes every live row of the session and is serialized against
rotation by a session-scoped advisory lock (`pg_advisory_xact_lock`) that **both** writers take
before touching rows — without it, a rotation starting mid-revocation inserts its successor after
the revoking update has already scanned the session, and the kicked session survives. A foreign or
unknown session is answered `404 COMMON_002` by the same user-scoped update, so the endpoint never
confirms that somebody else's session exists.

A kicked client does not know it was kicked and keeps refreshing. That must not read as theft:
reuse detection fires only when the session still has a live row (the replayed token is then a
rotated one), while a token whose session has no live row gets a plain `401 AUTH_003`. Getting this
wrong turns "sign out one device" into a sign-out everywhere.

Listing reports one entry per login: `createdAt` / `lastUsedAt` / `expiresAt` aggregate over
**every** row of the session (rotation revokes the previous one, so a live-only aggregate would
report a refreshed session as never used), while audience, device binding and User-Agent come from
the newest live row. Nullable session fields are always serialized, so a client can tell "a browser
session has no device" from "this client predates the field".

### Locking is about visibility, not just ordering

Taking the lock first is not enough. If a refresh decides the token's status **before** acquiring
the session lock, it may act on a snapshot that predates a revocation committing right then; the
lock only serializes the writes, not the decision. The order is always **lock → re-read → decide →
write** — re-reading (`entityManager.refresh`) after the lock is what makes a kick final. Measured
on 2026-10-19: with the lock but without the re-read, a refresh racing a revocation still succeeded
(200 instead of `401 AUTH_003`); the deterministic two-thread test now pins the corrected order.

## Test recipe

1. Flow ordering: a test per guard (each error code reachable independently).
2. Anti-enumeration: same response for existing and non-existent addresses.
3. Token single-use: consume twice, second attempt fails.
4. Kill switch: after reset, a previously issued refresh token is rejected.
5. Action coverage: adding an enum value without its controller branch must fail a test — assert the
   unhandled case explicitly rather than relying on the happy path.
