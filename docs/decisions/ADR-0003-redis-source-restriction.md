# ADR-0003: No source-address restriction for Redis until a routed path exists

**Date:** 2026-11-10 · **Status:** Accepted

## Context

P0.13 moved PostgreSQL and Redis off wildcard listeners: both now listen on loopback plus the
deployment host's LAN address, and `pg_hba.conf` accepts PostgreSQL clients only from loopback and
the LAN subnet. Redis has no equivalent: it is protected by a password alone. P0.13b asks for an
explicit decision — segment the network, install a minimal firewall rule set (not UFW by default),
or record the residual risk.

Facts measured on the deployment host (2026-11-10):

| Fact | Evidence |
| --- | --- |
| Redis 8.10.3, no wildcard listener | `ss -lnt` → `127.0.0.1:6379`, `192.168.5.178:6379` only |
| Listening interfaces are fixed | `CONFIG GET bind` → `127.0.0.1 192.168.5.178`; `protected-mode yes`; `requirepass` set |
| One full-privilege user | `ACL LIST` → `user default on … ~* &* +@all` (no address component) |
| One active IPv4 interface, one subnet | `ip -brief addr` → `enp3s0 192.168.5.178/24`, `lo`; Docker bridges are internal |
| No global IPv6 | `ip -6 addr show scope global` → empty; `net.ipv6.conf.all.forwarding=0` |
| Current data-plane peers | `ss -tn state established` → `192.168.5.101` (development workstation) only |
| Other containers do not use the data plane | `docker inspect` → `blogapi`, Jenkins, searxng and the LeetCode worker are bridge-networked with their own published ports |

Two platform constraints shape the options:

- Redis ACLs (Redis 6+) scope commands, key patterns and pub/sub channels per user. The grammar has
  no client-address predicate, and `bind` chooses listening interfaces, not accepted clients. Redis
  therefore cannot express "this subnet only"; the enforcement point must be outside the process.
- Docker-published ports bypass host INPUT policy, which is part of why P0.13 kept UFW disabled. For
  a host-level Redis the INPUT path does apply, so a firewall rule would work — it just would not
  reject anything the current topology can produce.

The declared threat model (P0.13, confirmed by the project owner) treats the home LAN as the trust
boundary: the router forwards no inbound ports, and the only external ingress is the tunnel. Absence
of port forwarding is being verified separately in P0.13c; it is a threat-model statement, not a
measurement in this ADR.

## Decision

**Do not install a source-address allowlist for Redis now.** The controls in force are the bind
restriction to loopback plus the LAN address, the Redis password, `protected-mode`, and the threat
model's no-inbound-forwarding boundary. Redis is not reachable from any source outside
`192.168.5.0/24` or loopback in the current topology, so an allowlist would reject no traffic while
adding host-wide firewall state to a shared host.

**A firewall allowlist becomes mandatory before any new path to port 6379 is enabled** (see the
revisit triggers). At that point the control is a host nftables rule set — not UFW — accepting
`tcp/6379` from loopback and the LAN subnet and dropping every other source, installed with its own
persistent unit so it cannot be lost on reboot. The exact rule set is written and verified when the
trigger fires; committing an unexercised ruleset now would be dead configuration.

## Alternatives considered

| Option | Why it was not chosen |
| --- | --- |
| Host nftables allowlist now (non-UFW) | It is the correct enforcement point, but today's reachable set is loopback plus the LAN subnet — the same set the rule would allow. It rejects nothing, while adding a persistent firewall unit to a host that also runs Jenkins, searxng, blogapi and frp, with no firewall management framework installed. Install it when a trigger makes it effective. |
| Re-enable UFW | Already rejected in P0.13: Docker-published ports bypass its INPUT policy, the unit reports `active` while `ENABLED=no` invites false confidence, and a subnet-wide allow duplicates the trust boundary. Re-enabling it would not restrict Redis beyond what the subnet rule does. |
| Network segmentation (dedicated VLAN or interface for the data plane) | Depends on router capabilities that are not in use, and would change every development client's path. No isolation gain while the LAN is the declared boundary. |
| Redis ACL user with restricted commands (keep the default user disabled) | Does not express source addresses, which is the requirement. Command restriction limits blast radius but leaves the accepted same-LAN risk unchanged; it is a separate hardening decision if the password is ever shared beyond this deployment. |
| Bind Redis to loopback only and reach it over an SSH tunnel | Removes LAN exposure entirely, but changes the development workflow, and PostgreSQL would stay LAN-exposed under `pg_hba`; not required by the threat model. Revisit if the LAN stops being trusted. |

