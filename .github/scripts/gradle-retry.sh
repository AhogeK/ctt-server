#!/usr/bin/env bash
#
# Runs Gradle, and repeats the exact same command once when the failure came
# from the outside world rather than from the commit.
#
# The motivating failure: a merge to master went red on `spotlessCheck` because
# Maven Central answered 429 to the buildscript classpath, minutes after the
# same commit passed on its pull request. Nothing in the tree changed; the
# runner's shared IP had simply exhausted the registry's budget. A red master
# that a rerun fixes is noise, and noise is what makes people stop reading the
# signal.
#
# The retry is deliberately narrow. Only these signatures qualify:
#   429 / Too Many Requests        registry rate limiting
#   Could not resolve / Could not GET   dependency download interrupted
#   Read timed out / Connection reset   transport-level noise
#   Could not connect to Ryuk      Testcontainers started before Docker was up
#
# A red test, a coverage gap or a formatting violation matches none of them and
# fails on the first attempt, as it should: retrying those would turn a
# deterministic failure into a green tick that nobody investigates.
#
# Usage: .github/scripts/gradle-retry.sh build jacocoTestReport

set -uo pipefail

log="${GRADLE_RETRY_LOG:-build/gradle.log}"
mkdir -p "$(dirname "$log")"

transient() {
  grep -Eq 'status code 429|Too Many Requests|Could not resolve|Could not GET|Read timed out|Connection reset|Could not connect to Ryuk' "$1"
}

run_once() {
  set -o pipefail
  ./gradlew "$@" 2>&1 | tee "$log"
  return "${PIPESTATUS[0]}"
}

if run_once "$@"; then
  exit 0
fi

if transient "$log"; then
  echo "" >&2
  echo "The failure looks external (registry or container runtime), not like a" >&2
  echo "defect in this commit. Retrying the same command once." >&2
  echo "" >&2
  run_once "$@"
  exit $?
fi

echo "" >&2
echo "The failure is not one a rerun would change; not retrying." >&2
exit 1
