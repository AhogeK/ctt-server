# api-contract — meta

## Boundary

The externally observable HTTP contract: response envelopes, error codes, authentication scopes,
rate limiting, idempotency, and the OpenAPI annotations that document all of it.

**In scope**: envelope shapes, error-code allocation and reuse, HTTP status mapping, scope
requirements, rate-limit and retry semantics, `@Schema`/`@ApiResponses` rules, endpoint-family
consistency (siblings with identical parameters must document the same codes).

**Out of scope**: the business rules behind each endpoint (their own domains), security headers and
filter-chain wiring (cross-cutting, `systemPatterns.md`), DB schema.

## Owned paths

- `src/main/java/com/ahogek/cttserver/common/exception/` — `ErrorCode`, exception types, global handler
- `src/main/java/com/ahogek/cttserver/common/response/` — `RestApiResponse`, `ErrorResponse`
- `src/main/java/com/ahogek/cttserver/common/ratelimit/`, `common/idempotent/` — the declarative
  frameworks every endpoint composes
- `@RequiresApiKeyScope` + `ApiKeyScope` — scope enforcement
- Every `*Controller.java` — the annotations that render the contract

## Where to start

1. `common/exception/ErrorCode.java` — the single registry; check it before inventing a code.
2. `common/exception/GlobalExceptionHandler.java` — status mapping and response shaping.
3. `common/response/RestApiResponse.java` / `ErrorResponse.java` — the two envelopes.
4. Any controller — the annotation style that all others must match.

## Terminology

| Term | Meaning |
| --- | --- |
| **Envelope** | `RestApiResponse`: `success`, `message`, `data`, `timestamp` |
| **Error body** | `ErrorResponse`: `code`, `message`, `details[]`, `traceId`, `httpStatus`, `timestamp` |
| **Scope** | API-key permission (`READ`, `WRITE`, `SYNC`, `ADMIN`); JWT users bypass scope checks |
| **Public API** | Endpoint explicitly marked `@PublicApi`; the registry drives the security whitelist |
| **Declarative governance** | `@RateLimit` / `@Idempotent` annotations resolved through SpEL |
| **Sibling endpoint** | Same shape and parameters as another endpoint (e.g. the stats reads) |
| **Trace id** | Correlation id echoed in error bodies for support |
