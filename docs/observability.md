# Observability

The application keeps its operational endpoints on a second, loopback-only HTTP server. Probes and
metrics therefore never share the port that serves user traffic, and nothing in the public API
surface reveals them.

## Two servers

| Server | Address | Serves | Configured by |
| --- | --- | --- | --- |
| application | `server.port` (default `8080`), context path `/ctt-server` | the REST API | `SERVER_PORT` |
| management | `127.0.0.1:8081` | `health`, `info`, `prometheus` | `MANAGEMENT_PORT`, `MANAGEMENT_ADDRESS` |

The management server has no context path: probes are `/actuator/health/liveness`,
`/actuator/health/readiness` and metrics are `/actuator/prometheus`. The application port serves none
of them: the allowlisted paths answer **404** under `/ctt-server/`, and any other `/actuator/**` path
is rejected by the security chain with **401** before dispatch. No management payload is reachable
there either way.

A management port that resolves to `server.port` is refused at startup
(`Management-specific server address cannot be configured as the management server is not listening on a separate port`).
That is deliberate: while both servers were one, metrics were reachable wherever the API was.

## Tracing

Every request is traced with Micrometer Tracing over OpenTelemetry. One identifier ties the three
places an operator looks at: the `traceId` in the log line, the `X-Trace-Id` response header and the
span the trace backend stores. They agree because all three come from the server span. Sampling decides
what the backend keeps, not whether the identifier exists: an unsampled request still writes its
trace id into the header and the log line, so correlation survives for traffic that is not recorded.

| Setting | Default | Purpose |
| --- | --- | --- |
| `management.tracing.sampling.probability` | `1.0` base, `0.1` production (`TRACING_SAMPLING_PROBABILITY`) | share of traces recorded; a sampled trace is complete, an unsampled one is dropped whole |
| `management.tracing.export.enabled` | `false` base, `true` production (`TRACING_EXPORT_ENABLED`) | whether spans leave the process over OTLP |
| `management.opentelemetry.tracing.export.otlp.endpoint` | `http://127.0.0.1:4318/v1/traces` (`OTLP_TRACING_ENDPOINT`) | OTLP receiver address (Tempo in the deployment stack); part of the deployment contract, not a code constant |

The incoming context is W3C `traceparent`. The gateway sets it, the server span continues that
trace, and `traceparent` is also what the exporter reports, so a span tree started by the edge stays
one tree.

The sampler is parent-based: a trace whose incoming `traceparent` is already marked sampled is
recorded whatever the ratio says, and one marked unsampled is dropped whole. The ratio therefore
applies to spans that start here — scheduled jobs and any request that arrives without a remote
parent — while the edge keeps the decision for proxied traffic. A gateway that always marks traffic
sampled bypasses the production ratio; if that matters, the ratio belongs at the gateway.

The `X-Trace-Id` request header is a fallback for callers that cannot send W3C context, not a way to
choose the identifier: it is honoured only when tracing is off or a request produced no span, which
keeps a client from detaching its logs from a trace the server exports. Outbound calls are traced
too — `CaptchaService` and `GitHubOAuthClient` build their clients from the application
`RestClient.Builder`, so each call becomes a client span and carries `traceparent` downstream.

The access log line is written after the request completes but still inside the MDC scope
(`RequestLoggingFilter` runs inside `RequestContextInitializerFilter`), so `ACCESS_LOG` lines carry
the same `traceId` as the application log line and the audit record. The production JSON encoder
writes the MDC map, so a log search and a trace view describe the same request.

Propagation does not depend on the exporter. Spring Boot only builds an OpenTelemetry propagator
while export is enabled and otherwise installs a no-op one, which would silently start a new trace
for every request; `TracingPropagationConfiguration` supplies the W3C propagator whenever export is
off, so local runs without a collector still correlate. Production refuses to start with the export
disabled or a zero sampling ratio (`ProductionConfigurationGuard`), so a production instance always
exports.

