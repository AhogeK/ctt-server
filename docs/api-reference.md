# API Reference

Detailed contract for every `/api/v1` endpoint: request and response shapes, error codes, rate limits and the semantics behind them. The running service publishes the same contract as OpenAPI — see [Interactive documentation](#interactive-documentation).

Start with authentication: every protected call takes either a short-lived JWT (`Authorization: Bearer <jwt>`) or a device-bound API key (`Authorization: Bearer cttak_<prefix>_<secret>`), and API keys are checked against a per-endpoint scope.

## Endpoints

### Authentication

| Endpoint                              | Method | Description                                                                |
|---------------------------------------|--------|----------------------------------------------------------------------------|
| `/api/v1/auth/register`               | POST   | Register new user (rate limited: 60/hour per IP)                           |
| `/api/v1/auth/login`                  | POST   | User login with JWT tokens (rate limited: 30/hour per IP)                  |
| `/api/v1/auth/refresh`                | POST   | Refresh access token using refresh token (rotation)                        |
| `/api/v1/auth/verify-email`           | GET    | Verify email with token param (public, 24h token TTL)                      |
| `/api/v1/auth/resend-verification`    | POST   | Resend verification email (rate limited: 3/1min per email)                 |
| `/api/v1/auth/logout`                 | POST   | Logout user and revoke refresh token (idempotent, BOLA-protected)          |
| `/api/v1/auth/logout-all`             | POST   | **Kill Switch**: Revoke all active sessions (requires JWT, 5/min per user) |
| `/api/v1/auth/password-reset/request` | POST   | Request password reset (rate limited: 3/10min per email)                   |
| `/api/v1/auth/password-reset/confirm` | POST   | Confirm password reset (rate limited: 15/10min per IP)                     |
| `/api/v1/auth/terms/accept`           | POST   | Accept current terms version (requires JWT)                                |

### GitHub OAuth

| Endpoint                                 | Method | Description                                                                                                                                            |
|------------------------------------------|--------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| `/api/v1/auth/oauth/github/authorize`    | GET    | Generate CSRF state, return GitHub authorization URL. Use `?action=login` (default, public) or `?action=bind` (requires JWT)                           |
| `/api/v1/auth/oauth/github/callback`     | GET    | Handle OAuth callback: validate state → exchange token → login/register → 302 redirect to frontend with tokens (public, 60/hour)                       |
| `/api/v1/auth/oauth/accounts`            | GET    | List current user's OAuth account bindings (requires JWT) — never exposes tokens, returns empty array when no bindings                                 |
| `/api/v1/auth/oauth/accounts/{provider}` | DELETE | Unbind an OAuth provider from the current user (requires JWT) — 204 on success, 409 if it would leave the user with no login method, 404 if not linked |

**OAuth Flow**:
```
Client → GET /authorize → {authUrl: "https://github.com/..."}
Client → redirect user to GitHub → user authorizes
GitHub → redirect to /callback?code=xxx&state=xxx
Server → validate state → exchange code → get user info → login/register
Server → 302 redirect to {frontendUrl}/oauth/callback?accessToken=...&refreshToken=...
```

**Error Handling**: All OAuth errors redirect to `{frontendUrl}/oauth/error?code={errorCode}`.

### User Profile

| Endpoint                                   | Method | Description                                                                                                              |
|--------------------------------------------|--------|--------------------------------------------------------------------------------------------------------------------------|
| `/api/v1/users/me`                         | GET    | Get current authenticated user's profile (JWT or API key) - excludes sensitive fields (passwordHash, lastLoginIp, version) |
| `/api/v1/users/me`                         | PATCH  | Change the display name (JWT or a WRITE-scoped API key) and return the updated profile - see below                          |
| `/api/v1/users/me`                         | DELETE | Delete the current user's account permanently (requires JWT, CSRF token) - see below                                      |
| `/api/v1/users/me/sessions`                | GET    | List the account's login sessions, most recently used first - see below                                                    |
| `/api/v1/users/me/sessions/{sessionId}`    | DELETE | Revoke one login session (204; a WRITE-scoped API key may call it too) - see below                                          |
| `/api/v1/users/me/sessions/revoke`         | POST   | Revoke several sessions by id; partial success reported per id - see below                                                  |
| `/api/v1/users/me/export`                  | GET    | Download everything the account holds as one JSON file, in the desktop client's import format - see below                    |

**Display name update** (`PATCH /api/v1/users/me`): body `{"displayName": "..."}`, validated with
the same rule as registration (2-50 characters: Chinese, Japanese, Korean, Latin letters, digits,
underscores, hyphens). The response is the same profile shape `GET /users/me` returns. Submitting
the name the account already has is a successful no-op — nothing is written and no audit event is
recorded — and a real change emits one `PROFILE_UPDATED` audit event carrying the old and new
values. Display names are not unique: they are a label, never an identifier. Rate limited to
10 changes/hour per user. Errors: `400 COMMON_003` (rule violation), `403 AUTH_020` (API key
without WRITE scope), `403 AUTH_022` (account not active).

**Account deletion** (`DELETE /api/v1/users/me`): permanent and irreversible, confirmed with the
account password in the request body (`{"password": "..."}`). Requires a signed-in session — an API
key cannot delete the account it was issued for (`AUTH_025`). Accounts created through an OAuth
provider have no password; for them the session is the whole of the available proof.

What happens:

- The user row is **deleted**, and the schema's cascade removes what hung off it: coding sessions,
  statistics, achievements, sync state, devices, API keys, refresh tokens and OAuth links.
- The account is removed from every leaderboard it was ranked in (`totalParticipants` drops).
- The email address is freed and can be registered again as a new, unrelated account.
- The audit trail survives: `audit_logs` keeps the events with the account reference cleared, and the
  `ACCOUNT_DELETED` event carries the deleted id as its resource.
- Already-issued access tokens stay valid until they expire — the 15-minute access-token TTL, the
  same window logout leaves open — because a JWT carries the status from when it was minted. Nothing
  can be obtained in that window: no credential exists to authenticate with, and none can be minted.

Clients should treat this as losing the server copy: the plugin's local database is the source of
truth for the user's history, and a new registration re-pushes it.

Errors: `401` wrong password (`USER_014`) or missing/invalid JWT, `403` no password supplied for an
account that has one (`USER_013`), or called with an API key (`AUTH_025`), `429 RATE_LIMIT_001` after
5 attempts within an hour. The limit exists because confirming the password is a bcrypt verification:
an unthrottled caller burns CPU rather than merely guessing.

**Login sessions** (`GET` / `DELETE /api/v1/users/me/sessions`): one entry per login, not per
refresh — rotation continues a session, so a client that refreshed ten times still has a single
entry. Each entry carries `kind` (`WEB` or `PLUGIN`), `deviceName` and `ideName` for a device-bound
session, the `userAgent` exactly as the client sent it, `createdAt` (when the login happened),
`lastUsedAt` (the most recent refresh, null until one happens), `expiresAt`, and `current` — true
for the session the calling access token belongs to. A call made with an API key has no session of
its own, so every entry reports `current: false`. An account with no registered device still lists
its sessions; their `deviceName`/`ideName` are null. Sessions are sorted by `lastUsedAt` descending
with never-refreshed sessions last, then by `createdAt` descending. The response body's `data` is the
array itself — there is no `{sessions: []}` envelope — and `limit` defaults to 20 with values clamped
to 1..100. `GET` is rate limited to 60/hour and `DELETE` to 10/hour.

`DELETE /api/v1/users/me/sessions/{sessionId}` revokes every live token of that session, so the
client holding it must sign in again; revoking the calling session is allowed and equals a logout.
A session that belongs to another account answers `404 COMMON_002`, exactly like one that does not
exist. Revocation is serialized against rotation for that session, so a refresh running at the same
instant cannot keep the session alive — and a client that keeps presenting a revoked session's
token gets `401 AUTH_003` for that session alone rather than taking every other session down with
it. Already-issued access tokens stay valid until they expire: the 15-minute window logout leaves
open, and the reason the list is the authority on "signed in where", not the tokens.
`POST /api/v1/users/me/sessions/revoke` revokes a list of sessions in one call. The body is
`{"sessionIds": [...]}`; duplicates are ignored and at most 100 distinct ids are accepted (more is
`400 COMMON_003`). The call is deliberately partial-success: ids that are unknown, already revoked,
or owned by another account come back in `skipped`, the rest are revoked, and the response is `200`
with `{revoked, skipped}` — a retry after a timeout therefore converges instead of failing forever.
Listing the calling session is allowed and equals a logout for it (its refresh token dies at once;
the access token stays valid until it expires, as with the single-session call). Every revoked
session emits its own `SESSION_REVOKED` audit event with the same `kind` detail. The service locks
the sessions in ascending id order, so overlapping batches acquire shared locks in the same
sequence. The batch is rate limited to 10 calls/hour per user (`429 RATE_LIMIT_001`), counted
**per endpoint**: one batch of up to 100 ids is a single call and draws on its own counter, not on
the single-session `DELETE /users/me/sessions/{sessionId}` allowance (also 10/hour). Every 429
carries a `Retry-After` header with the remaining seconds of the current fixed window, and
`retryAfter` in the body is the matching ISO-8601 instant — read either instead of assuming the
full window. An API key needs `WRITE` scope.

**Data export** (`GET /api/v1/users/me/export?timezoneOffset=<minutes>`): streams everything the
account holds as one JSON file. The shape is the desktop client's import format on purpose — a file
nobody can import is not much of a portability feature: `exportVersion` is `1.0`, sessions sit under
`sessions` with the client's field names, and every timestamp is written as a **local** date-time
without a zone, converted with the `timezoneOffset` the caller passes (`480` for UTC+8). The
importer parses those strings as local times, so a `Z` suffix would make the whole import fail;
`timezoneOffset` is therefore required, not defaulted, and an out-of-range value is
`400 COMMON_003`. A session whose device was never registered still reports empty strings, never
`null`, because the client model rejects nulls. Soft-deleted sessions are left out: the importer
inserts everything as live, so exporting them would resurrect rows the user deleted. Everything
else rides in `serverExport` (account, devices, materialized statistics, achievements, login
sessions, API key metadata, OAuth bindings), which the importer ignores by name — Gson skips
unknown fields. No credentials of any kind are included: no password, no token, no key hash.
Downloaded with `Content-Disposition: attachment; filename="ctt-export-<yyyy-MM-dd>.json"`, the
local date in the caller's offset; clients should read the header rather than build the name
themselves. Requires an active account, `READ` scope for an API key, and is rate limited to 3
exports/hour. Errors: `400 COMMON_003` (offset out of range),
`403 AUTH_020` / `403 AUTH_022`, `429 RATE_LIMIT_001`. The export is audited as `DATA_EXPORTED`.

**IDE attribution**: each session carries its own `ideName` / `ideVersion` — the IDE that produced
it, which is not the same thing as the device that pushed it (one installation shares a device id
across every IDE on it). Rows pushed by older clients, and rows written before the field existed,
carry none and fall back to the origin device's registration. `serverExport.devices` mirrors
`GET /api/v1/devices`: the IDE the device most recently used, with the registration value as the
fallback.

**Response Fields**:
- `id` (UUID)
- `email` (String)
- `displayName` (String)
- `emailVerified` (boolean, derived from `emailVerifiedAt != null`)
- `createdAt` (Instant, ISO 8601)
- `lastLoginAt` (Instant, ISO 8601, nullable)
- `termsVersion` (String)

**Avatar**: Avatar is not stored on the backend. Frontend should generate avatars from `md5(userId)` or similar client-side logic.

**Response Fields** (v0.31.0+):
- `emailChangePending` (boolean) — whether an email change request is pending

**Response Fields** (v0.34.0+):
- `hasPassword` (boolean) — whether the user has a password set (false for OAuth-only users)

### Email Change

| Endpoint                                     | Method | Description                                                                                              |
|----------------------------------------------|--------|----------------------------------------------------------------------------------------------------------|
| `/api/v1/users/me/email/change-request`      | POST   | Request email change — sends verification to new address (requires JWT, rate limited: 3/10min per email) |
| `/api/v1/users/me/email/change-confirm`      | POST   | Confirm email change via verification token (public, rate limited: 15/10min per IP)                      |
| `/api/v1/users/me/email/change-request`      | DELETE | Cancel pending email change request (requires JWT)                                                       |
| `/api/v1/users/me/email/resend-verification` | POST   | Resend verification for pending email change (requires JWT, rate limited: 1/60s per user)                |
| `/api/v1/users/me/email/status`              | GET    | Get email status including pending change info (requires JWT)                                            |

**Email Change Flow**:
```
User → POST /change-request {newEmail, password?} → System sends verification to new email
                                                          ↓
                                                  User clicks link
                                                          ↓
                                    POST /change-confirm {token} → Email changed, verification reset
```

**Security**: Password required if user has password set. OAuth-only users skip password verification. Token expires in 1 hour, max 5 attempts per token.

### Password Management

| Endpoint                           | Method | Description                                                                                          |
|------------------------------------|--------|------------------------------------------------------------------------------------------------------|
| `/api/v1/users/me/password/set`    | POST   | First-time password setup for users without a password (requires JWT, rate limited: 5/min per user)  |
| `/api/v1/users/me/password/change` | POST   | Change password for users who already have one (requires JWT, rate limited: 5/min per user)          |

**Request**: `{"currentPassword": "...", "newPassword": "..."}` — passwords are base64-encoded by the frontend; the server stores and compares them as-is.

**Error Codes**:

| Scenario                         | Code                 | HTTP |
|----------------------------------|----------------------|------|
| User not found                   | `USER_004`           | 404  |
| Current password wrong           | `USER_014`           | 401  |
| New password same as old         | `PASSWORD_SAME_AS_OLD` | 409  |
| User has no password (defensive) | `USER_015`           | 409  |
| Weak new password                | `COMMON_003`         | 400  |

### API Key Management

| Endpoint                        | Method | Description                                                                                                |
|---------------------------------|--------|------------------------------------------------------------------------------------------------------------|
| `/api/v1/auth/api-keys`         | POST   | Create API key (rate limited: 10/hour per user) — raw key returned once, then only hash is stored          |
| `/api/v1/auth/api-keys`         | GET    | List user's API keys (metadata only, no raw key/hash exposure)                                             |
| `/api/v1/auth/api-keys/{id}`    | GET    | Get single API key metadata (BOLA-protected: 401 if not owned by caller)                                   |
| `/api/v1/auth/api-keys/{id}`    | DELETE | Revoke API key (BOLA-protected, idempotent) — key remains in DB for audit, authentication is blocked       |
| `/api/v1/auth/api-keys/{id}/delete` | DELETE | Permanently delete a revoked or expired API key (BOLA-protected) — physically removed from DB, 409 if still active |

**Key Format**: `cttak_<prefix>_<secret>` — prefix is 8-char visible identifier, secret is 32-char URL-safe Base64.

**Security**: Raw key shown only at creation time; SHA-256 hash stored at rest. Per-user limit: 20 active keys.

**Authentication**: API keys authenticate via `Authorization: Bearer cttak_<prefix>_<secret>` header.
`ApiKeyAuthenticationFilter` validates the key, checks expiration/revocation status, and updates
`last_used_at` synchronously. The filter is registered **before** `BearerTokenAuthenticationFilter` in
the security chain, and a dedicated `ApiKeyAwareBearerTokenResolver` ensures the JWT filter never sees
an API key value (it returns `null` for `cttak_*`). After authentication succeeds, `ApiKeyPrincipal`
embeds the populated `CurrentUser` so downstream `getCurrentUserRequired()` calls work for both
authentication types without an extra database lookup.

**Scope Enforcement**: API keys are granted specific scopes (READ, WRITE, SYNC, ADMIN). Protected endpoints enforce scope requirements via `@RequiresApiKeyScope` annotation. API keys without the required scope receive 403 (AUTH_020). JWT-authenticated users bypass scope checks.

**Error Codes**:

| Scenario                              | Code      | HTTP |
|--------------------------------------|-----------|------|
| API key invalid / not found / BOLA    | `AUTH_010` | 401  |
| API key expired                       | `AUTH_011` | 401  |
| API key revoked                       | `AUTH_012` | 403  |
| API key header malformed              | `AUTH_021` | 401  |
| Per-user key limit exceeded           | `AUTH_024` | 409  |
| Missing required scope                | `AUTH_020` | 403  |
| Active keys must be revoked before deletion | `AUTH_023` | 409  |
| Authentication rate limit exceeded    | `RATE_LIMIT_001` | 429 |

### Device Management

| Endpoint | Method | Description | Required Scope |
|----------|--------|-------------|----------------|
| `/api/v1/devices` | POST | Register or update a device; binds the authenticating API key to it | SYNC |
| `/api/v1/devices` | GET | List the authenticated user's devices | READ or SYNC |
| `/api/v1/devices/{deviceId}` | DELETE | Revoke a device and its sessions | WRITE |

**Registration semantics**: device registration is the sync prerequisite — a plugin registers
its local device identifier with a SYNC-scoped API key so subsequent pull/push calls pass the
ownership check. Device queries accept either READ or SYNC scope (plugin device-status checks use SYNC). Registering an existing `deviceId` refreshes metadata; a `deviceId` owned by
another user is rejected with 409 `DEVICE_001`; API-key registration binds the key to the device
(`api_keys.device_id`). Revocation stamps `revokedAt` on the device (visible to clients); re-registering the device clears it. The endpoint is rate-limited to 10/hour per user.

**Last seen** (`lastSeenAt`): every successful `/sync/pull` and `/sync/push` advances it — a pull is
the plugin's heartbeat, so it moves on rounds that carry no new sessions too. Registration counts as
activity; a revoked device stops advancing while it stays revoked (a revoked device cannot sync
anyway). The write is a single statement that leaves the entity version untouched, so two syncs from
the same installation cannot collide into an optimistic-lock failure. Clients derive "active" from
this timestamp (the dashboard treats seven days as the threshold) and need no other field.

**Reported IDE** (`ideName` / `ideVersion`): both name the IDE this device most recently used,
derived from its newest session that carries one. A device with no such session reports its
registration values, and the endpoint still returns one row per device. Registration keeps storing
the IDE of the registering process, so the two can differ after the user switches products on the
same machine; per-session attribution is authoritative (see Sync Engine).

**Error Codes**:

| Scenario | Code | HTTP |
|----------|------|------|
| Device not found / not owned | `COMMON_002` | 404 |
| Device already registered to another user | `DEVICE_001` | 409 |

### Sync Engine

| Endpoint | Method | Description | Required Scope |
|----------|--------|-------------|----------------|
| `/api/v1/sync/pull` | POST | Pull changes since the device's watermark | SYNC |
| `/api/v1/sync/push` | POST | Push a batch of session states (LWW) | SYNC |

**Protocol**:
- `pull` accepts `{ deviceId, lastPulledChangeId }`, returns change-log entries (`changeId`, `sessionUuid`, `op`, `serverVersion`, `happenedAt`) joined with the winning session snapshot — including the session's `ideName`/`ideVersion` (null when the row has none) — plus `nextCursor` and a `hasMore` flag. Responses are paged (`ctt.sync.pull-batch-size`, default 1000): clients continue pulling with the returned cursor until `hasMore` is false. The per-device watermark advances monotonically; a pull with no new changes returns an empty list and the current cursor (idempotent).
- `push` accepts `{ deviceId, sessions[] }`, routes each session through `ConflictResolver` under LWW rules: incoming wins → fields applied + server version bumped + `UPSERT` change; delete wins → soft delete + `DELETE` change; server wins → idempotent no-op. The whole batch applies in a single transaction. Sessions may carry an optional `ideName`/`ideVersion` describing the IDE that produced them (length-bounded, blank treated as absent); older clients omit both and their new rows stay null, which statistics read as "use the origin device's registration". An update that omits them leaves the stored values untouched, so a client that cannot report an IDE cannot erase one a newer client recorded; a client that starts reporting one fills it in. Returns `nextCursor` for the next pull.
- `deviceId` ownership is validated (404 `COMMON_002` on mismatch, including devices that have been revoked); devices are registered via `POST /api/v1/devices` (see Device Management); endpoints are rate-limited to 120 req/min (`RATE_LIMIT_001`); successful and failed syncs are audited (`SYNC_PULL` / `SYNC_PUSH`).

### Statistics & Analytics

| Endpoint | Method | Description | Required Scope |
|----------|--------|-------------|----------------|
| `/api/v1/stats/summary` | GET | Today / daily average / this week / month / year / lifetime totals (seconds), each with its change against the previous complete period | READ |
| `/api/v1/stats/heatmap` | GET | Per-day coding seconds over a date range (default: this year) | READ |
| `/api/v1/stats/streaks` | GET | Current and longest consecutive coding day streaks | READ |
| `/api/v1/stats/distribution` | GET | Duration distribution by languages / projects / time-of-day / weekday / devices / IDEs | READ |
| `/api/v1/stats/hourly` | GET | Per-hour average coding seconds across active days | READ |
| `/api/v1/stats/hourly-days` | GET | Per-day hourly coding seconds behind that average (at most the 31 most recent days, oldest first) | READ |
| `/api/v1/stats/heatmap-years` | GET | Years with coding activity (timezone-aware), descending | READ |
| `/api/v1/stats/heatmap-months` | GET | Months (`yyyy-MM`) with coding activity, descending | READ |
| `/api/v1/stats/week-hour` | GET | Weekly coding heatmap: average seconds per weekday-hour cell | READ |
| `/api/v1/stats/recent` | GET | Most recent coding sessions (default 20, max 100) | READ |
| `/api/v1/stats/ide-filters` | GET | Distinct IDE names for filter dropdowns (session-reported plus registered) | READ |
| `/api/v1/stats/achievements` | GET | Achievement badges with unlock state and progress, unlocking newly reached badges | READ |

**Parameters**: `timezoneOffset` (minutes, e.g. `480` for UTC+8) shifts day/week/month/hour
boundaries to the caller's timezone; heatmap, hourly, hourly-days and distribution accept `start` /
`end` dates (ISO, inclusive, full history when omitted); every endpoint
except achievements accepts an optional `deviceId` (origin-device filter) or `ideName` (exact
IDE-name filter, matched against each session's own IDE and falling back to its origin device's
registration; mutually exclusive with deviceId, unknown IDE names yield 404); the distribution
endpoint additionally supports `type=DEVICES` / `type=IDES` (per-device / per-IDE seconds; IDE
buckets use the session's IDE, falling back to the origin device's registered name);
`GET /api/v1/stats/ide-filters` lists the distinct IDE names for the dashboard filter, taken from
both the sessions and the device registrations, so a name stays selectable after the user switched
products on one installation; and
`GET /api/v1/stats/heatmap-years` and `GET /api/v1/stats/heatmap-months` list the years and
`yyyy-MM` months that contain at least one non-zero coding day in the requested timezone
(`timezoneOffset`), newest first, for the heatmap year/month dropdowns — both derive from the
same activity rule as the heatmap, so a session crossing a month or year boundary contributes
to both and no listed option renders empty; `GET /api/v1/stats/week-hour` renders the plugin's
"Weekly Coding Activity by Hour" heatmap: weekday-hour cells (ISO weekday 1=Monday..7=Sunday)
averaged over the days each weekday appears in the window, optional `start`/`end` bounds
(inclusive, full history when omitted), only exercised cells returned;
achievements evaluate window-based badges (early bird 06:00-09:00, night owl 22:00-05:00, perfect
month) in the requested timezone. Aggregations merge overlapping sessions into unions (earliest
start to latest end) for summary/heatmap/streaks, the TIME_OF_DAY distribution and the week-hour
heatmap; TIME_OF_DAY
buckets follow the plugin's statistics (Night 00:00-05:59, Morning 06:00-11:59, Daytime 12:00-17:59,
Evening 18:00-23:59) and slice bucket-spanning sessions at bucket boundaries, so its entries sum to
the summary total; other distributions accumulate raw durations per session, matching the plugin
StatisticsView semantics. `GET /api/v1/stats/summary` also measures the first five metrics against
the previous **complete** period — yesterday, last week, last month, last year — so a change is
stated against a finished baseline rather than the same period to date: each comparison carries
`previousSeconds` and a two-decimal `changePercent`, which is `null` when that period holds no
coding (and for `dailyAverage`, which compares against its own value at the end of yesterday, on
the first coding day). All five keys are always present and `total` has no comparison. Badges
are unlocked lazily on query — 67 badges: 51 perpetual ones across seven families (streak 8, total
duration 8, language count 9, early bird 8, night owl 8, daily burst 5, perfect month 5) plus 16
windowed ones that reset every period (daily 3, weekly 5, monthly 4, yearly 4) — and unlock records
are idempotent (one `ACHIEVEMENT_UNLOCKED` audit event per badge per period). `unlockedAt` is
the instant the badge was **earned**, back-inferred from the session history by the same
computation that produced the progress (the third consecutive day, the moment a cumulative total
crossed its threshold), not the moment the page was opened; evaluation is lazy, so stamping the
observation time would misdate every badge a user collects before visiting. Reported progress is
monotonic: the highest value ever measured per family is persisted in `achievement_progress`, so
deleting sessions cannot make an awarded badge read "3 of 10" — the reported number is floored by
the stored mark and by the highest rung already earned for that family. Each badge carries
its family (`type`) and its 1-based rung within that family (`tier`), so a client groups badges into
ladders without maintaining its own code-to-family table; `window` (`LIFETIME` / `DAY` / `WEEK` /
`MONTH` / `YEAR`) says whether the badge resets, with `windowStart` / `windowEnd` giving the current
local period bounds (`null` for `LIFETIME`), and rungs are numbered per family *and* window, so a
day's 2-hour goal is not compared against the lifetime ladder's thresholds. Thresholds step up per
family so the gap
between consecutive unlocks grows, and `PERFECT_MONTH` measures the best calendar month's coverage
as a percentage of that month's own length (`progress` 0-100, `unit` `percent`), so a fully coded
28-day February scores 100 rather than being penalised for being short. Achievements responses are
cached in Redis for 60s under a format-versioned key and invalidated on push. Every badge also
carries its period history: `totalUnlocks` is how many periods it has been attained in and
`periodStreak` is how many periods in a row, ending with the current one. Those two are recomputed
from the session history rather than counted from the unlock rows, because evaluation is lazy — a
user who meets a weekly goal every week but only opens the page once would otherwise be told they
attained it once; the stored rows are unioned in so a period whose sessions were later deleted still
counts. A `LIFETIME` badge reports 0 or 1 and a streak of 0. All stats endpoints
are rate-limited to 60 req/min (`RATE_LIMIT_001`).

