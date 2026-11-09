#!/usr/bin/env bash
# One command for the local full stack (P0.0).
#
#   ./deploy/local/up.sh                 # postgres + redis + mailpit + app-blue + edge
#   ./deploy/local/up.sh --profile obs   # add Prometheus, Grafana and blackbox
#   ./deploy/local/up.sh --profile bluegreen --profile obs
#
# The jar is built on the host because the in-container Gradle build cannot
# reach Maven Central through this host's proxy.
set -euo pipefail

cd "$(dirname "$0")/../.."

# Keep the rendered group and the Compose group_add value identical even when
# the launcher is not the usual 1000-gid account.
export SECRET_GID="${SECRET_GID:-$(id -g)}"

echo "[1/4] rendering compose secrets"
./deploy/secrets/render.sh

echo "[2/4] building the boot jar"
./gradlew -q bootJar -x test

echo "[3/4] staging it for the local image"
mkdir -p build/local-image
jar="$(ls -t build/libs/*.jar | grep -v -- '-plain\.jar' | sed -n '1p')"
cp "$jar" build/local-image/app.jar
echo "      using $(basename "$jar")"

echo "[4/4] starting the stack"
exec docker compose -f docker-compose.yaml -f docker-compose.local.yaml "$@" up -d --build
