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