## Consequences

Positive:

- No new host-wide firewall state on a shared host, and no second policy that could drift from the
  documented binding.
- The decision, its evidence and the exact control to install on a trigger are written down instead
  of implied.
- PostgreSQL keeps its native enforcement (`pg_hba.conf`); Redis' gap is now an accepted,
  time-boxed residual risk instead of an open question.

Negative:

- Any client that obtains the Redis password on the LAN can read and write every logical database,
  including database `1` owned by another service, and can issue administrative commands such as
  `CONFIG SET` or `SHUTDOWN`. Within the accepted LAN trust boundary this is unchanged by a subnet
  allowlist — it is a password-distribution risk, not a routing one.
- If a routed path appears without this ADR being revisited, Redis is protected by the password
  alone.

Neutral:

- The two data-plane services now differ by design: PostgreSQL rejects out-of-subnet clients itself,
  Redis relies on the network boundary.

## Revisit triggers

The decision must be revisited, and the nftables allowlist installed, before any of these holds:

1. A port forward, DNAT rule, reverse proxy or tunnel exposes `tcp/6379` beyond `192.168.5.0/24`.
2. A guest VLAN, second LAN subnet or any routed network gains a route to the deployment host.
3. The host obtains a global IPv6 address, or Redis gains an IPv6 listener.
4. Redis moves to a container-published port, another host, or a managed service.
5. The Redis password is shared beyond this deployment, or a second application user is added.
6. Any Redis access from an unexpected source appears in logs or an incident review.

P0.13c (external negative verification) remains the measurement that confirms trigger 1 has not
already occurred.

## Verification and limits

Read-only evidence collected on 2026-11-10 with the commands quoted in the Context table. What this
ADR does **not** establish:

- That the router forwards no ports — P0.13c owns that check, from a path outside the LAN.
- That no host-level client can spoof a LAN source address. Spoofing is out of scope for a subnet
  allowlist as well; the effective control remains the password plus the trusted LAN.

When a trigger fires, acceptance requires: the allowlist applied and persisted, `tcp/6379` from the
LAN subnet still working (application and development clients), a non-subnet SYN dropped (sent from
a network namespace bound to an address outside the subnet, or from an external path), and P0.13c
re-run.

## Approval record

| Field | Value |
| --- | --- |
| Decision | No source-address restriction now; host nftables allowlist required on any new path to `6379` |
| Decision authority | Project owner, also the deployment operator; delegated the decision to engineering in the 2026-11-10 session, and this ADR is the written record |
| Recorded | 2026-11-10 |
| Status | Accepted |
| Traceability | Plan P0.13b / P0.13c, `docs/production-data-isolation.md`, P0.13 and P0.13a completion records |

## Evidence

- `ss -lnt`, `ss -tn state established "( sport = :6379 or sport = :5432 )"`, `ip -brief addr`,
  `ip -6 addr show scope global` on `local-home-ubuntu`, 2026-11-10.
- `redis-cli ACL LIST`, `CONFIG GET bind`, `CONFIG GET protected-mode`, `CONFIG GET requirepass`
  against `127.0.0.1:6379` on the deployment host, 2026-11-10.
- `docker inspect` network mode and port bindings for the running containers, 2026-11-10.
- `docs/production-data-isolation.md` (data-plane network isolation, Redis section).
- Plan sections P0.11 (host networking for edge and applications), P0.13, P0.13a, P0.13c in
  `.plans/ctt-server-development-plan.md`.
