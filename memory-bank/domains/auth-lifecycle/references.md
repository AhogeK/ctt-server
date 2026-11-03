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
| Passkey challenge | `auth/passkey/service/PasskeyChallengeService.java` |
| Passkey registration | `auth/passkey/service/PasskeyRegistrationService.java` |
| Passkey assertion/login | `auth/passkey/service/PasskeyAuthenticationService.java` |
| Passkey credential store | `auth/passkey/service/PasskeyCredentialRepository.java` + `user_passkeys` table |
| Passkey notification | `auth/passkey/service/PasskeyNotificationService.java` (`REQUIRES_NEW`) |
| Passkey second-factor API | `auth/passkey/controller/PasskeySecondFactorController.java` |
| Passkey second-factor service | `auth/passkey/service/PasskeyAuthenticationService.java` (`startSecondFactor`, `finishSecondFactor`) |

## Two-factor endpoints

| Endpoint | Method | Notes | Rate |
| --- | --- | --- | --- |
| `/api/v1/users/me/2fa` | GET | `enabled/confirmedAt/recoveryCodesRemaining/hasPassword/recentAuthentication` | — |
| `/api/v1/users/me/2fa/totp/start` | POST | Pending secret; password or recent session | 5/hour USER |
| `/api/v1/users/me/2fa/totp/confirm` | POST | Returns 10 recovery codes once | 5/5min USER |
| `/api/v1/users/me/2fa/disable` | POST | TOTP or recovery code; revokes other sessions | 5/hour USER |
| `/api/v1/users/me/2fa/recovery-codes/regenerate` | POST | Replaces the batch | 3/hour USER |
| `/api/v1/auth/login/2fa` | POST | Challenge + TOTP/recovery code | 10/5min IP |

**Code**: `auth/twofactor/` (`TwoFactorService`, `LoginTwoFactorService`,
`TwoFactorChallengeService`, `RecoveryCodeService`, `SecondFactorVerifier`, `TotpService`,
`TotpSecretCipher`). `SecondFactorVerifier` is the single place that decides what counts as a
valid second factor, so the login path and `/users/me/2fa/*` cannot drift apart.

## Passkey endpoints

| Endpoint | Method | Notes | Rate |
| --- | --- | --- | --- |
| `/api/v1/auth/passkey/options` | POST | Anonymous discoverable assertion options; empty `allowCredentials` | 30/5min IP |
| `/api/v1/auth/passkey/assert` | POST | Assertion + `LoginResponse` + cookies; `AUTH_032`/`AUTH_033` | 20/5min IP |
| `/api/v1/users/me/passkey/options` | POST | Registration options; recent auth (`AUTH_031`); browser session only | 10/hour USER |
| `/api/v1/users/me/passkey` | POST | Complete registration; `201`; `AUTH_034` on duplicate | 10/hour USER |
| `/api/v1/users/me/passkey` | GET | List credentials; no public key or credential ID exposure beyond metadata | — |
| `/api/v1/users/me/passkey/{id}` | PATCH | Rename the label | 20/hour USER |
| `/api/v1/users/me/passkey/{id}` | DELETE | Delete; recent auth; `AUTH_035` when not owned | 10/hour USER |

**Config**: `PASSKEY_RP_ID`, `PASSKEY_ALLOWED_ORIGINS` (exact origins), optional relying-party name.
**Errors**: `AUTH_032` challenge invalid/expired, `AUTH_033` assertion failed, `AUTH_034` credential
already registered, `AUTH_035` not found/not owned; `AUTH_025` for API-key callers.

## Second-factor passkey endpoints

| Endpoint | Method | Notes | Rate |
| --- | --- | --- | --- |
| `/api/v1/auth/2fa/passkey/options` | POST | Body `{challengeToken}`; account-bound `allowCredentials`; peeks 2FA challenge | 20/5min IP |
| `/api/v1/auth/2fa/passkey/assert` | POST | Body `{challengeToken, credential}`; consumes WebAuthn challenge; login challenge only on success | 10/5min IP |

**Error mapping**: `AUTH_026` login challenge invalid/expired; `AUTH_032` WebAuthn challenge invalid,
expired, consumed or binding mismatch; `AUTH_033` assertion failed/no passkey.
**Source**: `PasskeySecondFactorController`, `PasskeyAuthenticationService.startSecondFactor` /
`finishSecondFactor`; `PasskeyChallengeService.Operation.SECOND_FACTOR`.
**Response**: same `LoginResponse`, cookies and `Authorization` as `/auth/login/2fa`.
