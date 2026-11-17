#!/usr/bin/env bash
#
# Runs locally what CI runs, before the change leaves the machine.
#
# Two red pull requests were avoidable: a formatting violation and an image
# build whose context lacked a file. Both fail in seconds on the machine that
# wrote them, and both cost a full pipeline, a rerun and a round of reading
# logs. CI is the backstop for the change, not the first reader of it.
#
# Scope follows the change, so a documentation commit does not wait four
# minutes:
#   always          spotlessCheck — cheapest gate, most common red
#   code changed    ./gradlew build — tests, coverage thresholds, check
#   build inputs    docker build — Dockerfile, context, Gradle wiring
#   --tree          run every step regardless of what the range touches
#   --master        also: the range must carry no AI-only paths
#
# Usage: scripts/preflight.sh [--tree] [--master]
# Prints PREFLIGHT: green | PREFLIGHT: RED; exits non-zero on red.
set -uo pipefail

cd "$(dirname "$0")/.."
root=$(git rev-parse --show-toplevel)
cd "$root"

all_tree=0
master=0
for arg in "$@"; do
  case "$arg" in
    --tree) all_tree=1 ;;
    --master) master=1 ;;
    -h | --help)
      sed -n '3,20p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      echo "unknown argument: $arg" >&2
      exit 2
      ;;
  esac
done

if git rev-parse --verify --quiet origin/develop >/dev/null; then
  base=origin/develop
else
  base=origin/master
fi

changed=$(
  {
    git diff --name-only "$base"...HEAD
    git diff --name-only
    git diff --cached --name-only
    git ls-files --others --exclude-standard
  } | sort -u
)

matches() {
  printf '%s\n' "$changed" | grep -Eq "$1"
}

code_changed=0
build_changed=0
if [ "$all_tree" -eq 1 ]; then
  code_changed=1
  build_changed=1
else
  # Anything the compiler, the formatter or the test suite reads.
  matches '^(src/|gradle/|gradle\.properties$|build\.gradle\.kts$|settings\.gradle\.kts$)' && code_changed=1
  # Anything the image build copies or executes. `gradle-retry.sh` is in the
  # build stage, so a change to it changes the image.
  matches '^(src/|gradle/|gradle\.properties$|build\.gradle\.kts$|settings\.gradle\.kts$|Dockerfile$|\.dockerignore$|\.github/scripts/gradle-retry\.sh$)' &&
    build_changed=1
fi

step=""
run() {
  step=$1
  shift
  echo ""
  echo "== $step"
  if ! "$@"; then
    echo ""
    echo "PREFLIGHT: RED ($step)" >&2
    exit 1
  fi
}

# A branch bound for master may carry code and user-facing documentation, and
# nothing else: the AI workspace and the develop-only plans must not ship.
if [ "$master" -eq 1 ]; then
  blocked=$(printf '%s\n' "$changed" | grep -E '^(memory-bank/|\.plans/|\.agents/|dev-docs/|docs/plans/|AGENTS\.md$)' || true)
  if [ -n "$blocked" ]; then
    echo "AI-only paths must not reach master:" >&2
    printf '%s\n' "$blocked" >&2
    echo ""
    echo "PREFLIGHT: RED (master range)" >&2
    exit 1
  fi
  echo "checked: the range carries no AI-only paths"
fi

run "format" ./gradlew -q spotlessCheck

if [ "$code_changed" -eq 1 ]; then
  run "build and test" ./gradlew build
else
  echo ""
  echo "== build and test: skipped, no code in the range"
fi

if [ "$build_changed" -eq 1 ]; then
  run "image build" docker build --quiet -t ctt-preflight:local .
  # The image is a means to an end; leaving it behind would be a leak of its own.
  docker image rm -f ctt-preflight:local >/dev/null 2>&1 || true
else
  echo ""
  echo "== image build: skipped, no build inputs in the range"
fi

echo ""
echo "PREFLIGHT: green"
