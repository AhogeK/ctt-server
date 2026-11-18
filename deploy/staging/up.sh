#!/usr/bin/env bash
# One command for the staging stack (P3.6).
#
#   ./deploy/staging/up.sh        # secrets, jar, then the whole stack
#   ./deploy/staging/down.sh      # stop it, keep the volumes
#
# Staging runs beside the local stack, so nothing it owns is shared: project
# `ctt-staging`, containers `ctt-stg-*`, its own database and ports, and its own
# rendered secrets. That is also why it starts through its own stack file rather
# than through environment variables someone has to remember.
#
# This runs on the host: rendering the secrets needs the SOPS key, and building
# the jar needs the toolchain. The rehearsal itself runs where the probes work
# (`deploy/local/on-engine.sh`, see rehearse.sh) — starting containers does not,
# because Compose talks to the engine over its socket.
#
# Prometheus and blackbox are part of the stack because `release.sh` refuses to
# open an observation window without a probe: a rehearsal that cannot see whether
# traffic survived is not a rehearsal.
set -euo pipefail

cd "$(dirname "$0")/../.."

export CTT_STACK_FILE=deploy/staging/stack.env
# shellcheck source=deploy/lib/load-stack.sh
. ./deploy/lib/load-stack.sh

# Keep the rendered group and the Compose group_add value identical even when
# the launcher is not the usual 1000-gid account.
export SECRET_GID="${SECRET_GID:-$(id -g)}"

echo "[1/3] rendering staging secrets into deploy/secrets/run-staging"
./deploy/secrets/render.sh --out deploy/secrets/run-staging

echo "[2/3] building and staging the boot jar"
./deploy/lib/stage-jar.sh

# The edge imports this link; a fresh checkout has the variants but no choice
# made yet, and blue is the colour the stack starts with.
if [ ! -e "$CTT_UPSTREAM_DIR/active.caddy" ]; then
  ln -sfn blue.caddy "$CTT_UPSTREAM_DIR/active.caddy"
fi

echo "[3/3] starting the stack"
# shellcheck disable=SC2086 # CTT_COMPOSE_FILES is a list of -f arguments
exec docker compose $CTT_COMPOSE_FILES --profile bluegreen --profile obs "$@" up -d --build
