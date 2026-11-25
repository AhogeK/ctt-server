# The production stack (P3.8)

Two colours behind an edge that owns the switch. A release replaces the colour
that is not serving, starts it, moves the edge, and fails the window — sending
traffic back — if the new colour breaches. The previous colour keeps running,
so a rollback is a switch, not a deploy.

The implementation is the one the laptop and the staging stack run
(`deploy/lib/release.sh` and `switch-colour.sh`); this directory holds only what
differs: the Compose overlay, the upstream variants, the deployment facts and
the wrappers that name this stack.

## What runs where

| Piece | Where |
|---|---|
| `ctt-prod-app-blue` | host network, application port 8090, management 8081 |
| `ctt-prod-app-green` | host network, application port 8093, management 8082 |
| `ctt-prod-edge` | host network, Caddy on `127.0.0.1:18080`, admin on `127.0.0.1:2019` |
| PostgreSQL | a process on this host, reached at `127.0.0.1:5432`, database `ctt_server_prod` |
| Redis | a process on this host, `127.0.0.1:6379`, database 2 |
| Prometheus | a process on this host, scraping 8081 as `slot=blue` and 8082 as `slot=green` |

The management ports are a contract with that Prometheus: the window cannot
judge a colour it cannot see. The application ports are whatever is free on the
host — 8080 belongs to Jenkins and 8181 to another service there — and green
cannot take 8082 because that is its own management port.

## Preparing the host, once

1. `cp deploy/prod/prod.env.example deploy/prod/prod.env` and fill it in
   (origins, GitHub OAuth client id, WebAuthn RP id, sender address). It is
   git-ignored: the values name one deployment.
2. Make sure `deploy/secrets/secrets.prod.enc.yaml` carries every key the
   production guard requires — `DB_PASSWORD`, `REDIS_PASSWORD`,
   `JWT_SECRET_KEY`, `OAUTH_TOKEN_ENCRYPTION_KEY`, `TOTP_ENCRYPTION_KEY`,
   `GITHUB_CLIENT_SECRET`, `RESEND_API_KEY`. A missing key fails the start, by
   design; the deploy job checks the rendered files before it switches anything.
3. Create the database and the role the application uses (`ctt_server_prod`,
   role `ctt_app`) — Flyway builds the schema on the first start, and nothing
   copies development data.
4. If a container from before P3.8 is still running (`ctt-server` or
   `ctt-server-test`), remove it: it holds the management port blue needs, and
   the symptom would be a colour that never becomes ready. `up.sh` refuses to
   start while one of them is up.
5. `./deploy/prod/up.sh` seeds the edge's upstream choice and starts the edge.

## The first release

Nothing is running yet, so the release must be told that this is a first
release: a colour the edge points at but no container runs is reported as
"nothing serving", and only the explicit flag turns that into a deployment
rather than a rolled-back host.

```sh
./deploy/prod/release.sh --next --first-release \
  --image ghcr.io/ahogek/ctt-server@sha256:<digest the Publish workflow printed>
```

`--next` releases the colour that is not serving, and blue when nothing serves.
The rule lives in `release.sh` so the deploy job never has to know colour names.

## Every release after that

```sh
./deploy/prod/release.sh --next --image <digest>
```

`--image` implies `--no-build`: the image is pulled by that digest and the
release refuses to continue unless the container is running that reference —
the window would otherwise be judging code nobody deployed.

## Rolling back

```sh
./deploy/prod/release.sh --rollback
```

The other colour is still running the previous image. A rollback is the same
switch and the same window; if the window on the older image breaches too, the
script says so and leaves traffic where it is, because there is nothing healthy
to return to.

## What this proves, and what it does not

The window proves that the colour now serving answers the public path, that the
edge's probe stays at 1, that the application is not answering 5xx and that its
p95 stays inside the 300 ms contract, for the length of the window, against the
image the release was told to run.

It does not prove that a later point in time is fine, and it does not roll back
to an older release than the previous colour — that colour keeps whatever it was
running. Both are properties of the release chain, not of this switch.