`OtlpTraceExportIntegrationTest` performs a request against a stub collector and checks that the
exported protobuf payload carries the caller's trace id; `TracingIntegrationTest` checks the header
agreement over the real servlet container; `TracingSamplingIntegrationTest` drives the sampler to
zero and proves that correlation survives when nothing downstream is recorded; and
`RequestContextInitializerFilterTest` covers the fallback when no tracer is present.

### Log fields

Every line carries `traceId`, `clientIp` (masked) and, once the security chain has resolved a
principal, `userId`. The user dimension is the UUID only — an email would put personal data into
every log line and into whatever store ships them. Console output renders `[U:<uuid>]` and
`[U:anonymous]` for unauthenticated traffic; the production JSON encoder writes the MDC map, so the
same value is a queryable field.

The value is added inside the security chain (`UserMdcFilter`, as soon as the context holds a
principal, via `CurrentUserProvider`) and removed by `RequestContextInitializerFilter`, which owns
the MDC lifetime. That is why a filtered line and an empty context after the response hold at the
same time: the access line is written by `RequestLoggingFilter`, still inside the scope, and the
cleanup runs when the outermost context filter returns — so a reused thread never inherits the
previous request's user. `UserMdcFilterTest` covers the filter and `UserMdcIntegrationTest` covers
the observable contracts (the line carries the identifier, the context is empty afterwards, and a
rejection raised after authentication keeps the identifier).

The injection point sits before the terms gate, so a request the gate rejects (403) is attributed to
its user in both the access line and the gate's own warning; browser-session and API-key requests
both reach the injection point with a principal. What stays `anonymous` is traffic that never
authenticated (401), and work that runs after the response on another thread — asynchronous audit
writes, mail delivery and leaderboard recomputation — where the audit row carries the user instead.

## Endpoints

`management.endpoints.web.exposure.include` lists `health`, `info` and `prometheus` only. The
generic `metrics` endpoint stays closed because it exposes meter names and tags one by one, which
turns the management port into a discovery surface; the scrape endpoint carries the same data in the
format Prometheus expects.

| Endpoint | Purpose |
| --- | --- |
| `/actuator/health/liveness` | `livenessState` only — restart signal |
| `/actuator/health/readiness` | `readinessState`, `db` and `redis`; `DOWN`/`UNKNOWN` maps to `OUT_OF_SERVICE` with HTTP 503 |
| `/actuator/health` | aggregate of the same indicators |
| `/actuator/info` | build information (`info.app.version`) |
| `/actuator/prometheus` | Prometheus scrape payload |

`ActuatorSurfaceTest` pins the runtime surface (the three exposed endpoints, probes answering on the
management port, metrics present there, no management payload on the application port), and
`ManagementSurfaceContractTest` pins the declared defaults (`8081`, `127.0.0.1`, the endpoint list and
the export flag) so the random test port cannot hide drift.

## Prometheus registry

`micrometer-registry-prometheus` is declared without a version because the Spring Boot BOM manages
it, and the Prometheus registry and scrape endpoint are only created when the export is enabled
explicitly:

```yaml
management:
  prometheus:
    metrics:
      export:
        enabled: true
```

Without that property the endpoint answers 404 even though the registry is on the classpath.

## Business metrics

Six business signals are registered by the application itself. All of them live under the
`cttserver.business` prefix, so an alert can select them with one matcher, and each business
dimension comes from an enumeration in the domain — never a user, device, session or email value,
and never free text such as a platform string.

