# Production Data Isolation

The deployment host has one PostgreSQL server and one Redis server. Development and
production use separate database names, PostgreSQL roles and Redis logical databases.

## Current layout

| Environment | PostgreSQL database | PostgreSQL role | Redis database |
| --- | --- | --- | --- |
| local / dev | `ctt_server` | `postgres` (existing development role) | `0` |
| production | `ctt_server_prod` | `ctt_app` | `2` |

`ctt_server_prod` is owned by `ctt_app`. The production database starts empty; the
first production start runs Flyway and creates the schema. Do not copy development
rows into it.

Redis database `1` already contains another service's `check-approver-sort:*` keys.
It is not cleared and must not be used by this service. Production therefore uses
database `2`; this is the deliberate exception to the original plan's `db1` target.

## Required production environment

```text
SPRING_PROFILES_ACTIVE=prod
DB_HOST=<database host>
DB_PORT=5432
DB_NAME=ctt_server_prod
DB_USERNAME=ctt_app
DB_PASSWORD=<ctt_app secret>
REDIS_HOST=<redis host>
REDIS_PORT=6379
REDIS_PASSWORD=<redis secret>
REDIS_DATABASE=2
```

The production role password is not stored in the repository. A temporary
`PROD_DB_PASSWORD` exists only in the local gitignored `.env` for provisioning and
verification; P0.16 moves the production value into SOPS and supplies it as the
application's `DB_PASSWORD`.

## Database privileges

The role is a normal login role, not a superuser and not allowed to create
databases or roles. Its only database connection is `ctt_server_prod`; the
`PUBLIC` connect privilege was removed from both databases.

The following checks are read-only:

```bash
# role and database ownership
PGPASSWORD="$ADMIN_PASSWORD" psql -h "$DB_HOST" -U postgres -d postgres \
  -c "SELECT rolname, rolsuper, rolcreatedb, rolcreaterole FROM pg_roles WHERE rolname='ctt_app';" \
  -c "SELECT datname, pg_get_userbyid(datdba) FROM pg_database WHERE datname IN ('ctt_server','ctt_server_prod');"

# production role can connect and create in its own database
PGPASSWORD="$PROD_DB_PASSWORD" psql -h "$DB_HOST" -U ctt_app -d ctt_server_prod \
  -c "SELECT current_user, current_database();"

# production role cannot connect to the development database
PGPASSWORD="$PROD_DB_PASSWORD" psql -h "$DB_HOST" -U ctt_app -d ctt_server \
  -c "SELECT 1;"
```

The last command must fail with `permission denied for database "ctt_server"`.

## Redis

The production application writes only to Redis database `2`. Do not run
`FLUSHDB` or delete keys in database `1`; it belongs to another service.

## Data-plane network isolation

The deployment host exposes PostgreSQL and Redis only on loopback plus its LAN
address. The exact LAN address and subnet are kept in the private operations
notes, not in this repository.

| Service | Listener | Allowed clients |
| --- | --- | --- |
| PostgreSQL | `<LOOPBACK>:5432`, `<LAN_ADDRESS>:5432` | loopback and `<LAN_SUBNET>` |
| Redis | `<LOOPBACK>:6379`, `<LAN_ADDRESS>:6379` | loopback and clients that can route to `<LAN_ADDRESS>`; Redis password required |

PostgreSQL uses `listen_addresses` with the loopback and LAN addresses; its
`pg_hba.conf` keeps only loopback and the LAN subnet. Redis uses an explicit
`bind` with the same two addresses. Production containers connect through
loopback; LAN development uses the host LAN address.

Both services use a systemd `network-online.target` dependency because the LAN
address is assigned by DHCP. Without it, PostgreSQL can fail before the address
exists and does not retry (`Restart=no`).

The LAN address must be a DHCP reservation or static lease. If it changes,
both service configuration files must be updated before the next restart.

The host is behind a router that forwards no inbound ports. Adding a port
forward or a routed path from outside the LAN requires revisiting this design.

UFW is intentionally not enabled for this deployment. The host has no public
address, frp is an outbound tunnel, and the LAN is the declared trust boundary;
a host firewall that allows the same subnet adds no isolation from same-subnet
clients, while Docker-published ports bypass UFW. Revisit this decision if an
untrusted network or VLAN is added.

PostgreSQL enforces the client subnet in `pg_hba.conf`. Redis has no
source-address access control: its ACLs scope commands, keys and channels per
user, never client addresses, and `bind` selects listening interfaces rather
than accepted clients. The host has one IPv4 interface in the LAN subnet and
no global IPv6 address, so no remote source outside that subnet can reach the
port; the host's container bridges are local and add no remote path. A subnet
allowlist would therefore reject nothing and is not installed
([ADR-0003](decisions/ADR-0003-redis-source-restriction.md) records the
evidence, the accepted residual risk and the six revisit triggers). It becomes
mandatory — as a host nftables rule set, not UFW — before any new path to port
`6379` is enabled.

The UFW service unit may still report `active`; that only means the unit is
present. The rules are not enabled. Check `/etc/ufw/ufw.conf` or `ufw status`,
not only `systemctl is-active ufw`.

Verification uses `ss` to confirm that neither service listens on a wildcard
address, `pg_isready` for both PostgreSQL addresses, and TCP checks for both
Redis addresses. Redis authentication is separate from the bind check; an
unauthenticated `PING` returning `NOAUTH` is not a bind failure.
