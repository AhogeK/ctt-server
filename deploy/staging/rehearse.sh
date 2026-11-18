#!/usr/bin/env bash
# Rehearse a release on staging: publish, roll back, restore (P3.6).
#
#   ./deploy/staging/up.sh                                     # host: secrets and jar
#   ./deploy/local/on-engine.sh ./deploy/staging/rehearse.sh    # the rehearsal itself
#
# This is the acceptance for the staging stack: one command that does what a
# release does, in the order it does it, on a stack nobody depends on.
#
#   1. confirm the stack answers — both colours ready and the edge serving;
#   2. release the colour that is not serving — new image, switch, and a 60 s
#      window that fails on a probe failure, a 5xx or a p95 above 300 ms;
#   3. roll back to the colour that was serving, with the image it is already
#      running (`--no-build`): a rollback rebuilds nothing, which is the whole
#      reason it can be fast in production;
#   4. restore the database from a dump and ask the stack whether it still
#      serves (see restore-drill.sh).
#
# The jar comes from the host (`up.sh` built it); step 2 builds only the image,
# which is the half that needs the engine.
#
# Why `on-engine.sh`: the colours, the edge and Prometheus listen on the engine's
# loopback, not this machine's — Docker Desktop keeps its VM's network namespace
# to itself. Every step here is a probe, so the probe runs beside them. On a Linux
# host this script runs directly.
#
# The two colours run the same version, so what step 3 proves is the traffic path
# and the window, not image rollback: staging holds one built image per version,
# and a version that has been replaced would be rebuilt from the tree.
set -euo pipefail

cd "$(dirname "$0")/../.."

export CTT_STACK_FILE=deploy/staging/stack.env
# shellcheck source=deploy/lib/load-stack.sh
. ./deploy/lib/load-stack.sh

echo "== [1/4] confirm the stack answers"
ready=0
for management in "$CTT_BLUE_MANAGEMENT" "$CTT_GREEN_MANAGEMENT"; do
  if curl -sf -o /dev/null "http://127.0.0.1:$management/actuator/health/readiness"; then
    ready=$((ready + 1))
  fi
done
if [ "$ready" -ne 2 ]; then
  echo "only $ready of 2 colours answer readiness; start the stack with ./deploy/staging/up.sh on the host" >&2
  exit 1
fi
serving="$(./deploy/staging/switch-colour.sh | awk '{print $1}')"
case "$serving" in
  blue) other=green ;;
  green) other=blue ;;
  *) echo "cannot tell which colour serves now: $serving" >&2; exit 1 ;;
esac
echo "== $serving serves, both colours ready"

echo "== [2/4] publish to $other (60 s window); $serving keeps serving until the switch"
./deploy/staging/release.sh "$other" --no-jar --observe 60

echo "== [3/4] roll back to $serving (60 s window), no rebuild"
./deploy/staging/release.sh --rollback --observe 60 --no-build

echo "== [4/4] restore the database from a dump"
./deploy/staging/restore-drill.sh --yes

echo "STAGING: green (release $serving → $other, rollback → $serving, restore; no breach in either window)"
