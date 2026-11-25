#!/usr/bin/env bash
# Load the values that make one blue-green script stack-specific (P3.6).
#
# Sourced by `deploy/lib/switch-colour.sh` and `deploy/lib/release.sh`. A stack
# is one file — `deploy/local/stack.env` or `deploy/staging/stack.env` — and the
# caller points `CTT_STACK_FILE` at it; nothing else about a stack is code, so
# the two never drift apart the way a copied script would.
#
# Everything the file sets is exported: the Compose variables in it (`POSTGRES_DB`,
# the external ports) have to reach the `docker compose` process these scripts
# start, and an unexported shell variable would silently leave Compose on its
# defaults — a staging stack that publishes production's ports. The files carry
# ports, names and a database name; never a credential.
set -euo pipefail

CTT_STACK_FILE="${CTT_STACK_FILE:-deploy/local/stack.env}"
if [ ! -f "$CTT_STACK_FILE" ]; then
  echo "no stack file at $CTT_STACK_FILE; set CTT_STACK_FILE to deploy/local/stack.env or deploy/staging/stack.env" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
. "$CTT_STACK_FILE"
set +a

stack_name=${CTT_STACK_NAME:-$(basename "$(dirname "$CTT_STACK_FILE")")}

# A stack that forgot one of these would fall back to another stack's port and
# answer on it — the one failure mode that looks like success from inside the
# script. Name it instead.
for required in CTT_COMPOSE_FILES CTT_EDGE_CONTAINER CTT_EDGE_PUBLIC_PORT CTT_EDGE_ADMIN \
                CTT_UPSTREAM_DIR CTT_PROMETHEUS_URL CTT_PROBE_JOB \
                CTT_BLUE_PORT CTT_GREEN_PORT CTT_BLUE_MANAGEMENT CTT_GREEN_MANAGEMENT; do
  if [ -z "${!required:-}" ]; then
    echo "$CTT_STACK_FILE does not set $required; nothing was done" >&2
    exit 1
  fi
done

# The edge imports the active link, so it must exist before the edge starts; a
# fresh host has the two variants and no choice made yet. Blue is the colour a
# first release deploys, and the release moves the link from there. The link
# alone does not make a colour "serving" — that is decided by whether a container
# runs, which is what switch-colour.sh reports.
seed_upstream() {
  if [ ! -e "$CTT_UPSTREAM_DIR/active.caddy" ]; then
    echo "seeding the upstream choice: blue"
    ln -sfn blue.caddy "$CTT_UPSTREAM_DIR/active.caddy"
  fi
}

# The colour → port map is here because every script that asks "which colour
# serves, and where does it answer" needs the same two answers, and a copy per
# script is how the two drift apart.
port_of() {
  case "$1" in
    blue) echo "$CTT_BLUE_PORT" ;;
    green) echo "$CTT_GREEN_PORT" ;;
    *) return 1 ;;
  esac
}

management_of() {
  case "$1" in
    blue) echo "$CTT_BLUE_MANAGEMENT" ;;
    green) echo "$CTT_GREEN_MANAGEMENT" ;;
    *) return 1 ;;
  esac
}