| Prometheus name | Type | Tags | Answers |
| --- | --- | --- | --- |
| `cttserver_business_sync_count_total` | counter | `operation` (`PUSH`/`PULL`), `outcome` (`SUCCESS`/`CONFLICT`/`FAILURE`), `payload` (`CHANGED`/`EMPTY`/`UNKNOWN`) | how much sync traffic arrives, how much of it fails or is answered by a conflict, and how often a poll carries nothing. `CONFLICT` means the client's state differed and lost; an unchanged re-push is `SUCCESS` + `EMPTY`, and `UNKNOWN` marks a failed operation whose payload never became known |
| `cttserver_business_sync_duration_seconds` | timer | same three | how slow the two operations are; read p50/p95/p99 with `histogram_quantile()` over the `_bucket` series |
| `cttserver_business_ratelimit_rejections_total` | counter | `type` (`IP`/`USER`/`EMAIL`/`GLOBAL`) | whether the limiter is the reason clients get refused; `GLOBAL` is the installation-wide ceiling, which has no dimension |
| `cttserver_business_mail_pending` | gauge | `status` (`PENDING`/`FAILED`/`CANCELLED`) | the mail backlog: `PENDING` waits for a send, `FAILED` is scheduled for a retry, `CANCELLED` exhausted its retries and is a dead letter. `SENDING` is deliberately absent — a row flaps through it and returns to `PENDING` after the zombie reset, so it would add noise without answering a question |
| `cttserver_business_leaderboard_pending_recomputes` | gauge | none | whether leaderboard work is falling behind (depth of the Redis dirty set) |
| `cttserver_business_audit_events_total` | counter | `severity` (`INFO`/`WARNING`/`CRITICAL`), `outcome` (`WRITTEN`/`DROPPED`) | audit events offered to the writer and how many were lost. `WRITTEN` is counted only after the row's own transaction commits, so it counts durable rows, and a missing severity is tagged `UNKNOWN` |

Counters only move forward, so a restart resets them to zero — read them with `rate()` or
`increase()` over the scrape interval, never as a raw total. Client-side percentiles are not
published, because two slots scraped separately cannot be merged; the histogram buckets are, so
quantiles stay aggregatable. Both pending gauges are read when Prometheus scrapes — one SQL count
per status and one Redis `SCARD`. A failed read reports `NaN`, which is a sample carrying no value,
not a missing series: `absent()` stays empty and `sum()` propagates it, so an alert tests
`isnan(cttserver_business_mail_pending)` instead of `absent()`.

The three-tag budget covers the tags the application sets. Prometheus histograms add `le` to
`_bucket` series, which is structural, not a business dimension.

The names are pinned twice: `BusinessMetricsTest` asserts every meter and its tags, and
`BusinessMetricsScrapeIntegrationTest` scrapes the management port and fails if a family is missing
or a series carries more than three business labels. A renamed metric therefore breaks the build
instead of an alert. The per-domain gauges are covered by `MailOutboxMetricsTest` and
`LeaderboardMetricsTest`, including the `NaN` path.

## Scraping

A scrape needs two settings: `metrics_path: /actuator/prometheus` and a loopback target.

The local stack (`deploy/local/prometheus.yml`) scrapes both slots, because a blue/green deployment
runs them at the same time and each needs its own management port:

| Slot | Application | Management |
| --- | --- | --- |
| blue | `127.0.0.1:18081` | `127.0.0.1:8081` |
| green | `127.0.0.1:18082` | `127.0.0.1:8082` |

```yaml
- job_name: ctt-server
  metrics_path: /actuator/prometheus
  static_configs:
    - targets: ["127.0.0.1:8081"]
      labels:
        slot: blue
    - targets: ["127.0.0.1:8082"]
      labels:
        slot: green
```

`slot` is the only label the scrape adds; a target that is not running reports `up 0`, which is how
the inactive slot appears. Blackbox probes read readiness on those same management ports and check
one public path through the edge, so a proxy failure is distinguishable from an application failure.

## Observability stack

`deploy/observability/docker-compose.yaml` runs the stack that consumes what the application
produces. Every service binds `127.0.0.1` and the Compose project uses the host network, so nothing
is published beyond the machine: an operator reaches a UI through an SSH tunnel
(`ssh -L 3000:127.0.0.1:3000 <host>`), which is also why the stack needs no authentication story of
its own yet.

