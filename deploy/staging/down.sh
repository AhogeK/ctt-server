#!/usr/bin/env bash
# Stop the staging stack (P3.6).
#
#   ./deploy/staging/down.sh            # stop and remove the containers
#   ./deploy/staging/down.sh --volumes  # also delete staging's database volume
#
# The volumes are kept by default: a rehearsal that starts from an empty database
# every time cannot show that a migration ran against data. `--volumes` exists
# for the one case that needs a clean cluster, and it only reaches volumes whose
# names carry this project (`ctt-staging_*`) — the local stack's volumes are a
# different project.
set -euo pipefail

cd "$(dirname "$0")/../.."

export CTT_STACK_FILE=deploy/staging/stack.env
# shellcheck source=deploy/lib/load-stack.sh
. ./deploy/lib/load-stack.sh

purge=""
if [ "${1:-}" = "--volumes" ]; then
  purge="--volumes"
  shift
fi

# shellcheck disable=SC2086 # CTT_COMPOSE_FILES is a list of -f arguments
exec docker compose $CTT_COMPOSE_FILES --profile bluegreen --profile obs down ${purge:+"$purge"} "$@"
