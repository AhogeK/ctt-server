#!/usr/bin/env bash
# One command for the local full stack (P0.0).
#
#   ./deploy/local/up.sh                 # postgres + redis + mailpit + app-blue + edge
#   ./deploy/local/up.sh --profile obs   # add Prometheus, Grafana and blackbox
#   ./deploy/local/up.sh --profile bluegreen --profile obs
#
# This starts the stack; it does not move traffic. Rebuilding both colours is
# exactly what a release must not do to the live one — use `release.sh` for that.
#
# The jar is built on the host because the in-container Gradle build cannot
# reach Maven Central through this host's proxy.
set -euo pipefail

cd "$(dirname "$0")/../.."

# Keep the rendered group and the Compose group_add value identical even when
# the launcher is not the usual 1000-gid account.
export SECRET_GID="${SECRET_GID:-$(id -g)}"

export CTT_STACK_FILE="${CTT_STACK_FILE:-deploy/local/stack.env}"
# shellcheck source=deploy/lib/load-stack.sh
. ./deploy/lib/load-stack.sh

echo "[1/3] rendering compose secrets"
./deploy/secrets/render.sh

echo "[2/3] building and staging the boot jar"
./deploy/lib/stage-jar.sh

# The edge imports this link; a fresh checkout has the variants but no choice
# made yet, and blue is the colour the rest of the stack starts with.
if [ ! -e "$CTT_UPSTREAM_DIR/active.caddy" ]; then
  ln -sfn blue.caddy "$CTT_UPSTREAM_DIR/active.caddy"
fi

echo "[3/3] starting the stack"
# shellcheck disable=SC2086 # CTT_COMPOSE_FILES is a list of -f arguments
exec docker compose $CTT_COMPOSE_FILES "$@" up -d --build