**Materialization**: per-user per-UTC-day coding statistics are materialized in the
`daily_stats` table and maintained incrementally on every push (only the touched UTC dates are
recomputed); a user's first UTC read bootstraps the full history once under a per-user lock.
UTC statistics reads (summary / heatmap / streaks) are served from the materialized rows;
timezone-shifted requests aggregate live because a session crossing local midnight contributes
to two local days while living on a single UTC day. The summary's comparison windows follow the
same source as the values they compare against, so a multi-day baseline served from these rows can
differ from the live aggregation by up to one second per day it spans — the rows hold whole seconds
per day.

### Global Leaderboard

| Endpoint | Method | Description | Required Scope |
|----------|--------|-------------|----------------|
| `/api/v1/leaderboard` | GET | Global ranking by coding duration / streak / night-owl / early-bird / growth / active-days dimensions, over a lifetime or current period, with the calling user's rank and the size of the ranking | READ |

**Parameters**: `dimension` (`TOTAL` | `STREAK` | `NIGHT_OWL` | `EARLY_BIRD` | `GROWTH` |
`ACTIVE_DAYS` | `LANGUAGE`), `period` (`ALL` | `WEEK` | `MONTH` | `QUARTER` | `HALF_YEAR` | `YEAR`, defaults to the dimension's default:
`ALL` for every dimension except `GROWTH`, which defaults to `WEEK`), `limit` (default 20, max
100), `offset` (zero-based). Rankings are backed by Redis ZSets; a user's scores are recomputed
from the database after each successful push, so the ranking reflects new sessions immediately
without a full rebuild. When the scores themselves change, a one-off backfill recomputes every
user's scores shortly after startup: its marker key encodes the rule generation, a fingerprint of the
legal dimension/period matrix, and the vocabulary version. The fingerprint is derived from the enums,
so adding a dimension or opening one to more periods invalidates the marker on its own; only a
changed formula needs the generation bumped by hand. A change therefore forces exactly one
recompute, and a run interrupted by a restart simply
retries on the next start. Period keys are bucketed by their period start (ISO Monday for weeks) and
expire once the period closes (`ALL` never expires). An unsupported dimension/period combination
returns 400 `COMMON_003`. Endpoint is rate-limited to 60 req/min (`RATE_LIMIT_001`).

