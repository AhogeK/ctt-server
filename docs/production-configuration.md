# Production Configuration

`SPRING_PROFILES_ACTIVE=prod` turns the application into its deployment posture: API documentation is
off, CORS comes from an explicit origin list, GitHub OAuth credentials are mandatory, and
`ProductionConfigurationGuard` refuses to start while a development value is still in place. The guard
collects every violation into one message, so one restart lists everything to fix.

## Required configuration

| Setting | Channel | Notes |
| --- | --- | --- |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME` | environment | production database `ctt_server_prod`, role `ctt_app` |
| `DB_PASSWORD` | secret file | rendered from `secrets.prod.enc.yaml`; configtree exposes it as `DB_PASSWORD` |
| `REDIS_HOST`, `REDIS_PORT` | environment | database `2` is selected by the profile |
| `REDIS_PASSWORD` | secret file | the instance credential (shared; see below) |
| `JWT_SECRET_KEY`, `OAUTH_TOKEN_ENCRYPTION_KEY` | secret file | at least 32 characters; rotation rules below |
| `TOTP_ENCRYPTION_KEY` | secret file | no fallback in production; the public development value is rejected |
| `GITHUB_CLIENT_ID` | environment | the production OAuth App |
| `GITHUB_CLIENT_SECRET` | secret file | must join the production payload before activation |
| `PASSKEY_RP_ID`, `PASSKEY_ALLOWED_ORIGINS` | environment | the RP ID is a long-lived commitment |
| `FRONTEND_BASE_URL` | environment | the deployed site; email links, OAuth return and the CORS fallback use it |
| `CORS_ALLOWED_ORIGINS` | environment | optional, comma-separated; defaults to `FRONTEND_BASE_URL` |
| `RESEND_API_KEY` | secret file | SMTP password for `smtp.resend.com` |
| `MAIL_FROM_ADDRESS` | environment | sender address on a verified Resend domain |
| `AUDIT_RETENTION_DAYS` | environment, optional | the production profile defaults to `180` ([ADR-0002](decisions/ADR-0002-audit-retention-policy.md)); `0` stops deletion |
| `MANAGEMENT_PORT` | environment, optional | loopback management server for probes and metrics; defaults to `8081` and must differ from `SERVER_PORT` |
| `MANAGEMENT_ADDRESS` | environment, optional | defaults to `127.0.0.1` and must stay loopback in production — the guard rejects any other value, so the scraper shares the host network namespace |
| `TRACING_SAMPLING_PROBABILITY` | environment, optional | production profile defaults to `0.1`; a sampled trace is exported whole, an unsampled one is dropped |
| `TRACING_EXPORT_ENABLED` | environment, optional | production profile defaults to `true`; the guard refuses a production instance that disables it |
| `OTLP_TRACING_ENDPOINT` | environment, optional | collector address, defaults to `http://127.0.0.1:4318/v1/traces`; belongs to the deployment contract |

`SPRING_CONFIG_IMPORT=optional:configtree:/run/secrets/` maps every mounted file to a property of
the same name, so secret files are read without exporting anything. Compose already mounts
`DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET_KEY`, `OAUTH_TOKEN_ENCRYPTION_KEY` and
`RESEND_API_KEY` into the application; `TOTP_ENCRYPTION_KEY` and `GITHUB_CLIENT_SECRET` join
the payloads before a production instance can pass the guard.

Render the payload on the production host with `./deploy/secrets/render.sh --env prod`; see
[Deployment Secrets](deployment-secrets.md).

## What the profile changes

- `springdoc.api-docs.enabled` and `springdoc.swagger-ui.enabled` are `false`: `/ctt-server/v3/api-docs`
  and `/ctt-server/swagger-ui.html` return **404**. The paths stay in the public allowlist because they
  no longer exist.
- CORS reads `CORS_ALLOWED_ORIGINS` (and `CORS_ALLOWED_ORIGIN_PATTERNS`), falling back to
  `FRONTEND_BASE_URL`. The base configuration no longer ships the `ctt.example.com` placeholder.
- `ctt.security.oauth.frontend-url` and `ctt.mail.frontend.base-url` require `FRONTEND_BASE_URL`: a
  production instance cannot silently keep pointing at `localhost:5173`.
