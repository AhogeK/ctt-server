#!/usr/bin/env bash
# Build the boot jar on the host and stage it for the local image.
#
# The in-container Gradle build cannot reach Maven Central through this host's
# proxy, so the jar is built outside the image and copied in — see the local
# Dockerfile. One place, because both `up.sh` (start the stack) and `release.sh`
# (replace one colour) need exactly this step and a second copy would drift.
set -euo pipefail

cd "$(dirname "$0")/../.."

./gradlew -q bootJar -x test

mkdir -p build/local-image

# The newest runnable jar. `ls | grep` would break on a filename with a space
# and hides the case where bootJar produced nothing at all.
jar=""
for candidate in build/libs/*.jar; do
  case "$candidate" in
    *-plain.jar) continue ;;
  esac
  if [ -z "$jar" ] || [ "$candidate" -nt "$jar" ]; then
    jar=$candidate
  fi
done
if [ -z "$jar" ]; then
  echo "bootJar produced no runnable jar under build/libs" >&2
  exit 1
fi

cp "$jar" build/local-image/app.jar
echo "      using $(basename "$jar")"
