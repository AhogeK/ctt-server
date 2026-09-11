# api-contract — references

## Error code families

| Prefix | Count | Area |
| --- | --- | --- |
| `AUTH_` | 24 | Authentication, API keys, OAuth, scopes |
| `USER_` | 14 | User profile, email change, password |
| `MAIL_` | 8 | Outbox delivery, rate limits |
| `SECURITY_` | 7 | Captcha, terms, request hardening |
| `COMMON_` | 6 | Shared: validation, not-found, illegal argument |
| `SYSTEM_` | 5 | Unexpected server failures |
| `RATE_LIMIT_` | 3 | Throttling |
| `LEADERBOARD_` | 2 | Ranking |

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
