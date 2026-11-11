# Deployment Secrets

Credentials never travel through the Compose environment. The repository keeps two
SOPS-encrypted payloads — `secrets.enc.yaml` for development and `secrets.prod.enc.yaml` for
production — and a render step turns the selected one into mode `0440` group-readable files on the
host. Compose mounts those files as secrets, so `docker inspect` shows no password, secret key or
token — only the mount paths.

## What lives where

| Path | Committed | Purpose |
| --- | --- | --- |
| `.sops.yaml` | yes | SOPS creation rule and the age recipient |
| `deploy/secrets/secrets.enc.yaml` | yes (ciphertext only) | development values |
| `deploy/secrets/secrets.prod.enc.yaml` | yes (ciphertext only) | production values |
| `deploy/secrets/render.sh` | yes | decrypts one payload (`--env dev` by default, or `--env prod`) into `0440` files |
| `deploy/secrets/frp-token.sh` | yes | renders `FRP_TOKEN` to a `600` file on the access machine |
| `deploy/secrets/run/` | no (gitignored) | rendered files that Compose mounts |
| `~/.config/sops/age/keys.txt` | **never** | age private key (mode `600`) |

The development payload carries `POSTGRES_PASSWORD`, `DB_PASSWORD` (the same value, used by the
application), `REDIS_PASSWORD`, `JWT_SECRET_KEY`, `OAUTH_TOKEN_ENCRYPTION_KEY`,
`GRAFANA_ADMIN_PASSWORD` and `RESEND_API_KEY`. The production payload carries `DB_PASSWORD` (the
`ctt_app` role), `REDIS_PASSWORD`, `JWT_SECRET_KEY`, `OAUTH_TOKEN_ENCRYPTION_KEY`,
`GRAFANA_ADMIN_PASSWORD` and `RESEND_API_KEY`; it has no `POSTGRES_PASSWORD` because production
uses the host database, not the Compose container. Edit either file with
`sops deploy/secrets/secrets.enc.yaml` or `sops deploy/secrets/secrets.prod.enc.yaml`.

Two constraints remain. `FRP_TOKEN` is **not in either payload yet**; `frp-token.sh` exits
with "not in payload" until it is added. The Redis password is **shared by every database on the
instance**: Redis has one `requirepass` and only the `default` user, and database `1` belongs to
another service, so rotating it is a coordinated change rather than a per-environment one.

## Setup on a new machine

An age keypair alone cannot open an existing payload: SOPS needs one identity that is already a
recipient. Import the operator's key, or add a machine from a machine that can decrypt:

```bash
# on the machine that can already decrypt (the authorized one)
age-keygen -y ~/.config/sops/age/new-machine.pub   # or paste the new machine's public key
# add that age1… value to the `age:` list in .sops.yaml, then:
sops updatekeys deploy/secrets/secrets.enc.yaml
sops updatekeys deploy/secrets/secrets.prod.enc.yaml

# on the new machine: import the existing private key, do not generate a fresh one
mkdir -p ~/.config/sops/age
cp /path/to/authorized-keys.txt ~/.config/sops/age/keys.txt
chmod 600 ~/.config/sops/age/keys.txt
```

Generating a brand-new identity with `age-keygen` on the new machine and running `sops updatekeys`
there fails: it has no key that can unwrap the data key (the observed error is
`no identity matched any of the recipients`).

`sops` looks for `$SOPS_AGE_KEY_FILE` first; the render script defaults it to
`~/.config/sops/age/keys.txt` and fails immediately when the file is absent.

## Daily flow

```bash
./deploy/secrets/render.sh                 # development payload → deploy/secrets/run
./deploy/secrets/render.sh --env prod      # production payload → deploy/secrets/run
./deploy/local/up.sh                       # renders the development payload again, then starts the stack
```

The rendered directory holds one environment at a time: the last `render.sh` invocation wins.

How each service consumes the files:

