#!/usr/bin/env bash
# Prove that a staging backup restores into a serving database (P3.6).
#
#   ./deploy/staging/restore-drill.sh --yes
#
# This destroys staging's data on purpose: it drops the `public` schema, restores
# the dump over the empty database, and then asks the running stack whether it
# still serves. A backup nobody has restored is a copy; the difference only
# shows up here.
#
# The schema is recreated before the restore rather than letting `pg_restore
# --clean` drop objects one by one: an empty database makes the dump responsible
# for everything that has to exist, so a table the dump forgot shows up as a
# failed request instead of a leftover from before the drill.
#
# What it asserts afterwards, in order:
#   1. the marker row written before the dump is back — the dump carries data;
#   2. `flyway_schema_history` is back with the same last version — a restored
#      database that forgot its migrations would try to apply them again on the
#      next start;
#   3. the serving colour answers readiness, and the public path answers through
#      the edge — the stack recovers without a restart.
set -euo pipefail

cd "$(dirname "$0")/../.."

if [ "${1:-}" != "--yes" ]; then
  cat >&2 <<'USAGE'
restore-drill.sh drops staging's public schema and restores it from a dump.
That is the drill, and it is destructive: pass --yes to run it.
USAGE
  exit 2
fi

export CTT_STACK_FILE=deploy/staging/stack.env
# shellcheck source=deploy/lib/load-stack.sh
. ./deploy/lib/load-stack.sh

: "${CTT_DB_CONTAINER:?deploy/staging/stack.env must set CTT_DB_CONTAINER}"

# `--yes` says the caller means to destroy *staging*. Check the container too,
# so a stack file pointed at another database cannot pass `--yes` and lose it.
case "$CTT_DB_CONTAINER" in
  ctt-stg-*) ;;
  *)
    echo "refusing: CTT_DB_CONTAINER=$CTT_DB_CONTAINER is not a staging container" >&2
    exit 1
    ;;
esac

psql_staging() {
  docker exec -i "$CTT_DB_CONTAINER" sh -c \
    'PGPASSWORD="$(cat "$POSTGRES_PASSWORD_FILE")" psql -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
}

tag="drill-$(date -u +%Y%m%dT%H%M%SZ)-$$"
dump="deploy/staging/backups/$tag.dump"

echo "== marker: $tag"
psql_staging <<SQL
create table if not exists staging_restore_probe (
  tag text primary key,
  written_at timestamptz not null default now()
);
insert into staging_restore_probe (tag) values ('$tag');
SQL

before_version="$(printf 'select coalesce(max(version), %s) from flyway_schema_history;\n' "'none'" | psql_staging)"
echo "== flyway history before the drill: $before_version"

echo "== dump: $dump"
./deploy/staging/backup.sh "$dump"

echo "== drop schema public"
psql_staging <<'SQL'
drop schema public cascade;
create schema public;
SQL

echo "== restore"
docker exec -i "$CTT_DB_CONTAINER" sh -c \
  'PGPASSWORD="$(cat "$POSTGRES_PASSWORD_FILE")" pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-privileges' <"$dump"

restored="$(printf "select count(*) from staging_restore_probe where tag = '%s';\n" "$tag" | psql_staging)"
if [ "$restored" != "1" ]; then
  echo "the marker row did not come back ($restored rows); the dump is not a restore of this data" >&2
  exit 1
fi
echo "== marker survived the round trip"

after_version="$(printf 'select coalesce(max(version), %s) from flyway_schema_history;\n' "'none'" | psql_staging)"
if [ "$after_version" != "$before_version" ]; then
  echo "flyway history says $after_version after the restore, was $before_version: the dump does not carry the migration history" >&2
  exit 1
fi
echo "== flyway history after the drill: $after_version"

colour="$(./deploy/staging/switch-colour.sh | awk '{print $1}')"
management="$(management_of "$colour")" ||
  { echo "cannot tell which colour serves now: $colour" >&2; exit 1; }

if ! curl -sf -o /dev/null --retry 10 --retry-delay 3 --retry-all-errors \
     "http://127.0.0.1:$management/actuator/health/readiness"; then
  echo "$colour stopped answering readiness after the restore" >&2
  exit 1
fi
if ! curl -sf -o /dev/null --retry 10 --retry-delay 3 --retry-all-errors \
     "http://127.0.0.1:$CTT_EDGE_PUBLIC_PORT/ctt-server/api/v1/config/public"; then
  echo "the public path does not answer after the restore" >&2
  exit 1
fi
echo "== $colour serves, and the public path answers, on the restored database"

rm -f "$dump"
echo "RESTORE: green ($before_version, marker $tag survived, $colour serving)"
