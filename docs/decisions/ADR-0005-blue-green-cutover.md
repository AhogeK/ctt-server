# ADR-0005: Blue-green cutover through the edge

Date: 2026-11-16 · Status: Accepted

## Context

Releasing replaced the single application container: `docker compose up -d app`
stops the running process and starts the new image in its place. Two consequences
follow, and neither is visible in a health check afterwards.

The connections in flight at that moment are gone. The application is polled by a
desktop client on a schedule and holds long-lived OAuth and websocket sessions;
a restart is an outage for whoever is mid-request, however brief.

And there is no moment when the new build serves real traffic while the previous
one is still able to. A release that turns out to be wrong is discovered by
users, and the fix is a second restart — the same outage again, on a host that is
now also suspected.

The local stack already ran two colours (`18081` blue, `18082` green) behind one
edge on `18080`, but nothing moved traffic between them: the edge named its
upstream in an environment variable fixed at container creation.

## Decision

**Two colours always run; a release replaces the idle one.** The serving colour
is never touched by a deploy, so the new build starts next to live traffic and
the switch is the only moment anything changes for a caller.

**The serving colour is a file the edge imports.** `deploy/local/edge-upstream/`
holds one variant per colour and an `active.caddy` symlink; the edge's site block
is `handle { import /etc/caddy-upstream/active.caddy }`. Moving traffic is a
symlink repoint plus `caddy reload`.

**The reload is what makes the switch invisible.** Caddy keeps serving its
current configuration until the replacement is parsed and accepted, so a typo in
the upstream file leaves the previous colour serving rather than taking the site
down. Recreating the edge container would lose exactly the connections this
design exists to preserve, and would present as a deliberate outage.

**A switch is verified three ways, and only the last one counts for callers.**
The target must answer its readiness endpoint first — switching to a colour that
is still starting is how a rollout becomes an outage. Then the edge's running
configuration must name the target. Then a request must cross the edge and
succeed. The third check uses an application endpoint: the application port
serves no actuator payload (the two-port contract), so `/actuator/**` answers 404
there and would report a healthy colour as unreachable.

**Traffic is observed for five minutes, and the first breach returns traffic.**
Every ten seconds the release reads, from Prometheus, `probe_success` for the
public path, the application's 5xx rate, and p95 against the 300 ms contract —
and sends one synthetic request so those series have samples at all. A window
with no requests would otherwise read as a healthy one. Any breach repoints the
edge to the previous colour and fails the release.

**Rollback is a switch, not a deploy.** The previous colour is still running its
previous digest, so rolling back costs one reload and no build.

## Alternatives Considered

- **Restart the edge container with a new upstream.** One command, no admin
  endpoint, no second file. Rejected: it drops the connections the design exists
  to keep, and the resulting outage is indistinguishable from a deploy that went
  wrong.
- **Two nginx virtual hosts and `nginx -s reload`.** Comparable semantics and
  it is the language of the shared access host. Rejected for the local stack:
  it introduces a second proxy configuration language next to the Caddy that
  already terminates here, and the local stack would then differ from its own
  rehearsal.
- **Docker DNS round-robin across the two colours.** No upstream file and no
  reload. Rejected: traffic distribution becomes uncontrolled, there is no way
  to send everything to one colour, so neither an observation window nor a
  rollback can be expressed.
- **A separate pipeline per colour.** Each colour gets its own deploy job.
  Rejected: two pipelines that must stay identical drift, and the switch lives
  in neither of them.

## Consequences

- A release needs both colours running, and the idle one holds the resources of a
  full application instance. On the local stack that is acceptable; on the
  deployment host it is a second 900 MB container.
- The idle colour is not a backup. Its image is whatever was deployed last, so
  rolling back lifts a container that was already running, not a snapshot of the
  database — migrations still have to be expand/contract.
- Which colour serves is state, not content: `active.caddy` is ignored by git, so
  a fresh checkout starts on blue rather than inheriting someone's choice.
- The edge now exposes an admin endpoint on loopback. It exists for one
  operation and is bound to `127.0.0.1`; anything that can reach that port can
  reconfigure the edge.
- A static request that spans the switch can still 404 when the new colour does
  not have the asset the old one served. Nothing in this design fixes that; it is
  the cost of replacing a process rather than a file.

## Revisit triggers

- The two colours hold different image digests for longer than one release.
- More than one edge instance serves the site: a per-host symlink and a per-host
  reload stop being a single decision.
- The deployment host gains a second slot: the same three checks apply, with
  `nginx -s reload` in place of `caddy reload`, and this ADR's mechanism section
  should be restated for that edge rather than assumed equivalent.