- `ctt.audit.retention.days` defaults to `180`; `AUDIT_RETENTION_DAYS` overrides it and `0` stops
  deletion (in a Compose deployment the variable reaches the container through the production
  overlay described under Compose wiring). The base profile keeps every row.
  `AuditRetentionJob` logs the effective policy at startup and logs every sweep (cutoff, deleted
  rows, duration), including sweeps that delete nothing, so activation and each run can be verified
  from the log. A sweep that fails logs its cutoff and the rows already deleted before the exception,
  so a failing run is as visible as a successful one.
  A disabled period is logged at info level, and as a warning in the production profile.

## Compose wiring

The base `app` service forwards only `SPRING_PROFILES_ACTIVE`, `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME`, `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`, `SPRING_MAIL_HOST`,
`SPRING_MAIL_PORT`, `MANAGEMENT_PORT` and `MANAGEMENT_ADDRESS` from `.env`; the remaining environment
values in the table above, and any deployment-only secret file, are supplied by the production
Compose overlay, which this repository does not ship. That file is the local bridge stack, so it
defaults `MANAGEMENT_ADDRESS` to `0.0.0.0` and publishes the port on host loopback; a production
deployment runs host-networked with the loopback default. Its `depends_on` entries name the
development companions, so a production start uses `docker compose up -d --no-deps app`.

## Guard rules

| Check | Rejected when |
| --- | --- |
| API documentation | either `springdoc` flag is `true` |
| CORS | list is empty, or an entry is not an explicit `https://` host — `localhost`, `127.0.0.1`, `0.0.0.0`, `example.com`, `*` and placeholder words are refused |
| Frontend URLs | not a public `https://` URL (same placeholder rules) |
| GitHub OAuth | client id or secret is blank or a development literal |
| JWT key | blank, shorter than 32 characters, or carrying a development word |
| OAuth encryption key | same as the JWT key |
| Database password | blank, shorter than 16 characters, or carrying a development word |
| Redis password / Resend key / TOTP key | blank, a known development literal, or carrying a development word; Redis has no length rule because the instance credential is shared |
| Management surface | `management.server.port` missing, zero, or equal to `server.port`; `management.server.address` not a loopback address |
| Tracing surface | `management.tracing.enabled=false`; export disabled; a sampling ratio of zero or less; `management.tracing.propagation.produce` without `W3C` |

"Development word" means `dev`, `test`, `local`, `dummy`, `sample` or `change` delimited by
non-alphanumeric characters, plus the literals published in this repository (`ctt_local_pass`,
`ctt_redis_pass`, `admin`, `password`, `postgres`, `redis`, `secret`, and the TOTP fallback). A
base64 secret only matches when `/`, `+` or `=` happen to surround one of those words; regenerating
the value resolves it.

## Verifying a deployment

```bash
curl -s -o /dev/null -w '%{http_code}\n' https://<host>/ctt-server/v3/api-docs      # 404
curl -s -o /dev/null -w '%{http_code}\n' https://<host>/ctt-server/swagger-ui.html  # 404
journalctl -u <service> | grep 'Production configuration accepted'
journalctl -u <service> | grep 'Audit retention'                                   # policy and every sweep
```

The retention lines must show `enabled: deleting rows older than 180 days` at startup, then one
`sweep finished: cutoff=…, deleted=…` line per hour — an empty run prints `deleted=0`.

`curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8081/actuator/prometheus` must answer
`200`, and the same path under the application port must answer `404`. See
[Observability](observability.md).

Removing `GITHUB_CLIENT_SECRET`, pointing `CORS_ALLOWED_ORIGINS` at `https://ctt.example.com`, or
setting the JWT key to a development literal must stop the application with
`Production configuration rejected:` and the offending property.

## Shared Redis instance

Production uses database `2` of the same Redis server as development and another service (database
`1`). Redis has one `requirepass` and only the `default` user, so the password is an **instance**
credential, not a production-only one: rotating it changes every client. Do not rotate it without
coordinating the owner of database `1`; a per-environment password needs ACL users or a separate
instance, which is a separate change. Consequently the production payload carries the same Redis
password as development.

## Related

- [Deployment Secrets](deployment-secrets.md) — payloads, rendering and rotation
- [Production Data Isolation](production-data-isolation.md) — database, role and network boundaries
- [Security Architecture](security-architecture.md) — authentication and token rotation
- [Observability](observability.md) — management server, exposed endpoints and the scrape shape
