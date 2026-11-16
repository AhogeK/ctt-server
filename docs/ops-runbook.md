# Operations runbook

Operational procedures for running ctt-server. Each chapter is written to be followed under pressure:
what you see, what to check first, what to do, what to write down. Commands assume a shell on the
deployment host; every observability surface listens on loopback and is reached through an SSH tunnel.

## Alerts and on-call

### What a page looks like

A notification is one plain-text Telegram message per group of alerts, in Alertmanager's own layout:
an `Alerts Firing:` or `Alerts Resolved:` header, then each alert's `Labels:` and `Annotations:`, then
the `Source:` link back to the Prometheus expression.

```
Alerts Firing:
Labels:
 - alertname = PublicEndpointDown
 - host = deployment
 - instance = https://example.com/
 - severity = critical
Annotations:
 - description = probe_success has been 0 for five minutes.
 - summary = https://example.com/ stopped answering
Source: http://prometheus:9090/graph
```

There is no severity prefix and no one-line-per-alert digest: severity is a label like any other, so the
severity you act on is read from the `Labels:` block. Several alerts in one group are appended after the
header, each with its own labels and annotations. `critical` groups repeat hourly while the condition
holds, `warning` groups every four hours, and `info` groups are never sent.

Severity is the routing contract, not a guess: `critical` means a user-visible failure now or evidence
that is lost for good, `warning` means capacity pressure or a degraded-but-working feature, and `info`
is never notified — it only shows up in the [dashboards](observability.md#dashboards).

### The first five minutes

1. Read the rule name and the summary. They name the symptom, not a cause; do not start changing
   anything before you have a number that confirms it.
2. Open the matching dashboard panel. The panel and the rule read the same metric, and the panel draws
   the contract line the rule compares against, so the shape of the curve tells you whether the
   condition is a spike or a plateau.
3. Confirm with one query rather than by memory:

   ```bash
   docker exec ctt-prometheus wget -qO- \
     'http://127.0.0.1:9090/api/v1/query?query=probe_success%7Bjob%3D%22blackbox-public%22%7D'
   ```

4. Write down what you saw, in the order you saw it. A resolved alert with no note is a lesson lost.

### Per-alert triage

| Alert | Check first | Act when |
| --- | --- | --- |
| `TunnelDown` | `probe_success` for every public target; `systemctl status frps` on the edge host and `frpc` on the deployment host | all probes fail: restore the tunnel before anything else; per-endpoint alerts underneath this one are expected and suppressed |
| `PublicEndpointDown` | which `instance` label failed; resolve it from the host, then probe again from the deployment host | one endpoint fails while others answer: the site or its nginx is down, or DNS moved |
| `CertificateExpiring` | `probe_ssl_earliest_cert_expiry - time()` for the failing instance; `nginx -T` on the edge host | renew before the window closes; if renewal is blocked, silence with the ticket number instead of letting it repeat |
| `HostDiskSpaceLow` | `df -h /`, then the largest directories under the Docker data root | clear images and logs deliberately (`docker system df` first); never delete volume data to buy space |
| `Http5xxRatioHigh` | the requests dashboard, 5xx by status; application log at `WARN`/`ERROR` with the `X-Trace-Id` of a failing request | a code or dependency defect: roll back with the release procedure rather than patching live |
| `HttpP95LatencyHigh` | p95 and p99 on the panel, the slowest endpoints, then the database pool panel | latency plateaus after a deploy: roll back; plateaus without a deploy: look for a slow dependency |
| `Http429RatioHigh` | who is being limited (`type` label on the rate-limit counter) and whether a client is retry-looping | a single client: block it at the edge; many clients: the limit or the capacity is wrong, not the client |
| `DbPoolUsageHigh` | active vs idle vs max on the panel, then long-running queries | sustained saturation with growing queue time: find the slow query before raising the pool size |
| `MailOutboxStalled` | oldest-pending age and per-status backlog; `mail_outbox` rows in `FAILED` and their `last_error` | the provider is refusing: fix the credential or the sender; the queue drains itself afterwards |
| `AuditWritesLost` | application log around the `AuditLogWriter` failure; database health | rows are lost, not queued: capture the log excerpt, treat as a security-evidence incident |
| `LeaderboardRecomputeStalled` | recompute queue depth and the recompute worker log | informational: derived data is stale, nothing user-facing is lost; fix during working hours |
| `TelegramDeliveryFailing` | `docker logs ctt-alertmanager 2>&1 \| grep -i telegram`, then `/api/v2/status` for the loaded receiver and the two credential files under `deploy/secrets/run/` | the page did not arrive: fix the credential or the chat id, then send a `PathCheck` alert; a failed page is an outage of the paging path itself |

### Silencing

Silence the alert, not the monitoring, and always with a reason and an expiry:

```bash
docker exec ctt-alertmanager amtool silence add \
  --alertmanager.url=http://127.0.0.1:9093 \
  --author="<you>" --comment="<why, with a ticket or a plan>" --duration=2h \
  alertname="<rule>"
```

Use the labels you actually see in the notification. Prefer the narrowest matcher that covers the
known condition — silencing a whole `host` hides the next, unrelated failure on that host. Review open
silences when an incident ends; a forgotten silence is the same as a deleted alert rule.

### Proving the path still works

Do this after changing a rule, the routing or the bot credentials:

```bash
# 1. Alertmanager loaded the configuration you think it did.
docker exec ctt-alertmanager amtool check-config /etc/alertmanager/alertmanager.yml

# 2. Send one alert that expires on its own. An alert whose endsAt passes is the path
#    Prometheus takes when a series disappears, and in 0.35.0 it is the only resolver
#    that notifies: deleting an alert through the API (`amtool alert resolve`) removes
#    it without sending RESOLVED.
docker exec ctt-alertmanager amtool alert add --alertmanager.url=http://127.0.0.1:9093 \
  "--end=$(date -u -d '+90 seconds' +%Y-%m-%dT%H:%M:%SZ)" \
  alertname=PathCheck severity=critical host=deployment \
  summary="paging path check"

# 3. The FIRING message arrives within a minute. The RESOLVED message follows within one
#    group interval after the alert expires (up to five minutes; longer if other groups
#    are already queueing).

If the message does not arrive, check in this order: `docker logs ctt-alertmanager 2>&1 | grep -i telegram`
(Alertmanager logs the rejection reason and never the token), the two credential files under
`deploy/secrets/run/` (empty means the SOPS payload has no value yet — add it and re-render), and
`alertmanager_notifications_failed_total` in Prometheus (a non-zero rate also raises
`TelegramDeliveryFailing`). A failed delivery is visible; a channel that never receives anything is not,
which is why the rule exists.

### Rotating the bot credential

1. Create the replacement token with BotFather, then put it into the SOPS payload as
   `TELEGRAM_BOT_TOKEN` and re-render the secrets (`deploy/secrets/render.sh --env prod`).
2. Recreate Alertmanager from the stack directory:
   `docker compose -f deploy/observability/docker-compose.yaml up -d --force-recreate alertmanager`.
   The notifier re-reads
   `bot_token_file` and `chat_id_file` on every delivery, but `render.sh` writes a **new file** (new
   inode) and a single-file bind mount keeps pointing at the old one — so without the recreate the
   container keeps serving the previous credential.
3. Repeat the "Proving the path still works" steps, then revoke the old token in BotFather.

### Escalation

There is one operator and one channel today, so escalation is a decision, not a rota:

- First `critical` repeat (one hour): the condition is still true. If you cannot act now, silence it
  with the reason and the time you will act.
- Three repeats: write the incident down, including what you already ruled out.
- A `critical` that fires while you are already working another incident: fix the one with user-visible
  impact first; silence the other with a note that points at the active incident.

## Deploying a signed image

The deploy job ships a digest the publish workflow signed. It verifies before it
starts anything, and it never builds on the host: a host build would be an image
no one signed, which is the case this check exists to exclude.

```bash
# The digest is printed by the publish run and pinned in the deploy job.
IMAGE=ghcr.io/ahogek/ctt-server@sha256:<digest>

deploy/verify-image.sh "$IMAGE"     # signature + provenance, identity pinned
docker pull "$IMAGE"

IMAGE_REF="$IMAGE" docker compose up -d --no-build app
```

Pulling a private package needs a registry credential; the deploy job takes it
from the Jenkins credential `ctt-ghcr-registry` (username plus a token with
`read:packages`). Without that credential the job still runs the verification
and says the package has to be publicly pullable — `cosign` reads the
signature from the transparency log and the attestation from the registry, so
the check itself works either way, but `docker pull` does not.

```bash
# Only needed on a host that pulls by hand rather than through the job.
echo "$GHCR_TOKEN" | docker login ghcr.io --username AhogeK --password-stdin
```

`verify-image.sh` exits `2` when it cannot run at all — `cosign` missing, or a
tag instead of a digest — and `1` when the signature or the attestation does not
match this repository's publish workflow. Both are deployment blockers; neither
is a warning to look at later.

Rolling back is the same command with the previous digest. Tags are not a
rollback target: `latest` moves with every release, and a tag that names a
commit can name a different build of that commit, so a rollback that names
either can land on the build you were rolling back from. The release tag
`X.Y.Z` names the released image; the digest the publish run printed names it
exactly.

If verification fails on a digest that used to pass, treat the registry or the
signing identity as suspect before touching the host: re-run
`cosign verify-attestation` by hand, compare the certificate identity it prints
with `.github/workflows/publish.yml`, and check the Rekor entry's timestamp
against the publish run.

Replacing the single container restarts the only instance, so every release
still costs the connections in flight. The cutover that avoids this — replace
the idle colour, switch the edge, hold a five-minute window, roll back by
switching — is rehearsed in [`deploy/local/README.md`](../deploy/local/README.md);
it applies to this host once it runs two slots.

### Installing the verification tools

Two binaries do the verification work: `cosign` checks the image signature and
the provenance attestation, and `sops` decrypts the secret payloads during a
render. Install both from the repository rather than from a release page:

```bash
scripts/install.sh                      # both, into ~/.local/bin
scripts/install.sh --tool cosign        # one of them
scripts/install.sh --verify-only        # what is installed, download nothing
```

The script pins a version per tool (`v2.6.0` for cosign, `v3.13.3` for sops),
resolves the release asset name from a table — both projects publish a bare
binary, but cosign's name carries no version and sops' repeats it — verifies the
digest against the checksums file the project publishes, stages the file beside
the destination and renames it over the old one (a rename is atomic, so a failed
download or an interrupted copy cannot replace a working binary with a broken
one), and then runs the binary's own version command to prove it. It never uses
`sudo`, and a second run reports the current version instead of downloading
again. Exit codes: `0` success, `1` usage/platform/download failure, `2` a
binary that is installed but does not report the pinned version.

The default prefix `~/.local/bin` is not on `PATH` on a stock machine, and
`deploy/secrets/render.sh` calls `sops` by name. Add it once:

```bash
export PATH="$HOME/.local/bin:$PATH"    # or --prefix somewhere already on PATH
```

The pinned cosign is deliberate: `v3` changed flags that `verify-image.sh` uses,
and the deployment host verified `v2.6.0` against a real digest. Moving the pin
is a change with a verification run behind it.

What the checksum proves and what it does not: it proves the file that arrived
over TLS is the file the project published for that release — a truncated
download, a cached proxy body, or an HTML error page saved under the asset name
is caught. It does not prove the release itself is authentic. Both projects
publish signatures over their checksums files, and verifying those needs
`cosign`, which is the thing being installed; the script leaves that to whoever
has a working `cosign` afterwards rather than pretending the digest is a
signature.

`scripts/tests/install-functions-test.sh` covers the mapping tables without a
network, and `scripts/test-install-matrix.sh` runs `scripts/tests/install-probe.sh`
— the same end-to-end sequence — on this host plus Debian, Ubuntu and Alpine
containers, installing **both** tools in each so the libc-specific parts are
exercised on the shapes that matter — that is the evidence behind the claims on
this page. `.github/workflows/installer-test.yml` repeats the probe on
stock `ubuntu-24.04` and `macos-15` runners, but it is manual
(`workflow_dispatch`) and **has not been run yet**: it is there so a release can
ask a clean runner the same question, not because it has been heard to pass.

## Edge headers and limits

Two hops sit in front of the application, and each has one job in the header
conversation:

| Hop | Its job |
| --- | --- |
| nginx on the access host (`deploy/edge/nginx/ctt-server.conf`) | terminates TLS, appends the real client to `X-Forwarded-For`, states `X-Forwarded-Proto: https` |
| Caddy edge (`deploy/local/Caddyfile`) | trusts the tunnel peer so the chain survives, keeps stating `https`, bounds body size and timeouts |

The application reads the chain right-to-left and skips only the peers listed in
`ctt.security.trusted-proxy-addresses`. That is why order matters: the value our
own hop appends must be the rightmost one, and a value a caller prepends stays
harmlessly to the left.

Two settings are easy to get wrong, and both fail silently:

- **Caddy must trust the tunnel peer.** By default it replaces `X-Forwarded-For`
  with the immediate peer, so every visitor looks like `127.0.0.1` and the
  address-based rate limit sees one client. `servers { trusted_proxies static
  127.0.0.1 }` makes it append instead.
- **A catch-all `handle_errors` rewrites every status.** Without a status
  matcher it turns the body limit's `413` into `503`, which reports the service
  as down when the request was simply too large. The block is scoped to
  `502 503 504`.

Both hops cap the body at the same number of bytes — `4194304` (4 MiB) — because
whichever answers `413` first makes the other look innocent. The ceiling comes
from the largest legitimate body: a sync push of 1000 sessions with free-text
fields at their documented limits, about 1.7 MiB before the JSON envelope.

### What the Caddy hop received

The proof is what the next hop sees, so watch the application's own request log
while sending one request. The application masks addresses before logging, so a
probe address of `203.0.113.9` appears as `203.0.113.***`:

```bash
docker run --rm --network container:ctt-edge curlimages/curl:8.11.1 -sS \
  -H 'X-Forwarded-For: 203.0.113.9' \
  -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18080/ctt-server/api/v1/config/public
docker logs --tail=20 "$(docker ps --filter name=app-blue --format '{{.Names}}' | head -1)" \
  | grep 'clientIp=203.0.113.\*\*\*'
```

The body limit needs a body over it, not a special route — every path answers
`413` before the proxy forwards anything:

```bash
head -c 4200000 /dev/zero | docker run --rm -i --network container:ctt-edge \
  curlimages/curl:8.11.1 -sS -o /dev/null -w '%{http_code}\n' --data-binary @- \
  http://127.0.0.1:18080/ctt-server/api/v1/sync/push      # expect 413

docker stop "$(docker ps --filter name=app-blue --format '{{.Names}}' | head -1)"
docker run --rm --network container:ctt-edge curlimages/curl:8.11.1 -sS -o /dev/null \
  -w '%{http_code}\n' http://127.0.0.1:18080/check        # expect 503
docker start "$(docker ps --filter name=app-blue --format '{{.Names}}' | head -1)" 2>/dev/null || true
```

### What the nginx hop did

Only a request that traverses the access host proves its half, and only that
host's access log holds the unmasked client address:

```bash
curl -skI --resolve ctt.example.com:443:127.0.0.1 https://ctt.example.com/ctt-server/api/v1/config/public | head -1
tail -n 3 /var/log/nginx/ctt-access.log     # $remote_addr is the real client
```

Both stacks keep their limits in step, so after changing either one re-run the
two probes above rather than reading the files.

## Related

- [Observability](observability.md) — metrics, dashboards, alert rules and the paging path
- [Production Configuration](production-configuration.md) — required environment values
- [Deployment Secrets](deployment-secrets.md) — how secrets reach the containers
