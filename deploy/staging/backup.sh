#!/usr/bin/env bash
# Dump the staging database (P3.6).
#
#   ./deploy/staging/backup.sh                    # timestamped file under deploy/staging/backups
#   ./deploy/staging/backup.sh /tmp/before.dump   # a named file, for the restore drill
#
# `pg_dump -Fc` runs inside the postgres container and its stdout becomes the
# file: the custom format is restorable by `pg_restore` alone, and the dump
# cannot land on a database other than the one the container is serving, because
# the connection details are the container's own environment.
set -euo pipefail

cd "$(dirname "$0")/../.."

export CTT_STACK_FILE=deploy/staging/stack.env
# shellcheck source=deploy/lib/load-stack.sh
. ./deploy/lib/load-stack.sh

: "${CTT_DB_CONTAINER:?deploy/staging/stack.env must set CTT_DB_CONTAINER}"

out=${1:-deploy/staging/backups/staging-$(date -u +%Y%m%dT%H%M%SZ).dump}
mkdir -p "$(dirname "$out")"

# A pipe is not used on purpose: a failing pg_dump would leave a short file and
# a zero exit from the pipeline unless the caller checked both.
docker exec "$CTT_DB_CONTAINER" sh -c \
  'PGPASSWORD="$(cat "$POSTGRES_PASSWORD_FILE")" pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' >"$out"

bytes=$(wc -c <"$out" | tr -d ' ')
# A dump of this schema is far larger than a kilobyte; anything smaller is a
# truncated write that still exited 0 somewhere upstream.
if [ "$bytes" -lt 1024 ]; then
  echo "the dump is $bytes bytes, which is not a database; nothing to keep" >&2
  rm -f "$out"
  exit 1
fi

echo "backup: $out ($bytes bytes)"