**Ranking semantics**: ranks use competition ordering — tied scores share a rank and the next
distinct score resumes after the gap (1, 2, 2, 4). `entries[].rank` and `currentUserRank` are
computed from the same rule, so a caller's rank inside a page always agrees with the rank reported
separately. `totalParticipants` reports the number of ranked members for the requested
dimension/period, letting a client render "rank N of M" without inferring the size from a full
page. Tied members have no defined order between them.

**Only activity earns a place**: every dimension except `GROWTH` ranks positive scores alone, so a
user who did none of the measured activity inside the window is **absent** from that board rather
than listed at `0` — absence keeps them out of `totalParticipants` and out of the ranks below them,
and their `currentUserRank` is `null`. `GROWTH` is the exception on purpose: its score is a delta,
and `0` ("no change") or a negative value ("less than the period before") are measurements, not
missing ones. A user who stops coding loses their place on the next recompute, exactly as they do
when their sessions are deleted.

**Dimensions and supported periods**:

| Dimension | Periods | Score |
|-----------|---------|-------|
| `TOTAL` | `ALL`/`WEEK`/`MONTH`/`QUARTER`/`HALF_YEAR`/`YEAR` | Merged overlap-collapsed duration in seconds |
| `STREAK` | `ALL`/`WEEK`/`MONTH`/`QUARTER`/`HALF_YEAR`/`YEAR` | Longest run of consecutive coding days **fully inside** the window (UTC) |
| `NIGHT_OWL` | `ALL`/`WEEK`/`MONTH`/`QUARTER`/`HALF_YEAR`/`YEAR` | Merged 22:00-05:00 window duration (UTC) |
| `EARLY_BIRD` | `ALL`/`WEEK`/`MONTH`/`QUARTER`/`HALF_YEAR`/`YEAR` | Merged 06:00-09:00 window duration (UTC) |
| `GROWTH` | `WEEK`/`MONTH`/`QUARTER`/`HALF_YEAR`/`YEAR` | Net growth against the immediately preceding period (can be negative) |
| `ACTIVE_DAYS` | `ALL`/`WEEK`/`MONTH`/`QUARTER`/`HALF_YEAR`/`YEAR` | Number of distinct coding days |
| `LANGUAGE` | `ALL`/`WEEK`/`MONTH`/`QUARTER`/`HALF_YEAR`/`YEAR` | Merged duration in **one** language, selected by the required `language` parameter |

