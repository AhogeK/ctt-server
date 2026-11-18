# Staging

A second full stack on the same machine, for rehearsing a release and a restore
without touching the local stack or production. P3.6.

It is not a second production: no TLS, no external access, every port on
loopback. What it reproduces is the part of production that goes wrong — two
colours against one database, an edge that reloads instead of restarting, a
migration applied while an older release is still serving, and a database that
has to come back from a dump.

## Running it

```sh
./deploy/staging/up.sh                                      # secrets, jar, stack
./deploy/local/on-engine.sh ./deploy/staging/rehearse.sh     # publish, roll back, restore
./deploy/staging/down.sh                                    # stop, keep the volumes
./deploy/staging/down.sh --volumes                          # and delete staging's database
```

`up.sh` runs on the host because rendering secrets needs the SOPS key and
building the jar needs the toolchain. The rehearsal runs through
`deploy/local/on-engine.sh` because every step of it is a probe, and the colours,
the edge and Prometheus listen on the engine's loopback — on Docker Desktop that
is the VM's, not this machine's. On a Linux host the rehearsal runs directly.

## Ports

| What | Local stack | Staging |
|---|---|---|
| Edge (public path) | 18080 | 28080 |
| Blue colour | 18081 | 28081 |
| Green colour | 18082 | 28082 |
| Blue management | 8081 | 8181 |
| Green management | 8082 | 8182 |
| Caddy admin | 2019 | 2029 |
| Prometheus | 9090 | 9190 |
| Blackbox | 9115 | 9215 |
| PostgreSQL | 15432 | 25432 |
| Redis | 16379 | 26379 |
| Mailpit SMTP / UI | 1025 / 8025 | 2025 / 9025 |

## Isolation

Four things are namespaced, and each one was a failure the first time it was
missing:

* **Compose project `ctt-staging`** — the volumes carry it, so `postgres_data`
  here is a different volume from the local stack's and `down --volumes` cannot
  reach the local database.
* **Container names `ctt-stg-*`** — container names are global on a host.
* **Host ports** — the local colours are usually running while a rehearsal is.
* **Rendered secrets `deploy/secrets/run-staging/`** — sharing them would point
  staging's `postgres` and staging's application at different databases.

The database is `ctt_server_staging`, created by the same `postgres:latest` image
with the same hardening as the local one.

## What a rehearsal proves, and what it does not

`rehearse.sh` prints `STAGING: green` after: a release of the colour that was not
serving (new image, switch, 60 s window with the same assertions as a local
release: no probe failure, no 5xx, p95 ≤ 300 ms), a rollback with the previous
image (`--no-build`), and a restore of the database from a dump with the stack
confirmed to still serve.

What it does not prove: image rollback to an older *version* — staging builds
from the working tree, so a version that has been replaced would be rebuilt from
the current tree rather than restored from a registry. P3.8's digest-pinned
release is what closes that gap in production.

The restore drill is destructive on purpose: it drops the `public` schema,
restores the dump over the empty database and then asserts the marker row, the
Flyway history and the serving path. A backup nobody has restored is a copy. It
refuses to run without `--yes`, and it refuses a stack file whose database
container is not named `ctt-stg-*`, so `--yes` cannot reach another stack's data.

## The code it shares

`switch-colour.sh` and `release.sh` have one implementation, in `deploy/lib/`,
and take everything stack-specific from a stack file:

```sh
export CTT_STACK_FILE=deploy/staging/stack.env
exec ./deploy/lib/release.sh "$@"
```

`deploy/local/stack.env` and `deploy/staging/stack.env` set the same keys; that
is the whole difference between the two stacks as far as the scripts are
concerned. Adding a third stack is a new `stack.env` and a Compose overlay, not
a copy of either script.

`deploy/staging/docker-compose.staging.yaml` overrides only what Compose requires
to be unique (names, ports, secrets) plus the two settings that make it a
rehearsal: `SPRING_JPA_HIBERNATE_DDL_AUTO: validate` (production's rule, not the
local stack's `update`) and its own scrape config.
