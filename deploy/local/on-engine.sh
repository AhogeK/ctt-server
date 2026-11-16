#!/usr/bin/env bash
# Run a local deployment step where the Docker engine actually is.
#
#   ./deploy/local/on-engine.sh ./deploy/local/switch-colour.sh green
#   ./deploy/local/on-engine.sh ./deploy/local/release.sh blue --no-build --observe 300
#
# Docker Desktop keeps host networking inside its VM, so a script that probes
# 127.0.0.1:18080 from this shell reaches this machine — nothing listens there.
# The colours, the edge and Prometheus all live in the VM's loopback. Run the
# same script on a Linux host and none of this applies: it is already on the
# machine the engine runs on, and `on-engine.sh` would only add a copy of the
# repository it already has.
#
# The container is attached to the engine's network namespace and its socket,
# and the repository is mounted at the same absolute path — a compose file names
# its bind sources relative to itself, so a different mount point would silently
# bind the wrong directories.
set -euo pipefail

cd "$(dirname "$0")/../.."

if [ $# -eq 0 ]; then
  echo "usage: $0 <command> [args...]" >&2
  exit 2
fi

exec docker run --rm --network host \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$PWD:$PWD" -w "$PWD" \
  -e SECRET_GID="${SECRET_GID:-$(id -g)}" \
  --entrypoint sh docker:cli \
  -c 'apk add --no-cache bash curl >/dev/null 2>&1; exec "$@"' _ "$@"
