# auth-lifecycle — references

## Endpoints

| Endpoint | Method | Notes | Rate |
| --- | --- | --- | --- |
| `/api/v1/auth/register` | POST | Captcha-gated; sends verification mail | 60/hour per IP |
| `/api/v1/auth/login` | POST | Issues access + refresh; sets login metadata | 30/hour per IP |
| `/api/v1/auth/refresh` | POST | Rotating refresh; reuse is detected | — |
| `/api/v1/auth/verify-email` | GET | Public; 24h token TTL | — |
| `/api/v1/auth/resend-verification` | POST | Anti-enumeration | 3/min per email |
| `/api/v1/auth/logout` | POST | Revokes the presented refresh token; idempotent | — |
| `/api/v1/auth/logout-all` | POST | Kill switch: revokes every session | 5/min per user |
| `/api/v1/auth/password-reset/request` | POST | Anti-enumeration; mail outbox delivery | 3/10min per email |
| `/api/v1/auth/password-reset/confirm` | POST | Revokes sessions; unlocks the account | 15/10min per IP |
| `/api/v1/auth/terms/accept` | POST | Returns a fresh token carrying the new version | — |
| `/api/v1/auth/oauth/github/authorize` | GET | `?action=login` (public) or `action=bind` (JWT) | — |
| `/api/v1/auth/oauth/github/callback` | GET | Validates state → token exchange → login/register → 302 | 60/hour |
| `/api/v1/auth/oauth/accounts` | GET | Lists bindings; never exposes provider tokens | — |
| `/api/v1/auth/oauth/accounts/{provider}` | DELETE | Unbind; 409 if it would remove the last login method | — |
| `/api/v1/users/me/password/set` | POST | For users without a password | 5/min per user |
| `/api/v1/users/me/password/change` | POST | For users with a password | 5/min per user |
| `/api/v1/users/me/email/change-request` | POST | Password required if one is set | 3/10min per email |
| `/api/v1/users/me/email/change-confirm` | POST | Public; 1h token TTL, max 5 attempts | 15/10min per IP |
| `/api/v1/users/me/email/change-request` | DELETE | Cancels a pending change | — |
| `/api/v1/users/me/email/resend-verification` | POST | 1/60s per user | — |
| `/api/v1/users/me/email/status` | GET | Pending-change info | — |

## Error codes owned here

| Code | HTTP | Scenario |
| --- | --- | --- |
| `AUTH_001`–`AUTH_009` | varies | Login/registration/verification family |
| `AUTH_013`–`AUTH_019` | varies | OAuth binding, refresh, terms |
| `AUTH_022` | varies | Scope/authorisation adjunct |
| `USER_004` | 404 | User not found |
| `USER_014` | 401 | Current password wrong |
| `USER_015` | 409 | User has no password (defensive) |
| `PASSWORD_SAME_AS_OLD` | 409 | New password equals the old one |
| `SECURITY_006` | 403 | Captcha verification failed / token missing |

## Configuration

| Key | Default | Meaning |
| --- | --- | --- |
| `ctt.security.password.max-failed-attempts` | 5 | Attempts allowed in the window |
| `ctt.security.password.failure-window-seconds` | 900 | Sliding window (15 min) |
| `ctt.security.password.lock-duration` | 30m | Lockout length |
| `ctt.security.password.retention-duration` | PT720H | Attempt-record retention (30 days) |
| `ctt.security.password.storage` | `DB` | Attempt storage backend |
| `ctt.security.password.bcrypt-rounds` | 12 | Hash cost (minimum 10 enforced) |
| `ctt.security.jwt.access-token-ttl` | 15m | Access token lifetime |
| `ctt.security.jwt.refresh-token-ttl-plugin` | 14d | Plugin refresh lifetime |
| `ctt.security.jwt.refresh-token-ttl-web` | 30d | Web refresh lifetime |
| `ctt.security.hcaptcha.site-key` / `secret-key` | empty | Empty ⇒ verification skipped (local) |
| `ctt.terms.current-version` | `1.0.0` | Version gating protected endpoints |

## Audit actions

`ACCOUNT_LOCKED`, `ACCOUNT_UNLOCKED`, `PASSWORD_CHANGED`, `PASSWORD_RESET_*`, `API_KEY_*`,
`EMAIL_CHANGE_*`, `OAUTH_*`, `TERMS_ACCEPTED`.

## Code map

| Concern | File |
| --- | --- |
| OAuth state contract | `auth/oauth/model/OAuthStatePayload.java` |
| OAuth callback branches | `auth/oauth/controller/OAuthCallbackController.java` |
| Login/register via OAuth | `auth/oauth/service/OAuthLoginOrRegisterService.java` |
| Password login + metadata | `auth/service/UserLoginService.java` |
| Password set/change | `user/service/` (password service) |
| Email change | `user/service/` (email change service) |
| Captcha | `auth/captcha/CaptchaService.java` |
| Lockout | `auth/service/` (login attempt service) + `login_attempts` table |
| Terms gate | filter + `ctt.terms.*` config |
