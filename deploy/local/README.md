# Local stack

The scripts here share one rule: changing the live colour is never done by rebuilding it.

```bash
./deploy/local/up.sh                                # blue on 18080, data plane, edge
./deploy/local/up.sh --profile bluegreen --profile obs   # + green, Prometheus, Grafana, blackbox

./deploy/local/release.sh green                     # build, replace green, switch, watch 5 min
./deploy/local/release.sh green --no-build          # same, with the image already in the stack
./deploy/local/release.sh green --observe 60        # shorter window, for a rehearsal
./deploy/local/release.sh --rollback                # back to the other colour, same window

./deploy/local/switch-colour.sh                     # which colour serves, per the edge
./deploy/local/switch-colour.sh green               # move traffic, no build, no restart
```

The scripts behind these wrappers live in `deploy/lib/` and read every
stack-specific value — container names, ports, the Prometheus URL, the probe
job — from a stack file. This stack's file is `deploy/local/stack.env`; the
wrappers in this directory only name it and call the shared script. The second
stack, staging, is the same scripts with `deploy/staging/stack.env`; see
[`deploy/staging/README.md`](../staging/README.md).

`up.sh` starts the stack; it does not move traffic. `release.sh` replaces the
colour that is **not** serving, waits for it to answer its readiness endpoint,
sends it one request so its timer series exists at all (Micrometer registers
`http_server_requests` on the first request, and the idle colour receives none),
and only then waits until Prometheus has rateable series for that colour — a
container that started seconds ago has one sample, and an empty window sample
would fail a release that is fine.

Then it switches and holds the window: one synthetic request per ten seconds,
and on each cycle the edge's answer, `probe_success` of the public probe, the
5xx rate and the p95 of **the serving colour**. Both slots are scraped, so the
idle colour's samples are filtered out by their `slot` label: they describe the
build that was serving a minute ago, not the one being judged. The first breach
repoints the edge to the previous colour and fails the release.

A p95 that comes back `NaN` or empty is not a healthy measurement — with too
few observations a quantile proves nothing, so the sample is printed as `n/a`
and is not counted. The window fails if no sample could be measured at all,
rather than reporting a clean run it never verified. `--rollback` is the switch
without a build — the other colour never stopped running its previous image —
and if the colour rolled back *to* breaches, traffic stays on it instead of
returning to the build the rollback just removed.

On this machine the stack lives inside the Docker Desktop VM, so probes from the
macOS shell reach this host and not the edge. `on-engine.sh` runs the same script
on the engine's side of that boundary:

```bash
./deploy/local/on-engine.sh ./deploy/local/release.sh green --no-build --observe 120
./deploy/local/on-engine.sh ./deploy/local/switch-colour.sh
```

It mounts the repository at the same absolute path — a compose file resolves its
bind sources relative to itself, so a different mount point would bind the wrong
directories — and adds `bash` and `curl` to `docker:cli`, which carries neither.
On a Linux host the stack is already on the machine the engine runs on, and this
wrapper is unnecessary.

The edge keeps an admin endpoint on `127.0.0.1:2019` for one operation:
`caddy reload`. The running process keeps serving its configuration until the
replacement is accepted, so a switch costs no connection and a broken
configuration leaves the previous colour serving. Why the mechanism looks like
this, and what it costs, is in [ADR-0005](../../docs/decisions/ADR-0005-blue-green-cutover.md).