`STREAK` ranks the longest run of consecutive coding days **fully inside** the requested window:
runs are cut at both boundaries, so a streak that began before the window or continues past it
contributes only the days inside it (`ALL` keeps the lifetime definition). The alternative — the
streak as of the window's close — is rejected because it is undefined while the current period is
still running and would change retroactively as the window advances.
`GROWTH` excludes `ALL` because an unbounded history has no preceding period to compare against.
`LANGUAGE` is the only partitioned dimension: there is no single "language" board, so a request must
name one (`dimension=LANGUAGE&language=Java`), and the parameter is required there and rejected for
every other dimension rather than silently ignored. The score is merged duration in that language, so
two overlapping sessions in it count once, and a user with no sessions in a language is simply absent
from its board. Boards exist only for languages the vocabulary recognizes and does not classify as
`Other`, which keeps the key space bounded by the vocabulary rather than by whatever strings clients
submit — an unrecognized value is still stored and reported for classification, it simply has no board
until classified. `GET /api/v1/leaderboard/languages` lists the boards a caller can rank inside — the
languages that hold someone, so that a selector built from it cannot offer a board that answers with
an empty page. `includeEmpty=true` returns the whole vocabulary instead, for a client that wants to
browse which boards exist at all; every entry carries its category, and a `hasMembers` flag in that
form. A language appears in the directory when someone is first ranked in it, and leaves when its last
member stops coding in it — which also happens to the score: a user whose last session in a language
is deleted (or whose session changes language) is removed from that board on the next recompute
rather than staying ranked at the score they had.

