# ADR-0003: No source-address restriction for Redis until a routed path exists

**Date:** 2026-11-10 · **Status:** Accepted

## Context

PostgreSQL and Redis no longer listen on wildcard addresses: both listen on loopback plus the
deployment host's LAN address, and `pg_hba.conf` accepts PostgreSQL clients only from loopback and
the LAN subnet. Redis has no equivalent: it is protected by a password alone. This ADR records the
decision on whether to add a source restriction — segment the network, install a minimal firewall
rule set (not UFW by default), or record the residual risk.

Facts measured on the deployment host (2026-11-10):

| Fact | Evidence |
| --- | --- |
| Redis 8.10.3, no wildcard listener | `ss -lnt` → `127.0.0.1:6379` and the LAN address only |
| Listening interfaces are fixed | `CONFIG GET bind` → loopback plus the LAN address; `protected-mode yes`; `requirepass` set |
| One full-privilege user | `ACL LIST` → `user default on … ~* &* +@all` (no address component) |
| One active IPv4 interface, one subnet | `ip -brief addr` → one physical interface on a single `/24`, plus `lo`; Docker bridges are internal |
| No global IPv6 | `ip -6 addr show scope global` → empty; `net.ipv6.conf.all.forwarding=0` |
| Current data-plane peers | `ss -tn state established` → the development workstation only |
| Other containers do not use the data plane | `docker inspect` → the other services on the host are bridge-networked with their own published ports |

Two platform constraints shape the options:

- Redis ACLs (Redis 6+) scope commands, key patterns and pub/sub channels per user. The grammar has
  no client-address predicate, and `bind` chooses listening interfaces, not accepted clients. Redis
  therefore cannot express "this subnet only"; the enforcement point must be outside the process.
- Docker-published ports bypass host INPUT policy, which is one reason UFW stays disabled for this
  deployment. For a host-level Redis the INPUT path does apply, so a firewall rule would work — it
  just would not reject anything the current topology can produce.

The declared threat model (confirmed by the project owner) treats the home LAN as the trust
boundary: the router forwards no inbound ports, and the only external ingress is the tunnel.
Absence of forwarding was measured on 2026-11-10 from a public host outside the LAN, so it is no
longer only a threat-model statement.

## Decision

**Do not install a source-address allowlist for Redis now.** The controls in force are the bind
restriction to loopback plus the LAN address, the Redis password, `protected-mode`, and the threat
model's no-inbound-forwarding boundary. Redis is not reachable from any source outside the LAN
subnet or loopback in the current topology, so an allowlist would reject no traffic while
adding host-wide firewall state to a shared host.

**A firewall allowlist becomes mandatory before any new path to port 6379 is enabled** (see the
revisit triggers). At that point the control is a host nftables rule set — not UFW — accepting
`tcp/6379` from loopback and the LAN subnet and dropping every other source, installed with its own
persistent unit so it cannot be lost on reboot. The exact rule set is written and verified when the
trigger fires; committing an unexercised ruleset now would be dead configuration.

## Alternatives considered

| Option | Why it was not chosen |
| --- | --- |
| Host nftables allowlist now (non-UFW) | It is the correct enforcement point, but today's reachable set is loopback plus the LAN subnet — the same set the rule would allow. It rejects nothing, while adding a persistent firewall unit to a host that also runs other services and a tunnel, with no firewall management framework installed. Install it when a trigger makes it effective. |
| Re-enable UFW | Already rejected: Docker-published ports bypass its INPUT policy, the unit reports `active` while `ENABLED=no` invites false confidence, and a subnet-wide allow duplicates the trust boundary. Re-enabling it would not restrict Redis beyond what the subnet rule does. |
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

1. A port forward, DNAT rule, reverse proxy or tunnel exposes `tcp/6379` beyond the LAN subnet.
2. A guest VLAN, second LAN subnet or any routed network gains a route to the deployment host.
3. The host obtains a global IPv6 address, or Redis gains an IPv6 listener.
4. Redis moves to a container-published port, another host, or a managed service.
5. The Redis password is shared beyond this deployment, or a second application user is added.
6. Any Redis access from an unexpected source appears in logs or an incident review.

External probes on 2026-11-10 measured this from outside the LAN: `tcp/5432` and `tcp/6379` to the
network's internet-facing address time out from a public host, while both ports stay reachable
inside the LAN.

## Verification and limits

Read-only evidence collected on 2026-11-10 with the commands quoted in the Context table. What this
ADR does **not** establish:

- Whether the network has a dedicated public address or an ISP-level shared address. The external
  probes saw no inbound path either way, so the conclusion holds; confirming the router's WAN
  address and its forwarding table needs router access.
- That no host-level client can spoof a LAN source address. Spoofing is out of scope for a subnet
  allowlist as well; the effective control remains the password plus the trusted LAN.

When a trigger fires, acceptance requires: the allowlist applied and persisted, `tcp/6379` from the
LAN subnet still working (application and development clients), a non-subnet SYN dropped (sent from
a network namespace bound to an address outside the subnet, or from an external path), and the
external probes re-run.

## Approval record

| Field | Value |
| --- | --- |
| Decision | No source-address restriction now; host nftables allowlist required on any new path to `6379` |
| Decision authority | Project owner, also the deployment operator; delegated the decision to engineering on 2026-11-10, and this ADR is the written record |
| Recorded | 2026-11-10 |
| Status | Accepted |
| Traceability | `docs/production-data-isolation.md`; the Redis configuration and `pg_hba.conf` on the deployment host |

## Evidence

- `ss -lnt`, `ss -tn state established "( sport = :6379 or sport = :5432 )"`, `ip -brief addr`,
  `ip -6 addr show scope global` on the deployment host, 2026-11-10.
- `redis-cli ACL LIST`, `CONFIG GET bind`, `CONFIG GET protected-mode`, `CONFIG GET requirepass`
  against `127.0.0.1:6379` on the deployment host, 2026-11-10.
- `docker inspect` network mode and port bindings for the running containers, 2026-11-10.
- `docs/production-data-isolation.md` (data-plane network isolation, Redis section).
- External probes (2026-11-10): from a public host outside the LAN, both data-plane ports
  time out (filtered, not refused), ICMP is dropped and the path stops at the ISP backbone, while
  the tunnel from the same network stays connected and both ports answer inside the LAN.
