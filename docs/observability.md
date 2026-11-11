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

# probes
curl -s http://127.0.0.1:8081/actuator/health/readiness                                        # {"status":"UP",...}

# scrape configuration
docker run --rm -v "$PWD/deploy/local/prometheus.yml:/etc/prometheus/prometheus.yml:ro" \
  --entrypoint promtool prom/prometheus:latest check config /etc/prometheus/prometheus.yml
```

Compose healthchecks read the management port, so a container is only healthy when its probe server
answers — the same surface Prometheus scrapes.

## Not covered yet

Business metrics (sessions, sync, leaderboard), distributed tracing and long-term metric retention
are separate pieces of work. Until they exist, the scrape carries the JVM, process and HTTP server
instruments that Micrometer registers by default.

## Related

- [Security Architecture](security-architecture.md) — the public allowlist and why health is reachable without a token
- [Production Configuration](production-configuration.md) — required environment values and the startup guard
- [Deployment Secrets](deployment-secrets.md) — compose environment channels