`ACTIVE_DAYS` measures consistency rather than volume, so it stays reachable for users with
limited hours.

**Language vocabulary**: sessions carry whatever identifier the originating IDE reports — JetBrains a
file type name (`JAVA`, `GitIgnore file`), VS Code a language id (`typescript`, `shellscript`) — and
the two disagree on naming and casing. Language statistics therefore normalize every raw value to a
canonical name before grouping, following GitHub Linguist, so one language occupies one bucket across
clients. The stored value is left as the client sent it: the canonical name is derived when it is
read, which keeps the raw fact recoverable and the mapping fixable without a migration. A value the
vocabulary does not recognize is preserved rather than folded into an "Other" bucket, and reported, so
a genuinely new language surfaces instead of disappearing.

### Public Configuration

| Endpoint                | Method | Description                                          |
|-------------------------|--------|------------------------------------------------------|
| `/api/v1/config/public` | GET    | Get public app config (current terms version, etc.)  |

### Email Verification Flow

```
User registers → System sends verification email
                      ↓
              User clicks link
                      ↓
GET /verify-email?token=xxx → User status: ACTIVE
```

**Security**: Tokens are SHA-256 hashed before storage. Raw tokens never stored in database.

### Account Lockout Strategy

Protects against brute-force attacks with automatic temporary lockout:

