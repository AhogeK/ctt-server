# api-contract — references

## Error code families

| Prefix | Count | Area |
| --- | --- | --- |
| `AUTH_` | 25 | Authentication, API keys, OAuth, scopes |
| `USER_` | 14 | User profile, email change, password |
| `MAIL_` | 8 | Outbox delivery, rate limits |
| `SECURITY_` | 7 | Captcha, terms, request hardening |
| `COMMON_` | 6 | Shared: validation, not-found, illegal argument |
| `SYSTEM_` | 5 | Unexpected server failures |
| `RATE_LIMIT_` | 3 | Throttling |
| `LEADERBOARD_` | 2 | Ranking |
| `DEVICE_` | 1 | Device registration conflicts |

Counts verified against `ErrorCode` on 2026-10-28 (68 codes, v0.87.2).

## Frequently used codes

| Code | HTTP | Meaning |
| --- | --- | --- |
| `COMMON_002` | 404 | Not found **or** not owned (device, IDE) |
| `COMMON_003` | 400 | Validation error (including cross-field rules) |
| `AUTH_010` | 401 | API key invalid / not found / BOLA |
| `AUTH_011` | 401 | API key expired |
| `AUTH_012` | 403 | API key revoked |
| `AUTH_020` | 403 | Missing required scope |
| `AUTH_021` | 401 | Malformed auth header |
| `AUTH_023` | 409 | Active key must be revoked before deletion |
| `AUTH_024` | 409 | Per-user active key limit reached |
| `RATE_LIMIT_001` | 429 | Rate limit exceeded (carries `retryAfter` + header) |
| `DEVICE_001` | 409 | Device already registered to another user |
| `SECURITY_006` | 403 | Captcha verification failed / token missing |

## Account & security endpoints (verified 2026-10-28, v0.87.2)

| Endpoint | Contract facts |
| --- | --- |
| `PATCH /api/v1/users/me` | body `{displayName}` (2–50, shared registration rule) · responds with the re-read `UserProfileResponse` · same name = no-op (no write, no audit) · 10/hour per user |
| `GET /api/v1/users/me/sessions` | `data` is the **array** (no `{sessions: []}` envelope) · `limit` default 20, clamped 1..100 · sorted by `lastUsedAt` desc with nulls last, then `createdAt` desc · 60/hour |
| `DELETE /api/v1/users/me/sessions/{id}` | 204, no body · revokes every live token of that session · revoking the caller's own session is allowed · foreign/unknown → 404 `COMMON_002` · 10/hour |
| `POST /api/v1/users/me/sessions/revoke` | body `{sessionIds}` · duplicates ignored, ≤100 distinct (more → 400 `COMMON_003`) · partial success: unknown/already-revoked/foreign ids in `skipped`, response 200 `{revoked, skipped}` · one `SESSION_REVOKED` audit per revoked session · locks in ascending id order · 10 calls/hour per user · counter is per endpoint (`UserController.revokeLoginSessions`), so it never shares the 10/hour of the single-session revocation |
| `GET /api/v1/users/me/export` | streaming JSON · `timezoneOffset` required, ±720, out of range → 400 `COMMON_003` · `Content-Disposition: attachment; filename="ctt-export-<yyyy-MM-dd>.json"` (local date in the caller's offset) · 3/hour |

Introduced in v0.80.0 (display name), v0.82.0 (login sessions) and v0.83.0 (data export), so any
deployment at or above those versions carries them. Attribution rule: the first release that contains
a feature is the **first `chore: bump version` commit after the feature commit** — the `appVersion`
inside the feature commit itself is still the previous release (2026-10-28: reading it directly gave
0.79/0.81/0.82 instead of 0.80/0.82/0.83).

## Scopes

| Scope | Grants |
| --- | --- |
| `READ` | Statistics, device listing, profile reads |
| `WRITE` | Device revocation, mutating non-sync resources |
| `SYNC` | Device registration, pull, push |
| `ADMIN` | Superset used for administrative keys |

JWT-authenticated users bypass scope checks entirely.

## Declarative governance

| Annotation | Dimensions | Notes |
| --- | --- | --- |
| `@RateLimit` | `IP`, `USER`, `EMAIL`, `API` | Redis Lua atomic check; returns allowed + TTL |
| `@Idempotent` | SpEL-resolved key | Prevents duplicate side effects |
| `@PublicApi` | — | Registers the endpoint in the public whitelist |
| `@RequiresApiKeyScope` | scope(s) | Any-of semantics when several are listed |

## Envelope classes

| Class | Fields |
| --- | --- |
| `RestApiResponse<T>` | `success`, `message`, `data`, `timestamp` |
| `ErrorResponse` | `code`, `message`, `details[]`, `traceId`, `httpStatus`, `timestamp` |

## Retry contract

429 responses always carry both:
- `Retry-After` header — delta seconds
- `retryAfter` body field — ISO-8601 instant

Account-lockout (`403`) reuses the same dual signalling so clients have one retry path.

## Code map

| Concern | File |
| --- | --- |
| Code registry | `common/exception/ErrorCode.java` |
| Status mapping + shaping | `common/exception/GlobalExceptionHandler.java` |
| Envelopes | `common/response/RestApiResponse.java`, `ErrorResponse.java` |
| Rate limiting | `common/ratelimit/` |
| Idempotency | `common/idempotent/` |
| Scope enforcement | `apikey/` (`@RequiresApiKeyScope`, `ApiKeyScopeAspect`) |
| Public whitelist | `common/` (`@PublicApi`, `PublicApiEndpointRegistry`) |