- **Application**: `SPRING_CONFIG_IMPORT=optional:configtree:/run/secrets/` turns every
  mounted file into a property named after the file, so the existing `${DB_PASSWORD}`,
  `${REDIS_PASSWORD}`, `${JWT_SECRET_KEY}`, `${OAUTH_TOKEN_ENCRYPTION_KEY}` and
  `${RESEND_API_KEY}` placeholders resolve without any code change.
- **PostgreSQL**: `POSTGRES_PASSWORD_FILE=/run/secrets/postgres_password`.
- **Redis**: a small wrapper writes a config file on tmpfs, hands it to the `redis` user and
  execs the server, so the password stays out of the process list; the health check reads the
  mounted file directly.
- **Grafana**: `GF_SECURITY_ADMIN_PASSWORD__FILE=/run/secrets/grafana_admin_password`; the
  anonymous Admin session stays enabled locally, so the password is only a fallback.

Outside Compose (`./gradlew bootRun`) the application still reads the same names from
`.env`, which stays gitignored and is not used by the container stack.

On Linux, bind-mounted files keep the host owner and group. `render.sh` defaults to
mode `0440` and group `SECRET_GID` (default `1000`); Compose adds that GID to the
application and Grafana containers through `group_add`, so non-root processes can read
the secrets. `up.sh` and the Jenkins pipeline export the invoker's `id -g`; for a manual
render, export `SECRET_GID` yourself when the host group is not `1000`. Re-running the
script removes files for keys that disappeared from the payload, so a retired secret
cannot keep working.

## Failing fast

Missing values stop the deployment instead of starting a half-configured service:

- the age identity is absent → `render.sh` exits with `missing age identity at …`;
- a rendered file is absent → Compose refuses to create the container
  (`invalid mount config … bind source path does not exist`);
- the JWT key is empty → `SecurityProperties.JwtProperties` rejects the blank value at
  startup (`@NotBlank`);
- the production profile still carries a development value or a placeholder → `ProductionConfigurationGuard`
  rejects the startup and lists every offending property (see
  [Production Configuration](production-configuration.md)).

## Rotation

1. Edit the payload for that environment (`secrets.enc.yaml` or `secrets.prod.enc.yaml`), save.
2. Render with the matching environment: `./deploy/secrets/render.sh [--env prod]`.
3. Recreate the affected services: `docker compose … up -d --force-recreate <service>`.

Rotating `JWT_SECRET_KEY` invalidates every access and refresh token;
`OAUTH_TOKEN_ENCRYPTION_KEY` requires the rotation procedure in
[Security Architecture](security-architecture.md) so stored GitHub tokens stay readable.
Rotating the database password also needs the matching `ALTER ROLE` on PostgreSQL. Rotating the
Redis password changes the instance password for **every** database and client, including the
service that owns database `1` — coordinate it before touching that value.

## frp token (access machine)

The tunnel token is not part of this repository's Compose stack. Keep it in the same
SOPS payload under a `FRP_TOKEN` key, then on the access machine:

```bash
./deploy/secrets/frp-token.sh                   # writes /etc/frp/frp-token, mode 600
./deploy/secrets/frp-token.sh /etc/frp/token    # or an explicit target
```

The script fails until `FRP_TOKEN` exists in the payload (`sops deploy/secrets/secrets.enc.yaml`).
Point the `frps.toml` / `frpc.toml` authentication entry at that file instead of inlining the
value, then restart the unit. The token never enters shell history, Compose files or
`docker inspect`.

## Verifying a deployment

```bash
# no secret value appears in any container environment
docker inspect ctt-app-blue --format '{{json .Config.Env}}'
# the mount exists and is group-readable (0440)
docker exec ctt-app-blue ls -l /run/secrets
```

The acceptance for this work is exactly those two checks plus a healthy
`/actuator/health/readiness`, with the missing-file case reproduced at least once.
