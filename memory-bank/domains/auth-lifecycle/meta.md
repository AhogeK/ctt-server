# auth-lifecycle — meta

## Boundary

Identity and credential flows: JWT issuance/refresh, API-key authentication, GitHub OAuth login and
binding, email verification, email change, password reset/set/change, terms acceptance, and
account-lockout/rate-limit defence on those flows.

**In scope**: flow ordering and guards, state machines (token status, user status), OAuth state
payload semantics, login metadata, lockout thresholds, the audit events each flow must emit.

**Out of scope**: how keys are hashed and stored at rest (`apikey/` internals belong to
`api-contract` for its surface), HTTP envelope/error-code policy (`api-contract`), scope checks.

## Owned paths

- `src/main/java/com/ahogek/cttserver/auth/` — login, refresh, verification, password reset, OAuth
- `src/main/java/com/ahogek/cttserver/user/` — profile, email change, password set/change
- `src/main/java/com/ahogek/cttserver/auth/captcha/` — hCaptcha verification
- Terms acceptance (filter + controller) and the lockout storage/service

## Where to start

1. `auth/oauth/model/OAuthStatePayload.java` — the LOGIN vs BIND contract and its invariants.
2. `auth/oauth/controller/OAuthCallbackController.java` — where every Action branch is realised.
3. `user/service/` — password and email-change flows with their ordering rules.
4. `common/exception/ErrorCode.java` — the `AUTH_`/`USER_`/`SECURITY_` families this domain owns.

## Terminology

| Term | Meaning |
| --- | --- |
| **Action** | OAuth state discriminator: `LOGIN` (unauthenticated) or `BIND` (authenticated linking) |
| **State** | Short-lived Redis payload carrying CSRF nonce + action + initiating user |
| **Session invariant** | BIND must never mint tokens; LOGIN always mints fresh tokens |
| **Kill switch** | Revoking every active session for a user (logout-all, password reset) |
| **Lazy unlock** | Lockout expiry evaluated on the next attempt rather than by a timer |
| **Hybrid unlock** | Lazy check plus an hourly sweep for accounts nobody retries |
| **Terms version** | Version string carried in the JWT; a stale version gates protected endpoints |
| **Login metadata** | `lastLoginAt` / `lastLoginIp`, updated on every successful login |
