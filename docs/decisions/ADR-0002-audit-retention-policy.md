# ADR-0002: Retain production audit rows for 180 days

**Date:** 2026-11-09 · **Status:** Accepted

## Context

`audit_logs` is the accountability record for security events. A row holds the action, resource
type and identifier, severity, typed `details`, client IP, User-Agent, trace id and creation time.
The audit boundary (ADR-0001) keeps request and response bodies, display names, email addresses and
passkey labels out of those rows; the remaining personal data is the nullable user id (which links
to the account while it exists), client IP and User-Agent.

`AuditRetentionJob` can delete rows older than `ctt.audit.retention.days` in bounded batches. The
application default is 0, which keeps every row: deletion is deliberately an operations decision,
not something an application default does silently. Enabling it deletes the **whole row**, not just
IP/UA; the current code has no redaction path that would keep the event while removing those fields.

The development database held 29,801 audit rows / 11 MB on 2026-11-09. Production volume is not yet
measurable because `ctt_server_prod` has no schema until the first production start (P0.12/P0.16);
the post-deployment measurement is P3.8. The policy therefore has to be explicit now. The committed
base default stays 0; the approved period is part of the production profile (2026-11-11 amendment).

## Decision

The production audit retention period is **180 days**.

- Production runs with a 180-day period: since the 2026-11-11 amendment the production profile sets
  `ctt.audit.retention.days: ${AUDIT_RETENTION_DAYS:180}`, and `AUDIT_RETENTION_DAYS` overrides it per
  environment.
- The base profile keeps `0`, so development and test runs delete nothing. Deletion in production is
  a versioned, reviewed profile entry, not something an environment inherits by accident.
- Once enabled, `AuditRetentionJob` deletes whole rows older than 180 days in bounded batches. It
  does not retain an event with IP/UA stripped.
- A change to the period or to the deletion semantics requires a new ADR, not a silent configuration
  edit.

The decision uses a six-month investigation window. The alternatives table records why 90 days,
365 days, indefinite retention and redaction were not selected. The measured development volume
is small, but production measurement remains the gate for activation so the storage cost is known
rather than assumed.

## Alternatives considered

| Option | Why it was not chosen |
| --- | --- |
| 90 days | Lower exposure, but too short for investigations that begin after a delayed report. |
| 365 days | Stronger evidence retention, but keeps IP/UA and event history twice as long without a requirement; revisit only with a concrete need. |
| Keep indefinitely (`0`) | Preserves every row but defeats storage limitation and leaves the retention job permanently idle; not a production policy. |
| Redact IP/UA and keep the event | Better event-level history, but requires a new redaction path, a second retention rule and tests; it is a separate design, not the current job's contract. Revisit if an investigation needs events older than the window while their network identifiers can be discarded. |

## Consequences

Positive:

- Audit rows have a bounded storage and privacy lifetime.
- Security events remain available for a six-month investigation window.
- The base profile stays safe: development and test runs retain every row, while a production
  deployment inherits the reviewed 180-day period (amended 2026-11-11).
- The decision and its trade-off are traceable, rather than encoded only in an environment value.

Negative:

- Rows older than 180 days are unrecoverable except from a database backup.
- An incident discovered later than the window cannot be reconstructed from the audit table.
- Whole-row deletion also removes the event's non-personal facts; event-level retention would need
  the redaction design above.

Revisit trigger: a compliance requirement or a concrete incident that needs events older than 180
days, or a production volume that makes the storage cost unacceptable. Either case gets a new ADR.

## Deployment and Verification Checklist

This checklist is a plan for production activation, not part of the P0.6c decision.

Steps 3, 4 and 6 describe the original activation path. The 2026-11-11 amendment moved the value
into the production profile and made every sweep loggable, so read those steps together with it.

1. Complete P0.16 and P0.16b, and record the pre-activation `audit_logs` row count, table size and
   oldest/newest timestamps. Do not enable deletion before a baseline exists.
2. Confirm a recent database backup exists. A verified restore is an activation prerequisite; if
   it has not been verified, run the restore drill before enabling.
