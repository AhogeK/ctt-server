# api-contract — principles

## 1. Reuse error codes; never allocate a near-duplicate

`ErrorCode` is a registry, not a scratch pad. Before adding a code, search for one that already
carries the meaning — including a code whose message is slightly broader. A new code is justified
only when an existing one would mislead a client into the wrong branch. Two codes that differ only by
which internal path raised them are one code.

## 2. HTTP status comes from the code, not from the call site

Each code owns its status (401 unauthenticated, 403 authorized-but-forbidden, 404 not-found-or-not-yours,
409 conflict, 400 validation, 429 throttled). Throw the code and let the global handler map it; a
controller that picks a status by hand diverges from the registry.

## 3. Not-found and not-yours are indistinguishable

Resource ownership failures answer exactly like missing resources (`404 COMMON_002`). Distinguishing
them leaks existence across accounts and turns an id into an oracle.

## 4. Every endpoint declares its failure surface

An endpoint's `@ApiResponses` must cover every status it can emit, and **sibling endpoints with the
same parameters must declare the same set**. If the stats reads all take `deviceId`/`ideName`, they all
document the 404 path — a subset is a documentation defect even when the code is correct. The only
acceptable omission is a code the sibling family also omits.

## 5. Error examples must be complete and distinct

Every error response carries its own example, and each example is a full `ErrorResponse` shape
(`code`, `message`, `details`, `traceId`, `httpStatus`, `timestamp`) — a bare `{code, message}` snippet
teaches clients the wrong shape. `@ApiResponse` content is mandatory, never implied.

## 6. Schema annotations are part of the contract

Every DTO and every field carries `@Schema` with a description and an example; validation
annotations are not optional decoration. Documentation drift is a defect because the frontend
generates from it.

## 7. Throttling is announced, not discovered

Every rate-limited rejection returns both a `Retry-After` header (delta seconds) and a `retryAfter`
ISO-8601 body field, so clients can back off precisely. Idempotency is likewise declarative
(`@Idempotent`) rather than left to callers.

## 8. Scope checks fail closed

An API key without the required scope is `403 AUTH_020`; a JWT user bypasses scope checks because the
scope model describes keys, not humans. A new protected endpoint declares its scope explicitly or it
is a defect waiting for the next audit.