| Service | Loopback | Role |
| --- | --- | --- |
| Prometheus | `9090` | scrapes the application and the exporters, evaluates rules |
| Alertmanager | `9093` | groups and routes alerts; no rules are loaded yet, so its receiver is a placeholder |
| Grafana | `3000` | dashboards over the three signals |
| Loki | `3100` | log store, fed by Alloy |
| Tempo | `3200` query, `4318` OTLP/HTTP, `4317` OTLP/gRPC | trace store, written by the application directly |
| Alloy | `12345` | reads container logs from the Docker API and pushes them to Loki |
| node_exporter | `9100` | host metrics |
| postgres_exporter | `9187` | database metrics |
| redis_exporter | `9121` | Redis metrics |
| blackbox_exporter | `9115` | public URL and readiness probes |

The trace path has no collector hop: the application's default
`http://127.0.0.1:4318/v1/traces` is Tempo itself, so a span written by the application is queryable
in Tempo without an OTel Collector to deploy, configure and monitor. The log path needs one piece of
glue because the application writes JSON to stdout: Alloy tails the containers whose name matches
`ctt-`, keeps the line intact and labels it with the container name only. Grafana parses the line with
`| json`, which is what keeps `traceId`, `level` and `uri` queryable without turning them into index
labels.

Retention is thirty days in each store, expressed where each one keeps it:

| Store | Setting |
| --- | --- |
| Prometheus | `--storage.tsdb.retention.time=30d` |
| Tempo | `backend_worker.compaction.block_retention: 720h` (Tempo 3 replaced the `compactor` block) |
| Loki | `limits_config.retention_period: 720h` plus `compactor.retention_enabled: true` and `delete_request_store: filesystem` |

Three containers need a secret, and each gets it without the value appearing in `docker inspect`:
Grafana reads `GF_SECURITY_ADMIN_PASSWORD__FILE`, the Postgres exporter loads the value from a
mounted file in an entrypoint shim and exports it only for its own process, and the Redis exporter
needs a JSON map of URI to password, which `deploy/secrets/redis-exporter-passwords.sh` renders
(`-redis.password-file`). `render.sh` writes the files `0440` for the group named by `SECRET_GID`,
and the two containers that are neither root nor that group receive it through `group_add`.
The stack reads the **production** payload (`render.sh --env prod`): the Postgres exporter defaults
to `ctt_server_prod` and the `ctt_app` role, and `POSTGRES_EXPORTER_URI` / `POSTGRES_EXPORTER_USER`
point it elsewhere when a deployment names things differently.

Public URLs are probed by blackbox, and the list lives outside this repository in the deployment's
`prometheus/targets/public-http.json` — start from `targets/public-http.json.example` and copy it into
place before the first start, because a missing file leaves the job with no targets and therefore no
`probe_success` series at all. Each entry carries a `module` label — `http_2xx` for pages that must
answer 2xx, `http_reachable` for services whose root answers something else by design — and a target
without one defaults to `http_2xx`. Because the list is a file, Prometheus picks up a change within a
minute without a restart.

Mount configuration directories, not single files. A bind-mounted file is pinned to the inode it had
when the container started, so rewriting the file on the host leaves the container reading the old
content — `POST /-/reload` answers 200 while nothing changed. Every service here mounts its
configuration directory for that reason.

The dashboard provisioned today is the local stack's JSON (`deploy/local/grafana-dashboard.json`),
mounted into Grafana's provisioning path so a fresh stack has something to look at. A later change
replaces it with the full set built as code; until then the panels are a starting point, not a
specification.

### Verifying the stack

Run these on the deployment host after `docker compose up -d`:

```bash
# Every container up, the three with healthchecks healthy
docker ps --filter name=ctt- --format '{{.Names}} {{.Status}}'

# Public probes carry data; `up` only says blackbox answered the scrape
curl -s --get --data-urlencode 'query=probe_success{job="blackbox-public"}' \
  http://127.0.0.1:9090/api/v1/query

# The trace path end to end: Tempo answers 200 for a trace it stored
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:3200/api/traces/<trace-id>

# The log path: a container's own lines arrive with the container label (the
# query API answers immediately; /ready stays 503 for about ten minutes after a
# start while the compactor waits for its ring to settle)
curl -s --get --data-urlencode 'query={container="ctt-prometheus"}' --data-urlencode 'limit=1' \
  http://127.0.0.1:3100/loki/api/v1/query_range

# Value from the probes: TLS expiry and HTTP status per target
curl -s --get --data-urlencode 'query=probe_ssl_earliest_cert_expiry' http://127.0.0.1:9090/api/v1/query
```

Until the application is deployed, the two `ctt-server` scrape targets report `up 0` and the two
readiness probes report `probe_success 0` with a sub-millisecond duration — a refused connection, not
a broken stack. The real series and span tree are verified once the production overlay puts the
application on the host.

## Deployment rule

Publish the management port on loopback only. With bridge networking that means binding the host
side, for example `127.0.0.1:8081:8081`, while the container itself may listen on its own interfaces;
with host networking the default `MANAGEMENT_ADDRESS=127.0.0.1` already restricts it. Never publish
`MANAGEMENT_PORT` on a public interface: its endpoints are unauthenticated by design, because a
scraper cannot hold a user session.

The repository's Compose files follow that shape: the base file is the local bridge stack, so it
binds `MANAGEMENT_ADDRESS=0.0.0.0` inside the container and publishes
`${MANAGEMENT_BIND_ADDRESS:-127.0.0.1}:8081`; the blue/green overlay runs on host networking and
keeps `MANAGEMENT_ADDRESS=127.0.0.1`.

Production keeps the loopback address: `ProductionConfigurationGuard` rejects any other value, so a
production deployment runs the application on the host network (as the blue/green overlay does) and
the scraper reads `127.0.0.1:8081` directly. A bridge container cannot hand its loopback management
server to a host scraper, and rebinding it to the container interface is exactly the exposure the
guard refuses.

## Metric labels

Labels must stay bounded. Tag metrics by operation, outcome, HTTP status class or route template —
never by user id, device id, email, raw URL or free-text error message. A new label multiplies the
series count for every existing one, and Prometheus keeps them all in memory.

## Verification

```bash
# metrics answer on the management port
curl -s http://127.0.0.1:8081/actuator/prometheus | grep -m1 '# HELP'

# the application port serves no management endpoint
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/ctt-server/actuator/prometheus   # 404
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/ctt-server/actuator/health       # 404
# other management paths stop at the security chain instead
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/ctt-server/actuator/metrics      # 401

# business metrics appear once the corresponding path has run
curl -s http://127.0.0.1:8081/actuator/prometheus | grep -c '^cttserver_business_'

# probes
curl -s http://127.0.0.1:8081/actuator/health/readiness                                        # {"status":"UP",...}

# correlation: the response header repeats the trace id the caller sent
curl -s -D - -o /dev/null -H 'traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01' \
  http://127.0.0.1:8080/ctt-server/api/v1/config/public | grep -i '^x-trace-id'

# scrape configuration
docker run --rm -v "$PWD/deploy/local/prometheus.yml:/etc/prometheus/prometheus.yml:ro" \
  --entrypoint promtool prom/prometheus:latest check config /etc/prometheus/prometheus.yml
```

Compose healthchecks read the management port, so a container is only healthy when its probe server
answers — the same surface Prometheus scrapes.

## Not covered yet

Long-term metric retention is configured per store (see the table above) but has not yet been observed
across a month of production traffic. Alert rules, receivers and the on-call path are not part of the
stack yet: it runs Alertmanager with a placeholder receiver so the wiring is exercised, and until
rules land the business metrics above are what a dashboard or a first alert would read.

## Related

- [Security Architecture](security-architecture.md) — the public allowlist and why health is reachable without a token
- [Production Configuration](production-configuration.md) — required environment values and the startup guard
- [Deployment Secrets](deployment-secrets.md) — compose environment channels