- **Max Failed Attempts**: 5 (configurable)
- **Sliding Window**: 15 minutes (configurable)
- **Lockout Duration**: 30 minutes (configurable)
- **Storage**: DB (default) — login attempts stored in `login_attempts` table with SHA-256 hashed email/IP
- **Hybrid Unlock**:
  - **Lazy unlock**: On next login attempt, precise sliding window check unlocks if lockout expired
  - **Scheduled sweep**: Hourly background task unlocks abandoned locked accounts (no recent attempts in window)
- **Audit Trail**: All lock/unlock events emit `ACCOUNT_LOCKED` / `ACCOUNT_UNLOCKED` audit actions
- **Locked Response**: Returns HTTP 403 with `retryAfter` timestamp in response body
  and `Retry-After` header (seconds until unlock) for frontend countdown UI
- **Rate Limit Response**: All rate-limited endpoints (429) return a `retryAfter`
  ISO-8601 timestamp in the response body and a `Retry-After` header with the
  remaining seconds until the window resets (read atomically from the Redis TTL)

Locked accounts are automatically unlocked after lockout period expires, either on next login or via scheduled cleanup.

## Interactive documentation

Once the service runs, Swagger UI is available at `http://localhost:8080/ctt-server/swagger-ui.html`; the machine-readable document is served at `/v3/api-docs`.
