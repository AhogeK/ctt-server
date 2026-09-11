# api-contract — scenarios

Trigger → judgement → action.

## Adding an endpoint

| Trigger | Judgement | Action |
| --- | --- | --- |
| New endpoint | Who may call it? | Pick the scope: `@RequiresApiKeyScope(READ|WRITE|SYNC)`; unrelated to JWT users |
| | Is it unauthenticated? | Mark `@PublicApi` explicitly; implicit public access is a defect |
| | What throttling? | `@RateLimit(type, limit, windowSeconds)` matching its siblings |
| | What can fail? | Declare every status in `@ApiResponses` with a full example each |
| Endpoint shaped like an existing family (e.g. the stats reads) | Families must not diverge | Copy the sibling's parameter block, scope, rate limit, and error set wholesale |

## Choosing or adding an error code

| Trigger | Judgement | Action |
| --- | --- | --- |
| Need to signal a new failure | Does a code already say this? | grep `ErrorCode` first; reuse unless it would mislead |
| Genuinely new meaning | Prefix by area (AUTH/USER/MAIL/SECURITY/COMMON/SYSTEM/RATE_LIMIT/LEADERBOARD) | Add to the registry with its status; never hardcode the status at the throw site |
| Existing code overloaded by two meanings | Split only when clients need different handling | e.g. the key-limit case got its own code because clients act differently |

## Changing a response

| Trigger | Judgement | Action |
| --- | --- | --- |
| New optional response field | Clients tolerate additions | Additive change; document with `@Schema(example)` |
| Renaming or removing a field | Breaking for every consumer | Do not do it silently; a new field plus a deprecation path, or a requirement to the consuming project |
| New enum value | Consumers may switch exhaustively | Announce it; the frontend/plugin must handle the default branch |
| Validation rule change | Clients may be sending the old shape | Explicit 400 example for the new rule; mention in the README endpoint notes |

## Diagnosing contract complaints

| Trigger | Judgement | Action |
| --- | --- | --- |
| "The API returned a field the docs don't mention" | Docs are the contract | Add the `@Schema` entry; the code is usually right, the annotation stale |
| "Two similar endpoints behave differently" | Endpoint families must be consistent | Diff their parameter blocks, scopes, rate limits, and `@ApiResponses`; align them |
| "Client sees an unexpected status" | The code's registered status is authoritative | Check the registry entry, not the handler's branch |
| "Rate limit hit unexpectedly" | Limiter key dimension matters | Verify the declared `RateLimitType` (IP vs USER vs EMAIL vs API) matches the intent |
