# auth-lifecycle — principles

## 1. BIND never issues tokens; LOGIN always does

The OAuth state's `action` decides the whole flow. `BIND` links a provider to an already-authenticated
user and must not mint or rotate any token — if a bind ever returns tokens, the session invariant is
broken. `LOGIN` completes by issuing a fresh access/refresh pair.

`BIND` requires a non-null initiating user; `LOGIN` requires it to be null. Both constraints live in
the payload's canonical constructor, so an invalid payload cannot be constructed at all.

## 2. Every successful login updates login metadata

`lastLoginAt` (and, when a client IP is available, `lastLoginIp`) is written on **every** path that
authenticates a user: password login, OAuth login with an existing binding, and OAuth first-time
registration. Flows that merely authenticate without logging in (bind, unbind) do not touch it.
Missing metadata is a defect that surfaces much later as "last login shows never".

## 3. Lockout is layered, not a timer

Three thresholds govern brute-force defence: attempts within a sliding window, the window length, and
the lock duration. Unlocking is **lazy** — the next attempt re-evaluates the window and clears an
expired lock — plus an hourly sweep that releases accounts nobody retried. A pure in-memory timer
would not survive restarts; the DB-backed attempt log is the state.

## 4. Credential-changing actions invalidate other sessions

Password reset and logout-all revoke active refresh tokens (kill switch). Any new flow that changes
a credential must decide explicitly whether existing sessions survive; defaulting to "survive" is a
security regression.

## 5. Anti-enumeration: answers do not depend on account existence

Registration, password-reset request, and resend-verification respond identically whether or not the
address exists. Verification and reset tokens are hashed at rest (SHA-256) and single-use; the raw
token exists only in the email.

## 6. Terms are a gate with a version, not a boolean

The accepted terms version is carried in the JWT and compared against the current version on
protected endpoints. Raising the current version re-gates every existing session without touching
user rows — so a version bump must be deliberate, and must not be used for editorial changes.

## 7. Verification tokens are purpose-scoped

The verification-token table holds several purposes (initial verification, email change). A token
issued for one purpose must never satisfy another; the purpose is part of the lookup, and a stale
purpose is a defect even when the token is otherwise valid.

## 8. Captcha is a configuration switch, not an environment assumption

When the captcha secret is unconfigured the verifier logs and passes (local development); when it is
configured, a missing or failing token is `SECURITY_006`. Never hardcode a bypass, and never let a
verification outage be treated as success.

## 9. Deletion removes the row; the cascade is what makes it complete

`DELETE /api/v1/users/me` deletes the user row. Everything belonging to the account goes with it
through the schema's `ON DELETE CASCADE` foreign keys — sessions, statistics, achievements, sync
state, devices, API keys, refresh tokens, OAuth links — while the audit trail survives because
`audit_logs` uses `ON DELETE SET NULL`, keeping the events with the account reference cleared (the
`ACCOUNT_DELETED` event carries the deleted id as its `resource_id`, since its `user_id` cannot).

Deleting the row rather than marking it (`status = 'DELETED'`) is the deliberate part: a hand-written
list of tables to clean up rots silently, missing whichever table is added next, while a cascade
cannot. Anonymizing the row was the earlier candidate and it was rejected as incomplete — it cleared
four fields and left the login IP, the project names and every session behind. `UserStatus.DELETED`
remains in the enum and the store constraint, and the auth paths still refuse it, but nothing
produces it: the terminal state has no producer.

Two consequences worth stating:

- **The client, not the server, holds the history.** The plugin's local database is the source of
  truth, so deleting the server copy costs the user nothing they cannot re-push after registering
  again. That is what makes an irreversible server-side delete acceptable rather than reckless.
- **An access token outlives the row** for the rest of its lifetime (the 15-minute TTL, the same
  window logout leaves open), because a JWT carries the status from when it was minted and the
  request path reads that claim rather than the database. Nothing can be obtained in that window:
  every credential is gone with the row, and `createApiKey` refuses a non-`ACTIVE` account — which
  matters because the scope aspect only enforces scopes for API-key callers, so a session token
  minted before the deletion does reach that method.

The ranking cleanup is separate and runs **after** the transaction commits, never inside it: a
ranking write that outlived a rollback would leave a live account unranked, while the reverse failure
is logged and visible.