3. Set `AUDIT_RETENTION_DAYS=180` in the production runtime configuration owned by P0.16, and
   record the exact compose file/service or systemd unit in the deployment record. Do not change
   the committed default and do not put the value in a secret file unless the deployment already
   uses that channel for non-secret settings. (Amended 2026-11-11: `application-prod.yaml` already
   carries `${AUDIT_RETENTION_DAYS:180}`, so the variable is only an override and rollback channel.
   The base Compose file does not forward it; a deployment that changes it must do so through the
   production overlay or an explicit `-e`.)
4. Deploy and verify the application starts with the retention setting; observe the first scheduled
   sweep. The startup line reports the effective policy, and every sweep logs its cutoff, deleted
   row count and duration, including sweeps that delete nothing (amended 2026-11-11), so an empty run
   is visible in the log as well as in row counts.
5. After one sweep, verify rows older than the cutoff are gone and rows inside the window remain.
   Record the before/after counts and the cutoff timestamp.
6. Rollback: set `AUDIT_RETENTION_DAYS=0` and restart. This stops future deletion; rows already
   deleted require a database restore. (Amended 2026-11-11: the override must travel through the
   channel that supplies the container's environment — the production overlay or an explicit `-e`.
   Omit the variable to accept the profile default instead of setting it to an empty string: an
   empty value binds to an integer property and fails startup.)

## Amendment (2026-11-11, P0.16c)

The production profile now carries the approved period:
`application-prod.yaml` sets `ctt.audit.retention.days: ${AUDIT_RETENTION_DAYS:180}`. This
supersedes checklist step 3's instruction to set the variable only in an external runtime
configuration: the approved value is versioned with the deployment posture, and the environment
variable remains the per-environment override and the rollback switch (`0`). The base profile still
defaults to 0, so development and tests never delete audit rows.

Reason: the setting was documented as required, but neither the profile nor the existing deployment
provided it, so a production instance started with deletion disabled and no signal said so. The
empty-database baseline from P0.16b (0 tables, 2026-11-11) satisfies step 1 for this deployment;
steps 1–2 still apply to any populated database. `AuditRetentionJob` logs the effective policy at
startup (info, or a warning when the production profile keeps every row) and logs each sweep's
cutoff, deleted row count and duration, including sweeps that delete nothing and sweeps that fail
(a failure reports the cutoff and the rows deleted before the exception), so step 4 can be verified
from the log as well as from row counts. Step 5's production measurement remains open until the
first production start and real traffic (P3.8).

## Approval Record

| Field | Value |
| --- | --- |
| Decision | 180-day retention; whole-row deletion |
| Decision authority | Project owner, also the deployment operator/compliance authority; delegated the decision to engineering on 2026-11-09 |
| Approval basis | Project-owner instruction in the 2026-11-09 session; the project owner is the operator/compliance authority for this deployment, and this ADR is the written record. |
| Recorded | 2026-11-09 |
| Status | Accepted |
| Traceability | `AuditRetentionJob`, its unit and repository tests, `ProductionRetentionPolicyTest`, `application.yaml`, `application-prod.yaml`, ADR-0001, this ADR |

## Evidence

- `src/main/java/com/ahogek/cttserver/audit/service/AuditRetentionJob.java`
- `src/main/java/com/ahogek/cttserver/common/config/properties/AuditRetentionProperties.java`
- `src/main/java/com/ahogek/cttserver/audit/repository/AuditLogRepository.java`
- `src/test/java/com/ahogek/cttserver/audit/service/AuditRetentionJobTest.java`
- `src/test/java/com/ahogek/cttserver/audit/repository/AuditLogRepositoryTest.java`
- `src/main/resources/application.yaml` (`ctt.audit.retention`)
- `src/main/resources/application-prod.yaml` (`ctt.audit.retention`)
- `src/test/java/com/ahogek/cttserver/common/config/ProductionRetentionPolicyTest.java`
- `docs/decisions/ADR-0001-audit-payload-boundary.md`
- Local measurement (2026-11-09): development `audit_logs` 29,801 rows / 11 MB; production
  measurement after the first production start is P3.8.
