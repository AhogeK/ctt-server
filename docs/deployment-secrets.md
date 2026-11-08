# Deployment Secrets

Credentials never travel through the Compose environment. The repository keeps one
SOPS-encrypted payload; a render step turns it into mode `0400` files on the host, and
Compose mounts those files as secrets. `docker inspect` therefore shows no password,
secret key or token — only the mount paths.

## What lives where

| Path | Committed | Purpose |
| --- | --- | --- |
| `.sops.yaml` | yes | SOPS creation rule and the age recipient |
| `deploy/secrets/secrets.enc.yaml` | yes (ciphertext only) | the single source of truth for values |
| `deploy/secrets/render.sh` | yes | decrypts and writes one `0400` file per key |
| `deploy/secrets/frp-token.sh` | yes | renders `FRP_TOKEN` to a `600` file on the access machine |
| `deploy/secrets/run/` | no (gitignored) | rendered files that Compose mounts |
| `~/.config/sops/age/keys.txt` | **never** | age private key (mode `600`) |

The encrypted payload currently carries `POSTGRES_PASSWORD`, `REDIS_PASSWORD`,
`JWT_SECRET_KEY`, `OAUTH_TOKEN_ENCRYPTION_KEY`, `GRAFANA_ADMIN_PASSWORD` and `RESEND_API_KEY`.
Add keys with `sops deploy/secrets/secrets.enc.yaml`: it decrypts in memory, opens `$EDITOR`
and re-encrypts on save.

Two limits are deliberate and live in the plan's phase table, not only here: the payload currently
holds **local development values** (production values land with P0.16), and `FRP_TOKEN` is **not in
it yet** — `frp-token.sh` fails with "not in payload" until the token is added (P0.17). Do not
assume either is in place on a production host.

## Setup on a new machine

An age keypair alone cannot open an existing payload: SOPS needs one identity that is already a
recipient. Import the operator's key, or add a machine from a machine that can decrypt:

```bash
# on the machine that can already decrypt (the authorized one)
age-keygen -y ~/.config/sops/age/new-machine.pub   # or paste the new machine's public key
# add that age1… value to the `age:` list in .sops.yaml, then:
sops updatekeys deploy/secrets/secrets.enc.yaml

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
./deploy/secrets/render.sh        # writes deploy/secrets/run/* with mode 0400
./deploy/local/up.sh              # renders again, then starts the stack
```

How each service consumes the files:

- **Application**: `SPRING_CONFIG_IMPORT=optional:configtree:/run/secrets/` turns every
  mounted file into a property named after the file, so the existing `${DB_PASSWORD}`,
  `${REDIS_PASSWORD}`, `${JWT_SECRET_KEY}` and `${OAUTH_TOKEN_ENCRYPTION_KEY}`
  placeholders resolve without any code change.
- **PostgreSQL**: `POSTGRES_PASSWORD_FILE=/run/secrets/postgres_password`.
- **Redis**: a small wrapper writes a config file on tmpfs, hands it to the `redis` user and
  execs the server, so the password stays out of the process list; the health check reads the
  mounted file directly.
- **Grafana**: `GF_SECURITY_ADMIN_PASSWORD__FILE=/run/secrets/grafana_admin_password`; the
  anonymous Admin session stays enabled locally, so the password is only a fallback.

Outside Compose (`./gradlew bootRun`) the application still reads the same names from
`.env`, which stays gitignored and is not used by the container stack.

On Linux the bind-mounted files keep the host owner (Docker Desktop maps them to the container
user automatically). If the operator's UID differs from the container's, render with
`--mode 0440` and a group the container user belongs to, or run the render as that UID; the
default stays `0400`. Re-running the script removes files for keys that disappeared from the
payload, so a retired secret cannot keep working.

## Failing fast

Missing values stop the deployment instead of starting a half-configured service:

- the age identity is absent → `render.sh` exits with `missing age identity at …`;
- a rendered file is absent → Compose refuses to create the container
  (`invalid mount config … bind source path does not exist`);
- the JWT key is empty → `SecurityProperties.JwtProperties` rejects the blank value at
  startup (`@NotBlank`).

## Rotation

1. `sops deploy/secrets/secrets.enc.yaml`, change the value, save.
2. `./deploy/secrets/render.sh`.
3. Recreate the affected services: `docker compose … up -d --force-recreate <service>`.

Rotating `JWT_SECRET_KEY` invalidates every access and refresh token;
`OAUTH_TOKEN_ENCRYPTION_KEY` requires the rotation procedure in
[Security Architecture](security-architecture.md) so stored GitHub tokens stay readable.
Rotating the database password also needs the matching `ALTER ROLE` on PostgreSQL, and
the Redis password restart alone is enough.

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
# the mount exists and is readable only by its owner
docker exec ctt-app-blue ls -l /run/secrets
```

The acceptance for this work is exactly those two checks plus a healthy
`/actuator/health/readiness`, with the missing-file case reproduced at least once.
