# ADR-0001: Keep request bodies out of the audit trail

**Date:** 2026-11-08 · **Status:** Accepted

## Context

`audit_logs` is the accountability record, not a copy of application traffic. A row carries the
action, resource type and identifier, severity, environment metadata and a **typed** `details`
payload (`reason`, `errorCode`, `attemptCount`, `stateBefore`, `stateAfter`, optional `ext`); the
schema stores it as JSONB. Rows are append-only in practice, have no retention or cleanup job, and
reference the user with `ON DELETE SET NULL` — they are meant to survive account deletion.

That survival is only defensible because the details are meant to contain no user content — the
writers that still do are tracked as P0.6b. Account deletion removes the user, sessions, devices,
statistics and keys by cascade, and the audit row keeps the security event with the account
reference cleared. The deletion service states this explicitly: "What survives is the audit trail
... it is why this service does not need to anonymize anything."

On 2026-03-18 a configuration block `ctt.security.audit` was added with `log-payloads: true` and a
`masked-fields` list. It had no production consumer — only a binding test asserted its properties —
and it contradicted the audit boundary spec published two days earlier, which already modelled
`AuditDetails` as a strongly typed carrier. The request-logging filter has always documented the
opposite rule: "Never logs full Request/Response Body ... only metadata". The block was removed in
v0.93.7 (P0.6).

The only masking that exists is text-oriented: header masking by name and a regex fallback applied
to log text and to string key/value pairs. None of them is a JSON-aware payload masker. Running the
production regex
against representative bodies shows it masks simple string values such as `password`,
`authorization` and `newPassword`, but leaves `pwd`, `otp` and `apiKey` untouched, leaves nested
user content unmasked, and corrupts empty or object values (`{"token":""}` becomes
`{"token"******""`).

## Decision

Request and response bodies are **never persisted in the audit trail**, in any environment. Audit
records the security facts, the outcome, the reason and the correlation identifiers; it does not
record the data that crossed the boundary.

A configuration switch for this capability is not kept. A flag with no consumer is a statement to
operators that the capability exists; it is worse than no flag, because it invites reliance on a
control that never ran.

## Resolution (v0.93.9)

The writers named here were changed in P0.6b, so the boundary is now enforced by the current code:

- `UserService.updateDisplayName` writes only `field=displayName`.
- Passkey registration, rename and delete write empty details; the label is gone.
- `MailOutboxService` uses the business id as `resource_id` on idempotent skip and writes no
  recipient field; enqueued details carry ids, template and retry count only.
- `MailOutboxProcessor` stores ids, template and retry count only; no recipient or provider error.
- `TokenRefreshService` writes empty details; IP and User-Agent remain columns on the event.
- `PasswordResetService` logs a null resource id for `PASSWORD_RESET_EMAIL_NOT_FOUND`; the submitted
  email is not stored.
- `RateLimitAspect` logs the actor id (or null), never the email-keyed Redis cache key.

Retention of the remaining security metadata (IP, User-Agent, timestamps) and of historical rows
still needs a defined period, a cleanup job and operations/compliance sign-off. That remains an
explicit open item; it is not part of the code change.


## Alternatives considered

| Option | What it would require | Why it was not chosen |
|---|---|---|
| Masked body capture into `audit_logs.details` | A bounded `ContentCachingRequestWrapper`, JSON-aware recursive masking (case-insensitive keys, arrays, nested objects), size caps, exclusion of multipart/binary, retention policy, redaction on account deletion, access control and tests for every masking rule | Conflicts with erasure: bodies contain emails, project names and coding-session content, which would outlive the account with no user reference. Turns audit into a PII/secret store with a much larger breach impact. `SyncPushRequest` accepts up to 1000 sessions per call, so one row could hold hundreds of kilobytes of user content; audit details average 66 bytes today. Text masking is not safe for JSON (see above). |
| Body capture in application logs with short retention | An opt-in request logger, sampling, truncation, escaping, and a guarantee that the log pipeline is not shipped or indexed | Logs leave the trust boundary (Loki, backups) and the existing regex is heuristic; the same PII/secret exposure applies. The filter deliberately does not do this. |
| Shape-only audit metadata (field names, types, sizes, validation codes; **no values**) | A bounded request wrapper plus a small analyser and tests | Viable, but no current incident or support case requires it. It is the preferred design if a real need appears. |
| A separate forensic store with encryption, short retention, access approval and deletion hooks | New infrastructure, DPIA-style review, retention job, redaction path | Out of scope for a single-node service, and it does not remove the erasure conflict; it only relocates it. |

## Consequences

Positive:

- The account-deletion promise holds without rewriting append-only audit rows.
- Breach impact is limited to security metadata, not user work product or credentials.
- Audit growth stays predictable; the table is not multiplied by payload size.
- Operators are not offered a switch that does nothing.

Negative:

- A rejected request cannot be reproduced from the server alone; debugging relies on error codes,
  typed `details`, the client's own data and `traceId` correlation.
- A future forensic requirement will need a deliberate design, not a configuration flip.

Revisit trigger: a concrete incident or support case that cannot be resolved with typed details,
`traceId`, client logs and the plugin's local database. In that case, implement the shape-only
option first; raw bodies require a separate decision, a retention policy, and a proven JSON-aware
masker with tests.

## Evidence

Local (primary): `src/main/resources/db/migration/V20260303210000__init_base_schema.sql` (audit
table, `ON DELETE SET NULL`), `audit/model/AuditDetails.java`, `audit/service/AuditLogService.java`,
`audit/listener/AuditEventListener.java`, `common/context/RequestLoggingFilter.java`,
`user/service/AccountDeletionService.java`, `common/config/logging/MaskingMessageConverter.java`,
`common/config/logging/MaskingJsonEncoder.java`, `sync/dto/SyncPushRequest.java`,
`sync/dto/SyncSessionDto.java`, `docs/audit-boundary-spec.md`.

History (primary): `39bedaf` (2026-03-18, introduced the block and defaults), `c6db6ac` (2026-03-16,
audit boundary spec), `git log -S 'logPayloads()'` / `-S 'maskedFields()'` (only the config commit
and the binding-test commits; `git grep` finds no production consumer).

External (primary): OWASP Logging Cheat Sheet (data to exclude; extended details are optional and
must be sanitised; log injection and resource exhaustion), OWASP ASVS 5.0 V16 (16.2.5: log
sensitive data only by protection level; 16.3.2: log access decisions "without logging the
sensitive data itself"), CWE-532 (sensitive information in log files), GDPR Art. 5(1)(c)/(e) and
Art. 17(1)(a) (minimisation, storage limitation, erasure), Spring `AbstractRequestLoggingFilter`
(payload logging is opt-in, defaults to off, truncated to 50 characters).

Observation: the production regex was run against representative bodies on 2026-11-08; see the
"masked" cases above. Local database measurement: `audit_logs` 4 rows / 136 kB total, average
`details` 66 bytes.
