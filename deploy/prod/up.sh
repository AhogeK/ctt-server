#!/usr/bin/env bash
# Bring up what a first production release needs before any colour runs (P3.8).
#
#   ./deploy/prod/up.sh                     # seed the upstream choice, start the edge
#   ./deploy/prod/release.sh --next --first-release --image ghcr.io/ahogek/ctt-server@sha256:…
#   ./deploy/prod/release.sh --rollback
#
# The data plane is not started here: PostgreSQL and Redis are host processes on
# the deployment machine (docs/production-data-isolation.md), and the production
# payload is rendered by the deploy job (`./deploy/secrets/render.sh --env prod`)
# because it needs the SOPS identity.
#
# Only the edge starts here. The colours start through the release, which is the
# step that pulls the signed digest — a colour that came up from an arbitrary
# `docker compose up` would be an unverified build serving traffic.
set -euo pipefail

cd "$(dirname "$0")/../.."

export CTT_STACK_FILE=deploy/prod/stack.env
# shellcheck source=deploy/lib/load-stack.sh
. ./deploy/lib/load-stack.sh

# A single container from before P3.8 holds the same management port blue needs,
# and the symptom would be a colour that never becomes ready. Name it here.
for stale in ctt-server ctt-server-test; do
  if [ -n "$(docker ps -q -f "name=^/${stale}$")" ]; then
    echo "the pre-P3.8 container $stale is still running and holds the production ports; stop it with: docker rm -f $stale" >&2
    exit 1
  fi
done

seed_upstream

echo "starting the edge on 127.0.0.1:$CTT_EDGE_PUBLIC_PORT"
# shellcheck disable=SC2086 # CTT_COMPOSE_FILES is a list of -f arguments
exec docker compose $CTT_COMPOSE_FILES up -d --no-build edge
